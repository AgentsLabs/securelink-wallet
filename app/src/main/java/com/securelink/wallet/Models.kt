package com.securelink.wallet

data class Contact(
    val id: Long,
    val name: String,
    val deviceId: String,
    val identityFingerprint: String,
    val relayToken: String? = null,
    val trustLevel: String = "Paired device",
)

enum class MessageType { TEXT, CALL_SIGNAL }

data class ChatMessage(
    val id: Long,
    val contactId: Long,
    val type: MessageType,
    val text: String,
    val sentByMe: Boolean,
    val timestampMillis: Long,
)

data class CallHistoryEntry(
    val id: Long,
    val contactId: Long,
    val direction: String,
    val outcome: String,
    val timestampMillis: Long,
)

data class WalletDocument(
    val id: Long,
    val title: String,
    val kind: String,
    val note: String,
)

data class CredentialEntry(
    val id: Long,
    val label: String,
    val username: String,
    val password: String,
    val url: String,
)

data class AppState(
    val contacts: List<Contact> = emptyList(),
    val selectedContactId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val callHistory: List<CallHistoryEntry> = emptyList(),
    val documents: List<WalletDocument> = emptyList(),
    val credentials: List<CredentialEntry> = emptyList(),
    val p2pStatus: String = "Ready to create a private call invite",
    val localCallPayload: String? = null,
    val callStage: CallStage = CallStage.Idle,
    val mediaPermissionGranted: Boolean = false,
    val vaultUnlocked: Boolean = false,
    val vaultMessage: String? = null,
    val localPairingCode: String = "",
    val contactNotice: String? = null,
    val relayUrl: String = "",
)

enum class CallStage {
    Idle,
    Gathering,
    WaitingForPeer,
    Connecting,
    InCall,
    Failed,
}
