package com.saathi.app.core

import java.util.Locale

sealed interface LocalAction {
    val intent: String

    data class ListRead(val list: String = "shopping") : LocalAction {
        override val intent: String = "list_read"
    }

    data class ListClear(val list: String = "shopping") : LocalAction {
        override val intent: String = "list_clear"
    }

    data class AlarmSet(
        val time: String,
        val hour: Int,
        val minute: Int,
        val line: Line,
    ) : LocalAction {
        override val intent: String = "alarm_set"
    }

    data class ReminderSet(
        val text: String,
        val textHi: String,
        val time: String,
        val repeat: String,
        val line: Line,
    ) : LocalAction {
        override val intent: String = "reminder_set"
    }
}

/**
 * Tier 1 On-Device Deterministic Parser for basic commands (<50ms latency, zero network calls).
 * Returns null for anything ambiguous, multi-turn, relative-time, or multi-item so Cloud Run
 * (Tier 2 Flash-Lite / Tier 3 Hydrogen) handles it.
 */
object LocalCommandParser {
    private val BYPASS_REGEX = Regex(
        "(?i)\\b(parso|parson|kal|agle|baad|minute|min|ghante|ghanta|aur)\\b|,|" +
            "परसों|कल|अगले|बाद|मिनट|घंटे|घंटा|\\bऔर\\b"
    )

    private val LIST_READ_REGEX = Regex(
        "(?i)^\\s*(?:(shopping|todo|to-do)\\s+)?(?:list|लिस्ट)\\s*" +
            "(?:mein\\s+kya\\s+hai|kya\\s+hai|padho|batao|dikhao|sunao|पढ़ो|बताओ|दिखाओ|सुनाओ)\\s*[.?!।]?\\s*$"
    )

    private val LIST_CLEAR_REGEX = Regex(
        "(?i)^\\s*(?:(shopping|todo|to-do)\\s+)?(?:list|लिस्ट)\\s*(?:ko\\s+)?" +
            "(?:clear|saaf|khali|हटाओ|साफ|साफ़|खाली)(?:\\s+kar(?:\\s+do|o)?)?\\s*[.?!।]?\\s*$"
    )

    private val ALARM_KEYWORD = Regex("(?i)\\b(alarm)\\b|अलार्म")
    private val REMINDER_KEYWORD = Regex("(?i)\\b(reminder|yaad)\\b|रिमाइंडर|याद")
    private val DAILY_KEYWORD = Regex(
        "(?i)\\b(roz|har\\s+roz|har\\s+din|har\\s+subah|har\\s+raat|har\\s+shaam|daily)\\b|" +
            "रोज़|रोज|हर\\s+रोज़|हर\\s+दिन|हर\\s+सुबह|हर\\s+रात|हर\\s+शाम"
    )

    private val PERIOD_MORNING = Regex("(?i)\\b(subah|morning)\\b|सुबह")
    private val PERIOD_NOON = Regex("(?i)\\b(dopahar|afternoon)\\b|दोपहर")
    private val PERIOD_EVENING = Regex("(?i)\\b(shaam|evening)\\b|शाम")
    private val PERIOD_NIGHT = Regex("(?i)\\b(raat|night)\\b|रात")

    private val TIME_REGEX = Regex(
        "(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(baje|बजे|am|pm)\\b"
    )

    private val REMINDER_FILLER_WORDS = setOf(
        "reminder", "yaad", "dila", "dilana", "dilaun", "dilao", "do", "dena", "dijiye",
        "laga", "lagao", "lagana", "set", "kar", "karo", "karna", "kijiye",
        "ka", "ki", "ke", "ko", "ke", "liye", "mein", "me", "pe", "par",
        "subah", "dopahar", "shaam", "raat", "morning", "afternoon", "evening", "night",
        "roz", "har", "din", "daily", "baje", "am", "pm", "mujhe", "please",
        "रिमाइंडर", "याद", "दिला", "दिलाना", "दिलाओ", "दो", "देना", "दीजिए",
        "लगा", "लगाओ", "लगाना", "कर", "करो", "करना", "कीजिए",
        "का", "की", "के", "को", "लिए", "में", "पे", "पर",
        "सुबह", "दोपहर", "शाम", "रात", "रोज़", "रोज", "हर", "दिन", "बजे", "मुझे",
    )

