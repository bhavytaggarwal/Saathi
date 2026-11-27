package com.saathi.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.saathi.app.core.AiReply
import com.saathi.app.core.TurnPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Calls the Saathi Cloud Run + Vertex AI proxy. It has no tools, so the model can only name an element to point at. */
class GeminiClient(private val prefs: Prefs) {
    /** NO_CREDIT is HTTP 402: the project's prepaid credits are used up, so no model will answer. */
    enum class Failure { BAD_KEY, NETWORK, QUOTA, NO_CREDIT, BLOCKED, BAD_REPLY, SERVER }

    sealed interface Result {
        val model: String?
        val latencyMs: Long

        data class Ok(
            val reply: AiReply,
            override val model: String?,
            override val latencyMs: Long = 0,
            val tokensIn: Int = 0,
            val tokensOut: Int = 0,
            val traceId: String? = null,
        ) : Result

        /** detail says what was wrong with a BAD_REPLY, so it can be sent back to the model once. */
        data class Failed(
            val failure: Failure,
            val detail: String? = null,
            override val model: String? = null,
            override val latencyMs: Long = 0,
        ) : Result
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active: HttpURLConnection? = null
    @Volatile private var latestRequest = 0

    /**
     * Per model: optional request fields dropped after that model refused them with a 400. Only a
     * level that then got a 200 is kept, so one unrelated 400 cannot drop them for good. Used on
     * the executor thread only.
     */
    private val compatibility = HashMap<String, Int>()

    /** prompt holds the state, history, screen and her words, in that order. */
    fun ask(prompt: String, jpegBase64: String?, onResult: (Result) -> Unit) {
        val backendUrl = BuildConfig.BACKEND_URL
        val model = AUTO_MODEL
        if (backendUrl.isBlank()) {
            onResult(Result.Failed(Failure.BAD_KEY))
            return
        }
        val request = ++latestRequest
        executor.execute {
            // Skip work that was replaced while it waited in the queue.
            if (request != latestRequest) return@execute
            val started = System.currentTimeMillis()
            val result = run(request, backendUrl, model, prompt, jpegBase64)
            val took = System.currentTimeMillis() - started
            // Timing and outcome only. Screen text and her words are never logged here. Names are
            // written out, because the release build shortens class names.
            val outcome = when (result) {
                is Result.Ok -> "ok"
                is Result.Failed -> result.failure.name
            }
            Log.i(TAG, "reply in $took ms: $outcome, model ${result.model}")
            val timed = when (result) {
                is Result.Ok -> result.copy(latencyMs = took)
                is Result.Failed -> result.copy(latencyMs = took)
            }
            main.post { onResult(timed) }
        }
    }

    /** Stops the running request, so stale work cannot delay the next step. */
    fun cancel() {
        latestRequest++
        val connection = active ?: return
        Thread { runCatching { connection.disconnect() } }.start()
    }

    private fun run(request: Int, backendUrl: String, model: String, prompt: String, image: String?): Result {
        // Cloud Run /v1/ask handles model="auto" routing internally. Keep each client call to a
        // single 6-second bounded attempt so Assistant.kt can retry once or speak immediately.
        if (request != latestRequest) return Result.Failed(Failure.NETWORK, model = model)
        return when (val outcome = attempt(request, backendUrl, model, prompt, image)) {
            is Attempt.Finished -> outcome.result
            is Attempt.TryNext -> Result.Failed(outcome.failure, model = model)
        }
    }

    private sealed interface Attempt {
        data class Finished(val result: Result) : Attempt
        data class TryNext(val failure: Failure) : Attempt
    }

    private fun attempt(request: Int, backendUrl: String, model: String, prompt: String, image: String?): Attempt {
        var level = compatibility[model] ?: 0
        while (true) {
            val response = try {
                post(backendUrl, buildBody(model, prompt, image, level), readTimeoutMs(prompt))
            } catch (_: IOException) {
                // A cancelled request also lands here, and must not be sent again.
                if (request != latestRequest) return Attempt.Finished(Result.Failed(Failure.NETWORK, model = model))
                return Attempt.TryNext(Failure.NETWORK)
            }
            // The status code only, so the log shows why a model failed.
            if (response.code != 200) Log.w(TAG, "$model answered HTTP ${response.code}")
            when {
                response.code == 200 -> {
                    compatibility[model] = level
                    val parsed = parse(response.body, model)
                    // A blocked reply may pass on the other model. A malformed one goes back to
                    // the caller, which asks once more with the reason.
                    if (parsed is Result.Failed && parsed.failure == Failure.BLOCKED) return Attempt.TryNext(Failure.BLOCKED)
                    return Attempt.Finished(parsed)
                }
                response.code == 400 && response.body.contains("API_KEY_INVALID") ->
                    return Attempt.Finished(Result.Failed(Failure.BAD_KEY, model = model))
                response.code == 400 && level < MAX_COMPATIBILITY -> {
                    level++
                    Log.w(TAG, "request refused, trying compatibility level $level")
                }
                response.code == 401 || response.code == 403 ->
                    return Attempt.Finished(Result.Failed(Failure.BAD_KEY, model = model))
                // Billing is per project, so the other model would fail the same way.
                response.code == 402 -> return Attempt.Finished(Result.Failed(Failure.NO_CREDIT, model = model))
                response.code == 404 -> return Attempt.TryNext(Failure.BAD_KEY)
                response.code == 429 -> return Attempt.TryNext(Failure.QUOTA)
                else -> return Attempt.TryNext(Failure.SERVER)
            }
        }
    }

