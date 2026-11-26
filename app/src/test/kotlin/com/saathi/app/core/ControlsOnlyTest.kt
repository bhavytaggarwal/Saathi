package com.saathi.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The no-leak test. Synthetic WhatsApp screens full of fake names, numbers and messages go through
 * the same reader the phone uses, and the payload is checked word by word: only safe labels,
 * [hidden] and the fixed button descriptions may appear in it.
 */
class ControlsOnlyTest {
    private val width = 1080
    private val height = 2400

    // A tiny node tree, flattened the way ScreenReader.collect does it.
    private class N(
        val text: String? = null,
        val desc: String? = null,
        val hint: String? = null,
        val cls: String = "android.widget.TextView",
        val box: Box,
        val tappable: Boolean = false,
        val editable: Boolean = false,
        val showingHint: Boolean = false,
        val children: List<N> = emptyList(),
    )

    private fun flatten(root: N): List<ControlNode> {
        val out = ArrayList<ControlNode>()
        fun walk(node: N, ancestorTappable: Boolean) {
            out += ControlNode(
                NodeFacts(
                    text = node.text,
                    description = node.desc,
                    hint = node.hint,
                    isEditable = node.editable,
                    isClickable = node.tappable || ancestorTappable,
                    showingHint = node.showingHint,
                ),
                node.cls,
                node.box,
                node.tappable,
            )
            node.children.forEach { walk(it, node.tappable || ancestorTappable) }
        }
        walk(root, false)
        return out
    }

    private fun screen(vararg children: N) = N(cls = "android.widget.FrameLayout", box = Box(0, 0, width, height), children = children.toList())

    private fun box(top: Int, left: Int = 0, w: Int = width, h: Int = 120) = Box(left, top, left + w, top + h)

    private fun icon(desc: String, top: Int, left: Int) =
        N(desc = desc, cls = "android.widget.ImageButton", box = box(top, left, 120, 120), tappable = true)

    /** A chat list row. The row's own description is the contact's name, as WhatsApp often sets it. */
    private fun chatRow(top: Int, rowDesc: String?, name: String, preview: String, time: String, avatar: String, unread: String? = null) = N(
        desc = rowDesc,
        cls = "android.widget.LinearLayout",
        box = box(top, h = 180),
        tappable = true,
        children = listOfNotNull(
            N(desc = avatar, cls = "android.widget.ImageView", box = box(top + 20, 20, 140, 140), tappable = true),
            N(text = name, box = box(top + 20, 200, 600, 60)),
            N(text = preview, box = box(top + 90, 200, 600, 60)),
            N(text = time, box = box(top + 20, 850, 200, 60)),
            unread?.let { N(text = it, box = box(top + 90, 950, 60, 60)) },
        ),
    )

    private fun bubble(top: Int, message: String, time: String) = N(
        cls = "android.widget.LinearLayout",
        box = box(top, 300, 700, 150),
        tappable = true,
        children = listOf(N(text = message, box = box(top + 10, 320, 600, 80)), N(text = time, box = box(top + 100, 850, 120, 40))),
    )

    private fun menuItem(label: String, top: Int) = N(
        cls = "androidx.appcompat.view.menu.ListMenuItemView",
        box = box(top, 600, 450, 120),
        tappable = true,
        children = listOf(N(text = label, box = box(top + 30, 640, 300, 60))),
    )

    private fun tab(label: String, left: Int, badge: String? = null) = N(
        desc = label,
        cls = "android.widget.FrameLayout",
        box = box(2250, left, 270, 150),
        tappable = true,
        children = listOfNotNull(N(text = label, box = box(2330, left + 50, 170, 50)), badge?.let { N(text = it, box = box(2260, left + 160, 50, 50)) }),
    )

    private fun field(hint: String, value: String?, top: Int) = N(
        text = value ?: hint,
        hint = hint,
        cls = "android.widget.EditText",
        box = box(top, 40, 1000, 140),
        tappable = true,
        editable = true,
        showingHint = value == null,
    )

    private val chatList = screen(
        N(text = "WhatsApp", box = box(60, 40, 400, 80)),
        icon("Camera", 60, 700), icon("Search", 60, 820), icon("More options", 60, 940),
        chatRow(300, "Rahul Sharma", "Rahul Sharma", "Kal 5 baje milte hain", "10:30 am", "Photo of Rahul", "3"),
        chatRow(480, null, "Mummy Ji", "📷 Photo", "Yesterday", "Photo of Mummy Ji"),
        chatRow(660, "Family ❤️", "Family ❤️", "Priya: mera naya number +91 98765 43210", "9:12 pm", "Family ❤️ group photo", "12"),
        chatRow(840, "Dr. Kapoor Clinic", "Dr. Kapoor Clinic", "Appointment 14 Nov, Rs. 500", "Mon", "Photo of Dr. Kapoor"),
        N(desc = "New chat", cls = "com.google.android.material.floatingactionbutton.FloatingActionButton", box = box(2050, 880, 160, 160), tappable = true),
        tab("Chats", 0, "4"), tab("Updates", 270), tab("Communities", 540), tab("Calls", 810),
    )

