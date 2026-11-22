package com.saathi.app.core

import java.util.Locale

/** A screen rectangle in pixels. Plain Kotlin, so the reader can be tested without Android. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun contains(other: Box): Boolean =
        !isEmpty && left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

/** One visible node, as the accessibility walk found it. facts.isClickable includes tappable ancestors. */
data class ControlNode(val facts: NodeFacts, val className: String, val box: Box, val tappable: Boolean)

/** What a known control looks like, in Roman Hinglish and Devanagari. Position is added from the screen. */
data class Looks(val roman: String, val hindi: String)

/**
 * One element of a controls-only screen. label is a canonical safe label or [ControlsOnly.HIDDEN],
 * never anything else. hasText is set only for text boxes, and says nothing about what is in them.
 */
data class Control(
    val number: Int,
    val label: String,
    val type: String,
    val tappable: Boolean,
    val box: Box,
    val hasText: Boolean? = null,
) {
    val hidden: Boolean get() = label == ControlsOnly.HIDDEN
    val looks: Looks? get() = ControlsOnly.looks(label)
}

/** A controls-only reading. Nothing in it came from the screen except known labels and layout. */
class ControlsScreen(val controls: List<Control>) {
    /** Never a title: in a chat it would be the contact's name. */
    val title: String? = null

    /**
     * Used only on the phone, to tell whether the screen changed: safe labels, plus the type and
     * rough position of hidden elements.
     */
    val signature: Set<String> = controls.filter { it.tappable }.mapTo(HashSet()) {
        val base = if (it.hidden) "${it.label}:${it.type}:${it.box.top / 64}" else it.label
        // A box that fills or empties counts as a change, so a guide can move on after typing.
        if (it.hasText != null) "$base:text=${it.hasText}" else base
    }

    fun find(label: String): Control? = controls.firstOrNull { it.tappable && it.label == label }

    /** The numbered list given to the AI. This is everything that leaves the phone from the screen. */
    fun describe(screenWidth: Int, screenHeight: Int): String = buildString {
        for (control in controls) {
            val r = control.box
            val x = r.centerX * 100 / screenWidth.coerceAtLeast(1)
            val y = r.centerY * 100 / screenHeight.coerceAtLeast(1)
            val w = r.width * 100 / screenWidth.coerceAtLeast(1)
            val h = r.height * 100 / screenHeight.coerceAtLeast(1)
            val tap = if (control.tappable) "tappable" else "text-only"
            append("#${control.number} ${control.type} \"${control.label}\" $tap ")
            append("${ControlsOnly.place(x, y)} x=$x y=$y w=$w h=$h")
            control.hasText?.let { append(if (it) " has_text=yes" else " has_text=no") }
            control.looks?.let { append(" looks=\"${it.roman}\"") }
            append('\n')
        }
    }
}

/**
 * Controls-only reading for ask-first apps. It is an allowlist, never a detector of private data:
 * a node's own text, description or hint leaves the phone only if, after normalising case,
 * spacing and invisible characters, it exactly matches a label in [SAFE]. Everything else,
 * including names, messages, previews, times, counts and photo descriptions, becomes [HIDDEN] with
 * only its type, position and whether it is tappable. Text boxes say only whether they have text.
 *
 * The labels are a first guess at WhatsApp's own. Confirm them on a phone with the label probe
 * (see docs/TESTING.md) before relying on any of them.
 */
object ControlsOnly {
    const val HIDDEN = "[hidden]"
    private const val MAX_ELEMENTS = 70

    // Declared before the tables below, which normalise their keys while the object is created.
    // Bidi marks, zero-width characters and the like, which WhatsApp puts around names.
    private val INVISIBLE = Regex("[\\u00AD\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]")
    private val SPACES = Regex("\\s+")
    // A trailing ellipsis, as hints often end with one: "Search name or number…".
    private val ELLIPSIS = Regex("(…|\\.\\.\\.)$")

