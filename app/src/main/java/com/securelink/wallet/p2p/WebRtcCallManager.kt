package com.securelink.wallet.p2p

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcCallManager(
    private val context: Context,
    private val signaling: ManualSignaling,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatus(stage: Stage, message: String)
        fun onLocalSignal(payload: String)
        fun onTracksChanged(local: VideoTrack?, remote: VideoTrack?)
    }

    enum class Stage { GATHERING, WAITING_FOR_PEER, CONNECTING, IN_CALL, FAILED }

    val eglContext: EglBase.Context get() = eglBase.eglBaseContext
    var localVideoTrack: VideoTrack? = null
        private set
    var remoteVideoTrack: VideoTrack? = null
        private set

    private val eglBase = EglBase.create()
    private val audioModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var pendingSignalType: CallSignal.Type? = null
    private var awaitingAnswer = false
    private val gatheredCandidates = mutableListOf<IceCandidatePayload>()

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun createOffer() {
        resetConnection()
        if (!prepareConnection()) return
        pendingSignalType = CallSignal.Type.OFFER
        listener.onStatus(Stage.GATHERING, "Creating a secure call invite. Keep the code private while you share it.")
        peerConnection?.createOffer(descriptionObserver { description ->
            peerConnection?.setLocalDescription(noOpObserver(), description)
        }, MediaConstraints())
    }

    fun applySignal(encodedSignal: String) {
        val signal = runCatching { signaling.decodeCallSignal(encodedSignal.trim()) }.getOrElse {
            listener.onStatus(Stage.FAILED, it.message ?: "The call payload could not be read")
            return
        }
        when (signal.type) {
            CallSignal.Type.OFFER -> acceptOffer(signal)
            CallSignal.Type.ANSWER -> acceptAnswer(signal)
        }
    }

    fun close() {
        resetConnection()
    }

    fun dispose() {
        close()
        factory.dispose()
        audioModule.release()
        eglBase.release()
    }

    private fun acceptOffer(signal: CallSignal) {
        resetConnection()
        if (!prepareConnection()) return
        pendingSignalType = CallSignal.Type.ANSWER
        listener.onStatus(Stage.GATHERING, "Creating an answer for the incoming invite.")
        peerConnection?.setRemoteDescription(setDescriptionObserver {
            addRemoteCandidates(signal)
            peerConnection?.createAnswer(descriptionObserver { answer ->
                peerConnection?.setLocalDescription(noOpObserver(), answer)
            }, MediaConstraints())
        }, SessionDescription(SessionDescription.Type.OFFER, signal.sdp))
    }

    private fun acceptAnswer(signal: CallSignal) {
        val connection = peerConnection
        if (connection == null || !awaitingAnswer) {
            listener.onStatus(Stage.FAILED, "Create an invite before applying an answer.")
            return
        }
        awaitingAnswer = false
        listener.onStatus(Stage.CONNECTING, "Answer received. Connecting encrypted media…")
        connection.setRemoteDescription(setDescriptionObserver {
            addRemoteCandidates(signal)
        }, SessionDescription(SessionDescription.Type.ANSWER, signal.sdp))
    }

    private fun prepareConnection(): Boolean = runCatching {
        val connection = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(
                listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()),
            ),
            object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = Unit
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                    if (newState == PeerConnection.IceGatheringState.COMPLETE) publishLocalSignal()
                }
                override fun onIceCandidate(candidate: IceCandidate) {
                    gatheredCandidates += IceCandidatePayload(candidate.sdpMid.orEmpty(), candidate.sdpMLineIndex, candidate.sdp)
                }
                override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
                override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
                override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
                override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<org.webrtc.MediaStream>) {
                    remoteVideoTrack = receiver.track() as? VideoTrack
                    listener.onTracksChanged(localVideoTrack, remoteVideoTrack)
                }
                override fun onTrack(transceiver: RtpTransceiver) {
                    remoteVideoTrack = transceiver.receiver.track() as? VideoTrack
                    listener.onTracksChanged(localVideoTrack, remoteVideoTrack)
                }
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> listener.onStatus(Stage.IN_CALL, "Encrypted audio/video connection is live.")
                        PeerConnection.PeerConnectionState.FAILED -> listener.onStatus(Stage.FAILED, "The peer connection failed. Check both devices and try a new invite.")
                        PeerConnection.PeerConnectionState.CONNECTING -> listener.onStatus(Stage.CONNECTING, "Connecting encrypted media…")
                        else -> Unit
                    }
                }
            },
        ) ?: error("Unable to create peer connection")
        peerConnection = connection
        startLocalMedia(connection)
    }.fold(
        onSuccess = { true },
        onFailure = {
            listener.onStatus(Stage.FAILED, it.message ?: "Unable to start camera or microphone")
            false
        },
    )

    private fun startLocalMedia(connection: PeerConnection) {
        val capturer = createCameraCapturer() ?: error("No front camera is available")
        val helper = SurfaceTextureHelper.create("SecureLinkCapture", eglBase.eglBaseContext)
        val source = factory.createVideoSource(false)
        capturer.initialize(helper, context, source.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        val video = factory.createVideoTrack("securelink-video", source)
        val audio = factory.createAudioTrack(
            "securelink-audio",
            factory.createAudioSource(MediaConstraints()).also { audioSource = it },
        )
        connection.addTrack(video, listOf("securelink-stream"))
        connection.addTrack(audio, listOf("securelink-stream"))
        videoCapturer = capturer
        videoSource = source
        localVideoTrack = video
        listener.onTracksChanged(video, null)
    }

    private fun publishLocalSignal() {
        val connection = peerConnection ?: return
        val type = pendingSignalType ?: return
        val sdp = connection.localDescription?.description ?: return
        pendingSignalType = null
        awaitingAnswer = type == CallSignal.Type.OFFER
        listener.onLocalSignal(signaling.encodeCallSignal(CallSignal(type, sdp, gatheredCandidates.toList())))
        listener.onStatus(Stage.WAITING_FOR_PEER, if (type == CallSignal.Type.OFFER) "Invite ready. Send it to the other SecureLink device, then paste its answer here." else "Answer ready. Send it back to the caller to complete the connection.")
    }

    private fun addRemoteCandidates(signal: CallSignal) {
        signal.candidates.forEach { candidate ->
            peerConnection?.addIceCandidate(IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate))
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator: CameraEnumerator = Camera2Enumerator(context)
        val cameraName = enumerator.deviceNames.firstOrNull(enumerator::isFrontFacing)
            ?: enumerator.deviceNames.firstOrNull()
            ?: return null
        return enumerator.createCapturer(cameraName, null)
    }

    private fun resetConnection() {
        localVideoTrack?.dispose()
        remoteVideoTrack?.dispose()
        localVideoTrack = null
        remoteVideoTrack = null
        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        peerConnection?.dispose()
        peerConnection = null
        videoCapturer = null
        videoSource = null
        audioSource = null
        pendingSignalType = null
        awaitingAnswer = false
        gatheredCandidates.clear()
        listener.onTracksChanged(null, null)
    }

    private fun descriptionObserver(onSuccess: (SessionDescription) -> Unit): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = onSuccess(description)
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = listener.onStatus(Stage.FAILED, error)
        override fun onSetFailure(error: String) = listener.onStatus(Stage.FAILED, error)
    }

    private fun setDescriptionObserver(onSuccess: () -> Unit): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = onSuccess()
        override fun onCreateFailure(error: String) = listener.onStatus(Stage.FAILED, error)
        override fun onSetFailure(error: String) = listener.onStatus(Stage.FAILED, error)
    }

    private fun noOpObserver(): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = listener.onStatus(Stage.FAILED, error)
        override fun onSetFailure(error: String) = listener.onStatus(Stage.FAILED, error)
    }
}
