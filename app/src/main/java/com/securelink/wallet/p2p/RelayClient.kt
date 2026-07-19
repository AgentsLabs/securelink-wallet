package com.securelink.wallet.p2p

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RelayedSignal(val senderDeviceId: String, val payload: String)

/** Minimal HTTPS client for SecureLink's payload-blind signaling relay. */
class RelayClient {
    fun register(relayUrl: String, deviceId: String, token: String) {
        request(relayUrl, "POST", "/v1/devices/register", token, JSONObject().put("deviceId", deviceId))
    }

    fun deliver(relayUrl: String, recipientDeviceId: String, senderDeviceId: String, recipientToken: String, payload: String) {
        request(
            relayUrl,
            "POST",
            "/v1/signals",
            recipientToken,
            JSONObject().put("recipientDeviceId", recipientDeviceId).put("senderDeviceId", senderDeviceId).put("payload", payload),
        )
    }

    fun fetch(relayUrl: String, deviceId: String, token: String): List<RelayedSignal> {
        val response = request(relayUrl, "GET", "/v1/signals?deviceId=$deviceId", token)
        val signals = response.optJSONArray("signals") ?: JSONArray()
        return buildList {
            for (index in 0 until signals.length()) {
                val item = signals.getJSONObject(index)
                add(RelayedSignal(item.getString("senderDeviceId"), item.getString("payload")))
            }
        }
    }

    private fun request(relayUrl: String, method: String, path: String, token: String, body: JSONObject? = null): JSONObject {
        require(relayUrl.startsWith("https://")) { "Relay URL must use HTTPS" }
        val connection = (URL(relayUrl.removeSuffix("/") + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error(JSONObject(response.ifBlank { "{}" }).optString("error", "Relay returned HTTP ${connection.responseCode}"))
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
