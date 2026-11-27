package com.saathi.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.saathi.app.core.Allowlist
import com.saathi.app.core.Box
import com.saathi.app.core.ControlNode
import com.saathi.app.core.ControlsOnly
import com.saathi.app.core.ControlsScreen
import com.saathi.app.core.NodeFacts
import com.saathi.app.core.PrivacyFilter
import com.saathi.app.core.ScrollRows
import com.saathi.app.core.TurnPolicy
import java.util.IdentityHashMap

data class ScreenElement(
    val number: Int,
    val label: String,
    val type: String,
    val tappable: Boolean,
    val bounds: Rect,
    /** Row number in the main scrolling list, counted from the top of what is visible, or null. */
    val row: Int? = null,
    /** Visible rows in that list. */
    val rows: Int? = null,
)

/** One reading of the screen. Secret screens carry no elements, so there is nothing to send. */
data class ScreenSnapshot(
    val pkg: String,
    val secret: Boolean,
    val payment: Boolean,
    val scam: Boolean,
    val elements: List<ScreenElement>,
    val needsScreenshot: Boolean,
    /** The highest plain-text label, already masked. Sent only together with the element list. */
    val title: String? = null,
    /**
     * Set for ask-first apps such as WhatsApp. Then elements hold only safe labels and [hidden],
     * and [describe] sends this reading instead.
     */
    val controlsOnly: ControlsScreen? = null,
    /** The screen has a main list that scrolls. */
    val listScrolls: Boolean = false,
    /** That list goes on below the screen, so what she needs may be further down. */
    val moreBelow: Boolean = false,
    /** True when the Accessibility tree contains an active ProgressBar/loading spinner. */
    val hasProgressBar: Boolean = false,
    /** Number of meaningful non-decorative text nodes on the current screen. */
    val meaningfulTextCount: Int = 3,
    /** True when a merchant checkout / payment options screen (Flipkart/Amazon -> GPay/UPI) is detected. */
    val checkoutPayment: Boolean = false,
) {
    /** True when the screen appears to be in a loading/skeleton state (active ProgressBar or < 3 text nodes). */
    val isLoadingOrSkeleton: Boolean
        get() = hasProgressBar || meaningfulTextCount < 3

    val labels: Set<String> = controlsOnly?.signature ?: elements.filter { it.tappable }.map { it.label }.toSet()

    fun find(label: String): ScreenElement? = elements.firstOrNull { it.tappable && it.label == label }

    /** The numbered list given to the AI. Text in it is data from the screen, already masked. */
    fun describe(screenWidth: Int, screenHeight: Int): String = controlsOnly?.describe(screenWidth, screenHeight) ?: buildString {
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
            append("$vertical-$horizontal x=$x y=$y w=$w h=$h")
            if (element.row != null && element.rows != null) append(" row=${element.row}/${element.rows}")
            append("\n")
        }
        if (listScrolls) {
            append("LIST: the main list scrolls. More rows below the screen: ${if (moreBelow) "yes" else "no"}\n")
        }
    }
}

/**
 * The result of the on-device check. scam is only worked out on payment screens. namedButtons
 * counts tappable elements with a label, which a lock screen that draws no text does not have.
 */
data class PrivacyCheck(
    val secretReason: String?,
    val payment: Boolean,
    val scam: Boolean,
    val namedButtons: Int,
    val checkoutPayment: Boolean = false,
) {
    val secret: Boolean get() = secretReason != null
}

/** Walks the accessibility tree read-only and turns it into plain data. */
object ScreenReader {
    private const val MAX_NODES = 900
    private const val MAX_DEPTH = 40
    private const val MAX_ELEMENTS = 48
    private const val MAX_UNLABELED_ELEMENTS = 4
    private const val MAX_NON_TAPPABLE_TEXT = 10
    private const val MAX_LAUNCHER_ELEMENTS = 40
    private const val MAX_LABEL = 72
    private const val MAX_LAUNCHER_LABEL = 30
    private const val MAX_TITLE = 40
    private const val MIN_BOUND_PX = 4
    private const val NO_LABEL = "(no label)"
    private val DECORATIVE_ONLY = Regex("^[\\p{P}\\p{S}\\s•·|▶►›»✓✕×]+$")

    private class Raw(
        val facts: NodeFacts,
        val className: String,
        val bounds: Rect,
        val tappable: Boolean,
        val scrollable: Boolean,
    )

