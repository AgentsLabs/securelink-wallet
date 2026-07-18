package com.securelink.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.securelink.wallet.data.SecureLinkDatabase
import com.securelink.wallet.p2p.ManualSignaling
import com.securelink.wallet.security.PasswordGenerator
import com.securelink.wallet.security.VaultCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Base64

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SecureLinkDatabase(application)
    private val passwordGenerator = PasswordGenerator()
    private val vaultCrypto = VaultCrypto()
    private val signaling = ManualSignaling()
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<AppState> = _state

    fun selectContact(contactId: Long) {
        _state.value = loadState(contactId)
    }

    fun sendMessage(text: String) {
        val contactId = _state.value.selectedContactId ?: _state.value.contacts.firstOrNull()?.id ?: return
        if (text.isBlank()) return
        database.addMessage(contactId, text.trim(), true)
        _state.value = loadState(contactId)
    }

    fun addGeneratedCredential() {
        val generatedPassword = passwordGenerator.generate()
        val encryptedPassword = "vault:" + Base64.getEncoder().encodeToString(vaultCrypto.encrypt(generatedPassword))
        database.addCredential(
            label = "Generated login ${_state.value.credentials.size + 1}",
            username = "user@example.com",
            password = encryptedPassword,
            url = "https://example.com",
        )
        _state.value = loadState(_state.value.selectedContactId)
    }

    fun addDemoDocument() {
        database.addDocument(
            title = "Insurance Card ${_state.value.documents.size + 1}",
            kind = "Health",
            note = "Demo wallet item stored in the local database.",
        )
        _state.value = loadState(_state.value.selectedContactId)
    }

    fun refreshInvite() {
        val invite = signaling.createInvite("SecureLink Android")
        _state.value = _state.value.copy(p2pStatus = "Share invite: ${invite.publicCode}")
    }

    private fun loadState(selectedContactId: Long? = null): AppState {
        val contacts = database.contacts()
        val activeContact = selectedContactId ?: contacts.firstOrNull()?.id
        return AppState(
            contacts = contacts,
            selectedContactId = activeContact,
            messages = activeContact?.let { database.messages(it) }.orEmpty(),
            documents = database.documents(),
            credentials = database.credentials().map { it.copy(password = displayPassword(it.password)) },
        )
    }

    private fun displayPassword(value: String): String {
        if (!value.startsWith("vault:")) return value
        return runCatching {
            val payload = Base64.getDecoder().decode(value.removePrefix("vault:"))
            vaultCrypto.decrypt(payload)
        }.getOrElse { "Unable to decrypt on this device" }
    }
}
