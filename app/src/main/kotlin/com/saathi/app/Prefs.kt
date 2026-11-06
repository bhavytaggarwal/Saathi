package com.saathi.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local settings and memory. The API key is encrypted with a key held by Android Keystore. */
class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("saathi", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, null)?.let(::decrypt).orEmpty().ifBlank { BuildConfig.EMBEDDED_GEMINI_KEY }
        set(value) = prefs.edit().putString(KEY_API, if (value.isBlank()) null else encrypt(value.trim())).apply()

    var model: String
        get() = prefs.getString("model", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString("model", value.trim()).apply()

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
        set(value) = prefs.edit().putFloat("rate", value.coerceIn(0.55f, 1.1f)).apply()

    /** Off by default, so her voice stays on the phone. Needed only if no Hindi pack can be installed. */
    var allowOnlineSpeech: Boolean
        get() = prefs.getBoolean("online_speech", false)
        set(value) = prefs.edit().putBoolean("online_speech", value).apply()

    /** After an answer the mic reopens once for a follow-up. It closes if she stays silent. */
    var followUpListening: Boolean
        get() = prefs.getBoolean("follow_up", true)
        set(value) = prefs.edit().putBoolean("follow_up", value).apply()

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

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, sealed, 0, IV_SIZE))
        String(cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE))
    }.getOrNull()

    companion object {
        const val DEFAULT_MODEL = "gemini-3.6-flash"
        const val DEFAULT_RATE = 0.85f
        private const val KEY_API = "api_key_sealed"
        private const val ALIAS = "saathi_api_key"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
