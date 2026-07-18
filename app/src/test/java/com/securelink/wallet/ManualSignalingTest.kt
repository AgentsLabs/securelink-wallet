package com.securelink.wallet

import com.securelink.wallet.p2p.ManualSignaling
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
}
