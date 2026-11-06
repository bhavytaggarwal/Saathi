package com.saathi.app.core

import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/** One spoken line: Roman Hinglish for the bubble, Devanagari for a natural Hindi voice. */
data class Line(val text: String, val speech: String = text)

data class ListItem(val name: String, val nameHi: String, val qty: String)

data class AiReply(
    val mode: String,
    val line: Line,
    val pointAt: Int = 0,
    val gesture: String = "none",
    val task: String = "",
    val list: String = "shopping",
    val items: List<ListItem> = emptyList(),
    val remindAt: String = "",
    val remindTitle: String = "",
    val remindTitleHi: String = "",
) {
    companion object {
        val MODES = setOf(
            "step", "answer", "clarify", "done", "unsure", "need_screen", "need_screenshot",
            "list_add", "list_remove", "list_check", "list_read", "list_clear",
            "reminder_once", "reminder_daily", "reminder_list", "where_sound", "scam_warning",
        )

        /** Parses the model's reply. Returns null for anything that does not fit the contract. */
        fun parse(raw: String): AiReply? {
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = try {
                JSONObject(cleaned)
            } catch (_: Exception) {
                return null
            }
            val mode = json.optString("mode")
            if (mode !in MODES) return null
            val text = json.optString("text").trim()
            val speech = json.optString("speech").trim().ifEmpty { text }
            val items = buildList {
                val array = json.optJSONArray("items") ?: return@buildList
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isEmpty()) continue
                    add(ListItem(name, item.optString("name_hi").trim().ifEmpty { name }, item.optString("qty").trim()))
                }
            }
            return AiReply(
                mode = mode,
                line = Line(text, speech),
                pointAt = json.optInt("point_at", 0),
                gesture = json.optString("gesture", "none").ifEmpty { "none" },
                task = json.optString("task").trim(),
                list = if (json.optString("list") == "todo") "todo" else "shopping",
                items = items,
                remindAt = json.optString("remind_at").trim(),
                remindTitle = json.optString("remind_title").trim(),
                remindTitleHi = json.optString("remind_title_hi").trim(),
            )
        }

        /** Reads "yyyy-MM-dd HH:mm" and returns epoch millis, or null if it is not a real time. */
        fun parseOnceTime(value: String): Long? = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.parse(value)?.time
        } catch (_: ParseException) {
            null
        }

        /** Reads "HH:mm" and returns hour to minute, or null if it is not a real time. */
        fun parseDailyTime(value: String): Pair<Int, Int>? {
            val match = Regex("^(\\d{1,2}):(\\d{2})$").find(value) ?: return null
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            return if (hour in 0..23 && minute in 0..59) hour to minute else null
        }
    }
}
