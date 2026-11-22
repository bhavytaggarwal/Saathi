package com.saathi.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollRowsTest {
    private val screenHeight = 2400

    private fun list(bottom: Int = 2200) = ScrollRows.Node(Box(0, 300, 1080, bottom), scrollable = true, tappable = false)
    private fun row(top: Int, height: Int = 200) = ScrollRows.Node(Box(0, top, 1080, top + height), scrollable = false, tappable = true)

    @Test
    fun numbersRowsFromTheTop() {
        val nodes = listOf(list(), row(700), row(300), row(500))
        val result = ScrollRows.analyse(nodes, screenHeight)
        assertEquals(3, result.total)
        assertEquals(1, result.rows[2])
        assertEquals(2, result.rows[3])
        assertEquals(3, result.rows[1])
    }

    @Test
    fun aSwitchInsideARowIsNotARowOfItsOwn() {
        val switch = ScrollRows.Node(Box(900, 320, 1060, 480), scrollable = false, tappable = true)
        val wideInner = ScrollRows.Node(Box(20, 310, 1060, 490), scrollable = false, tappable = true)
        val result = ScrollRows.analyse(listOf(list(), row(300), switch, wideInner, row(500)), screenHeight)
        assertEquals(2, result.total)
        assertNull(result.rows[2])
        assertNull(result.rows[3])
        assertEquals(2, result.rows[4])
    }

    @Test
    fun aRowCutOffAtTheBottomMeansMoreBelow() {
        val result = ScrollRows.analyse(listOf(list(bottom = 2200), row(300), row(2100, height = 100)), screenHeight)
        assertTrue(result.moreBelow)
    }

    @Test
    fun aListThatEndsOnScreenHasNothingBelow() {
        val result = ScrollRows.analyse(listOf(list(bottom = 2200), row(300), row(500)), screenHeight)
        assertTrue(result.scrolls)
        assertFalse(result.moreBelow)
    }

    @Test
    fun noScrollingListMeansNoRows() {
        val result = ScrollRows.analyse(listOf(row(300), row(500)), screenHeight)
        assertFalse(result.scrolls)
        assertEquals(0, result.total)
    }

    @Test
    fun aSmallScrollingStripIsNotTheMainList() {
        val chips = ScrollRows.Node(Box(0, 100, 1080, 250), scrollable = true, tappable = false)
        val result = ScrollRows.analyse(listOf(chips, row(120, height = 100)), screenHeight)
        assertFalse(result.scrolls)
    }

    @Test
    fun tapLineCountsTheRowAndNeverSaysTap() {
        val line = Lines.tapHere(3)
        assertTrue(line.text.contains("teesri"))
        assertFalse(Regex("(?i)\\btap\\b").containsMatchIn(line.text + line.speech))
        assertTrue(Lines.tapHere(null).text.contains("ghera"))
        assertTrue(Lines.tapHere(14).text.contains("ghera"))
    }
}
