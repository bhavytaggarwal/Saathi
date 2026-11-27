package com.saathi.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Consent for WhatsApp: nothing is readable before Haan, and access ends by every rule. */
class ScreenAccessTest {
    private val wa = Allowlist.WHATSAPP
    private val t0 = 1_000_000L

    private fun granted(at: Long = t0) = ScreenAccess().apply {
        ask(wa)
        assertTrue(answer(wa, yes = true, now = at))
    }

    @Test fun whatsAppIsAskFirstAndAnyConsumerAppIsAllowedByDefault() {
        assertEquals(Allowlist.Tier.ASK_FIRST, Allowlist.tier(wa))
        assertEquals(Allowlist.Tier.NEVER, Allowlist.tier("com.whatsapp.w4b"))
        assertEquals(Allowlist.Tier.NEVER, Allowlist.tier("com.google.android.apps.messaging"))
        assertEquals(Allowlist.Tier.NEVER, Allowlist.tier("com.sbi.lotusintouch"))
        assertEquals(Allowlist.Tier.NEVER, Allowlist.tier(Allowlist.GPAY))
        assertEquals(Allowlist.Tier.NEVER, Allowlist.tier("com.phonepe.app"))
        assertEquals(Allowlist.Tier.NEVER, Allowlist.tier("net.one97.paytm"))
        assertEquals(Allowlist.Tier.NEVER, Allowlist.tier("com.cred.club"))
        assertEquals(Allowlist.Tier.NEVER, Allowlist.tier("com.sbi.SBIFreedomPlus"))
        assertEquals(Allowlist.Tier.ALLOWED, Allowlist.tier("com.android.chrome"))
        assertEquals(Allowlist.Tier.ALLOWED, Allowlist.tier("com.zeptoconsumerapp"))
        assertEquals(Allowlist.Tier.ALLOWED, Allowlist.tier("com.grofers.customerapp"))
        assertEquals(Allowlist.Tier.ALLOWED, Allowlist.tier("com.snapchat.android"))
        assertEquals(Allowlist.Tier.ALLOWED, Allowlist.tier("com.meesho.supply"))
        assertEquals(Allowlist.Tier.ALLOWED, Allowlist.tier("com.jio.jiomart"))
        assertEquals("WhatsApp", Allowlist.label(wa))
    }

    @Test fun strictBankPackagesIncludeMajorIndianBanksAndUpiApps() {
        val expectedBanksAndUpi = listOf(
            Allowlist.GPAY,
            "com.phonepe.app",
            "net.one97.paytm",
            "com.sbi.lotusintouch",
            "com.sbi.SBIFreedomPlus",
            "com.snapwork.hdfc",
            "com.csam.icici.bank.imobile",
            "com.axis.mobile",
            "com.msf.kbank.mobile",
            "com.bankofbaroda.mconnect",
            "com.infrasofttech.CentralBank",
            "com.fss.pnbpsp",
        )
        for (pkg in expectedBanksAndUpi) {
            assertTrue("$pkg should be in STRICT_BANK_PACKAGES", pkg in Allowlist.STRICT_BANK_PACKAGES)
            assertTrue(Allowlist.isStrictBankApp(pkg))
            assertEquals(Allowlist.Tier.NEVER, Allowlist.tier(pkg))
        }
        assertTrue("Flipkart should be allowed for guidance", Allowlist.isAllowed("com.flipkart.android"))
        assertTrue("Play Store should be allowed for guidance", Allowlist.isAllowed("com.android.vending"))
        assertFalse(Allowlist.isStrictBankApp("com.flipkart.android"))
    }

    @Test fun smartScreenshotPolicyUsesDomOnlyForTextHeavyAppsAndScreenshotsForOthers() {
        val domOnly = listOf(
            "com.android.settings",
            "com.android.vending",
            "com.whatsapp",
            "com.google.android.contacts",
            "com.android.contacts",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.launcher3",
        )
        for (pkg in domOnly) {
            assertTrue("$pkg should be DOM-only", Allowlist.isDomOnlyApp(pkg))
        }
        val screenshotApps = listOf(
            "com.flipkart.android",
            "in.swiggy.android",
            "com.myntra.android",
            "com.zeptoconsumerapp",
            "com.grofers.customerapp",
            "com.google.android.youtube",
        )
        for (pkg in screenshotApps) {
            assertFalse("$pkg should send compressed screenshot", Allowlist.isDomOnlyApp(pkg))
        }
    }

