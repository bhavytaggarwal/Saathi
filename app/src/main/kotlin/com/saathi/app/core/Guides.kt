package com.saathi.app.core

import java.util.Locale

/** What a guide can see of the current screen: the app, and its known labels and layout. */
class GuideScreen(val pkg: String?, val controls: List<Control>) {
    fun has(label: String): Boolean = controls.any { it.label == label }

    fun find(label: String): Control? = controls.firstOrNull { it.tappable && it.label == label }

    /** Whether the text box with this label has text, or null if there is no such box. */
    fun filled(label: String): Boolean? = controls.firstOrNull { it.type == "textbox" && it.label == label }?.hasText

    val textboxes: List<Control> get() = controls.filter { it.type == "textbox" }
}

/**
 * A screen a guide step expects, described only by safe labels and whether text boxes have text,
 * so it works on a controls-only reading. inApp false means any screen outside the guide's app.
 * textbox: true needs a text box, false forbids one, null does not care.
 */
data class ScreenSpec(
    val id: String,
    val inApp: Boolean = true,
    val needs: Set<String> = emptySet(),
    val without: Set<String> = emptySet(),
    val filled: Set<String> = emptySet(),
    val empty: Set<String> = emptySet(),
    val textbox: Boolean? = null,
    val textboxFilled: Boolean? = null,
) {
    fun matches(screen: GuideScreen, app: String): Boolean {
        if (!inApp) return screen.pkg != app
        if (screen.pkg != app) return false
        if (!needs.all(screen::has) || without.any(screen::has)) return false
        if (!filled.all { screen.filled(it) == true } || !empty.all { screen.filled(it) == false }) return false
        if (textbox != null && screen.textboxes.isNotEmpty() != textbox) return false
        if (textboxFilled != null && screen.textboxes.none { it.hasText == textboxFilled }) return false
        return true
    }
}

/**
 * One step: what she hears, the safe label to point at (null when the thing to tap is hidden,
 * such as a chat or her own message), the screen it is done on, and the screen that should
 * come next. "{where}" in a line becomes the button's position, from the screen when it can be
 * seen, otherwise from usualWhere.
 */
data class GuideStep(
    val roman: String,
    val hindi: String,
    /** English and Kannada versions. Kannada is drafted and still to be checked by a native speaker. */
    val en: String,
    val kn: String,
    val target: String?,
    val on: ScreenSpec,
    val next: ScreenSpec,
    /** False when the target is a nice-to-have, such as the WhatsApp icon on the home screen. */
    val mustSee: Boolean = true,
    /** Said when the screen is right but the target is not there. The guide then ends. */
    val missing: (() -> Line)? = null,
    val usualWhere: Place? = null,
    /** She types a number here, so a spoken number gets "type it yourself". */
    val numberStep: Boolean = false,
) {
    fun line(where: Pair<String, String>?): Line {
        val (text, speech) = where ?: usualWhere?.words() ?: ("" to "")
        fun fill(line: String, place: String) = line.replace("{where}", place).replace(Regex("\\s+"), " ").trim()
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
        return Lang.pick(
            Line(fill(roman, text), fill(hindi, speech)),
            Line(fill(en, text)),
            Line(fill(kn, text)),
        )
    }
}

data class Guide(
    val id: String,
    val app: String,
    /** The goal text in the session state, in short English like the model writes it. */
    val goal: String,
    val steps: List<GuideStep>,
    private val done: () -> Line,
) {
    val doneLine: Line get() = done()

    /** The screen after the last step, which means the task is finished. */
    val finished: ScreenSpec get() = steps.last().next
}

/**
 * Prepared guides for common WhatsApp tasks, as data. They run on the phone: a step whose target
 * is a known button is pointed at with no AI call. Gemini is asked only when the screen fits no
 * expected step.
 *
 * The screens below are built from the guessed labels in [ControlsOnly]. Check them with the label
 * probe and adjust the specs here if WhatsApp differs on the phone.
 */
object Guides {
    const val ADD_CONTACT = "whatsapp_add_contact"
    const val MESSAGE_NEW_NUMBER = "whatsapp_message_new_number"
    const val EDIT_MESSAGE = "whatsapp_edit_message"

    private const val APP = Allowlist.WHATSAPP

