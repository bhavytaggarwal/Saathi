package com.saathi.app

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.saathi.app.core.PrivacyFilter
import com.saathi.app.core.ProbeRules
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Test phones only. Exists in builds made with -Psaathi.labelProbe=true, and R8 removes it from
 * every other build. It runs only while she has said Haan to an ask-first app, and writes the
 * labels of buttons, icons, menus and tabs to label_probe.jsonl, so the real labels can be
 * checked by hand against ControlsOnly. Nothing inside chat or message rows is written
 * ([ProbeRules]), numbers are masked, typed text is never read, and nothing goes to the log or
 * the network.
 */
object LabelProbe {
    private const val FILE = "label_probe.jsonl"
    private const val MAX_NODES = 900
    private const val MAX_DEPTH = 40
    private val io = Executors.newSingleThreadExecutor()
    private val written = HashSet<String>()

    fun record(context: Context, root: AccessibilityNodeInfo) {
        val lines = ArrayList<String>()
        var visited = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int, insideLabel: Boolean) {
            if (visited++ > MAX_NODES || depth > MAX_DEPTH) return
            val className = node.className?.toString()
            val clickable = node.isClickable || node.isLongClickable
            if (ProbeRules.keep(className, clickable, node.isEditable, insideLabel)) {
                val json = JSONObject()
                    .put("class", className?.substringAfterLast('.') ?: JSONObject.NULL)
                    .put("id", node.viewIdResourceName ?: JSONObject.NULL)
                    .put("clickable", clickable)
                if (node.isEditable) {
                    // Only what labels the box, never what she typed.
                    json.put("hint", node.hintText?.toString()?.let(PrivacyFilter::maskScreenText) ?: JSONObject.NULL)
                } else {
                    json.put("text", node.text?.toString()?.let(PrivacyFilter::maskScreenText) ?: JSONObject.NULL)
                }
                json.put("desc", node.contentDescription?.toString()?.let(PrivacyFilter::maskScreenText) ?: JSONObject.NULL)
                lines += json.toString()
            }
            // Chat and message rows are never entered.
            if (ProbeRules.skipsRows(className)) return
            val childInsideLabel = insideLabel || ProbeRules.isLabelContainer(className)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                walk(child, depth + 1, childInsideLabel)
            }
        }
        walk(root, 0, false)

        val fresh = synchronized(written) { lines.filter(written::add) }
        if (fresh.isEmpty()) return
        val dir = context.getExternalFilesDir(null) ?: return
        io.execute {
            runCatching { File(dir, FILE).appendText(fresh.joinToString("\n", postfix = "\n")) }
            // A count only, never the labels.
            Log.i("SaathiProbe", "wrote ${fresh.size} new entries")
        }
    }
}
