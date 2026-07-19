package com.securelink.wallet

import com.securelink.wallet.p2p.ManualSignaling
import com.securelink.wallet.p2p.CallSignal
import com.securelink.wallet.p2p.IceCandidatePayload
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ManualSignalingTest {
    @Test
    fun inviteRoundTripsThroughPublicCode() {
        val signaling = ManualSignaling()
        val invite = signaling.createInvite("Pixel", "device-pixel", "p".repeat(40), nowMillis = 42)

        val parsed = signaling.parseInvite(invite.publicCode)

        assertEquals("Pixel", parsed.deviceName)
        assertEquals("device-pixel", parsed.deviceId)
        assertEquals("p".repeat(40), parsed.relayToken)
        assertEquals(42, parsed.createdAtMillis)
    }

    @Test
    fun legacyInviteDoesNotClaimToContainARoutableDeviceId() {
        val signaling = ManualSignaling()
        val legacyCode = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("Pixel:42".toByteArray())

        val parsed = signaling.parseInvite(legacyCode)

        assertEquals("Pixel", parsed.deviceName)
        assertEquals(null, parsed.deviceId)
    }

    @Test
    fun roomCodeIsShareableAndPrefixed() {
        val signaling = ManualSignaling()
        val code = signaling.createRoomCode("peer-asha-demo", nowMillis = 42)

        assertTrue(code.startsWith("sl-"))
        assertTrue(code.length > 8)
    }

    @Test
    fun callSignalRoundTripsSdpAndIceCandidates() {
        val signaling = ManualSignaling()
        val original = CallSignal(
            type = CallSignal.Type.OFFER,
            sdp = "v=0\r\no=- 123 2 IN IP4 127.0.0.1",
            candidates = listOf(IceCandidatePayload("0", 0, "candidate:1 1 udp 2122260223 192.0.2.1 54400 typ host")),
        )

        val decoded = signaling.decodeCallSignal(signaling.encodeCallSignal(original))

        assertEquals(original, decoded)
    }

    @Test
    fun contactSignalIsBoundToRecipientAndExpires() {
        val signaling = ManualSignaling()
        val signal = CallSignal(CallSignal.Type.ANSWER, "answer-sdp", emptyList())
        val payload = signaling.encodeContactCallSignal("peer-asha", signal, nowMillis = 1_000)

        assertEquals(signal, signaling.decodeContactCallSignal(payload, "peer-asha", nowMillis = 1_001).callSignal)
        assertThrows(IllegalArgumentException::class.java) {
            signaling.decodeContactCallSignal(payload, "peer-rohan", nowMillis = 1_001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            signaling.decodeContactCallSignal(payload, "peer-asha", nowMillis = 301_001)
        }
    }
}
