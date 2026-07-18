package com.securelink.wallet.p2p

import java.util.Base64

data class PeerInvite(
    val deviceName: String,
    val publicCode: String,
    val createdAtMillis: Long,
)

class ManualSignaling {
    fun createInvite(deviceName: String, nowMillis: Long = System.currentTimeMillis()): PeerInvite {
        val raw = "$deviceName:$nowMillis"
        return PeerInvite(
            deviceName = deviceName,
            publicCode = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray()),
            createdAtMillis = nowMillis,
        )
    }

    fun parseInvite(publicCode: String): PeerInvite {
        val decoded = String(Base64.getUrlDecoder().decode(publicCode))
        val parts = decoded.split(":")
        require(parts.size == 2) { "Invite code is invalid" }
        return PeerInvite(parts[0], publicCode, parts[1].toLong())
    }
}