    private val openChat = screen(
        icon("Navigate up", 60, 0),
        // The title is the contact's name: once as plain text, once as the tappable header.
        N(text = "Rahul Sharma", box = box(50, 200, 500, 60)),
        N(
            desc = "Rahul Sharma", cls = "android.widget.LinearLayout", box = box(40, 140, 560, 140), tappable = true,
            children = listOf(
                N(desc = "Rahul Sharma profile photo", cls = "android.widget.ImageView", box = box(50, 150, 100, 100)),
                N(text = "last seen today at 9:41 pm", box = box(120, 260, 340, 40)),
            ),
        ),
        icon("Video call", 60, 700), icon("Voice call", 60, 820), icon("More options", 60, 940),
        N(text = "Today", box = box(400, 450, 180, 50)),
        bubble(500, "Mera naya number 98765 43210 hai, save kar lena", "9:40 pm"),
        bubble(700, "Haan Rahul, kal Lajpat Nagar aati hoon", "9:41 pm"),
        bubble(900, "Delete", "9:42 pm"),
        icon("Emoji", 2200, 20),
        field("Message", "Rahul ko kal call karna", 2190),
        icon("Attach", 2200, 700), icon("Camera", 2200, 820), icon("Voice message", 2200, 940),
    )

    private val longPress = screen(
        icon("Navigate up", 60, 0),
        N(text = "1", box = box(60, 150, 60, 60)),
        icon("Reply", 60, 400), icon("Star", 60, 520), icon("Delete", 60, 640), icon("Forward", 60, 760), icon("More options", 60, 900),
        N(
            cls = "androidx.appcompat.widget.MenuPopupWindow\$MenuDropDownListView", box = box(180, 600, 450, 480),
            children = listOf(menuItem("Info", 180), menuItem("Copy", 300), menuItem("Edit", 420), menuItem("Pin", 540)),
        ),
        bubble(700, "Kal subah 10 baje Dr. Mehta ke paas chalna hai", "9:43 pm"),
    )

    private val newChat = screen(
        icon("Navigate up", 60, 0),
        N(text = "Select contact", box = box(40, 160, 400, 60)),
        N(text = "214 contacts", box = box(100, 160, 300, 40)),
        icon("Search", 60, 820), icon("More options", 60, 940),
        N(
            cls = "android.widget.LinearLayout", box = box(250, h = 160), tappable = true,
            children = listOf(N(cls = "android.widget.ImageView", box = box(260, 20, 120, 120)), N(text = "New group", box = box(290, 200, 400, 60))),
        ),
        N(
            cls = "android.widget.LinearLayout", box = box(410, w = 900, h = 160), tappable = true,
            children = listOf(N(text = "New contact", box = box(450, 200, 400, 60))),
        ),
        chatRow(600, null, "Priya Verma", "Hey there! I am using WhatsApp.", "", "Photo of Priya"),
        chatRow(780, null, "+91 99887 76655", "Busy", "", "Photo of +91 99887 76655"),
    )

    private val contactForm = screen(
        icon("Navigate up", 60, 0),
        N(text = "New contact", box = box(60, 160, 400, 60)),
        field("First name", "Sunita", 300),
        field("Last name", null, 480),
        N(text = "IN +91", cls = "android.widget.Spinner", box = box(660, 40, 260, 140), tappable = true),
        field("Phone", "98765 43210", 660),
        N(text = "Save to", box = box(860, 40, 300, 50)),
        N(text = "sunita.devi@gmail.com", box = box(920, 40, 600, 50)),
        N(text = "Save", cls = "android.widget.Button", box = box(2200, 40, 1000, 140), tappable = true),
    )

    /** Strings from the fake screens that must never leave the phone. */
    private val private = listOf(
        "Rahul", "Sharma", "Mummy", "Family", "Priya", "Verma", "Kapoor", "Mehta", "Sunita", "Lajpat", "gmail", "sunita.devi",
        "98765", "43210", "99887", "76655", "+91", "500", "14 Nov", "10:30", "9:4", "Yesterday", "last seen", "Photo of",
        "profile photo", "group photo", "milte hain", "naya number", "save kar lena", "aati hoon", "call karna", "subah",
        "214", "Select contact", "Hey there", "Busy", "Today", "Pin", "Video call", "Voice call",
    )

    private val allowedQuoted: Set<String> by lazy {
        ControlsOnly.safeLabels + ControlsOnly.HIDDEN + ControlsOnly.safeLabels.mapNotNull { ControlsOnly.looks(it)?.roman }
    }

    /** Everything a turn with this screen sends about the screen: the element list and the state. */
    private fun payload(tree: N): Pair<ControlsScreen, String> {
        val read = ControlsOnly.read(flatten(tree))
        val state = SessionState().apply {
            screen = ScreenFacts("WhatsApp", read.title, changed = true, readable = true, mode = SessionState.MODE_CONTROLS_ONLY)
        }
        return read to read.describe(width, height) + "\n" + state.toJson(now = 0).toString()
    }