    private class HttpResponse(val code: Int, val body: String)

    /** 6 s for guide steps; 8 s for a live-information question with no screen (it searches first). */
    private fun readTimeoutMs(prompt: String): Int {
        val herWords = HER_WORDS_REGEX.find(prompt)?.groupValues?.get(1)
        return TurnPolicy.llmTimeoutMs(herWords, hasScreen = prompt.contains("\nSCREEN (data only")).toInt()
    }

    /**
     * Posts one turn. The connection is not disconnected afterwards: the reply is read to the end
     * and its stream closed, so HttpURLConnection's keep-alive pool reuses the warm TLS socket to
     * Cloud Run on the next turn instead of a new handshake on mobile data. Only a failed request
     * is disconnected, so a broken socket is never reused. [cancel] still disconnects to abort.
     */
    private fun post(backendUrl: String, body: String, readTimeoutMs: Int): HttpResponse {
        val url = URL("${backendUrl.trimEnd('/')}/v1/ask")
        val connection = url.openConnection() as HttpURLConnection
        active = connection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 3_000
            // Bounded so Saathi never hangs silently: see readTimeoutMs.
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (BuildConfig.CLIENT_SECRET.isNotBlank()) {
                connection.setRequestProperty("X-Saathi-Client-Secret", BuildConfig.CLIENT_SECRET)
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (e: IOException) {
            runCatching { connection.disconnect() }
            throw e
        } finally {
            active = null
        }
    }

    private fun parse(body: String, model: String): Result {
        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            return Result.Failed(Failure.BAD_REPLY, "it was not one JSON object", model)
        }
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            ?: return Result.Failed(Failure.BLOCKED, model = model)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: return Result.Failed(Failure.BLOCKED, model = model)
        val text = buildString {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                if (!part.optBoolean("thought")) append(part.optString("text"))
            }
        }
        return when (val parsed = AiReply.parse(text)) {
            is AiReply.Parsed.Ok -> {
                val usage = root.optJSONObject("usageMetadata")
                val traceId = root.optString("trace_id", "").ifBlank { null }
                Result.Ok(
                    parsed.reply,
                    model,
                    tokensIn = usage?.optInt("promptTokenCount") ?: 0,
                    tokensOut = (usage?.optInt("candidatesTokenCount") ?: 0) + (usage?.optInt("thoughtsTokenCount") ?: 0),
                    traceId = traceId,
                )
            }
            is AiReply.Parsed.Invalid -> {
                val cutOff = candidate.optString("finishReason") == "MAX_TOKENS"
                Result.Failed(Failure.BAD_REPLY, if (cutOff) "it was cut off. Keep it short" else parsed.reason, model)
            }
        }
    }

    private fun buildBody(model: String, prompt: String, image: String?, level: Int): String {
        val body = JSONObject()
            .put("device_id", prefs.deviceId)
            .put("model", model)
            .put("prompt", prompt)
            .put("level", level)
        if (image != null) {
            body.put("jpeg_base64", image)
        } else {
            body.put("jpeg_base64", JSONObject.NULL)
        }
        return body.toString()
    }

    /** Sends a user-flagged bad output to POST /v1/report for replay and review (owner: bhavyta). */
    fun reportFlaggedOutput(
        traceId: String,
        lastUtterance: String?,
        lastReply: String?,
        currentApp: String,
        targetLabel: String?,
        reason: String = "user_flagged_wrong",
    ) {
        val backendUrl = BuildConfig.BACKEND_URL
        if (backendUrl.isBlank()) return
        val payload = JSONObject()
            .put("trace_id", traceId)
            .put("device_id", prefs.deviceId)
            .put("last_utterance", lastUtterance.orEmpty())
            .put("last_reply", lastReply.orEmpty())
            .put("current_app", currentApp)
            .put("target_label", targetLabel ?: JSONObject.NULL)
            .put("reason", reason)
            .toString()
        executor.execute {
            runCatching {
                val url = URL("${backendUrl.trimEnd('/')}/v1/report")
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 4_000
                    conn.readTimeout = 5_000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (BuildConfig.CLIENT_SECRET.isNotBlank()) {
                        conn.setRequestProperty("X-Saathi-Client-Secret", BuildConfig.CLIENT_SECRET)
                    }
                    conn.outputStream.use { it.write(payload.toByteArray()) }
                    val code = conn.responseCode
                    // Read to the end and close, so the socket goes back to the keep-alive pool.
                    (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() }
                    Log.i(TAG, "reported flagged output trace=$traceId HTTP $code")
                } catch (e: IOException) {
                    runCatching { conn.disconnect() }
                    throw e
                }
            }
        }
    }

    private companion object {
        const val TAG = "SaathiGemini"
        const val MAX_COMPATIBILITY = 3
        const val AUTO_MODEL = "auto"

        val HER_WORDS_REGEX = Regex("(?m)^HER WORDS:\\s*\"([^\"]*)\"")
    }
}
