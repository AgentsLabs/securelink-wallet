package com.securelink.wallet.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.securelink.wallet.ChatMessage
import com.securelink.wallet.CallHistoryEntry
import com.securelink.wallet.Contact
import com.securelink.wallet.CredentialEntry
import com.securelink.wallet.WalletDocument
import com.securelink.wallet.MessageType
import com.securelink.wallet.security.VaultCrypto
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class SecureLinkDatabase(context: Context) : SQLiteOpenHelper(context, "securelink_wallet.db", null, 5) {
    private val crypto = VaultCrypto()
    private val identityPreferences = context.getSharedPreferences("securelink_identity", Context.MODE_PRIVATE)

    /** Stable identifier used as the destination of a contact-bound signal. */
    fun localDeviceId(): String = identityPreferences.getString("device_id", null)
        ?: "device-${UUID.randomUUID()}".also { identityPreferences.edit().putString("device_id", it).apply() }

    /** The inbox token is a bearer credential, so persist it with the Keystore key. */
    fun localRelayToken(): String {
        val stored = identityPreferences.getString("relay_token", null)
        if (!stored.isNullOrBlank()) {
            if (stored.startsWith("enc:")) return open(stored)
            // Upgrade tokens written by the first relay prototype without changing
            // the inbox identity that paired contacts already know.
            identityPreferences.edit().putString("relay_token", seal(stored)).apply()
            return stored
        }
        val generated = ByteArray(32).also(SecureRandom()::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        identityPreferences.edit().putString("relay_token", seal(generated)).apply()
        return generated
    }

    fun relayUrl(): String = identityPreferences.getString("relay_url", "") ?: ""

    fun setRelayUrl(value: String) = identityPreferences.edit().putString("relay_url", value.trim().removeSuffix("/")).apply()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE contacts(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, device_id TEXT NOT NULL, identity_fingerprint TEXT NOT NULL, relay_token TEXT)")
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, contact_id INTEGER NOT NULL, type TEXT NOT NULL DEFAULT 'TEXT', text TEXT NOT NULL, sent_by_me INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE call_history(id INTEGER PRIMARY KEY AUTOINCREMENT, contact_id INTEGER NOT NULL, direction TEXT NOT NULL, outcome TEXT NOT NULL, timestamp_ms INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE documents(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, kind TEXT NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE credentials(id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, url TEXT NOT NULL)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.delete("credentials", "password NOT LIKE ?", arrayOf("vault:%"))
        }
        if (oldVersion < 3) migrateToEncryptedFields(db)
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE contacts ADD COLUMN identity_fingerprint TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN type TEXT NOT NULL DEFAULT 'TEXT'")
            db.execSQL("CREATE TABLE call_history(id INTEGER PRIMARY KEY AUTOINCREMENT, contact_id INTEGER NOT NULL, direction TEXT NOT NULL, outcome TEXT NOT NULL, timestamp_ms INTEGER NOT NULL)")
            db.rawQuery("SELECT id, device_id FROM contacts", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val deviceId = open(cursor.getString(1))
                    db.update("contacts", ContentValues().apply { put("identity_fingerprint", seal(fingerprint(deviceId))) }, "id = ?", arrayOf(cursor.getLong(0).toString()))
                }
            }
        }
        if (oldVersion < 5) db.execSQL("ALTER TABLE contacts ADD COLUMN relay_token TEXT")
    }

    fun contacts(): List<Contact> = readableDatabase.rawQuery("SELECT id, name, device_id, identity_fingerprint, relay_token FROM contacts ORDER BY name", null).use { c ->
        buildList {
            while (c.moveToNext()) add(Contact(c.getLong(0), open(c.getString(1)), open(c.getString(2)), open(c.getString(3)), c.getString(4)?.let(::open)))
        }
    }.sortedBy { it.name }

    fun messages(contactId: Long): List<ChatMessage> = readableDatabase.rawQuery(
        "SELECT id, contact_id, type, text, sent_by_me, timestamp_ms FROM messages WHERE contact_id = ? ORDER BY id",
        arrayOf(contactId.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(ChatMessage(c.getLong(0), c.getLong(1), MessageType.valueOf(c.getString(2)), open(c.getString(3)), c.getInt(4) == 1, c.getLong(5)))
        }
    }

    fun callHistory(contactId: Long): List<CallHistoryEntry> = readableDatabase.rawQuery(
        "SELECT id, contact_id, direction, outcome, timestamp_ms FROM call_history WHERE contact_id = ? ORDER BY id DESC",
        arrayOf(contactId.toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(CallHistoryEntry(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getLong(4))) } }

    fun documents(): List<WalletDocument> = readableDatabase.rawQuery("SELECT id, title, kind, note FROM documents ORDER BY id DESC", null).use { c ->
        buildList {
            while (c.moveToNext()) add(WalletDocument(c.getLong(0), open(c.getString(1)), open(c.getString(2)), open(c.getString(3))))
        }
    }

    fun credentials(): List<CredentialEntry> = readableDatabase.rawQuery("SELECT id, label, username, password, url FROM credentials ORDER BY label", null).use { c ->
        buildList {
            while (c.moveToNext()) add(CredentialEntry(c.getLong(0), open(c.getString(1)), open(c.getString(2)), open(c.getString(3)), open(c.getString(4))))
        }
    }.sortedBy { it.label }

    fun addMessage(contactId: Long, text: String, sentByMe: Boolean, type: MessageType = MessageType.TEXT) {
        writableDatabase.insert("messages", null, ContentValues().apply {
            put("contact_id", contactId)
            put("type", type.name)
            put("text", seal(text))
            put("sent_by_me", if (sentByMe) 1 else 0)
            put("timestamp_ms", System.currentTimeMillis())
        })
    }

    fun addContact(name: String, deviceId: String, relayToken: String?) {
        writableDatabase.insert("contacts", null, ContentValues().apply {
            put("name", seal(name))
            put("device_id", seal(deviceId))
            put("identity_fingerprint", seal(fingerprint(deviceId)))
            put("relay_token", relayToken?.let(::seal))
        })
    }

    fun addCallHistory(contactId: Long, direction: String, outcome: String) {
        writableDatabase.insert("call_history", null, ContentValues().apply {
            put("contact_id", contactId)
            put("direction", direction)
            put("outcome", outcome)
            put("timestamp_ms", System.currentTimeMillis())
        })
    }

    fun addCredential(label: String, username: String, password: String, url: String) {
        writableDatabase.insert("credentials", null, ContentValues().apply {
            put("label", seal(label))
            put("username", seal(username))
            put("password", seal(password))
            put("url", seal(url))
        })
    }

    fun addDocument(title: String, kind: String, note: String) {
        writableDatabase.insert("documents", null, ContentValues().apply {
            put("title", seal(title))
            put("kind", seal(kind))
            put("note", seal(note))
        })
    }

    private fun seed(db: SQLiteDatabase) {
        db.insert("contacts", null, ContentValues().apply {
            put("name", seal("Asha"))
            put("device_id", seal("peer-asha-demo"))
            put("identity_fingerprint", seal(fingerprint("peer-asha-demo")))
        })
        db.insert("contacts", null, ContentValues().apply {
            put("name", seal("Rohan"))
            put("device_id", seal("peer-rohan-demo"))
            put("identity_fingerprint", seal(fingerprint("peer-rohan-demo")))
        })
        db.insert("messages", null, ContentValues().apply {
            put("contact_id", 1)
            put("type", MessageType.TEXT.name)
            put("text", seal("Secure local-first chat is ready."))
            put("sent_by_me", 0)
            put("timestamp_ms", System.currentTimeMillis())
        })
        db.insert("messages", null, ContentValues().apply {
            put("contact_id", 2)
            put("type", MessageType.TEXT.name)
            put("text", seal("Tap Calls to create a direct call invite."))
            put("sent_by_me", 0)
            put("timestamp_ms", System.currentTimeMillis())
        })
        db.insert("documents", null, ContentValues().apply {
            put("title", seal("Passport"))
            put("kind", seal("Identity"))
            put("note", seal("Stored locally; encrypted file import is next."))
        })
    }

    private fun migrateToEncryptedFields(db: SQLiteDatabase) {
        migrateRows(db, "contacts", listOf("name", "device_id"))
        migrateRows(db, "messages", listOf("text"))
        migrateRows(db, "documents", listOf("title", "kind", "note"))
        migrateRows(db, "credentials", listOf("label", "username", "password", "url"))
    }

    private fun migrateRows(db: SQLiteDatabase, table: String, columns: List<String>) {
        db.query(table, arrayOf("id", *columns.toTypedArray()), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues()
                columns.forEachIndexed { index, column ->
                    values.put(column, seal(openLegacy(cursor.getString(index + 1))))
                }
                db.update(table, values, "id = ?", arrayOf(cursor.getLong(0).toString()))
            }
        }
    }

    private fun seal(value: String): String = "enc:" + Base64.getEncoder().encodeToString(crypto.encrypt(value))

    private fun fingerprint(deviceId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(deviceId.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0').uppercase() }
        .take(12)

    private fun open(value: String): String = runCatching {
        if (!value.startsWith("enc:")) value else crypto.decrypt(Base64.getDecoder().decode(value.removePrefix("enc:")))
    }.getOrDefault("Unable to decrypt on this device")

    private fun openLegacy(value: String): String = runCatching {
        if (!value.startsWith("vault:")) value else crypto.decrypt(Base64.getDecoder().decode(value.removePrefix("vault:")))
    }.getOrDefault(value)
}
