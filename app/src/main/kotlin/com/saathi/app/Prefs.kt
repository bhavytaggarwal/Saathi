package com.saathi.app

import android.content.Context
import android.provider.Settings
import com.saathi.app.core.Lang
import com.saathi.app.core.Language
import org.json.JSONObject
import java.security.MessageDigest

/** Local settings and memory. */
class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("saathi", Context.MODE_PRIVATE)

    init {
        // Every part of the app makes its own Prefs, so this keeps them all on one language.
        language
    }

    /** Anonymous SHA-256 hash of ANDROID_ID for Cloud Run per-device quota checks. */
    val deviceId: String = run {
        val rawId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().ifBlank { "unknown-device" }
        MessageDigest.getInstance("SHA-256")
            .digest(rawId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    var model: String
        get() = prefs.getString("model", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString("model", value.trim()).apply()

    /** Saathi's language. Chosen first in setup. Reading it also makes it the one in use. */
    var language: Language
        get() = Language.fromCode(prefs.getString("language", null)).also { Lang.current = it }
        set(value) {
            prefs.edit().putString("language", value.code).putBoolean("language_chosen", true).apply()
            Lang.current = value
        }

    /** Shows the language step again, from "Change language" in setup. */
    fun clearLanguageChoice() {
        prefs.edit().putBoolean("language_chosen", false).apply()
    }

    /** False until she or her family has picked a language, which is the first setup step. */
    val languageChosen: Boolean
        get() = prefs.getBoolean("language_chosen", false)

    var herName: String
        get() = prefs.getString("her_name", "").orEmpty()
        set(value) = prefs.edit().putString("her_name", value.trim()).apply()

    var sonName: String
        get() = prefs.getString("son_name", "").orEmpty()
        set(value) = prefs.edit().putString("son_name", value.trim()).apply()

    var voiceName: String?
        get() = prefs.getString("voice", null)
        set(value) = prefs.edit().putString("voice", value).apply()

    var speechRate: Float
        get() = prefs.getFloat("rate", DEFAULT_RATE)
        set(value) = prefs.edit().putFloat("rate", value.coerceIn(0.55f, 1.25f)).apply()

    /** Off by default, so her voice stays on the phone. Needed only if no Hindi pack can be installed. */
    var allowOnlineSpeech: Boolean
        get() = prefs.getBoolean("online_speech", false)
        set(value) = prefs.edit().putBoolean("online_speech", value).apply()

    /** After an answer the mic reopens once for a follow-up. It closes if she stays silent. */
    var followUpListening: Boolean
        get() = prefs.getBoolean("follow_up", true)
        set(value) = prefs.edit().putBoolean("follow_up", value).apply()

    /** Off by default. When on, each turn is saved on the phone, masked, for building eval cases. */
    var evalLog: Boolean
        get() = prefs.getBoolean("eval_log", false)
        set(value) = prefs.edit().putBoolean("eval_log", value).apply()

    var oemAutoStartDone: Boolean
        get() = prefs.getBoolean("oem_autostart_done", false)
        set(value) = prefs.edit().putBoolean("oem_autostart_done", value).apply()

    var oemRecentsLockDone: Boolean
        get() = prefs.getBoolean("oem_recents_lock_done", false)
        set(value) = prefs.edit().putBoolean("oem_recents_lock_done", value).apply()

    /** True when Saathi automatically called disableSelf() so a strict banking app could open. */
    var pausedForBanking: Boolean
        get() = prefs.getBoolean("paused_for_banking", false)
        set(value) = prefs.edit().putBoolean("paused_for_banking", value).apply()

    /** True once Accessibility has connected at least once, so app launch never nags when disabled. */
    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    var bubbleX: Int
        get() = prefs.getInt("bubble_x", -1)
        set(value) = prefs.edit().putInt("bubble_x", value).apply()

    var bubbleY: Int
        get() = prefs.getInt("bubble_y", -1)
        set(value) = prefs.edit().putInt("bubble_y", value).apply()

    /** Tasks she has finished and how many times, so hints can shrink as she learns. */
    fun learnedTasks(): Map<String, Int> {
        val json = JSONObject(prefs.getString("learned", "{}") ?: "{}")
        return json.keys().asSequence().associateWith { json.optInt(it) }
    }

    fun recordLearned(task: String) {
        if (task.isBlank()) return
        val json = JSONObject(prefs.getString("learned", "{}") ?: "{}")
        val key = task.trim().lowercase().take(80)
        json.put(key, json.optInt(key) + 1)
        prefs.edit().putString("learned", json.toString()).apply()
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.6-flash"
        const val DEFAULT_RATE = 0.85f
    }
}
