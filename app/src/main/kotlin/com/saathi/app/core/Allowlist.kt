package com.saathi.app.core

/**
 * The only apps Saathi may read. An allowlist is safer than a blocklist: a new SMS or bank app
 * installed later stays blocked automatically.
 *
 * Android's packageNames setting filters accessibility events only. It does not stop a service
 * from reading the active window of another app, so the code must also check this list before
 * every read. [isAllowed] is that second gate.
 */
object Allowlist {
    const val GPAY = "com.google.android.apps.nbu.paisa.user"
    const val SETTINGS = "com.android.settings"
    const val SYSTEM_UI = "com.android.systemui"

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

    private val APPS = mapOf(
        GPAY to "Google Pay",
        SETTINGS to "Settings",
        "com.google.android.youtube" to "YouTube",
        "com.android.vending" to "Play Store",
        "com.openai.chatgpt" to "ChatGPT",
        "com.google.android.apps.bard" to "Gemini",
        "com.google.android.apps.maps" to "Google Maps",
    )

    /** The fixed list. accessibility_service_config.xml must match it exactly. */
    val ALL: Set<String> = APPS.keys + LAUNCHERS + CLOCKS + CALCULATORS

    // This phone's own Clock, Calculator and home screen apps, found from Android at startup,
    // because phone makers name them differently (Vivo, Tecno and others).
    @Volatile private var phoneClocks: Set<String> = emptySet()
    @Volatile private var phoneCalculators: Set<String> = emptySet()
    @Volatile private var phoneLaunchers: Set<String> = emptySet()

    /** Apps that answer alarm or home intents but must never be read, such as the Google app. */
    private val NEVER = Regex(
        "(?i)whatsapp|messaging|truecaller|android\\.gm|systemui|sbi|hdfc|icici|" +
            "googlequicksearchbox|googleassistant|bank|paytm|phonepe"
    )

    /**
     * Adds the phone's own apps. Callers pass only preinstalled system apps. Anything matching
     * [NEVER] is dropped here as well. Returns what was added beyond the fixed list.
     */
    fun addPhoneApps(clocks: Set<String>, calculators: Set<String>, launchers: Set<String>): Set<String> {
        fun clean(found: Set<String>) = found.filterTo(HashSet()) { !NEVER.containsMatchIn(it) && it !in APPS }
        phoneClocks = clean(clocks)
        phoneCalculators = clean(calculators)
        phoneLaunchers = clean(launchers)
        return (phoneClocks + phoneCalculators + phoneLaunchers) - ALL
    }

    private fun isClock(pkg: String) = pkg in CLOCKS || pkg in phoneClocks
    private fun isCalculator(pkg: String) = pkg in CALCULATORS || pkg in phoneCalculators

    /** Number pads are normal in these apps, so a pad alone does not mean a PIN is being asked for. */
    fun hasOrdinaryNumberPad(pkg: String?): Boolean = pkg != null && (isClock(pkg) || isCalculator(pkg))

    fun isAllowed(pkg: String?): Boolean =
        pkg != null && (pkg in ALL || pkg in phoneClocks || pkg in phoneCalculators || pkg in phoneLaunchers)

    fun isLauncher(pkg: String?): Boolean = pkg != null && (pkg in LAUNCHERS || pkg in phoneLaunchers)

    fun label(pkg: String?): String = when {
        pkg == null -> "unknown app"
        isLauncher(pkg) -> "Phone home or recent apps screen"
        isClock(pkg) -> "Clock"
        isCalculator(pkg) -> "Calculator"
        else -> APPS[pkg] ?: "an app Saathi cannot see"
    }
}