    // Screens, by the safe labels they show.
    private val OUTSIDE = ScreenSpec("outside_whatsapp", inApp = false)
    private val ANY = ScreenSpec("any_whatsapp_screen")
    private val CHAT_LIST = ScreenSpec("chat_list", needs = setOf("New chat"), without = setOf("Message"))
    private val NEW_CHAT = ScreenSpec("new_chat", needs = setOf("New contact"), textbox = false)
    private val CONTACT_FORM = ScreenSpec("contact_form", needs = setOf("First name", "Phone"))
    private val FORM_NAME = CONTACT_FORM.copy(id = "contact_form_name", empty = setOf("First name"))
    private val FORM_PHONE = CONTACT_FORM.copy(id = "contact_form_phone", filled = setOf("First name"), empty = setOf("Phone"))
    private val FORM_READY = CONTACT_FORM.copy(id = "contact_form_ready", filled = setOf("First name", "Phone"))
    private val LEFT_FORM = ScreenSpec("after_save", without = setOf("First name"))
    private val SEARCH_EMPTY = ScreenSpec(
        "search_empty", without = setOf("Message", "First name", "New chat"), textbox = true, textboxFilled = false,
    )
    private val SEARCH_FILLED = SEARCH_EMPTY.copy(id = "search_with_number", textboxFilled = true)
    private val CHAT = ScreenSpec("open_chat", needs = setOf("Message"), without = setOf("Forward"))
    private val CHAT_EMPTY = ScreenSpec("open_chat_empty_box", needs = setOf("Message", "Voice message"), without = setOf("Send", "Forward"))
    private val CHAT_TYPED = ScreenSpec("open_chat_with_text", needs = setOf("Message", "Send"), without = setOf("Forward"))
    private val SELECTED = ScreenSpec("message_selected", needs = setOf("Forward", "Delete"))
    private val MESSAGE_MENU = ScreenSpec("message_menu", needs = setOf("Copy", "Info"), without = setOf("Forward", "Message"))

    private val BOTTOM_RIGHT = Place(Place.BOTTOM, Place.RIGHT)
    private val TOP_RIGHT = Place(Place.TOP, Place.RIGHT)
    private val TOP = Place(Place.TOP, bare = true)
    private val BOTTOM = Place(Place.BOTTOM, bare = true)
    private val MIDDLE = Place(Place.MIDDLE, bare = true)

    private fun openWhatsApp(next: ScreenSpec) = GuideStep(
        "Pehle WhatsApp kholiye. Hara icon hai, jisme phone ka nishaan hai.",
        "पहले WhatsApp खोलिए। हरा icon है, जिसमें phone का निशान है।",
        "First, open WhatsApp. It's the green icon with a phone in it.",
        "ಮೊದಲು WhatsApp ತೆರೆಯಿರಿ. ಫೋನ್ ಗುರುತು ಇರುವ ಹಸಿರು ಐಕಾನ್.",
        target = "WhatsApp", on = OUTSIDE, next = next, mustSee = false,
    )

    private val tapNewChat = GuideStep(
        "{where} hara gol button dabaiye, jisme chat ka nishaan hai.",
        "{where} हरा गोल button दबाइए, जिसमें chat का निशान है।",
        "Press the round green button {where}, the one with a chat sign.",
        "{where} ಚಾಟ್ ಗುರುತು ಇರುವ ಹಸಿರು ದುಂಡು ಬಟನ್ ಒತ್ತಿ.",
        target = "New chat", on = CHAT_LIST, next = NEW_CHAT, usualWhere = BOTTOM_RIGHT,
    )

    private fun typeAndSend(next: ScreenSpec) = listOf(
        GuideStep(
            "Ab {where} message wale dabbe mein apna message likhiye.",
            "अब {where} message वाले डब्बे में अपना message लिखिए।",
            "Now write your message in the message box {where}.",
            "ಈಗ {where} ಮೆಸೇಜ್ ಬಾಕ್ಸ್‌ನಲ್ಲಿ ನಿಮ್ಮ ಮೆಸೇಜ್ ಬರೆಯಿರಿ.",
            target = "Message", on = CHAT_EMPTY, next = CHAT_TYPED, usualWhere = BOTTOM,
        ),
        GuideStep(
            "Ab {where} hara gol teer wala button dabaiye.",
            "अब {where} हरा गोल तीर वाला button दबाइए।",
            "Now press the round green arrow button {where}.",
            "ಈಗ {where} ಹಸಿರು ದುಂಡು ಬಾಣದ ಬಟನ್ ಒತ್ತಿ.",
            target = "Send", on = CHAT_TYPED, next = next, usualWhere = BOTTOM_RIGHT,
        ),
    )