    private fun collect(root: AccessibilityNodeInfo): List<Raw> {
        val out = ArrayList<Raw>(128)
        var visited = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int, ancestorTappable: Boolean) {
            if (visited++ > MAX_NODES || depth > MAX_DEPTH) return
            val tappable = node.isClickable || node.isLongClickable
            if (node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                // Skip zero-bounds and tiny decorative/divider views (<= 4px).
                if (!bounds.isEmpty && bounds.width() > MIN_BOUND_PX && bounds.height() > MIN_BOUND_PX) {
                    val text = node.text?.toString()
                    val desc = node.contentDescription?.toString()
                    val hint = node.hintText?.toString()
                    val hasSignal = tappable || node.isEditable || node.isPassword || node.isScrollable ||
                        !text.isNullOrBlank() || !desc.isNullOrBlank() || !hint.isNullOrBlank()
                    if (hasSignal) {
                        out += Raw(
                            NodeFacts(
                                text = text,
                                description = desc,
                                hint = hint,
                                viewId = node.viewIdResourceName,
                                isPassword = node.isPassword,
                                isEditable = node.isEditable,
                                isClickable = tappable || ancestorTappable,
                                showingHint = node.isShowingHintText,
                            ),
                            className = node.className?.toString().orEmpty(),
                            bounds = bounds,
                            tappable = tappable,
                            scrollable = node.isScrollable,
                        )
                    }
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
    fun checkPrivacy(root: AccessibilityNodeInfo, pkg: String): PrivacyCheck {
        val raw = collect(root)
        val facts = raw.map { it.facts }
        val scam = PrivacyFilter.isScamScreen(facts)
        val checkoutPayment = PrivacyFilter.isCheckoutPaymentOptionsScreen(facts, pkg)
        val payment = checkoutPayment || (Allowlist.isAskFirst(pkg) && PrivacyFilter.isPaymentScreen(facts))
        val reason = PrivacyFilter.secretReason(
            facts,
            numberPadMeansSecret = !Allowlist.hasOrdinaryNumberPad(pkg),
            ignoreRowText = Allowlist.isAskFirst(pkg),
        )
        val named = raw.count { it.tappable && !(it.facts.text ?: it.facts.description).isNullOrBlank() }
        return PrivacyCheck(reason, payment, scam, named, checkoutPayment)
    }

    private fun hasSecret(facts: List<NodeFacts>, pkg: String) =
        PrivacyFilter.hasSecret(
            facts,
            numberPadMeansSecret = !Allowlist.hasOrdinaryNumberPad(pkg),
            ignoreRowText = Allowlist.isAskFirst(pkg),
        )

    /** Full read for an active help session. Callers must have checked the allowlist. */
    fun read(root: AccessibilityNodeInfo, pkg: String): ScreenSnapshot {
        val raw = collect(root)
        val facts = raw.map { it.facts }
        if (hasSecret(facts, pkg)) return ScreenSnapshot(pkg, true, false, false, emptyList(), false)

        // Worked out before the payment check: a collect request is a payment screen and a scam.
        val scam = PrivacyFilter.isScamScreen(facts)
        val checkoutPayment = PrivacyFilter.isCheckoutPaymentOptionsScreen(facts, pkg)
        if (checkoutPayment) {
            return ScreenSnapshot(pkg, false, true, scam, emptyList(), false, checkoutPayment = true)
        }
        val payment = Allowlist.isAskFirst(pkg) && PrivacyFilter.isPaymentScreen(facts)
        if (payment) return ScreenSnapshot(pkg, false, true, scam, emptyList(), false)
        // Decided by the app, not the caller, so an ask-first app can never be read in full.
        if (Allowlist.isAskFirst(pkg)) return readControlsOnly(raw, pkg, scam)
        val launcher = Allowlist.isLauncher(pkg)
        val maxElements = if (launcher) MAX_LAUNCHER_ELEMENTS else MAX_ELEMENTS
        val maxLabel = if (launcher) MAX_LAUNCHER_LABEL else MAX_LABEL

        val elements = ArrayList<ScreenElement>()
        val seen = HashSet<String>()
        var unlabeledTappable = 0
        var labeledTappable = 0
        var nonTappableTextCount = 0

        fun labelOf(item: Raw): String? {
            val node = item.facts
            // Never read a typed value. An editable box is described only by its hint.
            val source = if (node.isEditable) node.hint ?: node.description else node.text ?: node.description
            return source?.trim()?.takeIf { it.isNotEmpty() && !DECORATIVE_ONLY.matches(it) }
        }

        // Rows of the main list, so a step can say "upar se teesri line" and when to scroll.
        val scroll = if (launcher) {
            ScrollRows.NONE
        } else {
            ScrollRows.analyse(
                raw.map { ScrollRows.Node(Box(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom), it.scrollable, it.tappable) },
                screenHeight = raw.maxOfOrNull { it.bounds.bottom } ?: 0,
            )
        }
        val rowOf = IdentityHashMap<Raw, Int>()
        for ((index, row) in scroll.rows) rowOf[raw[index]] = row

        val resolvedLabels = IdentityHashMap<Raw, String?>()
        for (item in raw) {
            var label = labelOf(item)
            if (label == null && item.tappable) {
                label = labelFromChildren(item, raw)
            }
            resolvedLabels[item] = label
        }

        // Labeled tappable elements come first (top-to-bottom) so bottom sticky buttons like
        // "Select address" or "Place Order" are never pushed out by unlabeled containers above.
        val ordered = raw.sortedWith(
            compareBy<Raw> {
                val hasLabel = resolvedLabels[it] != null
                when {
                    it.tappable && hasLabel -> 0
                    !it.tappable && hasLabel -> 1
                    it.tappable -> 2
                    else -> 3
                }
            }.thenBy { it.bounds.top }.thenBy { it.bounds.left }
        )
        for (item in ordered) {
            if (elements.size >= maxElements) break
            val label = resolvedLabels[item]
            val itemType = typeOf(item)
            if (label == null) {
                if (item.tappable) {
                    // Skip unlabeled wrapper containers that enclose other tappable buttons.
                    val wrapsTappableChild = raw.any {
                        it !== item && it.tappable && item.bounds.contains(it.bounds) && it.bounds != item.bounds
                    }
                    if (!wrapsTappableChild) {
                        unlabeledTappable++
                        // Cap unlabeled tappable items so they never flood the element list.
                        if (!launcher && unlabeledTappable <= MAX_UNLABELED_ELEMENTS) {
                            elements += ScreenElement(elements.size + 1, NO_LABEL, itemType, true, item.bounds, rowOf[item], rowOf[item]?.let { scroll.total })
                        }
                    }
                }
                continue
            }
            // On the launcher only tappable names are read, so widgets and previews stay private.
            if (launcher && !item.tappable) continue
            // Skip non-clickable decorative images or excess plain-text nodes.
            if (!item.tappable && !item.facts.isEditable) {
                if (itemType == "image") continue
                // Plain text that sits inside a tappable row is already that row's label.
                if (item.facts.isClickable) continue
                if (++nonTappableTextCount > MAX_NON_TAPPABLE_TEXT) continue
            }

            val masked = PrivacyFilter.maskScreenText(label).take(maxLabel)
            if (!seen.add("$masked@${item.bounds.top / 8}")) continue
            if (item.tappable) labeledTappable++
            elements += ScreenElement(elements.size + 1, masked, itemType, item.tappable, item.bounds, rowOf[item], rowOf[item]?.let { scroll.total })
        }

        val hasProgressBar = raw.any {
            val cls = it.className.lowercase()
            "progressbar" in cls || "circularprogress" in cls || "shimmer" in cls || "skeleton" in cls
        }
        val meaningfulTextCount = raw.count { resolvedLabels[it] != null }
        // Smart Screenshot Policy, DOM first: DOM-only apps (Settings, Play Store, WhatsApp,
        // Contacts, Dialer, launchers) never send one routinely. Other apps send a 540px JPEG only
        // when the element list is too thin to guide from (see TurnPolicy.needsScreenshot).
        // Reprompts and low-confidence replies still escalate with a picture in Assistant.
        val needsScreenshot = TurnPolicy.needsScreenshot(
            Allowlist.isDomOnlyApp(pkg),
            elements.filter { it.tappable }.map { it.label },
            hasWebView = raw.any { "webview" in it.className.lowercase() },
        )
        return ScreenSnapshot(
            pkg = pkg,
            secret = false,
            payment = false,
            scam = scam,
            elements = elements,
            needsScreenshot = needsScreenshot,
            title = elements.filter { !it.tappable }.minByOrNull { it.bounds.top }?.label?.take(MAX_TITLE),
            listScrolls = scroll.scrolls,
            moreBelow = scroll.moreBelow,
            hasProgressBar = hasProgressBar,
            meaningfulTextCount = meaningfulTextCount,
        )
    }

    /** Known button labels only. No title, no screenshot, no text from chats or contacts. */
    private fun readControlsOnly(raw: List<Raw>, pkg: String, scam: Boolean): ScreenSnapshot {
        val screen = ControlsOnly.read(
            raw.map { ControlNode(it.facts, it.className, Box(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom), it.tappable) }
        )
        val elements = screen.controls.map {
            ScreenElement(it.number, it.label, it.type, it.tappable, Rect(it.box.left, it.box.top, it.box.right, it.box.bottom))
        }
        val hasProgressBar = raw.any { "progressbar" in it.className.lowercase() }
        return ScreenSnapshot(
            pkg = pkg,
            secret = false,
            payment = false,
            scam = scam,
            elements = elements,
            needsScreenshot = false,
            title = null,
            controlsOnly = screen,
            hasProgressBar = hasProgressBar,
            meaningfulTextCount = elements.size.coerceAtLeast(3),
        )
    }

    private fun labelFromChildren(parent: Raw, all: List<Raw>): String? {
        // Skip giant wrapper containers that contain smaller tappable buttons of their own.
        val childTappables = all.filter {
            it !== parent && it.tappable && parent.bounds.contains(it.bounds) && it.bounds != parent.bounds
        }
        val parts = all.asSequence()
            .filter { node ->
                node !== parent && !node.tappable && !node.facts.isEditable &&
                    parent.bounds.contains(node.bounds) &&
                    childTappables.none { child -> child.bounds.contains(node.bounds) }
            }
            .mapNotNull { (it.facts.text ?: it.facts.description)?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(5)
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
