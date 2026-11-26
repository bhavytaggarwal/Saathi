package com.saathi.app.core

import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One spoken line: Roman Hinglish for the bubble, Devanagari for a natural Hindi voice. */
data class Line(val text: String, val speech: String = text)

data class ListItem(val name: String, val nameHi: String, val qty: String)

/** Something Saathi saves itself. The model proposes it, the app decides whether to save it. */
data class ReplyAction(
    val type: String,
    val list: String = "shopping",
    val items: List<ListItem> = emptyList(),
    val text: String = "",
    val textHi: String = "",
    val time: String = "",
    val repeat: String = "once",
)

data class AiReply(
    val intent: String,
    val confidence: Double,
    val line: Line,
    val targetElement: Int? = null,
    val gesture: String = "none",
    val action: ReplyAction? = null,
    val statePatch: JSONObject? = null,
    val raw: String = "",
    /** A prepared guide the model chose for her request, from [Guides.IDS]. The app runs it. */
    val guide: String? = null,
) {
    sealed interface Parsed {
        data class Ok(val reply: AiReply) : Parsed

        /** reason is sent back to the model once, so it can correct itself. */
        data class Invalid(val reason: String) : Parsed
    }

    companion object {
        val INTENTS = setOf(
            "guide_step", "goal_done", "list_add", "list_remove", "list_check", "list_read", "list_clear",
            "reminder_set", "reminder_list", "alarm_teach", "sound_find", "bank_text_help", "chat", "clarify", "stop",
        )

        /** Intents that change what is saved on the phone, so a low confidence asks first. */
        val SAVING_INTENTS = setOf("list_add", "list_remove", "list_clear", "reminder_set")

        private val POINTING_INTENTS = setOf("guide_step", "alarm_teach")
        private val NEEDS_ITEMS = setOf("list_add", "list_remove", "list_check")

        /** Intents whose words come from the model, so empty words mean a broken reply. */
        private val NEEDS_WORDS = setOf("chat", "clarify", "bank_text_help")

        val GESTURES = setOf("none", "tap", "swipe_up", "swipe_down", "swipe_left", "swipe_right", "nav_recents", "nav_back", "nav_home")

        /** Parses the model's reply against the contract. Anything that does not fit is Invalid. */
        fun parse(raw: String): Parsed {
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = try {
                JSONObject(cleaned)
            } catch (_: Exception) {
                return Parsed.Invalid("it was not one JSON object")
            }
            val intent = json.optString("intent")
            if (intent !in INTENTS) return Parsed.Invalid("intent \"$intent\" is not one of the allowed intents")
            if (!json.has("confidence") || json.isNull("confidence")) return Parsed.Invalid("confidence is missing")
            val confidence = json.optDouble("confidence", 0.0).takeIf { !it.isNaN() }?.coerceIn(0.0, 1.0)
                ?: return Parsed.Invalid("confidence must be a number from 0 to 1")

            val roman = json.optString("say_roman").trim()
            val speech = json.optString("say_devanagari").trim().ifEmpty { roman }
            // Only Hindi has two scripts. In English or Kannada the bubble shows exactly what she
            // hears, so a Roman or Hinglish say_roman can never put Hindi on her screen.
            val text = if (Lang.current == Language.HINDI) roman else speech.ifEmpty { roman }
            if (intent in NEEDS_WORDS && text.isEmpty()) return Parsed.Invalid("say_roman is empty")

            val target = if (intent in POINTING_INTENTS && json.has("target_element") && !json.isNull("target_element")) {
                json.optInt("target_element", 0).takeIf { it > 0 }
            } else {
                null
            }
            val action = json.optJSONObject("action")?.let(::parseAction)
            if (intent in NEEDS_ITEMS && action?.items.isNullOrEmpty()) return Parsed.Invalid("$intent needs action.items")
            if (intent == "reminder_set") {
                if (action == null || action.text.isBlank()) return Parsed.Invalid("reminder_set needs action.text and action.time")
                val validTime = if (action.repeat == "daily") {
                    parseDailyTime(action.time) != null
                } else {
                    parseOnceTime(action.time) != null || parseDailyTime(action.time) != null
                }
                if (!validTime) {
                    return Parsed.Invalid("action.time \"${action.time}\" must be \"HH:mm\" for daily or \"yyyy-MM-dd HH:mm\" for once")
                }
            }
            return Parsed.Ok(
                AiReply(
                    intent = intent,
                    confidence = confidence,
                    line = Line(text, speech),
                    targetElement = target,
                    gesture = json.optString("gesture", "none").takeIf { it in GESTURES } ?: "none",
                    action = action,
                    statePatch = json.optJSONObject("state_patch"),
                    raw = cleaned,
                    guide = json.optString("guide").takeIf { it in Guides.IDS },
                )
            )
        }

        private fun parseAction(json: JSONObject): ReplyAction {
            val items = buildList {
                val array = json.optJSONArray("items") ?: return@buildList
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isEmpty()) continue
                    add(ListItem(name, item.optString("name_hi").trim().ifEmpty { name }, item.optString("qty").trim()))
                }
            }
            val text = json.optString("text").trim()
            return ReplyAction(
                type = json.optString("type").trim(),
                list = if (json.optString("list") == "todo") "todo" else "shopping",
                items = items,
                text = text,
                textHi = json.optString("text_hi").trim().ifEmpty { text },
                time = json.optString("time").trim(),
                repeat = if (json.optString("repeat") == "daily") "daily" else "once",
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

        /** The next time the clock shows hour:minute, today or tomorrow. */
        fun nextTime(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
            return calendar.timeInMillis
        }
    }
}
