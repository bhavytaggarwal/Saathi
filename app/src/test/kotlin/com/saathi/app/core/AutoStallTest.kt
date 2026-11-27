package com.saathi.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStallTest {
    @Test fun firstMissStillAllowsOneRetry() {
        val stall = AutoStall()
        assertFalse(stall.miss())
        assertFalse(stall.paused)
    }

    @Test fun secondMissPauses() {
        val stall = AutoStall()
        stall.miss()
        assertTrue(stall.miss())
        assertTrue(stall.paused)
    }

    @Test fun failedChainPausesAtOnce() {
        val stall = AutoStall()
        assertTrue(stall.failed())
        assertTrue(stall.paused)
    }

    @Test fun resetStartsCountingAgain() {
        val stall = AutoStall()
        stall.failed()
        stall.reset()
        assertFalse(stall.paused)
        assertFalse(stall.miss())
    }

    @Test fun pausedStaysPausedOnFurtherMisses() {
        val stall = AutoStall()
        stall.failed()
        assertTrue(stall.miss())
    }

    @Test fun holdPausesWithoutUsingTries() {
        val stall = AutoStall()
        stall.hold()
        assertTrue(stall.paused)
        stall.reset()
        assertFalse(stall.miss())
    }
}
