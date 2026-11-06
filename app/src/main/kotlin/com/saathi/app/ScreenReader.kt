package com.saathi.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.saathi.app.core.Allowlist
import com.saathi.app.core.NodeFacts
import com.saathi.app.core.PrivacyFilter

data class ScreenElement(
    val number: Int,
    val label: String,
    val type: String,
    val tappable: Boolean,
    val bounds: Rect,
)

/** One reading of the screen. Secret screens carry no elements, so there is nothing to send. */
data class ScreenSnapshot(
    val pkg: String,
    val secret: Boolean,
    val payment: Boolean,
    val scam: Boolean,
    val elements: List<ScreenElement>,
    val needsScreenshot: Boolean,
) {
    val labels: Set<String> = elements.filter { it.tappable }.map { it.label }.toSet()

    fun find(label: String): ScreenElement? = elements.firstOrNull { it.tappable && it.label == label }

    /** The numbered list given to the AI. Text in it is data from the screen, already masked. */
    fun describe(screenWidth: Int, screenHeight: Int): String = buildString {
        for (element in elements) {
            val r = element.bounds
            val x = r.centerX() * 100 / screenWidth.coerceAtLeast(1)
            val y = r.centerY() * 100 / screenHeight.coerceAtLeast(1)
            val w = r.width() * 100 / screenWidth.coerceAtLeast(1)
            val h = r.height() * 100 / screenHeight.coerceAtLeast(1)
            val vertical = if (y < 34) "upar" else if (y > 66) "neeche" else "beech"
            val horizontal = if (x < 34) "left" else if (x > 66) "right" else "center"
            val tap = if (element.tappable) "tappable" else "text-only"
            append("#${element.number} ${element.type} \"${element.label}\" $tap ")
            append("$vertical-$horizontal x=$x y=$y w=$w h=$h\n")
        }
    }
}

/** Walks the accessibility tree read-only and turns it into plain data. */
object ScreenReader {
    private const val MAX_NODES = 900
    private const val MAX_DEPTH = 40
    private const val MAX_ELEMENTS = 70
    private const val MAX_LAUNCHER_ELEMENTS = 40
    private const val MAX_LABEL = 80
    private const val MAX_LAUNCHER_LABEL = 30
    private const val NO_LABEL = "(no label)"

    private class Raw(val facts: NodeFacts, val className: String, val bounds: Rect, val tappable: Boolean)

    private fun collect(root: AccessibilityNodeInfo): List<Raw> {
        val out = ArrayList<Raw>(128)
        var visited = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int, ancestorTappable: Boolean) {
            if (visited++ > MAX_NODES || depth > MAX_DEPTH) return
            val tappable = node.isClickable || node.isLongClickable
            if (node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (!bounds.isEmpty) {
                    out += Raw(
                        NodeFacts(
                            text = node.text?.toString(),
                            description = node.contentDescription?.toString(),
                            hint = node.hintText?.toString(),
                            viewId = node.viewIdResourceName,
                            isPassword = node.isPassword,
                            isEditable = node.isEditable,
                            isClickable = tappable || ancestorTappable,
                        ),
                        className = node.className?.toString().orEmpty(),
                        bounds = bounds,
                        tappable = tappable,
                    )
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                walk(child, depth + 1, tappable || ancestorTappable)
            }
        }
        walk(root, 0, false)
        return out
    }

    /** The on-device check used even when resting. It keeps nothing and sends nothing. */
    fun checkPrivacy(root: AccessibilityNodeInfo, pkg: String): Pair<Boolean, Boolean> {
        val facts = collect(root).map { it.facts }
        return hasSecret(facts, pkg) to PrivacyFilter.isPaymentScreen(facts)
    }

    private fun hasSecret(facts: List<NodeFacts>, pkg: String) =
        PrivacyFilter.hasSecret(facts, numberPadMeansSecret = !Allowlist.hasOrdinaryNumberPad(pkg))

