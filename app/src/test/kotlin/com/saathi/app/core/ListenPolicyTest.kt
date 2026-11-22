package com.saathi.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenPolicyTest {
    private val waiting = ListenPolicy.Now(
        expectingAnswer = true,
        sessionActive = true,
        speaking = false,
        awaitingReply = false,
        listening = false,
        typing = false,
        hidden = false,
        msSinceSpeechEnded = 2_000,
    )

    @Test
    fun aClosedMicIsReopenedWhileSaathiWaitsForHerAnswer() {
        assertTrue(ListenPolicy.mustRearm(waiting))
    }

    @Test
    fun theMicIsLeftAloneWhenAnythingElseIsHappening() {
        assertFalse(ListenPolicy.mustRearm(waiting.copy(expectingAnswer = false)))
        assertFalse(ListenPolicy.mustRearm(waiting.copy(sessionActive = false)))
        assertFalse(ListenPolicy.mustRearm(waiting.copy(speaking = true)))
        assertFalse(ListenPolicy.mustRearm(waiting.copy(awaitingReply = true)))
        assertFalse(ListenPolicy.mustRearm(waiting.copy(listening = true)))
        assertFalse(ListenPolicy.mustRearm(waiting.copy(typing = true)))
        assertFalse(ListenPolicy.mustRearm(waiting.copy(hidden = true)))
    }

    @Test
    fun theWatchdogGivesTheNormalReopenTimeFirst() {
        // The normal path reopens 300ms after speech ends. The watchdog must not race it.
        assertTrue(ListenPolicy.STUCK_MIC_MS > ListenPolicy.ECHO_GAP_MS * 3)
        assertFalse(ListenPolicy.mustRearm(waiting.copy(msSinceSpeechEnded = ListenPolicy.ECHO_GAP_MS)))
    }

    @Test
    fun theEchoGapIs300ms() {
        assertTrue(ListenPolicy.ECHO_GAP_MS == 300L)
    }

    @Test
    fun aStuckSpeechGuardNeverCutsOffARealLine() {
        // About 15 Hinglish words at the slow rate still finish well inside the guard.
        val long = "Upar se teesri line par dabaiye, jahan ghera bana hai, phir neeche dekhiye."
        assertTrue(ListenPolicy.stuckSpeechMs(long.length, 0.8f) > 15_000)
        assertTrue(ListenPolicy.stuckSpeechMs(0, 1f) >= 6_000)
    }

    @Test
    fun herWordsComeBeforeTheGuideMovingOn() {
        assertTrue(ListenPolicy.holdAutoAdvance(listening = true, speaking = false, msSinceSheSpoke = 60_000))
        assertTrue(ListenPolicy.holdAutoAdvance(listening = false, speaking = true, msSinceSheSpoke = 1_000))
        // A step line still playing when she has said nothing lately does not block the guide.
        assertFalse(ListenPolicy.holdAutoAdvance(listening = false, speaking = true, msSinceSheSpoke = 30_000))
        assertFalse(ListenPolicy.holdAutoAdvance(listening = false, speaking = false, msSinceSheSpoke = 500))
    }
}
