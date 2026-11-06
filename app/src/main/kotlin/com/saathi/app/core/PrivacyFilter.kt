package com.saathi.app.core

/** Plain facts about one on-screen element, so privacy rules can be unit tested without Android. */
data class NodeFacts(
    val text: String? = null,
    val description: String? = null,
    val hint: String? = null,
    val viewId: String? = null,
    val isPassword: Boolean = false,
    val isEditable: Boolean = false,
    val isClickable: Boolean = false,
)

/** Rules enforced in code on the phone, never left to the AI. */
object PrivacyFilter {
    const val MASK = "[number]"
    private const val ID_MASK = "[id]"

    private val EMAIL_OR_UPI = Regex("[\\w.+-]+@[\\w.-]+")
    private val CURRENCY = Regex("(?i)(₹|\\brs\\.?|\\binr)\\s*\\p{Nd}[\\p{Nd},.]*")
    private val DIGIT_RUN = Regex("\\+?\\p{Nd}(?:[\\p{Nd}\\s\\-.,]*\\p{Nd})?")

    /** Masks account numbers, amounts, balances, phones, emails and UPI IDs before anything is sent. */
    fun maskScreenText(input: String): String {
        val masked = EMAIL_OR_UPI.replace(input, ID_MASK)
        return maskDigitRuns(CURRENCY.replace(masked, MASK))
    }

    private fun maskDigitRuns(input: String): String = DIGIT_RUN.replace(input) { match ->
        if (match.value.count(Char::isDigit) >= 4) MASK else match.value
    }

    private val NUMBER_WORDS = setOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "shunya", "ek", "do", "teen", "char", "chaar", "panch", "paanch", "che", "chhe", "chah",
        "saat", "sat", "aath", "ath", "nau", "no",
        "शून्य", "एक", "दो", "तीन", "चार", "पांच", "पाँच", "छह", "छः", "छे", "सात", "आठ", "नौ",
    )
    private val SECRET_TALK = Regex("(?i)\\b(otp|pin|password|code|cvv)\\b|ओटीपी|पिन|पासवर्ड|कोड")

    data class SpeechResult(val text: String, val masked: Boolean, val warn: Boolean)

    /**
     * Replaces any run of 4 or more digits she says with [MASK], whether the recogniser wrote them
     * as digits ("4 5 6 7"), Devanagari digits, or words ("ek do teen char").
     */
    fun filterSpeech(input: String): SpeechResult {
        val digitsMasked = maskDigitRuns(input)
        val tokens = digitsMasked.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val output = mutableListOf<String>()
        var run = mutableListOf<String>()
        var wordsMasked = false

        fun flush() {
            if (run.size >= 4) {
                output += MASK
                wordsMasked = true
            } else {
                output += run
            }
            run = mutableListOf()
        }
        for (token in tokens) {
            val bare = token.trim(',', '.', '?', '!', '।').lowercase()
            if (bare in NUMBER_WORDS || (bare.isNotEmpty() && bare.all(Char::isDigit))) {
                run += token
            } else {
                flush()
                output += token
            }
        }
        flush()

        val text = output.joinToString(" ")
        val masked = wordsMasked || digitsMasked != input
        val onlyNumber = masked && text.replace(MASK, "").none(Char::isLetter)
        return SpeechResult(text, masked, masked && (onlyNumber || SECRET_TALK.containsMatchIn(input)))
    }

    private val SECRET_FIELD = Regex(
        "(?i)\\b(pin|mpin|otp|passcode|password|passwd|pwd|cvv|cvc|aadhaar|aadhar|pan|passport)\\b|" +
            "one time|पिन|ओटीपी|पासवर्ड|आधार"
    )
    private val SECRET_PROMPT = Regex(
        "(?i)\\b(enter|confirm|re-?enter|type|create|set up|provide)\\s+(your\\s+|the\\s+)?" +
            "(\\d[- ]digit\\s+)?(upi\\s+|new\\s+|current\\s+|card\\s+)?" +
            "(pin|mpin|otp|passcode|password|cvv|cvc|aadhaar|aadhar|pan|passport|code)\\b|" +
            "\\bupi\\s*pin\\b|\\bone[- ]time\\s+(password|code)\\b|\\bverification code\\b|\\bcvv\\b|" +
            "पिन दर्ज|पिन डाल|ओटीपी|पासवर्ड दर्ज|आधार नंबर"
    )

    /** True when the screen is asking for a secret. Saathi must then hide and send nothing. */
    fun hasSecret(nodes: List<NodeFacts>, numberPadMeansSecret: Boolean = true): Boolean {
        val keypadDigits = HashSet<String>()
        for (node in nodes) {
            if (node.isPassword) return true
            if (node.isEditable) {
                // The typed value is deliberately not checked or kept, only what labels the field.
                val labels = listOfNotNull(node.hint, node.description, node.viewId)
                    .joinToString(" ")
                    .replace('_', ' ')
                    .replace('/', ' ')
                if (SECRET_FIELD.containsMatchIn(labels)) return true
            }
            val shown = listOfNotNull(node.text, node.description, node.hint)
            if (shown.any { SECRET_PROMPT.containsMatchIn(it) }) return true
            if (node.isClickable) {
                shown.firstOrNull { it.trim().length == 1 && it.trim()[0].isDigit() }
                    ?.let { keypadDigits += it.trim() }
            }
        }
        // A number pad drawn by the app, as custom PIN screens do.
        return numberPadMeansSecret && keypadDigits.size >= 9
    }

    private val PAYMENT = Regex(
        "(?i)\\bpay\\s*(₹|rs\\b)|\\bproceed to pay\\b|\\bpay now\\b|\\benter amount\\b|\\bpaying\\b|" +
            "\\bpayment (successful|failed|pending|details|method)\\b|\\bpaid to\\b|\\bsend money\\b|" +
            "\\brequest money\\b|\\bcheck (bank )?balance\\b|\\bbank balance\\b"
    )

    /** True on money-moving screens, where Saathi stops reading and helps by conversation only. */
    fun isPaymentScreen(nodes: List<NodeFacts>): Boolean = nodes.any { node ->
        listOfNotNull(node.text, node.description).any { PAYMENT.containsMatchIn(it) }
    }

    private val SCAM = Regex(
        "(?i)\\b(anydesk|teamviewer|rustdesk|quick ?support|airdroid)\\b|" +
            "\\b(collect request|wants to collect|requesting money|approve (this )?(request|payment))\\b|" +
            "\\bshare (this |the |your )?(code|otp|pin)\\b|otp (maang|bata)|कोड (बता|शेयर)"
    )

    /** True when the screen or her words point at a known scam pattern. */
    fun looksLikeScam(text: String): Boolean = SCAM.containsMatchIn(text)
}
