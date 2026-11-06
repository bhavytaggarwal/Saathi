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

    private val APPS = mapOf(
        GPAY to "Google Pay",
        SETTINGS to "Settings",
        "com.google.android.youtube" to "YouTube",
        "com.android.vending" to "Play Store",
        "com.openai.chatgpt" to "ChatGPT",
        "com.google.android.apps.bard" to "Gemini",
    )

    val ALL: Set<String> = APPS.keys + LAUNCHERS

    fun isAllowed(pkg: String?): Boolean = pkg != null && pkg in ALL

    fun isLauncher(pkg: String?): Boolean = pkg != null && pkg in LAUNCHERS

    fun label(pkg: String?): String = when {
        pkg == null -> "unknown app"
        pkg in LAUNCHERS -> "Phone home or recent apps screen"
        else -> APPS[pkg] ?: "an app Saathi cannot see"
    }
}
