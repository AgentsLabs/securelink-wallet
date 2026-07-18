package com.securelink.wallet

data class Contact(
    val id: Long,
    val name: String,
    val deviceId: String,
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
    val p2pStatus: String = "Manual QR offer/answer ready",
)