    @Test fun nothingIsReadableBeforeHaan() {
        val access = ScreenAccess()
        assertFalse(access.mayRead(wa, t0))
        assertTrue(access.needsAsking(wa, t0))
        access.ask(wa)
        assertFalse("question showing", access.mayRead(wa, t0))
        assertTrue(access.isAsked(wa))
        assertTrue(access.answer(wa, yes = true, now = t0))
        assertTrue(access.mayRead(wa, t0))
        assertFalse(access.needsAsking(wa, t0))
    }

    @Test fun theSharedGateFollowsTheSameRule() {
        val access = Allowlist.access
        try {
            assertFalse(Allowlist.isAllowed(wa, t0))
            access.ask(wa)
            assertFalse(Allowlist.isAllowed(wa, t0))
            access.answer(wa, yes = true, now = t0)
            assertTrue(Allowlist.isAllowed(wa, t0))
            // A grant for WhatsApp never opens anything else.
            assertFalse(Allowlist.isAllowed("com.google.android.apps.messaging", t0))
            assertFalse(Allowlist.isAllowed("com.whatsapp.w4b", t0))
            assertFalse(Allowlist.isAllowed(wa, t0 + ScreenAccess.IDLE_MS + 1))
        } finally {
            access.reset()
        }
        assertFalse(Allowlist.isAllowed(wa, t0))
    }

    @Test fun nahiKeepsItUnreadableForTheGoal() {
        val access = ScreenAccess()
        access.ask(wa)
        access.answer(wa, yes = false, now = t0)
        assertFalse(access.mayRead(wa, t0))
        assertTrue(access.isDeclined(wa))
        assertFalse("not asked again during the goal", access.needsAsking(wa, t0))
        // Leaving and coming back does not undo Nahi.
        assertNull(access.onForeground("com.android.launcher3"))
        assertTrue(access.isDeclined(wa))
        access.reset()
        assertTrue("a new goal asks again", access.needsAsking(wa, t0))
    }

    @Test fun onlyAskFirstAppsCanBeAskedAndOnlyTheAskedOneAnswered() {
        val access = ScreenAccess()
        assertFalse(access.ask("com.google.android.apps.messaging"))
        assertFalse(access.answer(wa, yes = true, now = t0))
        access.ask(wa)
        assertFalse(access.answer("com.android.chrome", yes = true, now = t0))
        assertFalse(access.mayRead(wa, t0))
    }

    @Test fun leavingWhatsAppEndsAccess() {
        val access = granted()
        assertNull("staying in WhatsApp", access.onForeground(wa))
        assertTrue(access.mayRead(wa, t0))
        assertEquals(wa, access.onForeground("com.android.launcher3"))
        assertFalse(access.mayRead(wa, t0))
        assertTrue("asked again on return", access.needsAsking(wa, t0))
        assertTrue("the second question can be shorter", access.grantedBefore)
    }

    @Test fun leavingWhileTheQuestionShowsCancelsIt() {
        val access = ScreenAccess()
        access.ask(wa)
        assertEquals(wa, access.onForeground("com.google.android.apps.maps"))
        assertFalse(access.isAsked(wa))
        assertFalse("a late Haan does nothing", access.answer(wa, yes = true, now = t0))
    }

    @Test fun twoMinutesIdleEndsAccessEvenWithoutARevoke() {
        val access = granted()
        assertTrue(access.mayRead(wa, t0 + ScreenAccess.IDLE_MS))
        assertFalse(access.mayRead(wa, t0 + ScreenAccess.IDLE_MS + 1))
        assertTrue(access.needsAsking(wa, t0 + ScreenAccess.IDLE_MS + 1))
        assertNull(access.expireIdle(t0 + ScreenAccess.IDLE_MS))
        assertEquals(wa, access.expireIdle(t0 + ScreenAccess.IDLE_MS + 1))
        assertEquals(ScreenAccess.State.NONE, access.state)
    }

    @Test fun activityKeepsAccessAlive() {
        val access = granted()
        access.touch(t0 + 90_000)
        assertTrue(access.mayRead(wa, t0 + 200_000))
        assertNull(access.expireIdle(t0 + 200_000))
        assertFalse(access.mayRead(wa, t0 + 90_000 + ScreenAccess.IDLE_MS + 1))
    }

    @Test fun stopAndGoalEndRevoke() {
        val stopped = granted()
        assertEquals(wa, stopped.revoke())
        assertFalse(stopped.mayRead(wa, t0))
        assertNull("nothing left to revoke", stopped.revoke())

        val done = granted()
        assertEquals(wa, done.reset())
        assertFalse(done.mayRead(wa, t0))
        assertFalse(done.grantedBefore)
    }
}