    fun parse(utterance: String, hasPendingQuestion: Boolean): LocalAction? {
        if (hasPendingQuestion) return null
        val clean = utterance.trim()
        if (clean.isEmpty()) return null

        // Strict bypass: relative dates, durations, or multi-item conjunctions always go to Cloud Run.
        if (BYPASS_REGEX.containsMatchIn(clean)) return null

        // 1. Simple List Read
        LIST_READ_REGEX.matchEntire(clean)?.let { match ->
            val rawList = match.groupValues[1].lowercase(Locale.ROOT)
            val listName = if (rawList.startsWith("to")) "todo" else "shopping"
            return LocalAction.ListRead(list = listName)
        }

        // 2. Simple List Clear
        LIST_CLEAR_REGEX.matchEntire(clean)?.let { match ->
            val rawList = match.groupValues[1].lowercase(Locale.ROOT)
            val listName = if (rawList.startsWith("to")) "todo" else "shopping"
            return LocalAction.ListClear(list = listName)
        }

        val hasAlarm = ALARM_KEYWORD.containsMatchIn(clean)
        val hasReminder = REMINDER_KEYWORD.containsMatchIn(clean)
        if (!hasAlarm && !hasReminder) return null

        // Extract explicit time + period
        val parsedTime = parseExplicitTime(clean) ?: return null
        val (hour24, minute, formattedTime, timeMatchRange) = parsedTime

        // 3. Simple Alarm (Explicit Time)
        if (hasAlarm && !hasReminder) {
            val line = Line(
                "Chaliye Clock app mein $formattedTime ka alarm lagate hain.",
                "चलिए Clock app में $formattedTime का अलार्म लगाते हैं।",
            )
            return LocalAction.AlarmSet(
                time = formattedTime,
                hour = hour24,
                minute = minute,
                line = line,
            )
        }

        // 4. Simple Reminder (Explicit Time + Non-empty Label)
        if (hasReminder) {
            val repeat = if (DAILY_KEYWORD.containsMatchIn(clean)) "daily" else "once"
            val withoutTime = clean.removeRange(timeMatchRange)
            val label = withoutTime
                .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { token ->
                    token.isNotBlank() && token.lowercase(Locale.ROOT) !in REMINDER_FILLER_WORDS
                }
                .joinToString(" ")
                .trim()

            if (label.isEmpty()) return null

            val repeatRoman = if (repeat == "daily") "roz " else ""
            val repeatHindi = if (repeat == "daily") "रोज़ " else ""
            val line = Line(
                "Theek hai, ${repeatRoman}$formattedTime $label yaad dila dungi.",
                "ठीक है, ${repeatHindi}$formattedTime $label याद दिला दूँगी।",
            )
            return LocalAction.ReminderSet(
                text = label,
                textHi = label,
                time = formattedTime,
                repeat = repeat,
                line = line,
            )
        }

        return null
    }

    private data class ExplicitTime(
        val hour24: Int,
        val minute: Int,
        val formatted: String,
        val range: IntRange,
    )

    private fun parseExplicitTime(text: String): ExplicitTime? {
        val match = TIME_REGEX.find(text) ?: return null
        val rawHour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return null
        val suffix = match.groupValues[3].lowercase(Locale.ROOT)
        if (minute !in 0..59 || rawHour !in 0..23) return null

        val isMorning = suffix == "am" || PERIOD_MORNING.containsMatchIn(text)
        val isNoon = PERIOD_NOON.containsMatchIn(text)
        val isEvening = PERIOD_EVENING.containsMatchIn(text)
        val isNight = PERIOD_NIGHT.containsMatchIn(text)
        val isPmSuffix = suffix == "pm"

        // If hour <= 12 and no AM/PM or time-of-day period is given, return null so Flash-Lite can clarify.
        if (rawHour in 1..12 && !isMorning && !isNoon && !isEvening && !isNight && !isPmSuffix) {
            return null
        }

        val hour24 = when {
            rawHour > 12 -> rawHour
            isMorning -> if (rawHour == 12) 0 else rawHour
            isNoon -> if (rawHour in 1..5) rawHour + 12 else rawHour
            isEvening || isPmSuffix -> if (rawHour == 12) 12 else rawHour + 12
            isNight -> when {
                rawHour == 12 -> 0
                rawHour in 1..3 -> rawHour
                else -> rawHour + 12
            }
            else -> rawHour
        }
        if (hour24 !in 0..23) return null
        val formatted = String.format(Locale.US, "%02d:%02d", hour24, minute)
        return ExplicitTime(hour24, minute, formatted, match.range)
    }
}