    val ALL: List<Guide> = listOf(
        Guide(
            id = ADD_CONTACT,
            app = APP,
            goal = "Save a new contact in WhatsApp",
            steps = listOf(
                openWhatsApp(CHAT_LIST),
                tapNewChat,
                GuideStep(
                    "Ab {where} New contact dabaiye.",
                    "अब {where} New contact दबाइए।",
                    "Now press New contact {where}.",
                    "ಈಗ {where} New contact ಒತ್ತಿ.",
                    target = "New contact", on = NEW_CHAT, next = FORM_NAME, usualWhere = TOP,
                ),
                GuideStep(
                    "Ab {where} naam wale dabbe mein naam likhiye.",
                    "अब {where} नाम वाले डब्बे में नाम लिखिए।",
                    "Now write the name in the name box {where}.",
                    "ಈಗ {where} ಹೆಸರಿನ ಬಾಕ್ಸ್‌ನಲ್ಲಿ ಹೆಸರು ಬರೆಯಿರಿ.",
                    target = "First name", on = FORM_NAME, next = FORM_PHONE, usualWhere = TOP,
                ),
                GuideStep(
                    "Ab {where} phone number wale dabbe mein number khud likhiye.",
                    "अब {where} phone number वाले डब्बे में number खुद लिखिए।",
                    "Now type the number yourself in the phone number box {where}.",
                    "ಈಗ {where} ಫೋನ್ ನಂಬರ್ ಬಾಕ್ಸ್‌ನಲ್ಲಿ ನಂಬರ್ ನೀವೇ ಬರೆಯಿರಿ.",
                    target = "Phone", on = FORM_PHONE, next = FORM_READY, usualWhere = MIDDLE,
                    numberStep = true,
                ),
                GuideStep(
                    "Ab {where} Save dabaiye.",
                    "अब {where} Save दबाइए।",
                    "Now press Save {where}.",
                    "ಈಗ {where} Save ಒತ್ತಿ.",
                    target = "Save", on = FORM_READY, next = LEFT_FORM, usualWhere = BOTTOM,
                ),
            ),
            done = { Lang.pick(Line("Bahut badhiya! Number save ho gaya.", "बहुत बढ़िया! Number save हो गया।"), Line("Well done! The number is saved."), Line("ತುಂಬಾ ಚೆನ್ನಾಗಿದೆ! ನಂಬರ್ ಸೇವ್ ಆಯಿತು.")) },
        ),
        Guide(
            id = MESSAGE_NEW_NUMBER,
            app = APP,
            goal = "Message a phone number that is not saved, in WhatsApp",
            steps = listOf(
                openWhatsApp(CHAT_LIST),
                tapNewChat,
                GuideStep(
                    "Ab {where} lens jaisa gol nishaan dabaiye.",
                    "अब {where} लेंस जैसा गोल निशान दबाइए।",
                    "Now press the round magnifying-glass sign {where}.",
                    "ಈಗ {where} ಭೂತಗನ್ನಡಿಯಂತಹ ದುಂಡು ಗುರುತು ಒತ್ತಿ.",
                    target = "Search", on = NEW_CHAT, next = SEARCH_EMPTY, usualWhere = TOP_RIGHT,
                ),
                GuideStep(
                    "Ab upar wale dabbe mein naya number khud likhiye.",
                    "अब ऊपर वाले डब्बे में नया number खुद लिखिए।",
                    "Now type the new number yourself in the box at the top.",
                    "ಈಗ ಮೇಲಿನ ಬಾಕ್ಸ್‌ನಲ್ಲಿ ಹೊಸ ನಂಬರ್ ನೀವೇ ಬರೆಯಿರಿ.",
                    target = null, on = SEARCH_EMPTY, next = SEARCH_FILLED, numberStep = true,
                ),
                GuideStep(
                    "Neeche aapka likha number dikhega. Us par dabaiye.",
                    "नीचे आपका लिखा number दिखेगा। उस पर दबाइए।",
                    "The number you typed will show below. Press it.",
                    "ನೀವು ಬರೆದ ನಂಬರ್ ಕೆಳಗೆ ಕಾಣುತ್ತದೆ. ಅದನ್ನು ಒತ್ತಿ.",
                    target = null, on = SEARCH_FILLED, next = CHAT_EMPTY, numberStep = true,
                ),
            ) + typeAndSend(next = CHAT_EMPTY),
            done = { Lang.pick(Line("Bahut badhiya! Message chala gaya.", "बहुत बढ़िया! Message चला गया।"), Line("Well done! The message has gone."), Line("ತುಂಬಾ ಚೆನ್ನಾಗಿದೆ! ಮೆಸೇಜ್ ಹೋಯಿತು.")) },
        ),
        Guide(
            id = EDIT_MESSAGE,
            app = APP,
            goal = "Edit a message she already sent in WhatsApp",
            steps = listOf(
                openWhatsApp(ANY),
                GuideStep(
                    "Jis chat mein message bheja tha, us chat ko kholiye.",
                    "जिस chat में message भेजा था, उस chat को खोलिए।",
                    "Open the chat where you sent the message.",
                    "ಮೆಸೇಜ್ ಕಳುಹಿಸಿದ ಚಾಟ್ ತೆರೆಯಿರಿ.",
                    target = null, on = CHAT_LIST, next = CHAT,
                ),
                GuideStep(
                    "Apne bheje message par ungli dabaakar rakhiye, jab tak wo rang na badle.",
                    "अपने भेजे message पर उंगली दबाकर रखिए, जब तक वो रंग न बदले।",
                    "Press and hold your message until its colour changes.",
                    "ನಿಮ್ಮ ಮೆಸೇಜ್ ಮೇಲೆ ಬೆರಳು ಒತ್ತಿ ಹಿಡಿಯಿರಿ, ಬಣ್ಣ ಬದಲಾಗುವವರೆಗೆ.",
                    target = null, on = CHAT, next = SELECTED,
                ),
                GuideStep(
                    "Ab {where} teen khade bindu dabaiye.",
                    "अब {where} तीन खड़े बिंदु दबाइए।",
                    "Now press the three dots {where}.",
                    "ಈಗ {where} ಮೂರು ಚುಕ್ಕೆಗಳನ್ನು ಒತ್ತಿ.",
                    target = "More options", on = SELECTED, next = MESSAGE_MENU, usualWhere = TOP_RIGHT,
                ),
                GuideStep(
                    "Ab Edit dabaiye.",
                    "अब Edit दबाइए।",
                    "Now press Edit.",
                    "ಈಗ Edit ಒತ್ತಿ.",
                    target = "Edit", on = MESSAGE_MENU, next = CHAT_TYPED, missing = { Lines.EDIT_TOO_LATE },
                ),
                GuideStep(
                    "Message theek kar lijiye. Phir {where} hara gol teer wala button dabaiye.",
                    "Message ठीक कर लीजिए। फिर {where} हरा गोल तीर वाला button दबाइए।",
                    "Correct the message. Then press the round green arrow button {where}.",
                    "ಮೆಸೇಜ್ ಸರಿಪಡಿಸಿ. ನಂತರ {where} ಹಸಿರು ದುಂಡು ಬಾಣದ ಬಟನ್ ಒತ್ತಿ.",
                    target = "Send", on = CHAT_TYPED, next = CHAT_EMPTY, usualWhere = BOTTOM_RIGHT,
                ),
            ),
            done = { Lang.pick(Line("Bahut badhiya! Message badal gaya.", "बहुत बढ़िया! Message बदल गया।"), Line("Well done! The message is changed."), Line("ತುಂಬಾ ಚೆನ್ನಾಗಿದೆ! ಮೆಸೇಜ್ ಬದಲಾಯಿತು.")) },
        ),
    )

