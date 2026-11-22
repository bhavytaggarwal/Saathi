package com.saathi.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySetupGuideTest {
    private fun say(language: String, manufacturer: String) =
        AccessibilitySetupGuide.spokenInstruction(language = language, manufacturer = manufacturer)

    @Test
    fun englishSamsungSaysInstalledApps() {
        val line = say("en", "samsung")
        assertTrue(line.contains("'Installed apps'"))
        assertTrue(line.contains("'Allow'"))
        assertFalse(line.contains("Downloaded apps"))
    }

    @Test
    fun englishXiaomiSaysDownloadedApps() {
        val line = say("en", "Xiaomi")
        assertTrue(line.contains("'Downloaded apps'"))
        assertTrue(line.contains("'Allow'"))
        assertFalse(line.contains("Installed apps"))
        // Other skins that use the same label.
        for (oem in listOf("redmi", "poco", "oppo", "oneplus", "realme", "vivo")) {
            assertEquals(line, say("en", oem))
        }
    }

    @Test
    fun englishPixelNamesBothDownloadedAppsAndSaathi() {
        val line = say("en", "Google")
        assertTrue(line.contains("'Downloaded apps' ya 'Saathi'"))
    }

    @Test
    fun hindiSamsungUsesTheHindiSettingsLabels() {
        val line = say("hi", "samsung")
        assertTrue(line.contains("इंस्टॉल किए गए ऐप्स"))
        assertTrue(line.contains("अनुमति दें"))
        assertTrue(say("hi", "xiaomi").contains("डाउनलोड किए गए ऐप्स"))
    }

    @Test
    fun regionTagsStillMatch() {
        assertEquals(say("en", "samsung"), say("en-IN", "samsung"))
        assertEquals(say("hi", "vivo"), say("hi_IN", "vivo"))
    }

    @Test
    fun unsupportedLanguageFallsBackToSpatialInstruction() {
        assertEquals(AccessibilitySetupGuide.FALLBACK_SPATIAL_INSTRUCTION, say("ta", "samsung"))
        assertEquals(AccessibilitySetupGuide.FALLBACK_SPATIAL_INSTRUCTION, say("mr", "xiaomi"))
        assertEquals(AccessibilitySetupGuide.FALLBACK_SPATIAL_INSTRUCTION, say("", "oneplus"))
    }

    @Test
    fun neverSaysTapOrClick() {
        for (lang in listOf("en", "hi", "ta")) for (oem in listOf("samsung", "xiaomi", "google")) {
            assertFalse(Regex("(?i)\\btap\\b|\\bclick\\b").containsMatchIn(say(lang, oem)))
        }
    }
}
