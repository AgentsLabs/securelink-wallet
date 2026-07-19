package com.securelink.wallet.p2p

import java.util.Base64

data class PeerInvite(
    val deviceName: String,
    val deviceId: String?,
    val relayToken: String?,
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

/**
 * A short-lived envelope that binds a WebRTC offer/answer to a saved contact.
 * The media is still protected by DTLS-SRTP; this prevents a signal intended
 * for one contact from being accidentally applied to another contact thread.
 */
data class ContactCallSignal(
    val recipientDeviceId: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val callSignal: CallSignal,
)

class ManualSignaling {
    companion object {
        private const val CONTACT_SIGNAL_PREFIX = "sl-contact-"
        private const val SIGNAL_TTL_MILLIS = 5 * 60 * 1000L
    }
    /**
     * A pairing code carries a device's stable routing identifier. It is deliberately
     * not treated as proof of identity: both people must compare the displayed
     * fingerprint over a trusted channel before using a contact for sensitive calls.
     */
    fun createInvite(
        deviceName: String,
        deviceId: String,
        relayToken: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): PeerInvite {
        val raw = listOf("v3", encodePart(deviceName), encodePart(deviceId), encodePart(relayToken), nowMillis.toString()).joinToString("|")
        val publicCode = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
        return PeerInvite(
            deviceName = deviceName,
            deviceId = deviceId,
            relayToken = relayToken,
            publicCode = publicCode,
            createdAtMillis = nowMillis,
        )
    }

    fun createRoomCode(contactDeviceId: String, nowMillis: Long = System.currentTimeMillis()): String {
        val raw = "$contactDeviceId:$nowMillis"
        return "sl-" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray()).take(18)
    }

    fun parseInvite(publicCode: String): PeerInvite {
        val decoded = String(Base64.getUrlDecoder().decode(publicCode), Charsets.UTF_8)
        val parts = decoded.split("|")
        if (parts.size == 5 && parts[0] == "v3") {
            return PeerInvite(decodePart(parts[1]), decodePart(parts[2]), decodePart(parts[3]), publicCode, parts[4].toLong())
        }
        if (parts.size == 4 && parts[0] == "v2") {
            return PeerInvite(decodePart(parts[1]), decodePart(parts[2]), null, publicCode, parts[3].toLong())
        }
        // Read legacy demo codes so existing users receive a clear pairing error in
        // the UI instead of a parsing failure.
        val legacyParts = decoded.split(":")
        require(legacyParts.size == 2) { "Invite code is invalid" }
        return PeerInvite(legacyParts[0], null, null, publicCode, legacyParts[1].toLong())
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

    fun encodeContactCallSignal(
        recipientDeviceId: String,
        signal: CallSignal,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val body = buildString {
            append("v1|").append(encodePart(recipientDeviceId)).append('|')
                .append(nowMillis).append('|').append(nowMillis + SIGNAL_TTL_MILLIS).append('\n')
            append(encodeCallSignal(signal))
        }
        return CONTACT_SIGNAL_PREFIX + encodePart(body)
    }

    fun decodeContactCallSignal(
        payload: String,
        expectedDeviceId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): ContactCallSignal {
        require(payload.startsWith(CONTACT_SIGNAL_PREFIX)) { "This signal is not addressed to a SecureLink contact" }
        val lines = String(Base64.getUrlDecoder().decode(payload.removePrefix(CONTACT_SIGNAL_PREFIX))).lineSequence().toList()
        require(lines.size == 2) { "Contact call signal is incomplete" }
        val header = lines.first().split('|')
        require(header.size == 4 && header[0] == "v1") { "Unsupported contact call signal" }
        val recipientDeviceId = decodePart(header[1])
        require(recipientDeviceId == expectedDeviceId) { "This call signal belongs to another contact" }
        val createdAtMillis = header[2].toLong()
        val expiresAtMillis = header[3].toLong()
        require(nowMillis in createdAtMillis..expiresAtMillis) { "This call signal has expired. Create a new invite." }
        return ContactCallSignal(recipientDeviceId, createdAtMillis, expiresAtMillis, decodeCallSignal(lines[1]))
    }

    private fun encodePart(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodePart(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