    /** Full read for an active help session. Callers must have checked the allowlist. */
    fun read(root: AccessibilityNodeInfo, pkg: String): ScreenSnapshot {
        val raw = collect(root)
        val facts = raw.map { it.facts }
        if (hasSecret(facts, pkg)) return ScreenSnapshot(pkg, true, false, false, emptyList(), false)

        val payment = PrivacyFilter.isPaymentScreen(facts)
        if (payment) return ScreenSnapshot(pkg, false, true, false, emptyList(), false)

        val scam = facts.any { node ->
            listOfNotNull(node.text, node.description).any(PrivacyFilter::looksLikeScam)
        }
        val launcher = Allowlist.isLauncher(pkg)
        val maxElements = if (launcher) MAX_LAUNCHER_ELEMENTS else MAX_ELEMENTS
        val maxLabel = if (launcher) MAX_LAUNCHER_LABEL else MAX_LABEL

        val elements = ArrayList<ScreenElement>()
        val seen = HashSet<String>()
        var unlabeledTappable = 0
        var labeledTappable = 0

        fun labelOf(item: Raw): String? {
            val node = item.facts
            // Never read a typed value. An editable box is described only by its hint.
            val source = if (node.isEditable) node.hint ?: node.description else node.text ?: node.description
            return source?.trim()?.takeIf { it.isNotEmpty() }
        }

        // Tappable elements come first so a long page cannot push them out of the list.
        val ordered = raw.sortedWith(compareBy<Raw> { !it.tappable }.thenBy { it.bounds.top }.thenBy { it.bounds.left })
        for (item in ordered) {
            if (elements.size >= maxElements) break
            var label = labelOf(item)
            if (label == null && item.tappable) {
                label = labelFromChildren(item, raw)
            }
            if (label == null) {
                if (item.tappable) {
                    unlabeledTappable++
                    // The launcher is limited to named apps and icons, nothing else.
                    if (!launcher) {
                        elements += ScreenElement(elements.size + 1, NO_LABEL, typeOf(item), true, item.bounds)
                    }
                }
                continue
            }
            // On the launcher only tappable names are read, so widgets and previews stay private.
            if (launcher && !item.tappable) continue
            // Plain text that sits inside a tappable row is already that row's label.
            val insideTappable = !item.tappable && item.facts.isClickable
            if (insideTappable) continue

            val masked = PrivacyFilter.maskScreenText(label).take(maxLabel)
            if (!seen.add("$masked@${item.bounds.top / 8}")) continue
            if (item.tappable) labeledTappable++
            elements += ScreenElement(elements.size + 1, masked, typeOf(item), item.tappable, item.bounds)
        }

        val totalTappable = unlabeledTappable + labeledTappable
        val mostlyUnlabeled = totalTappable > 0 && unlabeledTappable * 100 / totalTappable >= 40
        val tooLittle = labeledTappable < 2 && raw.size > 5
        return ScreenSnapshot(
            pkg = pkg,
            secret = false,
            payment = false,
            scam = scam,
            elements = elements,
            needsScreenshot = !launcher && (mostlyUnlabeled || tooLittle),
        )
    }

    private fun labelFromChildren(parent: Raw, all: List<Raw>): String? {
        val parts = all.asSequence()
            .filter { it !== parent && !it.tappable && !it.facts.isEditable && parent.bounds.contains(it.bounds) }
            .mapNotNull { (it.facts.text ?: it.facts.description)?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(3)
            .toList()
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun typeOf(item: Raw): String {
        val name = item.className.substringAfterLast('.').lowercase()
        return when {
            item.facts.isEditable || "edittext" in name -> "textbox"
            "switch" in name || "toggle" in name -> "switch"
            "checkbox" in name -> "checkbox"
            "radio" in name -> "radio"
            "image" in name -> if (item.tappable) "icon-button" else "image"
            "tab" in name -> "tab"
            item.tappable -> "button"
            else -> "text"
        }
    }
}
