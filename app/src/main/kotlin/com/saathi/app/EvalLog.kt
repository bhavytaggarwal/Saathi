package com.saathi.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * An on-device log of turns for building eval cases. Off by default and turned on in setup.
 *
 * Each line holds the turn, her masked words, the state before the turn, the reply, whether it
 * passed validation, the latency and the model. No screen text is written: the masked screen title
 * is removed from the state. "Galat tha" copies the last line to a second file.
 */
class EvalLog(context: Context, private val prefs: Prefs) {
    private val dir: File? = context.applicationContext.getExternalFilesDir(null)
    private val io = Executors.newSingleThreadExecutor()
    private var lastLine: String? = null

    /** Directory where local eval logs are written. */
    val folder: String? get() = dir?.absolutePath

    /** One model turn, logged with level "gemini". Turns answered on the phone use [writeLocal]. */
    fun write(
        words: String?,
        stateBefore: JSONObject,
        reply: String?,
        validation: String,
        latencyMs: Long,
        model: String?,
        intent: String? = null,
        tokensIn: Int = 0,
        tokensOut: Int = 0,
    ) {
        if (!prefs.evalLog) return
        val replyJson: Any = reply?.let { runCatching { JSONObject(it) }.getOrNull() ?: it } ?: JSONObject.NULL
        record(
            JSONObject()
                .put("time", System.currentTimeMillis())
                .put("turn", stateBefore.optInt("turn"))
                .put("level", "gemini")
                .put("words", words ?: JSONObject.NULL)
                .put("intent", intent ?: JSONObject.NULL)
                .put("state_before", withoutScreenText(stateBefore))
                .put("reply", replyJson)
                .put("validation", validation)
                .put("latency_ms", latencyMs)
                .put("model", model ?: JSONObject.NULL)
                .put("tokens_in", tokensIn)
                .put("tokens_out", tokensOut)
        )
    }

    /**
     * A turn answered on the phone with no model call, such as "phir se", an OTP warning or a
     * guide step. words is null for a step that followed a screen change.
     */
    fun writeLocal(words: String?, rule: String) {
        if (!prefs.evalLog) return
        record(
            JSONObject()
                .put("time", System.currentTimeMillis())
                .put("level", "local")
                .put("words", words ?: JSONObject.NULL)
                .put("intent", rule)
                .put("validation", "ok")
                .put("latency_ms", 0)
        )
    }

    private fun record(json: JSONObject) {
        val line = json.toString()
        lastLine = line
        append(LOG_FILE, line)
    }

    /** Marks the last turn as wrong. Returns false if there was nothing to mark. */
    fun markLast(): Boolean {
        if (!prefs.evalLog) return false
        val line = lastLine ?: return false
        lastLine = null
        append(MARKED_FILE, JSONObject(line).put("marked", true).toString())
        return true
    }

    fun clear() {
        lastLine = null
        val folder = dir ?: return
        io.execute {
            File(folder, LOG_FILE).delete()
            File(folder, MARKED_FILE).delete()
        }
    }

    private fun withoutScreenText(state: JSONObject): JSONObject {
        val copy = JSONObject(state.toString())
        copy.optJSONObject("screen")?.remove("title")
        return copy
    }

    private fun append(name: String, line: String) {
        val folder = dir ?: return
        io.execute {
            runCatching {
                val file = File(folder, name)
                if (file.length() < MAX_BYTES) file.appendText(line + "\n")
            }
        }
    }

    companion object {
        const val LOG_FILE = "turns.jsonl"
        const val MARKED_FILE = "marked.jsonl"
        private const val MAX_BYTES = 5L * 1024 * 1024
    }
}