    /** Canonical label to what it looks like. Position words are worked out from the screen. */
    private val SAFE: Map<String, Looks> = linkedMapOf(
        // Chat list and tabs
        "New chat" to Looks("hara gol button, jisme chat ka nishaan hai", "हरा गोल button, जिसमें chat का निशान है"),
        "More options" to Looks("teen khade bindu", "तीन खड़े बिंदु"),
        "Search" to Looks("lens jaisa gol nishaan", "लेंस जैसा गोल निशान"),
        "Camera" to Looks("camera ka nishaan", "camera का निशान"),
        "Chats" to Looks("Chats likha hai, chat ka nishaan", "Chats लिखा है, chat का निशान"),
        "Updates" to Looks("Updates likha hai", "Updates लिखा है"),
        "Status" to Looks("Status likha hai", "Status लिखा है"),
        "Calls" to Looks("Calls likha hai, phone ka nishaan", "Calls लिखा है, phone का निशान"),
        "Communities" to Looks("Communities likha hai, logon ka nishaan", "Communities लिखा है, लोगों का निशान"),
        "Settings" to Looks("Settings likha hai", "Settings लिखा है"),
        // Open chat
        "Navigate up" to Looks("peeche jaane ka teer", "पीछे जाने का तीर"),
        "Message" to Looks("message likhne ka lamba dabba", "message लिखने का लंबा डब्बा"),
        "Emoji" to Looks("hansta chehra", "हँसता चेहरा"),
        "Attach" to Looks("paperclip ka nishaan", "paperclip का निशान"),
        "Send" to Looks("hara gol button, jisme teer hai", "हरा गोल button, जिसमें तीर है"),
        "Voice message" to Looks("hara gol button, jisme mic hai", "हरा गोल button, जिसमें mic है"),
        // After a long press on a message
        "Reply" to Looks("ulti taraf mudta teer", "उल्टी तरफ़ मुड़ता तीर"),
        "Star" to Looks("taare ka nishaan", "तारे का निशान"),
        "Delete" to Looks("kooda daan ka nishaan", "कूड़ेदान का निशान"),
        "Forward" to Looks("seedhi taraf mudta teer", "सीधी तरफ़ मुड़ता तीर"),
        "Copy" to Looks("do kaagaz, ek ke upar ek", "दो काग़ज़, एक के ऊपर एक"),
        "Edit" to Looks("Edit likha hai, ya pencil ka nishaan", "Edit लिखा है, या pencil का निशान"),
        "Info" to Looks("gol ghere mein i", "गोल घेरे में i"),
        // New chat and the contact form
        "Search name or number" to Looks("upar lamba dabba, naam ya number likhne ka", "ऊपर लंबा डब्बा, नाम या number लिखने का"),
        "New group" to Looks("New group likha hai, logon ka nishaan", "New group लिखा है, लोगों का निशान"),
        "New contact" to Looks("New contact likha hai, insaan aur plus ka nishaan", "New contact लिखा है, इंसान और plus का निशान"),
        "First name" to Looks("naam likhne ka dabba", "नाम लिखने का डब्बा"),
        "Last name" to Looks("doosra naam likhne ka dabba", "दूसरा नाम लिखने का डब्बा"),
        "Phone" to Looks("phone number likhne ka dabba", "phone number लिखने का डब्बा"),
        "Save" to Looks("Save likha hai", "Save लिखा है"),
        "Cancel" to Looks("Cancel likha hai", "Cancel लिखा है"),
        "OK" to Looks("OK likha hai", "OK लिखा है"),
    )

    private val BY_KEY: Map<String, String> = SAFE.keys.associateBy(::normalise)

    /** Every label that can ever leave the phone from an ask-first app. */
    val safeLabels: Set<String> get() = SAFE.keys

    fun looks(label: String): Looks? = SAFE[label]

    fun normalise(value: String): String =
        INVISIBLE.replace(value, "").replace(SPACES, " ").trim().replace(ELLIPSIS, "").trim().lowercase(Locale.ROOT)

    /** The canonical safe label for this text, or null. Exact match only. */
    fun safeLabel(value: String?): String? = value?.let { BY_KEY[normalise(it)] }

