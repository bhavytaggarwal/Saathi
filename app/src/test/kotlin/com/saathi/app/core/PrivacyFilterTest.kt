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

    @Test fun namesWhyAScreenIsSecretWithoutScreenText() {
        assertEquals("password_field", PrivacyFilter.secretReason(listOf(NodeFacts(isPassword = true))))
        assertEquals("prompt_text", PrivacyFilter.secretReason(listOf(NodeFacts(text = "ENTER UPI PIN"))))
        assertEquals("number_pad", PrivacyFilter.secretReason((0..9).map { NodeFacts(text = it.toString(), isClickable = true) }))
        assertNull(PrivacyFilter.secretReason(listOf(NodeFacts(text = "Add bank account", isClickable = true))))
    }

    @Test fun clockAndCalculatorKeepNumberPadsButStillHideOnRealSecrets() {
        val keypad = (0..9).map { NodeFacts(text = it.toString(), isClickable = true) }
        listOf("com.android.deskclock", "com.vivo.calculator").forEach { pkg ->
            assertTrue(pkg, Allowlist.isAllowed(pkg))
            assertTrue(pkg, Allowlist.hasOrdinaryNumberPad(pkg))
        }
        assertFalse(PrivacyFilter.hasSecret(keypad, numberPadMeansSecret = false))
        assertTrue(PrivacyFilter.hasSecret(keypad + NodeFacts(text = "Enter PIN"), numberPadMeansSecret = false))
        assertTrue(PrivacyFilter.hasSecret(listOf(NodeFacts(isPassword = true)), numberPadMeansSecret = false))
        // Maps and every other app keep the full check.
        assertTrue(Allowlist.isAllowed("com.google.android.apps.maps"))
        assertFalse(Allowlist.hasOrdinaryNumberPad("com.google.android.apps.maps"))
        assertFalse(Allowlist.hasOrdinaryNumberPad(Allowlist.GPAY))
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

    @Test fun addsThePhonesOwnClockButNeverAssistantOrMessagingApps() {
        try {
            val added = Allowlist.addPhoneApps(
                clocks = setOf("com.android.BBKClock", "com.google.android.googlequicksearchbox"),
                calculators = setOf("com.android.bbkcalculator"),
                launchers = setOf("com.android.settings", "com.google.android.apps.messaging"),
            )
            assertEquals(setOf("com.android.BBKClock", "com.android.bbkcalculator"), added)
            assertTrue(Allowlist.isAllowed("com.android.BBKClock"))
            assertTrue(Allowlist.hasOrdinaryNumberPad("com.android.BBKClock"))
            assertEquals("Clock", Allowlist.label("com.android.BBKClock"))
            assertFalse(Allowlist.isAllowed("com.google.android.googlequicksearchbox"))
            assertFalse(Allowlist.isAllowed("com.google.android.apps.messaging"))
            assertEquals("Settings", Allowlist.label("com.android.settings"))
        } finally {
            Allowlist.addPhoneApps(emptySet(), emptySet(), emptySet())
        }
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

    @Test fun masksAmountsWithCurrencyAfterOrInHindi() {
        assertEquals("Paid [number]", PrivacyFilter.maskScreenText("Paid 500 rupees"))
        assertEquals("[number] bheje", PrivacyFilter.maskScreenText("50 Rs bheje"))
        assertEquals("[number] बाकी", PrivacyFilter.maskScreenText("रु 250 बाकी"))
    }

    @Test fun masksDigitsSpokenAsEnglishWordsOrMixed() {
        assertEquals("otp [number]", PrivacyFilter.filterSpeech("otp फोर फाइव सिक्स सेवन").text)
        assertEquals("pin [number]", PrivacyFilter.filterSpeech("pin 45 ek 67").text)
        assertFalse(PrivacyFilter.filterSpeech("10 do packet").masked)
    }

    @Test fun recognisesACollectRequestOnAPaymentScreen() {
        val collect = listOf(NodeFacts(text = "Rahul is requesting ₹500"), NodeFacts(text = "Pay ₹500", isClickable = true))
        assertTrue(PrivacyFilter.isPaymentScreen(collect))
        assertTrue(PrivacyFilter.isScamScreen(collect))
        assertFalse(PrivacyFilter.isScamScreen(listOf(NodeFacts(text = "Pay ₹500"))))
    }

    // AI reply contract

    private fun parsed(raw: String) = (AiReply.parse(raw) as AiReply.Parsed.Ok).reply

    private fun invalid(raw: String) = (AiReply.parse(raw) as AiReply.Parsed.Invalid).reason

    @Test fun parsesAGuideStep() {
        val reply = parsed(
            """{"intent":"guide_step","confidence":0.92,"say_roman":"Add bank account pe tap karo","say_devanagari":"Add bank account पे tap करो","target_element":3,"gesture":"tap","state_patch":{}}"""
        )
        assertEquals("guide_step", reply.intent)
        assertEquals(3, reply.targetElement)
        assertEquals("Add bank account पे tap करो", reply.line.speech)
    }

    @Test fun ignoresATargetOutsidePointingIntents() {
        val reply = parsed("""{"intent":"chat","confidence":1,"say_roman":"Namaste","say_devanagari":"नमस्ते","target_element":4,"state_patch":{}}""")
        assertNull(reply.targetElement)
    }

    @Test fun parsesListActionAndFence() {
        val reply = parsed(
            "```json\n{\"intent\":\"list_add\",\"confidence\":0.9,\"say_roman\":\"\",\"say_devanagari\":\"\"," +
                "\"action\":{\"type\":\"list_add\",\"list\":\"shopping\",\"items\":[{\"name\":\"Maggi\",\"name_hi\":\"मैगी\",\"qty\":\"2 packet\"}]},\"state_patch\":{}}\n```"
        )
        assertEquals("2 packet", reply.action!!.items.single().qty)
        assertEquals("मैगी", reply.action!!.items.single().nameHi)
    }

    @Test fun parsesADailyReminder() {
        val reply = parsed(
            """{"intent":"reminder_set","confidence":0.8,"say_roman":"Roz raat 9 baje bolungi.","say_devanagari":"","action":{"type":"reminder_set","text":"dawai","time":"21:00","repeat":"daily"},"state_patch":{}}"""
        )
        assertEquals("daily", reply.action!!.repeat)
        assertEquals("dawai", reply.action!!.textHi)
    }

    @Test fun rejectsAnythingOffContractWithAReason() {
        assertEquals("it was not one JSON object", invalid("not json"))
        assertTrue(invalid("""{"intent":"tap_button","confidence":1,"say_roman":"x","say_devanagari":"x"}""").contains("tap_button"))
        assertEquals("confidence is missing", invalid("""{"intent":"chat","say_roman":"x","say_devanagari":"x"}"""))
        assertEquals("say_roman is empty", invalid("""{"intent":"chat","confidence":1,"say_roman":"","say_devanagari":""}"""))
        assertEquals("list_add needs action.items", invalid("""{"intent":"list_add","confidence":1,"say_roman":"","say_devanagari":""}"""))
        assertTrue(
            invalid("""{"intent":"reminder_set","confidence":1,"say_roman":"","say_devanagari":"","action":{"type":"reminder_set","text":"dawai","time":"raat 9","repeat":"daily"}}""")
                .contains("raat 9")
        )
    }

    // Session state

    @Test fun startsAGoalAndKeepsAppOwnedFields() {
        val state = SessionState()
        val first = state.applyPatch(org.json.JSONObject("""{"goal":{"text":"Add a bank account in GPay","kind":"guide","app":"Google Pay"}}"""))
        assertTrue(first.goalStarted)
        state.goal = state.goal!!.copy(step = 3)
        // A text-only change refines the same goal and keeps the step count.
        val refined = state.applyPatch(org.json.JSONObject("""{"goal":{"text":"Add SBI account in GPay","step":99}}"""))
        assertFalse(refined.goalStarted)
        assertEquals(3, state.goal!!.step)
        assertEquals("Add SBI account in GPay", state.goal!!.text)
    }

    @Test fun aNewKindStartsFreshAndNullClears() {
        val state = SessionState()
        state.applyPatch(org.json.JSONObject("""{"goal":{"text":"Add bank","kind":"guide"},"last_step":{"target_label":"Add bank account","expected_next":"bank list"}}"""))
        assertEquals("Add bank account", state.lastStep.targetLabel)
        val topic = state.applyPatch(org.json.JSONObject("""{"goal":{"text":"Medicine reminder at 9 pm","kind":"reminder"}}"""))
        assertTrue(topic.goalStarted)
        assertNull(state.lastStep.targetLabel)
        state.applyPatch(org.json.JSONObject("""{"last_step":{"expected_next":"x"},"pending_question":{"question":"List ya reminder?","options":["list","reminder"]}}"""))
        assertEquals(listOf("list", "reminder"), state.pendingQuestion!!.options)
        state.applyPatch(org.json.JSONObject("""{"goal":null,"pending_question":null}"""))
        assertNull(state.goal)
        assertNull(state.pendingQuestion)
    }

    @Test fun ignoresStatusesTheAppOwns() {
        val state = SessionState()
        state.applyPatch(org.json.JSONObject("""{"goal":{"text":"Add bank","kind":"guide"}}"""))
        state.applyPatch(org.json.JSONObject("""{"goal":{"status":"expired"}}"""))
        assertEquals(SessionState.ACTIVE, state.goal!!.status)
        state.applyPatch(org.json.JSONObject("""{"goal":{"status":"done"}}"""))
        assertEquals(SessionState.DONE, state.goal!!.status)
    }

    @Test fun stateJsonHasEveryFieldAndNoMissingKeys() {
        val state = SessionState()
        state.lastActivityAt = 1_000
        state.applyPatch(org.json.JSONObject("""{"goal":{"text":"Add bank","kind":"guide"}}"""))
        state.lastAction = LastAction("list_add", list = "shopping", items = listOf("doodh"))
        val json = state.toJson(now = 13_000)
        assertEquals(12, json.getJSONObject("goal").getInt("idle_s"))
        assertTrue(json.isNull("pending_question"))
        assertTrue(json.has("screen"))
        assertEquals("doodh", json.getJSONObject("last_action").getJSONArray("items").getString(0))
    }

    @Test fun validatesReminderTimes() {
        assertEquals(9 to 5, AiReply.parseDailyTime("09:05"))
        assertNull(AiReply.parseDailyTime("25:00"))
        assertNull(AiReply.parseOnceTime("kal subah"))
        assertTrue(AiReply.parseOnceTime("2026-11-07 10:00") != null)
    }
}
