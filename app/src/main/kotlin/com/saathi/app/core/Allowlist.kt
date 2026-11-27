package com.saathi.app.core

/**
 * Decides which apps Saathi may read using a not-allowed blocklist ([NEVER]) plus [ASK_FIRST]
 * consent for WhatsApp. Ordinary apps (Zepto, Blinkit, Snapchat, Settings, YouTube, etc.) are
 * allowed by default, while banking, UPI, SMS, email, password managers, and system UI stay
 * blocked. [isAllowed] is checked before every read.
 */
object Allowlist {
    const val GPAY = "com.google.android.apps.nbu.paisa.user"
    const val SETTINGS = "com.android.settings"
    const val SYSTEM_UI = "com.android.systemui"
    const val WHATSAPP = "com.whatsapp"

    /**
     * ALLOWED apps are read during a help session. ASK_FIRST apps are read only after she agrees,
     * and only buttons with known labels leave the phone ([ControlsOnly]). NEVER apps are never
     * read.
     */
    enum class Tier { ALLOWED, ASK_FIRST, NEVER, BLOCKED }

    /** Read only with her consent, one help session at a time. See [access]. */
    private val ASK_FIRST = mapOf(WHATSAPP to "WhatsApp")

    /** Consent for [ASK_FIRST] apps. The one shared instance used by every read. */
    val access = ScreenAccess()

