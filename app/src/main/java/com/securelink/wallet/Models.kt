package com.securelink.wallet

data class Contact(
    val id: Long,
    val name: String,
    val deviceId: String,
    val trustLevel: String = "Verified device",
)

data class ChatMessage(
    val id: Long,
    val contactId: Long,
    val text: String,
    val sentByMe: Boolean,
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
    val documents: List<WalletDocument> = emptyList(),
    val credentials: List<CredentialEntry> = emptyList(),
    val p2pStatus: String = "Ready to create a private call invite",
    val localCallPayload: String? = null,
    val callStage: CallStage = CallStage.Idle,
    val mediaPermissionGranted: Boolean = false,
    val vaultUnlocked: Boolean = false,
    val vaultMessage: String? = null,
)

enum class CallStage {
    Idle,
    Gathering,
    WaitingForPeer,
    Connecting,
    InCall,
    Failed,
}
