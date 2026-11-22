package com.saathi.app.core

/**
 * Consent for "ask first" apps such as WhatsApp. Such an app is never readable by default. It
 * becomes readable only after she says Haan during a help session, and only until one of these
 * happens: she leaves the app, the goal ends, she says stop, or [IDLE_MS] pass with no activity.
 *
 * This is the real boundary. Android's event filter is changed too, but [mayRead] decides on its
 * own, including the idle limit, so a missed revoke still runs out on time.
 *
 * Every method takes the time explicitly, so the rules can be tested without Android.
 */
class ScreenAccess {
    enum class State { NONE, ASKED, GRANTED, DECLINED }

    var state = State.NONE
        private set

    /** The app the question, grant or refusal is about. */
    var pkg: String? = null
        private set

    /** True once she has said Haan during this goal, so a second question can be shorter. */
    var grantedBefore = false
        private set

    private var lastActivityAt = 0L

    /** The single check every read goes through. */
    @Synchronized
    fun mayRead(pkg: String?, now: Long): Boolean =
        pkg != null && state == State.GRANTED && pkg == this.pkg && now - lastActivityAt <= IDLE_MS

    /** Granted, and not yet past the idle limit. */
    fun grantedPackage(now: Long): String? = pkg?.takeIf { mayRead(it, now) }

    /** True when Saathi should ask about this app now: it is ask-first, not readable and not refused. */
    @Synchronized
    fun needsAsking(pkg: String?, now: Long): Boolean =
        Allowlist.isAskFirst(pkg) && !mayRead(pkg, now) && !isDeclined(pkg)

    @Synchronized
    fun isDeclined(pkg: String?): Boolean = state == State.DECLINED && pkg == this.pkg

    @Synchronized
    fun isAsked(pkg: String?): Boolean = state == State.ASKED && pkg == this.pkg

    /** Records that the question is showing. Only ask-first apps can be asked about. */
    @Synchronized
    fun ask(pkg: String): Boolean {
        if (!Allowlist.isAskFirst(pkg)) return false
        state = State.ASKED
        this.pkg = pkg
        return true
    }

    /** Her answer. Ignored unless the question is showing for that app. Returns whether it counted. */
    @Synchronized
    fun answer(pkg: String?, yes: Boolean, now: Long): Boolean {
        if (state != State.ASKED || pkg == null || pkg != this.pkg) return false
        state = if (yes) State.GRANTED else State.DECLINED
        if (yes) grantedBefore = true
        lastActivityAt = now
        return true
    }

    /** She spoke, tapped or moved to a new screen. Keeps a grant alive. */
    @Synchronized
    fun touch(now: Long) {
        if (state == State.GRANTED) lastActivityAt = now
    }

    /**
     * A different app came to the front. A grant or a pending question for another app ends. A
     * refusal stays for the goal, so she is not asked again each time she comes back.
     */
    @Synchronized
    fun onForeground(pkg: String): String? {
        if (pkg == this.pkg) return null
        return when (state) {
            State.GRANTED, State.ASKED -> end()
            else -> null
        }
    }

    /** Ends a grant that has gone idle. Returns the app it was for. */
    @Synchronized
    fun expireIdle(now: Long): String? =
        if (state == State.GRANTED && now - lastActivityAt > IDLE_MS) end() else null

    /** Ends a grant or pending question. Returns the app it was for, or null if there was none. */
    @Synchronized
    fun revoke(): String? = if (state == State.GRANTED || state == State.ASKED) end() else null

    /** The goal is over: everything is forgotten, including a refusal. */
    @Synchronized
    fun reset(): String? {
        val ended = revoke()
        state = State.NONE
        pkg = null
        grantedBefore = false
        return ended
    }

    private fun end(): String? {
        val ended = pkg
        state = State.NONE
        pkg = null
        return ended
    }

    companion object {
        const val IDLE_MS = 2 * 60_000L
    }
}
