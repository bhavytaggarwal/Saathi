package com.saathi.app.core

/**
 * Stops automatic steps from looping on a screen Saathi cannot read well, such as a WebView page
 * whose text keeps changing. Without this, every settle on such a page started a new call, and
 * each failed call said "Ek second, main phir se dekh rahi hoon" again, without end.
 *
 * One automatic step gets at most [MAX_ATTEMPTS] tries on the same screen: the first and one
 * retry. After that Saathi says so once and waits until she speaks, changes app or moves to a
 * clearly new page.
 */
class AutoStall {
    var paused = false
        private set
    private var misses = 0

    /** An automatic try came back with no clear button. True when automatic steps now stop. */
    fun miss(): Boolean {
        misses++
        if (misses >= MAX_ATTEMPTS) paused = true
        return paused
    }

    /** A call failed after its own single retry: both tries are used up. */
    fun failed(): Boolean {
        misses = MAX_ATTEMPTS
        paused = true
        return true
    }

    /**
     * A step with no button on purpose, such as "fill this form yourself": wait for her without
     * counting a failure or saying anything more.
     */
    fun hold() {
        paused = true
    }

    /** A clear step, her tap, her words, a new app or a new goal: start counting again. */
    fun reset() {
        misses = 0
        paused = false
    }

    companion object {
        const val MAX_ATTEMPTS = 2
    }
}
