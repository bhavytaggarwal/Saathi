package com.saathi.app.core

/** When Saathi's mic should reopen by itself, kept apart from Android so it can be tested. */
object ListenPolicy {
    /** Wait this long after speech ends, so the speaker's tail is not heard as her words. */
    const val ECHO_GAP_MS = 300L

    /** The watchdog waits this long after speech ends before deciding the mic is stuck closed. */
    const val STUCK_MIC_MS = 1_500L

    /** Her words take priority over the guide moving on for this long after she speaks. */
    const val HER_TURN_MS = 4_000L

    /**
     * How long a line may take before the speech engine is treated as stuck: a generous reading
     * time for [chars] characters at [rate], plus a margin, so it never cuts off a real line.
     */
    fun stuckSpeechMs(chars: Int, rate: Float): Long {
        val perChar = 120.0 / rate.coerceIn(0.5f, 2f)
        return 6_000L + (chars.coerceAtLeast(0) * perChar).toLong()
    }

    /** What the watchdog needs to know about the session at one moment. */
    data class Now(
        val expectingAnswer: Boolean,
        val sessionActive: Boolean,
        val speaking: Boolean,
        val awaitingReply: Boolean,
        val listening: Boolean,
        val typing: Boolean,
        val hidden: Boolean,
        val msSinceSpeechEnded: Long,
    )

    /**
     * The mic should reopen when Saathi asked her something and is waiting, nothing is being
     * said or fetched, and the mic is still closed well after the line ended.
     */
    fun mustRearm(now: Now): Boolean =
        now.expectingAnswer && now.sessionActive && !now.speaking && !now.awaitingReply &&
            !now.listening && !now.typing && !now.hidden && now.msSinceSpeechEnded >= STUCK_MIC_MS

    /**
     * The guide must not move on by itself while she is talking, or while Saathi is still
     * answering something she said a moment ago.
     */
    fun holdAutoAdvance(listening: Boolean, speaking: Boolean, msSinceSheSpoke: Long): Boolean =
        listening || (speaking && msSinceSheSpoke < HER_TURN_MS)
}
