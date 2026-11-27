package com.saathi.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnPolicyTest {
    private val nl = TurnPolicy.NO_LABEL

    @Test
    fun aWellLabelledShoppingScreenSendsNoScreenshot() {
        val labels = listOf("Search", "Cart", "Add to cart", "Buy now", "Share", "Back", nl)
        assertFalse(TurnPolicy.needsScreenshot(domOnlyApp = false, tappableLabels = labels))
    }

    @Test
    fun aThinOrMostlyUnnamedScreenSendsOne() {
        assertTrue(TurnPolicy.needsScreenshot(false, listOf("Back", "Search", nl)))
        assertTrue(TurnPolicy.needsScreenshot(false, listOf("A", "B", "C", "D", nl, nl, nl)))
        assertTrue(TurnPolicy.needsScreenshot(false, emptyList()))
    }

    @Test
    fun aWebViewScreenSendsOneEvenWhenWellLabelled() {
        val labels = listOf("Search", "Cart", "Add to cart", "Buy now", "Back")
        assertTrue(TurnPolicy.needsScreenshot(false, labels, hasWebView = true))
        assertFalse(TurnPolicy.needsScreenshot(true, labels, hasWebView = true))
    }

    @Test
    fun domOnlyAppsNeverSendOneRoutinely() {
        assertFalse(TurnPolicy.needsScreenshot(domOnlyApp = true, tappableLabels = listOf(nl, nl)))
    }

    @Test
    fun howAndWhereQuestionsCarryTheScreenOnTheFirstTurn() {
        for (w in listOf("GPay mein bank kaise jodun?", "Flipkart mein order kahan dikhega?",
                "bluetooth kaise chalu karun", "how do I change the wallpaper")) {
            assertTrue(w, TurnPolicy.isScreenHelpQuestion(w))
        }
    }

    @Test
    fun listsRemindersAndLiveQuestionsDoNot() {
        for (w in listOf("doodh list mein kaise daalun", "dawai ka reminder kaise lagaun", "alarm kahan lagta hai",
                "aaj petrol ka rate kya hai", "Namaste", null)) {
            assertFalse(w.toString(), TurnPolicy.isScreenHelpQuestion(w))
        }
    }

    @Test
    fun liveQuestionsWithoutAScreenGetTheLongerTimeout() {
        assertEquals(8_000L, TurnPolicy.llmTimeoutMs("kal baarish hogi kya", hasScreen = false))
        assertEquals(6_000L, TurnPolicy.llmTimeoutMs("kal baarish hogi kya", hasScreen = true))
        assertEquals(6_000L, TurnPolicy.llmTimeoutMs("kal subah dawai yaad dilana", hasScreen = false))
        assertFalse(TurnPolicy.isLiveInfoQuestion("mujhe sona hai"))
        assertTrue(TurnPolicy.isLiveInfoQuestion("aaj sone ka bhav"))
    }

    @Test
    fun settlingIsQuickerRightAfterHerTap() {
        assertEquals(650L, TurnPolicy.settleMs(clickedSinceSettle = true))
        assertEquals(750L, TurnPolicy.settleMs(clickedSinceSettle = false))
    }
}
