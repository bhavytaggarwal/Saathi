package com.saathi.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivacyFilterTest {
    // Screen masking

    @Test fun masksAmountsPhonesAccountsAndUpiIds() {
        assertEquals("Balance [number]", PrivacyFilter.maskScreenText("Balance ₹500"))
        assertEquals("[number]", PrivacyFilter.maskScreenText("Rs. 1,250.00"))
        assertEquals("Call [number]", PrivacyFilter.maskScreenText("Call +91 98765 43210"))
        assertEquals("A/c XXXX[number]", PrivacyFilter.maskScreenText("A/c XXXX1234"))
        assertEquals("[id]", PrivacyFilter.maskScreenText("rohith@okhdfcbank"))
    }

    @Test fun keepsLabelsAndSmallNumbers() {
        assertEquals("Add bank account", PrivacyFilter.maskScreenText("Add bank account"))
        assertEquals("Step 2 of 3", PrivacyFilter.maskScreenText("Step 2 of 3"))
        assertEquals("12 November", PrivacyFilter.maskScreenText("12 November"))
    }

    // Speech filter

    @Test fun masksDigitsSpokenAnyWay() {
        assertEquals("mera otp [number] hai", PrivacyFilter.filterSpeech("mera otp 4567 hai").text)
        assertEquals("otp [number]", PrivacyFilter.filterSpeech("otp 4 5 6 7").text)
        assertEquals("[number]", PrivacyFilter.filterSpeech("४५६७").text)
        assertEquals("pin [number] hai", PrivacyFilter.filterSpeech("pin ek do teen char hai").text)
        assertEquals("पिन [number]", PrivacyFilter.filterSpeech("पिन एक दो तीन चार").text)
    }

    @Test fun keepsOrdinaryHinglish() {
        val time = PrivacyFilter.filterSpeech("kal subah 10 baje bijli ka bill bharna hai")
        assertEquals("kal subah 10 baje bijli ka bill bharna hai", time.text)
        assertFalse(time.masked)
        assertFalse(PrivacyFilter.filterSpeech("do packet maggi aur ek doodh").masked)
    }

    @Test fun warnsOnlyWhenItLooksLikeASecret() {
        assertTrue(PrivacyFilter.filterSpeech("mera otp 456789 hai").warn)
        assertTrue(PrivacyFilter.filterSpeech("4 5 6 7").warn)
        val amount = PrivacyFilter.filterSpeech("bijli ka bill 2500 aaya")
        assertTrue(amount.masked)
        assertFalse(amount.warn)
    }

    // Hiding on secrets

    @Test fun hidesOnPasswordField() {
        assertTrue(PrivacyFilter.hasSecret(listOf(NodeFacts(isPassword = true, isEditable = true))))
    }

    @Test fun hidesOnLabelledSecretFields() {
        listOf("Enter OTP", "CVV", "Aadhaar number", "PAN", "Passport no").forEach {
            assertTrue(it, PrivacyFilter.hasSecret(listOf(NodeFacts(hint = it, isEditable = true))))
        }
        assertTrue(PrivacyFilter.hasSecret(listOf(NodeFacts(viewId = "com.x:id/edit_pin", isEditable = true))))
    }

    @Test fun hidesOnPinPromptWithCustomKeypad() {
        assertTrue(PrivacyFilter.hasSecret(listOf(NodeFacts(text = "ENTER UPI PIN"))))
        assertTrue(PrivacyFilter.hasSecret(listOf(NodeFacts(text = "Enter 6-digit PIN"))))
        assertTrue(PrivacyFilter.hasSecret(listOf(NodeFacts(text = "We sent a verification code"))))
        val keypad = (0..9).map { NodeFacts(text = it.toString(), isClickable = true) }
        assertTrue(PrivacyFilter.hasSecret(keypad))
    }

    @Test fun staysVisibleOnOrdinaryScreens() {
        val screen = listOf(
            NodeFacts(text = "Add bank account", isClickable = true),
            NodeFacts(text = "Spinner", isClickable = true),
            NodeFacts(text = "Shopping"),
            NodeFacts(text = "Pinterest"),
            NodeFacts(text = "Pattern, PIN, Password"),
            NodeFacts(hint = "Search apps", isEditable = true),
            NodeFacts(hint = "Company name", isEditable = true),
        )
        assertFalse(PrivacyFilter.hasSecret(screen))
    }

    @Test fun neverUsesTypedValueToClassifyButStillMatchesLabels() {
        assertFalse(PrivacyFilter.hasSecret(listOf(NodeFacts(text = "pan masala", isEditable = true))))
    }

    // Payment and scams

    @Test fun recognisesPaymentScreens() {
        assertTrue(PrivacyFilter.isPaymentScreen(listOf(NodeFacts(text = "Pay ₹500"))))
        assertTrue(PrivacyFilter.isPaymentScreen(listOf(NodeFacts(text = "Proceed to pay"))))
        assertFalse(PrivacyFilter.isPaymentScreen(listOf(NodeFacts(text = "Pay bills"), NodeFacts(text = "Add bank account"))))
    }

    @Test fun recognisesScams() {
        assertTrue(PrivacyFilter.looksLikeScam("Install AnyDesk now"))
        assertTrue(PrivacyFilter.looksLikeScam("Rahul wants to collect ₹2000"))
        assertTrue(PrivacyFilter.looksLikeScam("Please share the code"))
        assertFalse(PrivacyFilter.looksLikeScam("Add bank account"))
    }

    // Allowlist

    @Test fun neverAllowsMessagingOrBankingApps() {
        listOf(
            "com.whatsapp", "com.google.android.apps.messaging", "com.truecaller", "com.google.android.gm",
            "com.android.systemui", "com.sbi.lotusintouch", "com.snapwork.hdfc", "com.csam.icici.bank.imobile",
        ).forEach { assertFalse(it, Allowlist.isAllowed(it)) }
        assertFalse(Allowlist.isAllowed(null))
    }

    @Test fun androidAllowlistMatchesCodeAllowlist() {
        val config = File("src/main/res/xml/accessibility_service_config.xml").readText()
        val declared = Regex("android:packageNames=\"([^\"]+)\"").find(config)!!.groupValues[1].split(",").toSet()
        assertEquals(Allowlist.ALL, declared)
    }

    @Test fun serviceConfigCannotPerformGestures() {
        val config = File("src/main/res/xml/accessibility_service_config.xml").readText()
        assertFalse(config.contains("android:canPerformGestures"))
    }

    // AI reply contract

    @Test fun parsesAStep() {
        val reply = AiReply.parse(
            """{"mode":"step","text":"Add bank account pe tap karo","speech":"Add bank account पे tap करो","point_at":3,"gesture":"tap","task":"add bank"}"""
        )!!
        assertEquals("step", reply.mode)
        assertEquals(3, reply.pointAt)
        assertEquals("Add bank account पे tap करो", reply.line.speech)
    }

    @Test fun parsesListItemsAndFence() {
        val reply = AiReply.parse(
            "```json\n{\"mode\":\"list_add\",\"text\":\"\",\"speech\":\"\",\"items\":[{\"name\":\"Maggi\",\"name_hi\":\"मैगी\",\"qty\":\"2 packet\"}]}\n```"
        )!!
        assertEquals("2 packet", reply.items.single().qty)
        assertEquals("मैगी", reply.items.single().nameHi)
    }

    @Test fun rejectsAnythingOffContract() {
        assertNull(AiReply.parse("not json"))
        assertNull(AiReply.parse("""{"mode":"tap_button","text":"x","speech":"x"}"""))
    }

    @Test fun validatesReminderTimes() {
        assertEquals(9 to 5, AiReply.parseDailyTime("09:05"))
        assertNull(AiReply.parseDailyTime("25:00"))
        assertNull(AiReply.parseOnceTime("kal subah"))
        assertTrue(AiReply.parseOnceTime("2026-11-07 10:00") != null)
    }
}
