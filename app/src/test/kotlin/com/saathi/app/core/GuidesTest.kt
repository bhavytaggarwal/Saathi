package com.saathi.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The prepared WhatsApp guides: their data, the router, and how the runner moves through screens. */
class GuidesTest {
    private val wa = Allowlist.WHATSAPP
    private val hidden = ControlsOnly.HIDDEN

    /** A WhatsApp screen with these labels, top to bottom. Text boxes are given with whether they have text. */
    private fun screen(vararg labels: String, boxes: Map<String, Boolean> = emptyMap(), pkg: String = wa) = GuideScreen(
        pkg,
        (labels.toList() + boxes.keys).mapIndexed { i, label ->
            val box = Box(0, i * 100, 200, i * 100 + 90)
            if (label in boxes) Control(i + 1, label, "textbox", true, box, boxes[label]) else Control(i + 1, label, "button", true, box)
        },
    )

    private val launcher = GuideScreen("com.android.launcher3", listOf(Control(1, "WhatsApp", "button", true, Box(0, 0, 100, 100))))
    private val chatList = screen("Camera", "Search", "More options", hidden, hidden, "New chat", "Chats", "Updates", "Calls")
    private val newChat = screen("Navigate up", "Search", "More options", "New group", "New contact", hidden, hidden)
    private fun form(name: Boolean, phone: Boolean) =
        screen("Navigate up", "Save", boxes = mapOf("First name" to name, "Last name" to false, "Phone" to phone))
    private val searchEmpty = screen("Navigate up", "New group", "New contact", boxes = mapOf(hidden to false))
    private val searchFilled = screen("Navigate up", hidden, boxes = mapOf(hidden to true))
    private val chatEmpty = screen("Navigate up", hidden, "More options", hidden, hidden, "Emoji", "Attach", "Camera", "Voice message", boxes = mapOf("Message" to false))
    private val chatTyped = screen("Navigate up", hidden, "More options", hidden, "Emoji", "Attach", "Camera", "Send", boxes = mapOf("Message" to true))
    private val selected = screen("Navigate up", hidden, "Reply", "Star", "Delete", "Forward", "More options", hidden)
    private val menu = screen("Info", "Copy", "Edit")
    private val menuWithoutEdit = screen("Info", "Copy", hidden)
    private val whatsAppSettings = screen("Navigate up", "Settings", hidden, hidden)

    private fun runner(id: String) = GuideRunner(Guides.byId(id)!!)

    /** Feeds the screens in order and returns what the runner decided for each. */
    private fun GuideRunner.walk(vararg screens: GuideScreen) = screens.map(::onScreen)

    private fun shows(decision: GuideRunner.Decision, index: Int, target: String?) {
        assertTrue("expected step $index, got $decision", decision is GuideRunner.Decision.Show)
        decision as GuideRunner.Decision.Show
        assertEquals(index, decision.index)
        assertEquals(target, decision.control?.label)
    }

    // The data

    @Test fun everyGuideIsWellFormed() {
        assertEquals(setOf(Guides.ADD_CONTACT, Guides.MESSAGE_NEW_NUMBER, Guides.EDIT_MESSAGE), Guides.IDS)
        for (guide in Guides.ALL) {
            assertEquals(wa, guide.app)
            for ((i, step) in guide.steps.withIndex()) {
                val name = "${guide.id} step $i"
                assertTrue("$name: Roman and Devanagari", step.roman.isNotBlank() && step.hindi.isNotBlank())
                assertTrue("$name: Devanagari line", step.hindi.any { it in 'ऀ'..'ॿ' })
                // Points only at labels that may leave the phone, or the app's icon on the home screen.
                step.target?.let { assertTrue("$name: $it", it in ControlsOnly.safeLabels || !step.on.inApp) }
                if ("{where}" in step.roman) assertTrue("$name needs usualWhere", step.usualWhere != null)
                assertEquals("$name: {where} in both lines", "{where}" in step.roman, "{where}" in step.hindi)
            }
        }
    }

    @Test fun everyLineIsShortWithAnyPosition() {
        val longestPlace = "neeche right mein" to "नीचे right में"
        val lines = Guides.ALL.flatMap { g -> g.steps.flatMap { listOf(it.line(longestPlace), it.line(null)) } + g.doneLine } +
            Lines.EDIT_TOO_LATE + Lines.TYPE_NUMBER + Lines.CONSENT + Lines.CONSENT_AGAIN + Lines.CONSENT_DECLINED + Lines.ACCESS_ENDED
        for (line in lines) {
            for (text in listOf(line.text, line.speech)) {
                for (sentence in text.split(Regex("[.?!।]")).filter(String::isNotBlank)) {
                    val words = sentence.trim().split(Regex("\\s+")).size
                    assertTrue("$words words: \"$sentence\"", words < 15)
                }
            }
        }
    }

