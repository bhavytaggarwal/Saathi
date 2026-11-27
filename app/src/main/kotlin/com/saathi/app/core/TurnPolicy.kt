package com.saathi.app.core

/**
 * Plain rules for how fast and how heavily one turn is sent, kept apart from Android so they can
 * be unit tested: when a screenshot is worth its cost, when her question needs the screen on the
 * first call, and how long to wait for a reply.
 *
 * None of these rules can widen what is read. Every caller still runs the payment, secret, scam
 * and ask-first checks before any screen leaves the phone.
 */
object TurnPolicy {
    const val NO_LABEL = "(no label)"

    /** Fewer labelled buttons than this and the element list alone is too thin to guide from. */
    const val MIN_LABELED_TAPPABLE = 4

    /** Above this share of unnamed buttons, only a picture can say which one to press. */
    const val MAX_UNLABELED_SHARE = 0.35

    // Not lower: Indian 4G and slow Wi-Fi often need this long to draw the next screen.
    const val SETTLE_AFTER_TAP_MS = 650L
    const val SETTLE_MS = 750L
    const val LLM_TIMEOUT_MS = 6_000L

    /** Live answers search Google first, so they get a little longer than a guide step. */
    const val LIVE_INFO_TIMEOUT_MS = 8_000L

    /**
     * DOM first, vision on demand. A screenshot goes only when the readable list cannot carry the
     * step on its own: fewer than [MIN_LABELED_TAPPABLE] labelled buttons, or more than
     * [MAX_UNLABELED_SHARE] of the buttons unnamed, or a WebView on screen (hybrid apps, such as
     * many government apps, draw their page inside one and often expose little or wrong text).
     * DOM-only apps never send one routinely. Reprompts and low-confidence replies still escalate
     * with a picture elsewhere.
     */
    fun needsScreenshot(domOnlyApp: Boolean, tappableLabels: List<String>, hasWebView: Boolean = false): Boolean {
        if (domOnlyApp) return false
        if (hasWebView) return true
        val unlabeled = tappableLabels.count { it.isBlank() || it == NO_LABEL }
        val labeled = tappableLabels.size - unlabeled
        if (labeled < MIN_LABELED_TAPPABLE) return true
        return unlabeled.toDouble() / tappableLabels.size > MAX_UNLABELED_SHARE
    }

    // Same topics as LIVE_INFO_REGEX in backend/main.py. Topic words only: "aaj" and "kal" also
    // appear in reminders, and "sona" alone also means "to sleep".
    private val LIVE_INFO = Regex(
        "(?i)\\b(bhav|bhaav|daam|keemat|kimat|rate|price|mausam|weather|baarish|barish|temperature|" +
            "taapmaan|tapmaan|news|khabar|khabren|samachar|score|match|chunav|election|result|natija|" +
            "petrol|diesel|gold|chandi|silver|sensex|nifty|holiday|chhutti)\\b|" +
            "\\bsone\\s+(ka|ki|ke)\\b|" +
            "भाव|दाम|कीमत|मौसम|बारिश|तापमान|खबर|समाचार|स्कोर|चुनाव|नतीजा|पेट्रोल|सोने\\s+(का|की|के)|चांदी|छुट्टी"
    )

    fun isLiveInfoQuestion(words: String?): Boolean = !words.isNullOrBlank() && LIVE_INFO.containsMatchIn(words)

    // How or where to do something on the phone.
    private val HOW_OR_UI_HELP = Regex(
        "(?i)\\b(kaise|kaisey|kese|kahan|kaha|kidhar|jodun|jodu|chalu|band\\s+karun|badlun|badalu|bhejun|bheju|" +
            "dikhega|dikhta|milega|how\\s+(do|to|can)|where)\\b|" +
            "कैसे|कहाँ|कहां|किधर|जोड़ूँ|जोड़ूं|चालू|बदलूँ|बदलूं|भेजूँ|भेजूं|दिखेगा|ಹೇಗೆ|ಎಲ್ಲಿ"
    )

    // Requests Saathi answers without the screen.
    private val NOT_SCREEN_HELP = Regex(
        "(?i)\\b(list|reminder|yaad|alarm)\\b|लिस्ट|याद|रिमाइंडर|अलार्म"
    )

    /**
     * True when her words ask how or where to do something on the phone, so the first call can
     * carry the screen and point straight away instead of asking for it in a second call.
     */
    fun isScreenHelpQuestion(words: String?): Boolean =
        !words.isNullOrBlank() && HOW_OR_UI_HELP.containsMatchIn(words) &&
            !NOT_SCREEN_HELP.containsMatchIn(words) && !isLiveInfoQuestion(words)

    /** Wait for a reply: longer only for a spoken live-information question with no screen. */
    fun llmTimeoutMs(words: String?, hasScreen: Boolean): Long =
        if (!hasScreen && isLiveInfoQuestion(words)) LIVE_INFO_TIMEOUT_MS else LLM_TIMEOUT_MS

    /** After her own tap the next screen is usually ready sooner; loading screens defer anyway. */
    fun settleMs(clickedSinceSettle: Boolean): Long = if (clickedSinceSettle) SETTLE_AFTER_TAP_MS else SETTLE_MS
}