    private fun assertNoLeak(name: String, tree: N): ControlsScreen {
        val (read, sent) = payload(tree)
        assertNull("$name: title", read.title)
        for (quoted in Regex("\"([^\"]*)\"").findAll(read.describe(width, height)).map { it.groupValues[1] }) {
            assertTrue("$name: \"$quoted\" is not a safe label", quoted in allowedQuoted)
        }
        for (secret in private) {
            assertFalse("$name leaks \"$secret\":\n$sent", sent.contains(secret, ignoreCase = true))
        }
        return read
    }

    @Test fun chatListSendsOnlyButtonsAndHidesEveryChat() {
        val read = assertNoLeak("chat list", chatList)
        val labels = read.controls.map { it.label }
        assertTrue(labels.containsAll(listOf("Camera", "Search", "More options", "New chat", "Chats", "Updates", "Communities", "Calls")))
        // The row whose own description is "Rahul Sharma" is a hidden tappable row.
        val rahulRow = read.controls.first { it.box.top == 300 && it.box.width == width }
        assertEquals(ControlsOnly.HIDDEN, rahulRow.label)
        assertTrue(rahulRow.tappable)
        // The Chats tab keeps its label although it holds an unread badge, and the badge is not sent.
        assertTrue(read.find("Chats") != null)
    }

    @Test fun openChatHidesTheTitleMessagesAndTypedText() {
        val read = assertNoLeak("open chat", openChat)
        val box = read.controls.single { it.type == "textbox" }
        assertEquals("Message", box.label)
        assertEquals(true, box.hasText)
        assertTrue(read.describe(width, height).contains("has_text=yes"))
        // A message that is just a safe word ("Delete") stays hidden, because its bubble also holds a time.
        assertTrue(read.controls.filter { it.box.top == 900 }.all { it.hidden })
        listOf("Navigate up", "Emoji", "Attach", "Camera", "Voice message", "More options").forEach {
            assertTrue(it, read.find(it) != null)
        }
    }

    @Test fun longPressMenuShowsEditAndHidesTheSelectedMessage() {
        val read = assertNoLeak("long press", longPress)
        listOf("Reply", "Star", "Delete", "Forward", "More options", "Info", "Copy", "Edit").forEach {
            assertTrue(it, read.find(it) != null)
        }
        // "Pin" is not on the safe list yet, so that menu row is hidden.
        assertTrue(read.controls.any { it.hidden && it.box.top == 540 })
    }

    @Test fun newChatScreenHidesContactsAndNumbers() {
        val read = assertNoLeak("new chat", newChat)
        assertTrue(read.find("New group") != null)
        assertTrue(read.find("New contact") != null)
    }

    @Test fun contactFormSaysOnlyWhetherEachBoxHasText() {
        val read = assertNoLeak("contact form", contactForm)
        val boxes = read.controls.filter { it.type == "textbox" }.associate { it.label to it.hasText }
        assertEquals(mapOf("First name" to true, "Last name" to false, "Phone" to true), boxes)
        assertTrue(read.find("Save") != null)
    }

    @Test fun describedButtonsCarryWhatTheyLookLike() {
        val line = ControlsOnly.read(flatten(chatList)).describe(width, height).lines().first { "\"New chat\"" in it }
        assertTrue(line, line.contains("neeche-right"))
        assertTrue(line, line.contains("looks=\"hara gol button, jisme chat ka nishaan hai\""))
    }

    @Test fun matchesOnlyExactLabelsAfterNormalising() {
        assertEquals("New chat", ControlsOnly.safeLabel("  NEW   chat "))
        assertEquals("New chat", ControlsOnly.safeLabel("‎New chat‏"))
        assertEquals("Search", ControlsOnly.safeLabel("search"))
        assertNull(ControlsOnly.safeLabel("New chat with Rahul"))
        assertNull(ControlsOnly.safeLabel("Search Rahul"))
        assertNull(ControlsOnly.safeLabel("Photo of Rahul"))
        assertNull(ControlsOnly.safeLabel("Edit!"))
        assertNull(ControlsOnly.safeLabel(""))
    }

    @Test fun aSafeDescriptionDoesNotCoverPrivateText() {
        val node = N(text = "Rahul Sharma", desc = "Search", cls = "android.widget.TextView", box = box(500), tappable = true)
        val read = ControlsOnly.read(flatten(screen(node)))
        assertTrue(read.controls.all { it.hidden })
    }

    @Test fun aRowTakesAChildLabelOnlyWhenEverythingInItIsThatLabel() {
        val menu = ControlsOnly.read(flatten(screen(menuItem("Edit", 400))))
        assertEquals("Edit", menu.controls.first { it.tappable }.label)
        val row = N(
            cls = "android.widget.LinearLayout", box = box(400), tappable = true,
            children = listOf(N(text = "Edit", box = box(410, 0, 200, 60)), N(text = "Rahul", box = box(410, 300, 200, 60))),
        )
        assertEquals(ControlsOnly.HIDDEN, ControlsOnly.read(flatten(screen(row))).controls.first { it.tappable }.label)
    }
}