    val IDS: Set<String> = ALL.mapTo(LinkedHashSet()) { it.id }

    fun byId(id: String?): Guide? = ALL.firstOrNull { it.id == id }

    // The router. Her words are masked already, and are matched in Roman and Devanagari.
    private val WHATSAPP = Regex("(?i)whats\\s?app|whatsap|watsapp|watsap|वॉट्सऐप|व्हाट्सएप|व्हाट्सऐप|वाट्सएप|वॉट्सएप|ವಾಟ್ಸಾಪ್|ವಾಟ್ಸ್ಆ್ಯಪ್|ವಾಟ್ಸಪ್|ವಾಟ್ಸ್ ಆಪ್")
    private val MESSAGE = Regex("(?i)message|msg|massage|mesej|messej|sandesh|मैसेज|मेसेज|संदेश|ಮೆಸೇಜ್|ಮೆಸೇಜನ್ನು|ಸಂದೇಶ")
    private val EDIT = Regex("(?i)edit|badal|badl|sudhar|theek kar|thik kar|galat likh|change|correct|fix|एडिट|बदल|सुधार|ठीक कर|गलत लिख|ಎಡಿಟ್|ಬದಲಿಸ|ಬದಲಾಯಿಸ|ಸರಿಪಡಿಸ|ತಪ್ಪಾಗಿ ಬರೆ")
    private val NEW_NUMBER = Regex(
        "(?i)nay[ae] number|nayi number|new number|bina save|without sav|unsaved|anjaan number|" +
            "नया नंबर|नए नंबर|नये नंबर|बिना सेव|बिना save|not saved|ಹೊಸ ನಂಬರ್|ಸೇವ್ ಮಾಡದ|save ಮಾಡದ"
    )
    private val CONTACT = Regex("(?i)contact|kontact|number|कॉन्टैक्ट|कांटेक्ट|कॉन्टेक्ट|नंबर|ಕಾಂಟ್ಯಾಕ್ಟ್|ಕಾಂಟಾಕ್ಟ್|ನಂಬರ್")
    private val SAVE = Regex("(?i)save|sev\\b|add|jod|daal|सेव|जोड़|जोड|ऐड|डाल|ಸೇವ್|ಸೇರಿಸ")

