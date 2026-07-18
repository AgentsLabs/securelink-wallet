package com.securelink.wallet

import com.securelink.wallet.p2p.ManualSignaling
import com.securelink.wallet.p2p.CallSignal
import com.securelink.wallet.p2p.IceCandidatePayload
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualSignalingTest {
    @Test
    fun inviteRoundTripsThroughPublicCode() {
        val signaling = ManualSignaling()
        val invite = signaling.createInvite("Pixel", nowMillis = 42)

        val parsed = signaling.parseInvite(invite.publicCode)

        assertEquals("Pixel", parsed.deviceName)
        assertEquals(42, parsed.createdAtMillis)
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
}