    /** upar/beech/neeche and left/center/right, from percent coordinates. */
    fun place(x: Int, y: Int): String {
        val vertical = if (y < 34) "upar" else if (y > 66) "neeche" else "beech"
        val horizontal = if (x < 34) "left" else if (x > 66) "right" else "center"
        return "$vertical-$horizontal"
    }

    /** The position she hears, such as "neeche right mein", from where the button really is. */
    fun spokenPlace(box: Box, screenWidth: Int, screenHeight: Int): Pair<String, String> {
        val x = box.centerX * 100 / screenWidth.coerceAtLeast(1)
        val y = box.centerY * 100 / screenHeight.coerceAtLeast(1)
        val (vertical, verticalHi) = if (y < 34) "upar" to "ऊपर" else if (y > 66) "neeche" to "नीचे" else "beech" to "बीच"
        val side = if (x < 34) " left" else if (x > 66) " right" else ""
        return "$vertical$side mein" to "$verticalHi$side में"
    }

    /** Reads a screen of an ask-first app. PrivacyFilter must already have run on these nodes. */
    fun read(nodes: List<ControlNode>): ControlsScreen {
        val ordered = nodes.sortedWith(compareBy<ControlNode> { !it.tappable }.thenBy { it.box.top }.thenBy { it.box.left })
        val controls = ArrayList<Control>()
        val seen = HashSet<String>()
        for (node in ordered) {
            if (controls.size >= MAX_ELEMENTS) break
            val facts = node.facts
            val type = typeOf(node)
            if (facts.isEditable) {
                // The typed value is never read, only whether there is one.
                val label = safeLabel(facts.hint) ?: safeLabel(facts.description) ?: HIDDEN
                val hasText = !facts.showingHint && !facts.text.isNullOrBlank()
                if (seen.add("box@${node.box.top / 8}:${node.box.left / 8}")) {
                    controls += Control(controls.size + 1, label, type, true, node.box, hasText)
                }
                continue
            }
            val own = listOfNotNull(facts.text, facts.description).filter { it.isNotBlank() }
            // Plain text inside a tappable row belongs to that row and is not listed again.
            if (!node.tappable && facts.isClickable) continue
            if (!node.tappable && own.isEmpty()) continue
            val label = when {
                own.isNotEmpty() -> own.firstNotNullOfOrNull(::safeLabel) ?: HIDDEN
                node.tappable -> labelFromChildren(node, nodes) ?: HIDDEN
                else -> HIDDEN
            }
            // A node whose own text is private stays hidden even if its description is a safe word.
            val finalLabel = if (own.isNotEmpty() && own.any { safeLabel(it) == null }) HIDDEN else label
            if (!seen.add("$finalLabel@${node.box.top / 8}:${node.box.left / 8}")) continue
            controls += Control(controls.size + 1, finalLabel, type, node.tappable, node.box)
        }
        return ControlsScreen(controls)
    }

    /**
     * A tappable row with no label of its own takes one only if everything written inside it is
     * the same safe label, as in a menu item that says "Edit". Small counts such as an unread
     * badge are ignored here and never sent. A chat row holds a name, so it stays hidden.
     */
    private fun labelFromChildren(parent: ControlNode, all: List<ControlNode>): String? {
        var found: String? = null
        for (child in all) {
            if (child === parent || child.tappable || child.facts.isEditable || !parent.box.contains(child.box)) continue
            for (value in listOfNotNull(child.facts.text, child.facts.description)) {
                if (value.isBlank() || BADGE.matches(value.trim())) continue
                val safe = safeLabel(value) ?: return null
                if (found != null && found != safe) return null
                found = safe
            }
        }
        return found
    }

    private val BADGE = Regex("\\d{1,3}\\+?")

    private fun typeOf(node: ControlNode): String {
        val name = node.className.substringAfterLast('.').lowercase(Locale.ROOT)
        return when {
            node.facts.isEditable || "edittext" in name -> "textbox"
            "switch" in name || "toggle" in name -> "switch"
            "checkbox" in name -> "checkbox"
            "radio" in name -> "radio"
            "image" in name -> if (node.tappable) "icon-button" else "image"
            "tab" in name -> "tab"
            node.tappable -> "button"
            else -> "text"
        }
    }
}
