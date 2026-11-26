package com.saathi.app.core

import java.util.Locale

/**
 * The language Saathi speaks, listens in and replies in. Chosen once in setup, before anything
 * else. A language is "ready" only when every fixed line, word list and guide exists for it and
 * has been checked, so Saathi never mixes in Hindi for someone who asked for another language.
 */
enum class Language(
    val code: String,
    /** BCP 47 tag for speech recognition, the speech pack and the voice. */
    val tag: String,
    /** The name in its own script, as she would recognise it. */
    val nativeName: String,
    /** The English name, for the model prompt and for logs. */
    val englishName: String,
    val ready: Boolean,
) {
    HINDI("hi", "hi-IN", "हिन्दी", "Hindi", ready = true),
    ENGLISH("en", "en-IN", "English", "English", ready = true),
    // Drafted, still to be checked by a native speaker before wide use.
    KANNADA("kn", "kn-IN", "ಕನ್ನಡ", "Kannada", ready = true),
    TELUGU("te", "te-IN", "తెలుగు", "Telugu", ready = false),
    TAMIL("ta", "ta-IN", "தமிழ்", "Tamil", ready = false),
    MARATHI("mr", "mr-IN", "मराठी", "Marathi", ready = false),
    BENGALI("bn", "bn-IN", "বাংলা", "Bengali", ready = false),
    GUJARATI("gu", "gu-IN", "ગુજરાતી", "Gujarati", ready = false),
    MALAYALAM("ml", "ml-IN", "മലയാളം", "Malayalam", ready = false),
    PUNJABI("pa", "pa-IN", "ਪੰਜਾਬੀ", "Punjabi", ready = false),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        val DEFAULT = HINDI

        fun fromCode(code: String?): Language = entries.firstOrNull { it.code == code && it.ready } ?: DEFAULT

        /** The language to suggest first: the phone's own language when Saathi speaks it. */
        fun suggestedFor(phoneLanguage: String?): Language {
            val code = phoneLanguage.orEmpty().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
            return entries.firstOrNull { it.code == code && it.ready } ?: DEFAULT
        }
    }
}

/** The language in use right now. Prefs sets it whenever it is read or changed. */
object Lang {
    @Volatile
    var current: Language = Language.DEFAULT

    /** Picks the version of something for the language in use. */
    fun <T> pick(hi: T, en: T, kn: T): T = when (current) {
        Language.ENGLISH -> en
        Language.KANNADA -> kn
        else -> hi
    }
}