    @Test fun fillsInWhereTheButtonIs() {
        val step = Guides.byId(Guides.ADD_CONTACT)!!.steps[1]
        assertEquals("Neeche right mein hara gol button dabaiye, jisme chat ka nishaan hai.", step.line(null).text)
        assertEquals("Upar left mein hara gol button dabaiye, jisme chat ka nishaan hai.", step.line("upar left mein" to "ऊपर left में").text)
        assertEquals("ऊपर left में हरा गोल button दबाइए, जिसमें chat का निशान है।", step.line("upar left mein" to "ऊपर left में").speech)
        assertEquals("neeche right mein" to "नीचे right में", ControlsOnly.spokenPlace(Box(900, 2100, 1060, 2260), 1080, 2400))
    }

    // The router

    @Test fun routesHinglishAndHindiRequests() {
        fun id(words: String, inWhatsApp: Boolean = false) = Guides.match(words, inWhatsApp)?.id
        assertEquals(Guides.EDIT_MESSAGE, id("WhatsApp mein message edit kaise karte hain"))
        assertEquals(Guides.EDIT_MESSAGE, id("whatsapp pe bheja hua message badalna hai"))
        assertEquals(Guides.EDIT_MESSAGE, id("व्हाट्सएप पर मैसेज बदलना है"))
        assertEquals(Guides.EDIT_MESSAGE, id("galat likh diya message, theek kaise karun", inWhatsApp = true))
        assertEquals(Guides.ADD_CONTACT, id("WhatsApp mein naya number save karna hai"))
        assertEquals(Guides.ADD_CONTACT, id("whatsapp pe contact kaise jodte hain"))
        assertEquals(Guides.ADD_CONTACT, id("नंबर सेव करना है", inWhatsApp = true))
        assertEquals(Guides.MESSAGE_NEW_NUMBER, id("naye number pe whatsapp message bhejna hai"))
        assertEquals(Guides.MESSAGE_NEW_NUMBER, id("bina save kiye message kaise bheju", inWhatsApp = true))
    }

