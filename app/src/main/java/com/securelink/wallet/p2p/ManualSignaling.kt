package com.securelink.wallet.p2p

import java.util.Base64

data class PeerInvite(
    val deviceName: String,
    val publicCode: String,
    val createdAtMillis: Long,
)

data class IceCandidatePayload(
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val candidate: String,
)

data class CallSignal(
    val type: Type,
    val sdp: String,
    val candidates: List<IceCandidatePayload>,
) {
    enum class Type { OFFER, ANSWER }
}

class ManualSignaling {
    fun createInvite(deviceName: String, nowMillis: Long = System.currentTimeMillis()): PeerInvite {
        val raw = "$deviceName:$nowMillis"
        return PeerInvite(
            deviceName = deviceName,
            publicCode = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray()),
            createdAtMillis = nowMillis,
        )
    }

    fun createRoomCode(contactDeviceId: String, nowMillis: Long = System.currentTimeMillis()): String {
        val raw = "$contactDeviceId:$nowMillis"
        return "sl-" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray()).take(18)
    }

    fun parseInvite(publicCode: String): PeerInvite {
        val decoded = String(Base64.getUrlDecoder().decode(publicCode))
        val parts = decoded.split(":")
        require(parts.size == 2) { "Invite code is invalid" }
        return PeerInvite(parts[0], publicCode, parts[1].toLong())
    }

    /**
     * Encodes a complete WebRTC offer/answer for transfer through a trusted channel.
     * WebRTC protects media with DTLS-SRTP; callers should still keep this payload private.
     */
    fun encodeCallSignal(signal: CallSignal): String {
        val body = buildString {
            append("v1|").append(signal.type.name).append('\n')
            append(encodePart(signal.sdp)).append('\n')
            signal.candidates.forEach { candidate ->
                append(encodePart(candidate.sdpMid))
                    .append('|').append(candidate.sdpMLineIndex)
                    .append('|').append(encodePart(candidate.candidate))
                    .append('\n')
            }
        }
        return "sl-call-" + encodePart(body)
    }

    fun decodeCallSignal(payload: String): CallSignal {
        require(payload.startsWith("sl-call-")) { "This is not a SecureLink call payload" }
        val lines = String(Base64.getUrlDecoder().decode(payload.removePrefix("sl-call-"))).lineSequence().toList()
        require(lines.size >= 2) { "Call payload is incomplete" }
        val header = lines.first().split("|")
        require(header.size == 2 && header[0] == "v1") { "Unsupported call payload" }
        val type = runCatching { CallSignal.Type.valueOf(header[1]) }.getOrElse { error("Invalid call payload type") }
        val candidates = lines.drop(2).filter { it.isNotBlank() }.map { line ->
            val parts = line.split("|", limit = 3)
            require(parts.size == 3) { "Invalid ICE candidate" }
            IceCandidatePayload(decodePart(parts[0]), parts[1].toInt(), decodePart(parts[2]))
        }
        return CallSignal(type, decodePart(lines[1]), candidates)
    }

    private fun encodePart(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodePart(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