    /** Home and recent-apps screens across common phone makers. */
    val LAUNCHERS = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.android.launcher3",
        "com.android.launcher",
        "com.oppo.launcher",
        "net.oneplus.launcher",
        "com.bbk.launcher2",
        "com.vivo.simplelauncher",
        "com.transsion.hilauncher",
        "com.nothing.launcher",
    )

    private val CLOCKS = setOf(
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.coloros.alarmclock",
        "com.oneplus.deskclock",
    )

    private val CALCULATORS = setOf(
        "com.vivo.calculator",
        "com.google.android.calculator",
        "com.android.calculator2",
        "com.sec.android.app.popupcalculator",
        "com.miui.calculator",
        "com.coloros.calculator",
        "com.oneplus.calculator",
    )

    // This phone's own Clock, Calculator and home screen apps, found from Android at startup,
    // because phone makers name them differently (Vivo, Tecno and others).
    @Volatile private var phoneClocks: Set<String> = emptySet()
    @Volatile private var phoneCalculators: Set<String> = emptySet()
    @Volatile private var phoneLaunchers: Set<String> = emptySet()

    // Populated at runtime from Android's PackageManager so any installed Indian or global app gets
    // its real user-visible app name without maintaining a hardcoded list of consumer apps.
    @Volatile var appLabelResolver: ((String) -> String?)? = null

    /**
     * Major Indian banking and UPI apps that block or refuse to proceed if any third-party
     * Accessibility Service is active. When one of these enters the foreground, Saathi posts a
     * 1-tap re-enable notification, removes its overlays, saves pausedForBanking = true, speaks a
     * calm Hindi reassurance, and calls disableSelf().
     */
    val STRICT_BANK_PACKAGES = setOf(
        GPAY,                            // com.google.android.apps.nbu.paisa.user (Google Pay)
        "com.phonepe.app",               // PhonePe
        "net.one97.paytm",               // Paytm
        "com.sbi.lotusintouch",          // YONO SBI
        "com.sbi.SBIFreedomPlus",        // YONO Lite
        "com.snapwork.hdfc",             // HDFC Bank
        "com.csam.icici.bank.imobile",   // iMobile Pay
        "com.axis.mobile",               // Axis Mobile
        "com.msf.kbank.mobile",          // Kotak811
        "com.bankofbaroda.mconnect",     // bob World
        "com.infrasofttech.CentralBank", // Central Bank
        "com.fss.pnbpsp",                // PNB One
    )

    /** True when [pkg] is a strict banking or UPI app that requires Saathi to call disableSelf(). */
    fun isStrictBankApp(pkg: String?): Boolean =
        !pkg.isNullOrBlank() && pkg in STRICT_BANK_PACKAGES

    /**
     * Explicit Indian financial, UPI, banking, and credential apps where Saathi must immediately
     * pause the AI overlay and never inspect screen contents.
     */
    val FINANCIAL_AND_SECURE_PACKAGES = STRICT_BANK_PACKAGES + setOf(
        "com.cred.club",
        "in.org.npci.upiapp",
        "com.mobikwik_new",
        "com.freecharge.android",
    )

    private val FINANCIAL_OR_SECURE_REGEX = Regex(
        "(?i)sbi|hdfc|icici|axis|kotak|bank|paytm|phonepe|bhim|cred|mobikwik|freecharge|" +
            "npci|upi|bitwarden|1password|lastpass|keepass|authenticator"
    )

    /** True when [pkg] is a financial, banking, UPI, or credential-manager app. */
    fun isFinancialOrSecureApp(pkg: String?): Boolean =
        !pkg.isNullOrBlank() && (pkg in FINANCIAL_AND_SECURE_PACKAGES || FINANCIAL_OR_SECURE_REGEX.containsMatchIn(pkg))

    /**
     * Apps that must never be read (the not-allowed blocklist), including banking, UPI, SMS,
     * email, password managers, and system UI. WhatsApp matches too, so WhatsApp Business stays
     * blocked and personal WhatsApp is read only through [ASK_FIRST].
     */
    private val NEVER = Regex(
        "(?i)whatsapp|messaging|mms|truecaller|android\\.gm|systemui|sbi|hdfc|icici|axis|kotak|" +
            "bank|paytm|phonepe|bhim|cred|mobikwik|" +
            "bitwarden|1password|lastpass|keepass|authenticator"
    )

    /**
     * Packages that answer [AlarmClock.ACTION_SHOW_ALARMS] or [Intent.ACTION_MAIN] on some phones
     * (e.g. Google App / Assistant, Settings) but are not the phone's Clock, Calculator, or Home
     * launcher.
     */
    private val NON_CLOCK_OR_LAUNCHER_APPS = Regex(
        "(?i)googlequicksearchbox|googleassistant|bard|gemini|bixby|alexa|android\\.settings"
    )

    /**
     * Adds the phone's own Clock, Calculator and Launcher apps so [isLauncher] and
     * [hasOrdinaryNumberPad] recognise them on every phone maker. Anything matching [NEVER] or
     * [NON_CLOCK_OR_LAUNCHER_APPS] is dropped here as well.
     */
    fun addPhoneApps(clocks: Set<String>, calculators: Set<String>, launchers: Set<String>): Set<String> {
        fun clean(found: Set<String>) = found.filterTo(HashSet()) {
            !NEVER.containsMatchIn(it) && !NON_CLOCK_OR_LAUNCHER_APPS.containsMatchIn(it)
        }
        phoneClocks = clean(clocks)
        phoneCalculators = clean(calculators)
        phoneLaunchers = clean(launchers)
        return (phoneClocks + phoneCalculators + phoneLaunchers) - (LAUNCHERS + CLOCKS + CALCULATORS)
    }

    private fun isClock(pkg: String) = pkg in CLOCKS || pkg in phoneClocks
    private fun isCalculator(pkg: String) = pkg in CALCULATORS || pkg in phoneCalculators

    /**
     * Known clean, text-heavy packages where DOM-only reading is used and screenshots are skipped
     * to minimize latency: Android Settings, Play Store, WhatsApp, Contacts, and Phone/Dialer.
     */
    val DOM_ONLY_PACKAGES = setOf(
        SETTINGS,                          // com.android.settings
        "com.android.vending",             // Play Store
        WHATSAPP,                          // com.whatsapp
        "com.google.android.contacts",     // Google Contacts
        "com.android.contacts",            // AOSP / OEM Contacts
        "com.samsung.android.app.contacts", // Samsung Contacts
        "com.google.android.dialer",       // Google Phone / Dialer
        "com.android.dialer",              // AOSP / OEM Dialer
        "com.samsung.android.dialer",      // Samsung Phone / Dialer
        "com.android.incallui",            // In-call UI
    )

    private val CONTACTS_OR_DIALER_REGEX = Regex(
        "(?i)(^|\\.)(contacts|dialer|phone|telecom|incallui)(\\.|$)"
    )

    /**
     * True when [pkg] is a clean, text-heavy package (Android Settings, Play Store, WhatsApp,
     * Contacts, Phone/Dialer, or Launcher) that skips takeScreenshot() on routine turns.
     * For all other allowed apps (shopping, media, unfamiliar apps like Flipkart/Swiggy/Myntra),
     * a 540px JPEG-60 screenshot is attached.
     */
    fun isDomOnlyApp(pkg: String?): Boolean =
        !pkg.isNullOrBlank() && (
            pkg in DOM_ONLY_PACKAGES ||
                isLauncher(pkg) ||
                isAskFirst(pkg) ||
                CONTACTS_OR_DIALER_REGEX.containsMatchIn(pkg)
            )

    /** Number pads are normal in these apps, so a pad alone does not mean a PIN is being asked for. */
    fun hasOrdinaryNumberPad(pkg: String?): Boolean = pkg != null && (isClock(pkg) || isCalculator(pkg))

    /** Allowed by default unless on the [NEVER] / financial blocklist or in [ASK_FIRST]. */
    private fun isAlwaysAllowed(pkg: String?): Boolean =
        !pkg.isNullOrBlank() && !isAskFirst(pkg) && !isFinancialOrSecureApp(pkg) && !NEVER.containsMatchIn(pkg)

    /** The gate before every read: an allowed app, or an ask-first app she has said Haan to. */
    fun isAllowed(pkg: String?, now: Long = System.currentTimeMillis()): Boolean =
        isAlwaysAllowed(pkg) || access.mayRead(pkg, now)

    fun isAskFirst(pkg: String?): Boolean = pkg != null && pkg in ASK_FIRST

    fun tier(pkg: String?): Tier = when {
        pkg.isNullOrBlank() -> Tier.BLOCKED
        isAskFirst(pkg) -> Tier.ASK_FIRST
        isFinancialOrSecureApp(pkg) || NEVER.containsMatchIn(pkg) -> Tier.NEVER
        else -> Tier.ALLOWED
    }

    fun isLauncher(pkg: String?): Boolean = pkg != null && (pkg in LAUNCHERS || pkg in phoneLaunchers)

    fun label(pkg: String?): String = when {
        pkg == null -> "unknown app"
        isLauncher(pkg) -> "Phone home or recent apps screen"
        isClock(pkg) -> "Clock"
        isCalculator(pkg) -> "Calculator"
        pkg == SETTINGS -> "Settings"
        pkg == "com.android.vending" -> "Play Store"
        pkg in ASK_FIRST -> ASK_FIRST.getValue(pkg)
        !isAllowed(pkg) -> "an app Saathi cannot see"
        else -> appLabelResolver?.invoke(pkg)?.takeIf { it.isNotBlank() }
            ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