    /**
     * Every phrasing in eval/router_cases.jsonl, where cases from the phone are collected. A case
     * with "guide": null must not be routed on the phone. Gemini handles those, and cases.jsonl
     * checks that it names the right guide.
     */
    @Test fun routesEveryCaseInTheEvalSet() {
        val lines = java.io.File("../eval/router_cases.jsonl").readLines().filter(String::isNotBlank)
        assertTrue("router cases", lines.size >= 10)
        val wrong = lines.mapNotNull { line ->
            val case = org.json.JSONObject(line)
            val expected = if (case.isNull("guide")) null else case.getString("guide")
            val got = Guides.match(case.getString("words"), case.getBoolean("in_whatsapp"))?.id
            if (got == expected) null else "\"${case.getString("words")}\": expected $expected, got $got"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun routesOnlyClearWhatsAppTasks() {
        assertNull("not about WhatsApp", Guides.match("message edit kaise karun", inWhatsApp = false))
        assertNull(Guides.match("doodh list mein daal do", inWhatsApp = true))
        assertNull(Guides.match("mera number kya hai", inWhatsApp = true))
        assertNull(Guides.match("WhatsApp pe video call kaise karun", inWhatsApp = false))
    }

    @Test fun recognisesHoGaya() {
        listOf("ho gaya", "Ho gaya.", "kar diya", "aage", "हो गया।").forEach { assertTrue(it, Guides.isDone(it)) }
        listOf("ho gaya kya?x", "nahi hua", "phir se").forEach { assertTrue(it, !Guides.isDone(it)) }
    }

    @Test fun theModelCanPickAGuideById() {
        fun guide(value: String) = (AiReply.parse(
            """{"intent":"guide_step","confidence":0.9,"say_roman":"","say_devanagari":"","guide":"$value","state_patch":{}}"""
        ) as AiReply.Parsed.Ok).reply.guide
        assertEquals(Guides.EDIT_MESSAGE, guide("whatsapp_edit_message"))
        assertNull(guide("none"))
        assertNull(guide("whatsapp_send_money"))
    }

    // The runner

    @Test fun addContactStepsAdvanceWithMatchingScreens() {
        val run = runner(Guides.ADD_CONTACT)
        val d = run.walk(launcher, chatList, newChat, form(false, false), form(false, false), form(true, false), form(true, true), newChat)
        shows(d[0], 0, "WhatsApp")
        shows(d[1], 1, "New chat")
        shows(d[2], 2, "New contact")
        shows(d[3], 3, "First name")
        assertTrue("same screen again", d[4] is GuideRunner.Decision.Stay)
        shows(d[5], 4, "Phone")
        assertTrue((d[5] as GuideRunner.Decision.Show).step.numberStep)
        shows(d[6], 5, "Save")
        assertEquals(GuideRunner.Decision.Done, d[7])
    }

    @Test fun messageNewNumberStepsAdvanceWithMatchingScreens() {
        val d = runner(Guides.MESSAGE_NEW_NUMBER).walk(chatList, newChat, searchEmpty, searchFilled, chatEmpty, chatTyped, chatEmpty)
        shows(d[0], 1, "New chat")
        shows(d[1], 2, "Search")
        shows(d[2], 3, null)
        shows(d[3], 4, null)
        shows(d[4], 5, "Message")
        shows(d[5], 6, "Send")
        assertEquals(GuideRunner.Decision.Done, d[6])
    }

    @Test fun editMessageStepsAdvanceWithMatchingScreens() {
        val d = runner(Guides.EDIT_MESSAGE).walk(chatList, chatEmpty, selected, menu, chatTyped, chatEmpty)
        shows(d[0], 1, null)
        shows(d[1], 2, null)
        shows(d[2], 3, "More options")
        shows(d[3], 4, "Edit")
        shows(d[4], 5, "Send")
        assertEquals(GuideRunner.Decision.Done, d[5])
    }

    @Test fun startsAtTheStepForTheScreenSheIsOn() {
        // She is already inside a chat, so the edit guide starts at the long press.
        shows(runner(Guides.EDIT_MESSAGE).onScreen(chatTyped), 2, null)
    }

    @Test fun saysEditIsTooLateWhenTheOptionIsMissing() {
        val d = runner(Guides.EDIT_MESSAGE).walk(chatEmpty, selected, menuWithoutEdit)
        assertEquals(GuideRunner.Decision.Missing(4, Lines.EDIT_TOO_LATE), d[2])
    }

    @Test fun fallsBackToGeminiWhenTheScreenDoesNotMatch() {
        val run = runner(Guides.EDIT_MESSAGE)
        run.walk(chatEmpty, selected)
        // She opened Settings instead of the menu.
        assertEquals(GuideRunner.Decision.Fallback, run.onScreen(whatsAppSettings))
        assertEquals("the step is kept", 3, run.index)
        // A screen from an earlier step does not move the guide back either.
        assertEquals(GuideRunner.Decision.Fallback, run.onScreen(chatList))
        // Back on the expected screen, the guide takes over again without a model call.
        assertTrue(run.onScreen(selected) is GuideRunner.Decision.Stay)
        shows(run.onScreen(menu), 4, "Edit")
    }

    @Test fun fallsBackWhenTheButtonToPointAtIsMissing() {
        val run = runner(Guides.ADD_CONTACT)
        assertEquals(GuideRunner.Decision.Fallback, run.onScreen(screen("Navigate up", "Search", "New contact", hidden).let {
            // New chat screen without its own button: fits the step, but there is nothing to point at.
            GuideScreen(wa, it.controls.filter { c -> c.label != "New contact" } + Control(9, "New contact", "text", false, Box(0, 900, 10, 990)))
        }))
        assertEquals(GuideRunner.Decision.Fallback, run.onScreen(whatsAppSettings))
    }

    @Test fun whatsAppIconOnTheHomeScreenIsANiceToHave() {
        val emptyHome = GuideScreen("com.android.launcher3", emptyList())
        shows(runner(Guides.ADD_CONTACT).onScreen(emptyHome), 0, null)
        shows(runner(Guides.ADD_CONTACT).onScreen(GuideScreen("com.android.chrome", emptyList())), 0, null)
    }

    @Test fun conversationOnlyMovesOnWithHoGaya() {
        val run = runner(Guides.EDIT_MESSAGE)
        val said = generateSequence { run.advance(inApp = true) }.takeWhile { it != GuideRunner.Decision.Done }.toList()
        assertEquals(listOf(1, 2, 3, 4, 5), said.map { (it as GuideRunner.Decision.Show).index })
        assertTrue(said.all { (it as GuideRunner.Decision.Show).control == null })
        assertEquals(GuideRunner.Decision.Done, run.advance(inApp = true))
        assertEquals(0, (runner(Guides.ADD_CONTACT).advance(inApp = false) as GuideRunner.Decision.Show).index)
    }

    @Test fun worksOnARealControlsOnlyReading() {
        fun node(desc: String, top: Int) = ControlNode(NodeFacts(description = desc, isClickable = true), "android.widget.ImageButton", Box(900, top, 1060, top + 160), true)
        val nodes = listOf(
            node("Search", 60), node("More options", 60),
            ControlNode(NodeFacts(description = "Photo of Rahul", isClickable = true), "android.widget.ImageView", Box(20, 300, 160, 440), true),
            node("New chat", 2100),
        )
        val read = ControlsOnly.read(nodes)
        val decision = runner(Guides.ADD_CONTACT).onScreen(GuideScreen(wa, read.controls))
        shows(decision, 1, "New chat")
        val place = ControlsOnly.spokenPlace((decision as GuideRunner.Decision.Show).control!!.box, 1080, 2400)
        assertEquals("Neeche right mein hara gol button dabaiye, jisme chat ka nishaan hai.", decision.step.line(place).text)
    }

    @Test fun stateCarriesTheGuide() {
        val state = SessionState()
        state.goal = Goal("Edit a message she already sent in WhatsApp", kind = "guide", app = "WhatsApp", guide = Guides.EDIT_MESSAGE)
        assertEquals(Guides.EDIT_MESSAGE, state.toJson(0).getJSONObject("goal").getString("guide"))
        // The model can refine the goal, but cannot drop or change the guide.
        state.applyPatch(org.json.JSONObject("""{"goal":{"text":"Edit the last message","guide":"x"}}"""))
        assertEquals(Guides.EDIT_MESSAGE, state.goal!!.guide)
    }
}
