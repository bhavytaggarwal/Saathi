package com.saathi.app.core

import org.json.JSONArray
import org.json.JSONObject

/** What she is trying to do. The model owns text, kind, app and status. The app owns step. */
data class Goal(
    val text: String,
    val kind: String = SessionState.KIND_CHAT,
    val app: String? = null,
    val status: String = SessionState.ACTIVE,
    val step: Int = 0,
    /** The prepared guide running for this goal, if any. Set by the app only. */
    val guide: String? = null,
)

/** What Saathi last pointed at. The model writes the label and expected screen, the app the rest. */
data class LastStep(
    val targetLabel: String? = null,
    val expectedNext: String? = null,
    val matched: Boolean? = null,
    val retries: Int = 0,
)

/**
 * Facts from the accessibility service. title is set only when the screen list is sent too. mode
 * tells the model what kind of screen list it can get: see [SessionState.MODE_FULL] and the rest.
 */
data class ScreenFacts(
    val app: String,
    val title: String?,
    val changed: Boolean,
    val readable: Boolean,
    val mode: String = SessionState.MODE_FULL,
)

data class PendingQuestion(val question: String, val options: List<String> = emptyList())

/** The last thing Saathi actually saved, so "aur Maggi bhi" and "usko hata do" have something to refer to. */
data class LastAction(
    val type: String,
    val list: String? = null,
    val items: List<String> = emptyList(),
    val text: String? = null,
    val time: String? = null,
    val repeat: String? = null,
)

/**
 * The small state object sent with every request instead of a long chat history. The model never
 * rewrites it. It sends a state_patch, and [applyPatch] merges that with fixed rules.
 *
 * Nothing in here comes from the screen except the masked title, and only on turns that send the
 * screen list anyway.
 */
class SessionState {
    var turn = 0
    var goal: Goal? = null
    var lastStep = LastStep()
    var screen: ScreenFacts? = null
    var pendingQuestion: PendingQuestion? = null
    var lastAction: LastAction? = null
    var lastActionAt = 0L

    /** When she last spoke or the screen last changed during a goal. */
    var lastActivityAt = 0L

    data class PatchOutcome(val goalStarted: Boolean, val setsTargetLabel: Boolean)

    fun toJson(now: Long): JSONObject = JSONObject()
        .put("turn", turn)
        .put("goal", goal?.let {
            JSONObject()
                .put("text", it.text)
                .put("kind", it.kind)
                .put("app", it.app ?: JSONObject.NULL)
                .put("status", it.status)
                .put("step", it.step)
                .put("guide", it.guide ?: JSONObject.NULL)
                .put("idle_s", ((now - lastActivityAt) / 1000).coerceAtLeast(0))
        } ?: JSONObject.NULL)
        .put(
            "last_step",
            JSONObject()
                .put("target_label", lastStep.targetLabel ?: JSONObject.NULL)
                .put("expected_next", lastStep.expectedNext ?: JSONObject.NULL)
                .put("matched", lastStep.matched ?: JSONObject.NULL)
                .put("retries", lastStep.retries),
        )
        .put("screen", screen?.let {
            JSONObject()
                .put("app", it.app)
                .put("title", it.title ?: JSONObject.NULL)
                .put("changed", it.changed)
                .put("readable", it.readable)
                .put("mode", it.mode)
        } ?: JSONObject.NULL)
        .put("pending_question", pendingQuestion?.let {
            JSONObject().put("question", it.question).put("options", JSONArray(it.options))
        } ?: JSONObject.NULL)
        .put("last_action", lastAction?.let {
            JSONObject()
                .put("type", it.type)
                .put("list", it.list ?: JSONObject.NULL)
                .put("items", JSONArray(it.items))
                .put("text", it.text ?: JSONObject.NULL)
                .put("time", it.time ?: JSONObject.NULL)
                .put("repeat", it.repeat ?: JSONObject.NULL)
        } ?: JSONObject.NULL)

    /**
     * Shallow merge per field, and null clears a field. Only goal (text, kind, app, status),
     * last_step (target_label, expected_next) and pending_question can be changed by the model.
     * A goal with a different kind, or a goal arriving when none is active, starts fresh.
     */
    fun applyPatch(patch: JSONObject?): PatchOutcome {
        if (patch == null) return PatchOutcome(goalStarted = false, setsTargetLabel = false)
        var started = false
        if (patch.has("goal")) {
            val value = patch.opt("goal")
            if (value !is JSONObject) {
                goal = null
                lastStep = LastStep()
            } else {
                val current = goal
                val kind = value.optString("kind").takeIf { it in KINDS }
                val isNew = current == null || current.status != ACTIVE || (kind != null && kind != current.kind)
                val base = if (isNew) Goal(text = "", kind = kind ?: KIND_CHAT) else current!!
                val text = value.textOrNull("text") ?: base.text
                val app = if (value.has("app")) value.textOrNull("app") else base.app
                val status = value.optString("status").takeIf { it in MODEL_STATUSES } ?: base.status
                goal = base.copy(text = text, kind = kind ?: base.kind, app = app, status = status)
                if (isNew) {
                    started = true
                    lastStep = LastStep()
                }
            }
        }
        var setsLabel = false
        if (patch.has("last_step")) {
            val value = patch.opt("last_step")
            if (value !is JSONObject) {
                lastStep = lastStep.copy(targetLabel = null, expectedNext = null)
            } else {
                if (value.has("target_label")) {
                    setsLabel = true
                    lastStep = lastStep.copy(targetLabel = value.textOrNull("target_label"))
                }
                if (value.has("expected_next")) {
                    lastStep = lastStep.copy(expectedNext = value.textOrNull("expected_next"))
                }
            }
        }
        if (patch.has("pending_question")) {
            pendingQuestion = when (val value = patch.opt("pending_question")) {
                is JSONObject -> value.textOrNull("question")?.let { question ->
                    val options = value.optJSONArray("options")
                    PendingQuestion(question, List(options?.length() ?: 0) { options!!.optString(it) }.filter(String::isNotBlank))
                }
                is String -> value.trim().takeIf(String::isNotEmpty)?.let(::PendingQuestion)
                else -> null
            }
        }
        return PatchOutcome(started, setsLabel)
    }

    private fun JSONObject.textOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).trim().takeIf(String::isNotEmpty)

    companion object {
        const val ACTIVE = "active"
        const val DONE = "done"
        const val ABANDONED = "abandoned"
        const val KIND_CHAT = "chat"
        val KINDS = setOf("guide", "list", "reminder", "alarm_teach", "sound", "bank_text_help", "chat")
        private val MODEL_STATUSES = setOf(ACTIVE, DONE, ABANDONED)

        /** An allowed app: the masked screen list. */
        const val MODE_FULL = "full"
        /** An ask-first app she said Haan to: known buttons only, everything else is [hidden]. */
        const val MODE_CONTROLS_ONLY = "controls_only"
        /** An ask-first app she has not been asked about yet. The app asks her before reading it. */
        const val MODE_ASK_FIRST = "ask_first"
        /** An ask-first app she said Nahi to. Help by conversation only. */
        const val MODE_NOT_SHARED = "not_shared"
        /** An app Saathi never reads. */
        const val MODE_BLOCKED = "blocked"

        /** Goals that point at the screen, so the screen list is sent with them. */
        val GUIDED_KINDS = setOf("guide", "alarm_teach", "sound")
    }
}
