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
    /** True when an empty text box is showing its hint as its text. */
    val showingHint: Boolean = false,
)

/** Rules enforced in code on the phone, never left to the AI. */
object PrivacyFilter {
    const val MASK = "[number]"
    private const val ID_MASK = "[id]"

    private val EMAIL_OR_UPI = Regex("[\\w.+-]+@[\\w.-]+")
    // Amounts with the currency before or after the number: "₹500", "Rs. 50", "500 rupees", "रु 50".
    private val CURRENCY = Regex(
        "(?i)(?:₹|\\brs\\.?|\\binr|\\brupees?|रुपये|रुपए|रु\\.?)\\s*\\p{Nd}[\\p{Nd},.]*|" +
            "\\p{Nd}[\\p{Nd},.]*\\s*(?:₹|rs\\b\\.?|inr\\b|rupees?\\b|रुपये|रुपए|रु\\.?)"
    )
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
        // The Hindi recogniser often writes English number words in Devanagari.
        "जीरो", "ज़ीरो", "वन", "टू", "थ्री", "फोर", "फ़ोर", "फाइव", "फ़ाइव", "सिक्स", "सेवन", "एट", "नाइन",
        // Kannada, in Kannada script and Roman, and English number words in Kannada script.
        "ಸೊನ್ನೆ", "ಒಂದು", "ಎರಡು", "ಮೂರು", "ನಾಲ್ಕು", "ಐದು", "ಆರು", "ಏಳು", "ಎಂಟು", "ಒಂಬತ್ತು",
        "sonne", "ondu", "eradu", "mooru", "naalku", "nalku", "aidu", "aaru", "elu", "entu", "ombattu",
        "ಜೀರೋ", "ಒನ್", "ಟು", "ತ್ರೀ", "ಫೋರ್", "ಫೈವ್", "ಸಿಕ್ಸ್", "ಸೆವೆನ್", "ಎಯ್ಟ್", "ನೈನ್",
    )
    private val SECRET_TALK = Regex("(?i)\\b(otp|pin|password|code|cvv)\\b|ओटीपी|पिन|पासवर्ड|कोड|ಒಟಿಪಿ|ಓಟಿಪಿ|ಪಿನ್|ಪಾಸ್‌ವರ್ಡ್|ಪಾಸ್ವರ್ಡ್|ಕೋಡ್")

    data class SpeechResult(val text: String, val masked: Boolean, val warn: Boolean)

    /** True when her words talk about an OTP, PIN, password or code. */
    fun mentionsSecret(text: String): Boolean = SECRET_TALK.containsMatchIn(text)

    /**
     * Replaces any run of 4 or more digits she says with [MASK], whether the recogniser wrote them
     * as digits ("4 5 6 7"), Devanagari digits, words ("ek do teen char") or a mix ("45 ek 67").
     */
    fun filterSpeech(input: String): SpeechResult {
        val digitsMasked = maskDigitRuns(input)
        val tokens = digitsMasked.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val output = mutableListOf<String>()
        var run = mutableListOf<String>()
        var runDigits = 0
        var wordsMasked = false

        fun flush() {
            if (runDigits >= 4) {
                output += MASK
                wordsMasked = true
            } else {
                output += run
            }
            run = mutableListOf()
            runDigits = 0
        }
        for (token in tokens) {
            val bare = token.trim(',', '.', '?', '!', '।').lowercase()
            if (bare in NUMBER_WORDS) {
                run += token
                runDigits += 1
            } else if (bare.isNotEmpty() && bare.all(Char::isDigit)) {
                run += token
                runDigits += bare.length
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
            "one time|पिन|ओटीपी|पासवर्ड|आधार|ಪಿನ್|ಒಟಿಪಿ|ಓಟಿಪಿ|ಪಾಸ್‌ವರ್ಡ್|ಆಧಾರ್"
    )
    private val SECRET_PROMPT = Regex(
        "(?i)\\b(enter|confirm|re-?enter|type|create|set up|provide)\\s+(your\\s+|the\\s+)?" +
            "(\\d[- ]digit\\s+)?(upi\\s+|new\\s+|current\\s+|card\\s+)?" +
            "(pin|mpin|otp|passcode|password|cvv|cvc|aadhaar|aadhar|pan|passport|code)\\b|" +
            "\\bupi\\s*pin\\b|\\bone[- ]time\\s+(password|code)\\b|\\bverification code\\b|\\bcvv\\b|" +
            "पिन दर्ज|पिन डाल|ओटीपी|पासवर्ड दर्ज|आधार नंबर|ಪಿನ್ ನಮೂದಿಸಿ|ಪಿನ್ ಹಾಕಿ|ಒಟಿಪಿ|ಓಟಿಪಿ|ಪಾಸ್‌ವರ್ಡ್ ನಮೂದಿಸಿ|ಆಧಾರ್ ಸಂಖ್ಯೆ"
    )

    /** True when the screen is asking for a secret. Saathi must then hide and send nothing. */
    fun hasSecret(nodes: List<NodeFacts>, numberPadMeansSecret: Boolean = true, ignoreRowText: Boolean = false): Boolean =
        secretReason(nodes, numberPadMeansSecret, ignoreRowText) != null

    /**
     * Why the screen counts as asking for a secret, or null if it doesn't. The reason is a fixed
     * word, never screen text, so it can be logged.
     */
    fun secretReason(nodes: List<NodeFacts>, numberPadMeansSecret: Boolean = true, ignoreRowText: Boolean = false): String? {
        val keypadDigits = HashSet<String>()
        for (node in nodes) {
            if (node.isPassword) return "password_field"
            if (node.isEditable) {
                // The typed value is deliberately not checked or kept, only what labels the field.
                val labels = listOfNotNull(node.hint, node.description, node.viewId)
                    .joinToString(" ")
                    .replace('_', ' ')
                    .replace('/', ' ')
                if (SECRET_FIELD.containsMatchIn(labels)) return "labelled_field"
            }
            val shown = listOfNotNull(node.text, node.description, node.hint)
            // In a chat app, text inside a tappable chat or message row is someone's message,
            // such as a bank's "123456 is your verification code", not the app asking her for a
            // code. Password fields, labelled fields and the app's own prompts still count.
            val messageText = ignoreRowText && node.isClickable && !node.isEditable
            if (!messageText && shown.any { SECRET_PROMPT.containsMatchIn(it) }) return "prompt_text"
            if (node.isClickable) {
                shown.firstOrNull { it.trim().length == 1 && it.trim()[0].isDigit() }
                    ?.let { keypadDigits += it.trim() }
            }
        }
        // A number pad drawn by the app, as custom PIN screens do.
        return if (numberPadMeansSecret && keypadDigits.size >= 9) "number_pad" else null
    }

    private val PAYMENT = Regex(
        "(?i)\\bpay\\s*(₹|rs\\b)|\\bproceed to pay\\b|\\bpay now\\b|\\benter amount\\b|\\bpaying\\b|" +
            "\\bpayment (successful|failed|pending|details|method)\\b|\\bpaid to\\b|\\bsend money\\b|" +
            "\\brequest money\\b|\\bcheck (bank )?balance\\b|\\bbank balance\\b|\\bsend payment\\b"
    )

    // Screen class names of payment screens inside chat apps, such as WhatsApp's UPI screens.
    private val PAYMENT_CLASS = Regex("(?i)payment|upi")

    /**
     * True when a screen's class name says it handles money. Used for ask-first apps only, whose
     * payment screens may not use the words [isPaymentScreen] looks for. Class names are not screen
     * content.
     */
    fun isPaymentScreenClass(className: String?): Boolean =
        className != null && PAYMENT_CLASS.containsMatchIn(className)

    /** True on money-moving screens, where Saathi stops reading and helps by conversation only. */
    fun isPaymentScreen(nodes: List<NodeFacts>): Boolean = nodes.any { node ->
        listOfNotNull(node.text, node.description).any { PAYMENT.containsMatchIn(it) }
    }

    private val CHECKOUT_INDICATOR = Regex(
        "(?i)\\b(payment\\s+options?|payment\\s+methods?|select\\s+(a\\s+)?payment|choose\\s+payment|" +
            "complete\\s+payment|pay\\s+using|pay\\s+via|pay\\s+with|pay\\s+by|continue\\s+with|" +
            "payments?|checkout|all\\s+other\\s+options|price\\s+details|total\\s+amount|" +
            "payable\\s+amount|amount\\s+payable|step\\s*3\\s*of\\s*3)\\b"
    )

    private val DIRECT_PAY_USING_UPI = Regex(
        "(?i)\\b(pay\\s+(using|via|with|by)|continue\\s+with)\\s+(google\\s*pay|gpay|phone\\s*pe|paytm|bhim|upi)\\b"
    )

    private val UPI_OR_BANK_INSTRUMENT = Regex(
        "(?i)\\b(google\\s*pay|gpay|phone\\s*pe|paytm|bhim|upi|pay\\s+by\\s+any\\s+upi|" +
            "net\\s*banking|credit\\s*[/&]\\s*debit|debit\\s*card|credit\\s*card|atm\\s*card|" +
            "saved\\s*cards?|add\\s+new\\s+card|add\\s+new\\s+upi)\\b"
    )

    private val INSTRUMENT_UPI = Regex("(?i)\\b(google\\s*pay|gpay|phone\\s*pe|paytm|bhim|upi)\\b")
    private val INSTRUMENT_CARD = Regex("(?i)\\b(credit\\s*[/&]\\s*debit|debit\\s*card|credit\\s*card|atm\\s*card)\\b")
    private val INSTRUMENT_NETBANKING = Regex("(?i)\\b(net\\s*banking)\\b")
    private val INSTRUMENT_COD = Regex("(?i)\\b(cash\\s+on\\s+delivery|pay\\s+on\\s+delivery)\\b")
    private val INSTRUMENT_WALLET = Regex("(?i)\\b(wallets?|gift\\s+card)\\b")

    private val PRODUCT_PAGE_CTA = Regex(
        "(?i)\\b(add\\s+to\\s+cart|add\\s+to\\s+bag|buy\\s+now|similar\\s+products|product\\s+details|view\\s+cart)\\b"
    )

    private val CHECKOUT_EXCLUDED_PACKAGES = setOf(
        Allowlist.SETTINGS,
        "com.android.vending",
    )

    /**
     * Deterministic payment-options / merchant checkout detector (e.g. Flipkart / Amazon / Swiggy
     * Payment Options screen before GPay launches).
     *
     * Returns true when:
     * 1. [pkg] is outside of Play Store, Android Settings, Launchers, Clock, and Calculator.
     * 2. The screen is not a product detail/listing page ("Add to Cart" / "Buy Now"), unless a
     *    direct "Pay using Google Pay / UPI" sheet is open.
     * 3. Either:
     *    a) A checkout/payment indicator ("Payment", "Payments", "Payment Options", "Checkout",
     *       "Pay using", "Total Amount", "Step 3 of 3") appears together with a UPI/bank trigger
     *       ("Google Pay", "GPay", "PhonePe", "Paytm", "UPI", "Net Banking", "Card"), OR
     *    b) A direct "Pay using / Pay with Google Pay / UPI" CTA is visible, OR
     *    c) A WebView payment list shows UPI/GPay alongside at least 2 other payment method rows
     *       (Card, Net Banking, Cash on Delivery, Wallets).
     */
    fun isCheckoutPaymentOptionsScreen(nodes: List<NodeFacts>, pkg: String? = null): Boolean {
        if (pkg != null && (
                pkg in CHECKOUT_EXCLUDED_PACKAGES ||
                    Allowlist.isLauncher(pkg) ||
                    Allowlist.hasOrdinaryNumberPad(pkg)
                )
        ) {
            return false
        }
        val texts = nodes.flatMap { listOfNotNull(it.text, it.description, it.hint) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (texts.isEmpty()) return false

        // Direct "Pay using Google Pay / PhonePe / Paytm / UPI" sheet or button always triggers.
        if (texts.any { DIRECT_PAY_USING_UPI.containsMatchIn(it) }) return true

        // Product listing / product detail screens ("Add to cart", "Buy Now", "Similar Products")
        // are never the final payment options screen.
        if (texts.any { PRODUCT_PAGE_CTA.containsMatchIn(it) }) return false

        val hasUpiOrBankTrigger = texts.any { UPI_OR_BANK_INSTRUMENT.containsMatchIn(it) }
        if (!hasUpiOrBankTrigger) return false

        val hasCheckoutIndicator = texts.any { CHECKOUT_INDICATOR.containsMatchIn(it) }
        if (hasCheckoutIndicator) return true

        // WebView fallback: if the header word "Payments" wasn't exposed as text, check if UPI/GPay
        // appears alongside at least 2 other distinct payment method categories (Card, Net Banking, COD, Wallet).
        val hasUpi = texts.any { INSTRUMENT_UPI.containsMatchIn(it) }
        if (hasUpi) {
            var otherCategories = 0
            if (texts.any { INSTRUMENT_CARD.containsMatchIn(it) }) otherCategories++
            if (texts.any { INSTRUMENT_NETBANKING.containsMatchIn(it) }) otherCategories++
            if (texts.any { INSTRUMENT_COD.containsMatchIn(it) }) otherCategories++
            if (texts.any { INSTRUMENT_WALLET.containsMatchIn(it) }) otherCategories++
            if (otherCategories >= 2) return true
        }
        return false
    }

    private val SCAM = Regex(
        "(?i)\\b(anydesk|teamviewer|rustdesk|quick ?support|airdroid)\\b|" +
            "\\b(collect request|wants to collect|requesting money|approve (this )?(request|payment))\\b|" +
            "\\brequesting\\s*(₹|rs\\b)|" +
            "\\bshare (this |the |your )?(code|otp|pin)\\b|otp (maang|bata)|कोड (बता|शेयर)|" +
            "ಕೋಡ್ (ಹೇಳಿ|ಕೊಡಿ|ಶೇರ್)|ಒಟಿಪಿ (ಹೇಳಿ|ಕೊಡಿ|ಕೇಳ)|ಓಟಿಪಿ (ಹೇಳಿ|ಕೊಡಿ|ಕೇಳ)"
    )

    /** True when the screen or her words point at a known scam pattern. */
    fun looksLikeScam(text: String): Boolean = SCAM.containsMatchIn(text)

    /**
     * True when any element on screen looks like a scam. Checked on payment screens too, because a
     * collect request is itself a payment screen and must still be warned about.
     */
    fun isScamScreen(nodes: List<NodeFacts>): Boolean = nodes.any { node ->
        listOfNotNull(node.text, node.description).any(::looksLikeScam)
    }
}
