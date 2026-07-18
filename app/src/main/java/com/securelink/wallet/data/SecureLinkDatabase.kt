package com.securelink.wallet.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.securelink.wallet.ChatMessage
import com.securelink.wallet.Contact
import com.securelink.wallet.CredentialEntry
import com.securelink.wallet.WalletDocument

class SecureLinkDatabase(context: Context) : SQLiteOpenHelper(context, "securelink_wallet.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE contacts(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, device_id TEXT NOT NULL)")
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, contact_id INTEGER NOT NULL, text TEXT NOT NULL, sent_by_me INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE documents(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, kind TEXT NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE credentials(id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, url TEXT NOT NULL)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.delete("credentials", "password NOT LIKE ?", arrayOf("vault:%"))
        }
    }

    fun contacts(): List<Contact> = readableDatabase.rawQuery("SELECT id, name, device_id FROM contacts ORDER BY name", null).use { c ->
        buildList {
            while (c.moveToNext()) add(Contact(c.getLong(0), c.getString(1), c.getString(2)))
        }
    }

    fun messages(contactId: Long): List<ChatMessage> = readableDatabase.rawQuery(
        "SELECT id, contact_id, text, sent_by_me, timestamp_ms FROM messages WHERE contact_id = ? ORDER BY id",
        arrayOf(contactId.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(ChatMessage(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3) == 1, c.getLong(4)))
        }
    }

    fun documents(): List<WalletDocument> = readableDatabase.rawQuery("SELECT id, title, kind, note FROM documents ORDER BY id DESC", null).use { c ->
        buildList {
            while (c.moveToNext()) add(WalletDocument(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
        }
    }

    fun credentials(): List<CredentialEntry> = readableDatabase.rawQuery("SELECT id, label, username, password, url FROM credentials ORDER BY label", null).use { c ->
        buildList {
            while (c.moveToNext()) add(CredentialEntry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4)))
        }
    }

    fun addMessage(contactId: Long, text: String, sentByMe: Boolean) {
        writableDatabase.insert("messages", null, ContentValues().apply {
            put("contact_id", contactId)
            put("text", text)
            put("sent_by_me", if (sentByMe) 1 else 0)
            put("timestamp_ms", System.currentTimeMillis())
        })
    }

    fun addContact(name: String, deviceId: String) {
        writableDatabase.insert("contacts", null, ContentValues().apply {
            put("name", name)
            put("device_id", deviceId)
        })
    }

    fun addCredential(label: String, username: String, password: String, url: String) {
        writableDatabase.insert("credentials", null, ContentValues().apply {
            put("label", label)
            put("username", username)
            put("password", password)
            put("url", url)
        })
    }

    fun addDocument(title: String, kind: String, note: String) {
        writableDatabase.insert("documents", null, ContentValues().apply {
            put("title", title)
            put("kind", kind)
            put("note", note)
        })
    }

    private fun seed(db: SQLiteDatabase) {
        db.insert("contacts", null, ContentValues().apply {
            put("name", "Asha")
            put("device_id", "peer-asha-demo")
        })
        db.insert("contacts", null, ContentValues().apply {
            put("name", "Rohan")
            put("device_id", "peer-rohan-demo")
        })
        db.insert("messages", null, ContentValues().apply {
            put("contact_id", 1)
            put("text", "Secure local-first chat is ready.")
            put("sent_by_me", 0)
            put("timestamp_ms", System.currentTimeMillis())
        })
        db.insert("messages", null, ContentValues().apply {
            put("contact_id", 2)
            put("text", "Tap Calls to create a direct call invite.")
            put("sent_by_me", 0)
            put("timestamp_ms", System.currentTimeMillis())
        })
        db.insert("documents", null, ContentValues().apply {
            put("title", "Passport")
            put("kind", "Identity")
            put("note", "Stored locally; encrypted file import is next.")
        })
    }
}
