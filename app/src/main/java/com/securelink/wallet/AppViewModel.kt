package com.securelink.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securelink.wallet.data.SecureLinkDatabase
import com.securelink.wallet.p2p.ManualSignaling
import com.securelink.wallet.p2p.RelayClient
import com.securelink.wallet.p2p.WebRtcCallManager
import com.securelink.wallet.security.PasswordGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SecureLinkDatabase(application)
    private val passwordGenerator = PasswordGenerator()
    private val signaling = ManualSignaling()
    private val relayClient = RelayClient()
    private var activeCallDirection: String? = null
    private var callOutcomeRecorded = false
    val callManager = WebRtcCallManager(application.applicationContext, signaling, object : WebRtcCallManager.Listener {
        override fun onStatus(stage: WebRtcCallManager.Stage, message: String) {
            if (stage == WebRtcCallManager.Stage.IN_CALL || stage == WebRtcCallManager.Stage.FAILED) {
                recordCallOutcome(if (stage == WebRtcCallManager.Stage.IN_CALL) "Connected" else "Failed")
            }
            _state.value = _state.value.copy(callStage = stage.toAppStage(), p2pStatus = message)
        }

        override fun onLocalSignal(payload: String) {
            val contact = selectedContact() ?: return
            val contactPayload = signaling.encodeContactCallSignal(contact.deviceId, signaling.decodeCallSignal(payload))
            database.addMessage(contact.id, contactPayload, true, MessageType.CALL_SIGNAL)
            _state.value = loadState(contact.id, _state.value.vaultUnlocked).copy(localCallPayload = contactPayload)
        }

        override fun onTracksChanged(local: org.webrtc.VideoTrack?, remote: org.webrtc.VideoTrack?) = Unit
    })
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<AppState> = _state

    fun selectContact(contactId: Long) {
        _state.value = loadState(contactId, _state.value.vaultUnlocked)
    }

    fun addContact(name: String, pairingCode: String) {
        val cleaned = name.trim()
        if (cleaned.isBlank() || pairingCode.isBlank()) return
        val invite = runCatching { signaling.parseInvite(pairingCode.trim()) }.getOrElse {
            _state.value = _state.value.copy(contactNotice = "That pairing code is invalid.")
            return
        }
        val deviceId = invite.deviceId
        if (deviceId.isNullOrBlank()) {
            _state.value = _state.value.copy(contactNotice = "This is an older pairing code. Ask the contact to share a new code.")
            return
        }
        if (deviceId == database.localDeviceId()) {
            _state.value = _state.value.copy(contactNotice = "You cannot pair this device with itself.")
            return
        }
        if (_state.value.contacts.any { it.deviceId == deviceId }) {
            _state.value = _state.value.copy(contactNotice = "This device is already in your contacts.")
            return
        }
        database.addContact(cleaned, deviceId, invite.relayToken)
        _state.value = loadState(_state.value.selectedContactId, _state.value.vaultUnlocked)
            .copy(contactNotice = "Paired with ${invite.deviceName}. Compare the fingerprint before calling.")
    }

    fun sendMessage(text: String) {
        val contactId = _state.value.selectedContactId ?: _state.value.contacts.firstOrNull()?.id ?: return
        if (text.isBlank()) return
        database.addMessage(contactId, text.trim(), true)
        _state.value = loadState(contactId, _state.value.vaultUnlocked)
    }

    fun addGeneratedCredential() {
        val generatedPassword = passwordGenerator.generate()
        database.addCredential(
            label = "Generated login ${_state.value.credentials.size + 1}",
            username = "user@example.com",
            password = generatedPassword,
            url = "https://example.com",
        )
        _state.value = loadState(_state.value.selectedContactId, _state.value.vaultUnlocked)
    }

    fun addDemoDocument() {
        database.addDocument(
            title = "Insurance Card ${_state.value.documents.size + 1}",
            kind = "Health",
            note = "Demo wallet item stored in the local database.",
        )
        _state.value = loadState(_state.value.selectedContactId, _state.value.vaultUnlocked)
    }

    fun updateMediaPermission(granted: Boolean) {
        _state.value = _state.value.copy(mediaPermissionGranted = granted)
    }

    fun unlockVault() {
        _state.value = _state.value.copy(
            credentials = database.credentials(),
            vaultUnlocked = true,
            vaultMessage = null,
        )
    }

    fun lockVault() {
        _state.value = _state.value.copy(
            credentials = database.credentials().map { it.copy(password = maskedPassword(it.password)) },
            vaultUnlocked = false,
        )
    }

    fun reportVaultAuthenticationUnavailable() {
        _state.value = _state.value.copy(
            vaultMessage = "Set a secure screen lock or enroll a strong biometric to unlock saved credentials.",
        )
    }

    fun createCallInvite() {
        if (selectedContact() == null) {
            _state.value = _state.value.copy(p2pStatus = "Select a verified contact before creating a call invite.", callStage = CallStage.Failed)
            return
        }
        activeCallDirection = "Outgoing"
        callOutcomeRecorded = false
        _state.value = _state.value.copy(localCallPayload = null, callStage = CallStage.Gathering)
        callManager.createOffer()
    }

    fun setRelayUrl(url: String) {
        database.setRelayUrl(url)
        _state.value = _state.value.copy(relayUrl = database.relayUrl())
        val relayUrl = database.relayUrl()
        if (relayUrl.isNotBlank()) viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { relayClient.register(relayUrl, database.localDeviceId(), database.localRelayToken()) } }
                .onSuccess { _state.value = _state.value.copy(p2pStatus = "Relay inbox registered.") }
                .onFailure { _state.value = _state.value.copy(p2pStatus = "Relay registration failed: ${it.message}") }
        }
    }

    fun deliverLocalSignal() {
        val contact = selectedContact() ?: return
        val payload = _state.value.localCallPayload ?: return
        val relayUrl = database.relayUrl()
        val relayToken = contact.relayToken
        if (relayUrl.isBlank() || relayToken.isNullOrBlank()) {
            _state.value = _state.value.copy(p2pStatus = "Add a relay URL and pair with a current pairing code first.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { relayClient.deliver(relayUrl, contact.deviceId, database.localDeviceId(), relayToken, payload) }
            _state.value = _state.value.copy(p2pStatus = result.fold({ "Signal delivered through relay." }, { "Relay delivery failed: ${it.message}" }))
        }
    }

    fun fetchRelaySignals() {
        val relayUrl = database.relayUrl()
        if (relayUrl.isBlank()) return
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                relayClient.register(relayUrl, database.localDeviceId(), database.localRelayToken())
                relayClient.fetch(relayUrl, database.localDeviceId(), database.localRelayToken())
            } }
            result.onSuccess { signals ->
                if (signals.isEmpty()) {
                    _state.value = _state.value.copy(p2pStatus = "No relay signals waiting.")
                } else {
                    var applied = 0
                    signals.forEach { signal ->
                        val contact = _state.value.contacts.firstOrNull { it.deviceId == signal.senderDeviceId } ?: return@forEach
                        database.addMessage(contact.id, signal.payload, false, MessageType.CALL_SIGNAL)
                        applied++
                    }
                    _state.value = loadState(_state.value.selectedContactId, _state.value.vaultUnlocked).copy(
                        p2pStatus = if (applied == 0) "Ignored relay signals from unpaired devices." else "Fetched $applied signal(s). Review and apply one when ready.",
                    )
                }
            }.onFailure { _state.value = _state.value.copy(p2pStatus = "Relay fetch failed: ${it.message}") }
        }
    }

    fun applyCallSignal(payload: String) {
        processCallSignal(payload, persistIncoming = true)
    }

    fun applyQueuedCallSignal(payload: String) {
        processCallSignal(payload, persistIncoming = false)
    }

    private fun processCallSignal(payload: String, persistIncoming: Boolean) {
        if (payload.isBlank()) return
        val contact = selectedContact() ?: return
        val rawSignal = runCatching {
            if (payload.trim().startsWith("sl-contact-")) {
                signaling.decodeContactCallSignal(payload.trim(), database.localDeviceId()).callSignal.let(signaling::encodeCallSignal)
            } else {
                payload.trim()
            }
        }.getOrElse {
            _state.value = _state.value.copy(callStage = CallStage.Failed, p2pStatus = it.message ?: "Unable to validate the call signal")
            return
        }
        activeCallDirection = "Incoming"
        callOutcomeRecorded = false
        if (persistIncoming) database.addMessage(contact.id, payload.trim(), false, MessageType.CALL_SIGNAL)
        _state.value = _state.value.copy(localCallPayload = null, callStage = CallStage.Connecting)
        callManager.applySignal(rawSignal)
    }

    fun endCall() {
        recordCallOutcome("Ended")
        _state.value = _state.value.copy(
            localCallPayload = null,
            callStage = CallStage.Idle,
            p2pStatus = "Ready to create a private call invite",
        )
        callManager.close()
    }

    private fun loadState(selectedContactId: Long? = null, vaultUnlocked: Boolean = false): AppState {
        val contacts = database.contacts()
        val activeContact = selectedContactId ?: contacts.firstOrNull()?.id
        return AppState(
            contacts = contacts,
            selectedContactId = activeContact,
            messages = activeContact?.let { database.messages(it) }.orEmpty(),
            callHistory = activeContact?.let { database.callHistory(it) }.orEmpty(),
            documents = database.documents(),
            credentials = database.credentials().map { credential ->
                credential.copy(password = if (vaultUnlocked) credential.password else maskedPassword(credential.password))
            },
            vaultUnlocked = vaultUnlocked,
            localPairingCode = signaling.createInvite("SecureLink device", database.localDeviceId(), database.localRelayToken()).publicCode,
            relayUrl = database.relayUrl(),
        )
    }

    private fun maskedPassword(value: String): String = if (value.isBlank()) "" else "********"

    private fun selectedContact(): Contact? = _state.value.contacts.firstOrNull { it.id == _state.value.selectedContactId }
        ?: _state.value.contacts.firstOrNull()

    private fun recordCallOutcome(outcome: String) {
        val contact = selectedContact() ?: return
        val direction = activeCallDirection ?: return
        if (callOutcomeRecorded) return
        database.addCallHistory(contact.id, direction, outcome)
        callOutcomeRecorded = true
    }

    override fun onCleared() {
        callManager.dispose()
        super.onCleared()
    }

    private fun WebRtcCallManager.Stage.toAppStage(): CallStage = when (this) {
        WebRtcCallManager.Stage.GATHERING -> CallStage.Gathering
        WebRtcCallManager.Stage.WAITING_FOR_PEER -> CallStage.WaitingForPeer
        WebRtcCallManager.Stage.CONNECTING -> CallStage.Connecting
        WebRtcCallManager.Stage.IN_CALL -> CallStage.InCall
        WebRtcCallManager.Stage.FAILED -> CallStage.Failed
    }
}