    /**
     * Picks a guide for her words, or null. Only when the task is clearly about WhatsApp: she
     * named it, or she is in it or in a WhatsApp goal. Anything less clear goes to Gemini, which
     * can still pick a guide by its id.
     */
    fun match(words: String, inWhatsApp: Boolean): Guide? {
        if (!inWhatsApp && !WHATSAPP.containsMatchIn(words)) return null
        val id = when {
            NEW_NUMBER.containsMatchIn(words) && MESSAGE.containsMatchIn(words) -> MESSAGE_NEW_NUMBER
            EDIT.containsMatchIn(words) && MESSAGE.containsMatchIn(words) -> EDIT_MESSAGE
            CONTACT.containsMatchIn(words) && SAVE.containsMatchIn(words) -> ADD_CONTACT
            else -> return null
        }
        return byId(id)
    }

    private val DONE_WORDS = Regex("(?i)^(ho gaya|ho gya|hogaya|kar diya|kar liya|aage|next|haan ho gaya|done|finished|i did it|हो गया|कर दिया|कर लिया|आगे|ಆಯಿತು|ಆಯ್ತು|ಮಾಡಿದೆ|ಮುಂದೆ)$")

    /** "Ho gaya" and the like, said after a step. */
    fun isDone(words: String): Boolean = DONE_WORDS.matches(words.trim().trimEnd('.', '!', '?', '।').lowercase(Locale.ROOT))
}

/**
 * Runs one guide on the phone. [onScreen] is given every settled screen and says what to do.
 * It moves only forward: the current step, or a later one she has already reached. Anything
 * else goes to Gemini ([Decision.Fallback]), and the guide takes over again once a screen
 * matches.
 */
class GuideRunner(val guide: Guide) {
    sealed interface Decision {
        /** Say this step. control is what to point at, or null when it is said only. */
        data class Show(val index: Int, val step: GuideStep, val control: Control?) : Decision
        /** Same step, same screen: keep pointing and say nothing. */
        data class Stay(val control: Control?) : Decision
        data class Missing(val index: Int, val line: Line) : Decision
        object Done : Decision
        object Fallback : Decision
    }

    /** The step shown last, or -1 before the first. */
    var index = -1
        private set

    val step: GuideStep? get() = guide.steps.getOrNull(index)

    fun onScreen(screen: GuideScreen): Decision {
        val steps = guide.steps
        if (index == steps.lastIndex && guide.finished.matches(screen, guide.app)) return Decision.Done
        val found = (index.coerceAtLeast(0) until steps.size).firstOrNull { steps[it].on.matches(screen, guide.app) }
            ?: return Decision.Fallback
        val step = steps[found]
        val control = step.target?.let(screen::find)
        if (step.target != null && control == null && step.mustSee) {
            step.missing?.let { return Decision.Missing(found, it()) }
            return Decision.Fallback
        }
        if (found == index) return Decision.Stay(control)
        index = found
        return Decision.Show(found, step, control)
    }

    /**
     * For help by conversation only, when she said Nahi to the screen: "ho gaya" moves to the
     * next step. The first call starts at the first step inside the app if she is already in it.
     */
    fun advance(inApp: Boolean): Decision {
        val next = if (index < 0) guide.steps.indexOfFirst { it.on.inApp || !inApp } else index + 1
        if (next > guide.steps.lastIndex) return Decision.Done
        index = next
        return Decision.Show(next, guide.steps[next], null)
    }
}
