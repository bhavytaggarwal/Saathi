package com.saathi.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import java.util.Locale

/**
 * Builds OEM- and locale-aware Hindi TTS instructions for enabling Saathi in Android Accessibility
 * Settings, and attaches `:settings:fragment_args_key` + `:settings:show_fragment_args` so
 * supported Android phones highlight the Saathi row automatically.
 */
object AccessibilitySetupGuide {
    const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    const val FALLBACK_SPATIAL_INSTRUCTION =
        "Is list mein neeche jaakar 'Saathi' ka icon dhoondhiye, us par dabaiye, button ko on kijiye, aur neeche confirm daba dijiye."

    private val DOWNLOADED_APPS_OEMS = listOf(
        "xiaomi",
        "redmi",
        "poco",
        "oppo",
        "oneplus",
        "realme",
        "vivo",
        "iqoo",
    )

    /** Reads the active system Settings language code (e.g. "en", "hi", "ta"). */
    fun systemLanguage(): String =
        runCatching { Resources.getSystem().configuration.locales[0].language }
            .getOrNull()
            .orEmpty()
            .ifBlank { Locale.getDefault().language }
            .lowercase(Locale.ROOT)

    /** Reads the device OEM manufacturer in lowercase (e.g. "samsung", "xiaomi", "vivo"). */
    fun deviceManufacturer(): String =
        runCatching { Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT) }
            .getOrDefault("")

    /**
     * Returns the spoken Hindi TTS instruction tailored to the phone's system Settings language
     * ([language]) and OEM skin ([manufacturer]).
     */
    fun spokenInstruction(
        language: String = systemLanguage(),
        manufacturer: String = deviceManufacturer(),
    ): String {
        val lang = language.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
        val oem = manufacturer.trim().lowercase(Locale.ROOT)
        val isSamsung = "samsung" in oem
        val isDownloadedAppsOem = DOWNLOADED_APPS_OEMS.any { it in oem }

        return when (lang) {
            "en" -> when {
                isSamsung ->
                    "Ab 'Installed apps' par dabaiye, phir 'Saathi' ko chalu karke 'Allow' daba dijiye."
                isDownloadedAppsOem ->
                    "Ab 'Downloaded apps' par dabaiye, phir 'Saathi' ko chalu karke 'Allow' daba dijiye."
                else ->
                    "Ab 'Downloaded apps' ya 'Saathi' par dabaiye, phir 'Saathi' ko chalu karke 'Allow' daba dijiye."
            }
            "hi" -> when {
                isSamsung ->
                    "Ab 'इंस्टॉल किए गए ऐप्स' par dabaiye, phir 'Saathi' ko chalu karke 'अनुमति दें' daba dijiye."
                else ->
                    "Ab 'डाउनलोड किए गए ऐप्स' par dabaiye, phir 'Saathi' ko chalu karke 'अनुमति दें' daba dijiye."
            }
            else -> FALLBACK_SPATIAL_INSTRUCTION
        }
    }

    /**
     * Builds an [Intent] for [Settings.ACTION_ACCESSIBILITY_SETTINGS] with
     * `:settings:fragment_args_key` and `:settings:show_fragment_args` pointing to
     * [SaathiAccessibilityService] so supported devices scroll to and highlight Saathi automatically.
     */
    fun createSettingsIntent(context: Context, newTask: Boolean = false): Intent {
        val component = ComponentName(
            context.packageName,
            SaathiAccessibilityService::class.java.name,
        ).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
            val bundle = Bundle().apply {
                putString(EXTRA_FRAGMENT_ARG_KEY, component)
            }
            putExtra(EXTRA_SHOW_FRAGMENT_ARGS, bundle)
            if (newTask) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
