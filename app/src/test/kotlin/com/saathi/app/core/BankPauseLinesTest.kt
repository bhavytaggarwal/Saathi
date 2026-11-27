package com.saathi.app.core

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPauseLinesTest {
    @After fun reset() { Lang.current = Language.HINDI }

    @Test fun hindiStepOutTellsHerHowToComeBack() {
        Lang.current = Language.HINDI
        val line = Lines.BANK_STEP_OUT_SPEECH
        assertTrue(line, line.startsWith("Bank ki suraksha ke liye main abhi band ho rahi hoon."))
        assertTrue(line, "notification" in line && "Saathi app" in line)
    }

    @Test fun everyLanguageHasTheCardAndNotification() {
        for (language in listOf(Language.HINDI, Language.ENGLISH, Language.KANNADA)) {
            Lang.current = language
            for (text in listOf(
                Lines.BANK_STEP_OUT_SPEECH, Lines.REENABLE_NOTIFICATION_TITLE, Lines.REENABLE_NOTIFICATION_TEXT,
                Lines.BankPaused.TITLE, Lines.BankPaused.BODY, Lines.BankPaused.SAFETY_NOTE,
                Lines.BankPaused.ASK_BUTTON, Lines.BankPaused.REENABLE_BUTTON,
            )) assertTrue("$language: empty line", text.isNotBlank())
            if (language == Language.KANNADA) {
                assertTrue(Lines.BankPaused.SAFETY_NOTE.any { it in 'ಀ'..'೿' })
            }
        }
    }

    @Test fun safetyNoteWarnsAgainstSearchingForHelplines() {
        Lang.current = Language.ENGLISH
        val note = Lines.BankPaused.SAFETY_NOTE
        assertTrue(note, "Google" in note && "YouTube" in note && "ATM card" in note && "passbook" in note)
    }
}
