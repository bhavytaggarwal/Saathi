package com.saathi.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCommandParserTest {

    @Test fun parsesSimpleMorningAlarm() {
        val action = LocalCommandParser.parse("subah 7 baje alarm laga do", hasPendingQuestion = false)
        assertTrue(action is LocalAction.AlarmSet)
        val alarm = action as LocalAction.AlarmSet
        assertEquals("07:00", alarm.time)
        assertEquals(7, alarm.hour)
        assertEquals(0, alarm.minute)
    }

    @Test fun parsesDailyNightReminderWithLabel() {
        val action = LocalCommandParser.parse("roz raat 9 baje dawai ka reminder", hasPendingQuestion = false)
        assertTrue(action is LocalAction.ReminderSet)
        val reminder = action as LocalAction.ReminderSet
        assertEquals("dawai", reminder.text)
        assertEquals("21:00", reminder.time)
        assertEquals("daily", reminder.repeat)
    }

    @Test fun parsesOnceReminderWithLabel() {
        val action = LocalCommandParser.parse("raat 9 baje dawai ka reminder laga do", hasPendingQuestion = false)
        assertTrue(action is LocalAction.ReminderSet)
        val reminder = action as LocalAction.ReminderSet
        assertEquals("dawai", reminder.text)
        assertEquals("21:00", reminder.time)
        assertEquals("once", reminder.repeat)
    }

    @Test fun parsesSimpleListReadAndClear() {
        val read = LocalCommandParser.parse("shopping list padho", hasPendingQuestion = false)
        assertEquals(LocalAction.ListRead("shopping"), read)

        val clear = LocalCommandParser.parse("todo list saaf karo", hasPendingQuestion = false)
        assertEquals(LocalAction.ListClear("todo"), clear)
    }

    @Test fun returnsNullForAmbiguousOrRelativeOrPendingTurnInputs() {
        // Ambiguous without explicit list/reminder command
        assertNull(LocalCommandParser.parse("doodh khatam ho gaya", hasPendingQuestion = false))

        // Relative date ("parso")
        assertNull(LocalCommandParser.parse("parso dopahar 2 baje doctor", hasPendingQuestion = false))
        assertNull(LocalCommandParser.parse("parso dopahar 2 baje doctor ka reminder", hasPendingQuestion = false))

        // Relative duration ("5 minute baad")
        assertNull(LocalCommandParser.parse("5 minute baad chai band karna", hasPendingQuestion = false))

        // Missing AM/PM period on <= 12 hour time
        assertNull(LocalCommandParser.parse("7 baje alarm laga do", hasPendingQuestion = false))

        // Active pending question must always bypass Tier 1
        assertNull(LocalCommandParser.parse("shopping list padho", hasPendingQuestion = true))
        assertNull(LocalCommandParser.parse("subah 7 baje alarm laga do", hasPendingQuestion = true))
        assertNull(LocalCommandParser.parse("roz raat 9 baje dawai ka reminder", hasPendingQuestion = true))
    }
}
