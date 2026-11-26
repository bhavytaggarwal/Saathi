package com.saathi.app.core

import com.saathi.app.AccessibilitySetupGuide
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTest {
    private companion object {
        val OLDER_LONG_HINDI = setOf(
            "getCHECKOUT_PAYMENT_STEP_OUT_SPEECH", "getCHECKOUT_PAYMENT_STEP_OUT",
            "getBANK_STEP_OUT_SPEECH", "getBANK_STEP_OUT", "getNO_CREDIT",
        )
    }

    private val ready = Language.entries.filter { it.ready }
    private val tapOrClick = Regex("(?i)\\btap\\b|\\bclick\\b|टैप|क्लिक|ಟ್ಯಾಪ್|ಕ್ಲಿಕ್")

    @After
    fun backToHindi() {
        Lang.current = Language.HINDI
    }

    private fun String.hasKannada() = any { it in 'ಀ'..'೿' }
    private fun String.hasDevanagari() = any { it in 'ऀ'..'ॿ' }

    /** Every fixed line and label in Lines, found by reflection so a new one cannot be missed. */
    private fun allFixedLines(): Map<String, Line> {
        val out = LinkedHashMap<String, Line>()
        for (obj in listOf<Any>(Lines, Lines.Setup)) {
            for (m in obj.javaClass.methods) {
                if (m.parameterCount != 0 || !m.name.startsWith("get") || m.declaringClass != obj.javaClass) continue
                when (val v = m.invoke(obj)) {
                    is Line -> out[m.name] = v
                    is String -> out[m.name] = Line(v)
                }
            }
        }
        out["tapHere(3)"] = Lines.tapHere(3)
        out["tapHere(null)"] = Lines.tapHere(null)
        out["scamWarning"] = Lines.scamWarning("")
        out["listEmpty"] = Lines.listEmpty()
        return out
    }

    @Test
    fun everyLineExistsInEveryReadyLanguageInItsOwnScript() {
        val hindi = allFixedLines()
        assertTrue("found the lines", hindi.size > 60)
        for (language in ready) {
            Lang.current = language
            for ((name, line) in allFixedLines()) {
                if (name == "getCHOOSE_TITLE") continue // written in all three scripts at once
                assertTrue("$language $name is blank", line.text.isNotBlank() && line.speech.isNotBlank())
                assertFalse("$language $name says tap or click", tapOrClick.containsMatchIn(line.text + " " + line.speech))
                when (language) {
                    Language.KANNADA -> assertTrue("$name not in Kannada: ${line.text}", line.text.hasKannada())
                    Language.ENGLISH -> assertFalse("$name has Hindi in English: ${line.text}", line.text.hasDevanagari() || line.text.hasKannada())
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun spokenLinesStayShort() {
        // Under 15 words, as for Hindi. Longer safety and payment lines are allowed a little more.
        val longAllowed = setOf("getCHECKOUT_PAYMENT_STEP_OUT_SPEECH", "getCHECKOUT_PAYMENT_STEP_OUT", "scamWarning", "getSOUND_UNKNOWN")
        val tooLong = ArrayList<String>()
        for (language in ready) {
            Lang.current = language
            for ((name, line) in allFixedLines()) {
                val words = line.text.split(Regex("\\s+")).count { it.isNotBlank() }
                val limit = if (name in longAllowed) 20 else 15
                // Hindi lines that were already longer before other languages were added.
                val knownHindi = language == Language.HINDI && name in OLDER_LONG_HINDI
                if (words > limit && !knownHindi) tooLong += "$language $name ($words): ${line.text}"
            }
        }
        assertTrue(tooLong.joinToString("\n"), tooLong.isEmpty())
    }

    @Test
    fun hindiLinesAreUnchangedByTheLanguageWork() {
        assertEquals(Line("Sun rahi hoon...", "सुन रही हूँ..."), Lines.LISTENING_NOW)
        assertEquals("Upar se teesri line par dabaiye, jahan ghera bana hai.", Lines.tapHere(3).text)
        assertEquals("Bolo", Lines.BUTTON_SPEAK)
    }

    @Test
    fun englishAndKannadaSayTheirOwnWords() {
        Lang.current = Language.ENGLISH
        assertEquals("Press the third line from the top, where the circle is.", Lines.tapHere(3).text)
        assertEquals("Speak", Lines.BUTTON_SPEAK)
        Lang.current = Language.KANNADA
        assertTrue(Lines.tapHere(3).text.contains("ಮೂರನೇ"))
        assertEquals("ಹೇಳಿ", Lines.BUTTON_SPEAK)
        assertTrue(Lines.OTP_WARNING.speech.hasKannada())
    }

    @Test
    fun everyGuideStepHasEnglishAndKannada() {
        for (guide in Guides.ALL) for ((i, step) in guide.steps.withIndex()) {
            val name = "${guide.id} step $i"
            assertTrue("$name English", step.en.isNotBlank() && !step.en.hasDevanagari())
            assertTrue("$name Kannada", step.kn.hasKannada())
            assertEquals("$name {where} in English", "{where}" in step.roman, "{where}" in step.en)
            assertEquals("$name {where} in Kannada", "{where}" in step.roman, "{where}" in step.kn)
        }
        Lang.current = Language.ENGLISH
        val newChat = Guides.byId(Guides.ADD_CONTACT)!!.steps[1]
        assertEquals("Press the round green button at the bottom right, the one with a chat sign.", newChat.line(null).text)
        assertEquals("Well done! The number is saved.", Guides.byId(Guides.ADD_CONTACT)!!.doneLine.text)
        Lang.current = Language.KANNADA
        assertTrue(newChat.line(null).text.startsWith("ಕೆಳಗೆ ಬಲಭಾಗದಲ್ಲಿ"))
    }

    @Test
    fun placesAreSaidInTheLanguageInUse() {
        val bottomRight = Box(900, 2100, 1060, 2260)
        assertEquals("neeche right mein" to "नीचे right में", ControlsOnly.spokenPlace(bottomRight, 1080, 2400))
        Lang.current = Language.ENGLISH
        assertEquals("at the bottom right" to "at the bottom right", ControlsOnly.spokenPlace(bottomRight, 1080, 2400))
        assertEquals("in the middle", Place(Place.MIDDLE).words().first)
        Lang.current = Language.KANNADA
        assertEquals("ಕೆಳಗೆ ಬಲಭಾಗದಲ್ಲಿ", ControlsOnly.spokenPlace(bottomRight, 1080, 2400).first)
    }

    @Test
    fun kannadaGuideRouting() {
        assertEquals(Guides.ADD_CONTACT, Guides.match("ವಾಟ್ಸಾಪ್ ನಲ್ಲಿ ಹೊಸ ಕಾಂಟ್ಯಾಕ್ಟ್ ಸೇವ್ ಮಾಡುವುದು ಹೇಗೆ", inWhatsApp = false)?.id)
        assertEquals(Guides.EDIT_MESSAGE, Guides.match("ಮೆಸೇಜ್ ಬದಲಿಸಬೇಕು", inWhatsApp = true)?.id)
        assertTrue(Guides.isDone("ಆಯಿತು"))
        assertTrue(Guides.isDone("done"))
    }

    @Test
    fun kannadaSecretsAreMaskedAndWarned() {
        // Kannada digits, Kannada number words, and the word OTP in Kannada.
        val digits = PrivacyFilter.filterSpeech("ನನ್ನ ಒಟಿಪಿ ೧೨೩೪೫೬")
        assertTrue(digits.masked)
        assertTrue(digits.warn)
        assertFalse(digits.text.contains("೧೨೩೪"))
        val words = PrivacyFilter.filterSpeech("ಒಂದು ಎರಡು ಮೂರು ನಾಲ್ಕು")
        assertEquals(PrivacyFilter.MASK, words.text)
        assertTrue(PrivacyFilter.mentionsSecret("ನನ್ನ ಪಿನ್ ಮರೆತುಹೋಯಿತು"))
        assertTrue(PrivacyFilter.looksLikeScam("ಅವರು ಒಟಿಪಿ ಕೇಳುತ್ತಿದ್ದಾರೆ"))
        // A short count of things is not a secret.
        assertFalse(PrivacyFilter.filterSpeech("ಎರಡು ಹಾಲು ಪ್ಯಾಕೆಟ್").masked)
    }

    @Test
    fun theSuggestedLanguageIsThePhonesWhenSaathiSpeaksIt() {
        assertEquals(Language.KANNADA, Language.suggestedFor("kn"))
        assertEquals(Language.ENGLISH, Language.suggestedFor("en-IN"))
        assertEquals(Language.HINDI, Language.suggestedFor("te"))
        assertEquals(Language.HINDI, Language.fromCode("te"))
        assertEquals(Language.HINDI, Language.fromCode(null))
        assertTrue(Language.entries.filter { !it.ready }.isNotEmpty())
    }

    @Test
    fun accessibilitySetupLineIsInSaathisLanguage() {
        val hi = AccessibilitySetupGuide.spokenInstruction("en", "samsung", Language.HINDI)
        val en = AccessibilitySetupGuide.spokenInstruction("en", "samsung", Language.ENGLISH)
        val kn = AccessibilitySetupGuide.spokenInstruction("en", "xiaomi", Language.KANNADA)
        assertEquals("Ab 'Installed apps' par dabaiye, phir 'Saathi' ko chalu karke 'Allow' daba dijiye.", hi)
        assertEquals("Now press 'Installed apps', then turn on Saathi and press 'Allow'.", en)
        assertTrue(kn.contains("'Downloaded apps'") && kn.hasKannada())
        assertNotEquals(AccessibilitySetupGuide.FALLBACK_SPATIAL_INSTRUCTION, AccessibilitySetupGuide.spokenInstruction("ta", "samsung", Language.KANNADA))
    }

    @Test
    fun outsideHindiTheBubbleShowsWhatSheHears() {
        // As the model sent it in the eval: Hinglish in say_roman for a Kannada speaker.
        val json = """{"intent":"guide_step","confidence":0.9,"say_roman":"Upar se teesri line mein Bluetooth dabaiye.",""" +
            """"say_devanagari":"ಮೇಲಿನಿಂದ ಮೂರನೇ ಸಾಲಿನಲ್ಲಿ Bluetooth ಒತ್ತಿ.","target_element":4,"guide":"none","gesture":"tap","state_patch":{}}"""
        Lang.current = Language.KANNADA
        val kn = (AiReply.parse(json) as AiReply.Parsed.Ok).reply
        assertEquals("ಮೇಲಿನಿಂದ ಮೂರನೇ ಸಾಲಿನಲ್ಲಿ Bluetooth ಒತ್ತಿ.", kn.line.text)
        Lang.current = Language.HINDI
        val hi = (AiReply.parse(json) as AiReply.Parsed.Ok).reply
        assertEquals("Upar se teesri line mein Bluetooth dabaiye.", hi.line.text)
    }
}
