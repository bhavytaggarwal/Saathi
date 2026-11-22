package com.saathi.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.saathi.app.core.Lines

/**
 * Senior-friendly voice-guided 3-step visual onboarding plus collapsible phone settings.
 * Each onboarding step speaks its Hindi instruction aloud automatically when shown.
 */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var voice: VoiceIO
    private lateinit var phone: PhoneHelp
    private val refreshers = ArrayList<() -> Unit>()

    private lateinit var stepperContainer: LinearLayout
    private lateinit var inAppAssistantContainer: LinearLayout
    private var inAppAssistant: Assistant? = null
    private var currentStepIndex = -1
    private var lastSpokenStep = -1

    private lateinit var voiceSpinner: Spinner
    private lateinit var voiceStatus: TextView
    private var voiceNames: List<String> = emptyList()
    private lateinit var packStatus: TextView
    private lateinit var packButton: View
    private var packChecksLeft = 0
    private val packPoll = Runnable { refreshPack() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        voice = VoiceIO(this, prefs)
        phone = PhoneHelp(this)
        build()
    }

    override fun onResume() {
        super.onResume()
        updateOnboardingStep(autoSpeak = true)
        refreshers.forEach { it() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateOnboardingStep(autoSpeak = true)
        refreshers.forEach { it() }
    }

    override fun onDestroy() {
        packStatus.removeCallbacks(packPoll)
        inAppAssistant?.shutdown()
        inAppAssistant = null
        voice.shutdown()
        super.onDestroy()
    }

    private fun build() {
        val (scroll, root) = page()
        setContentView(scroll)

        root.addView(title("Saathi"))

        // 3-Card Voice-Guided Visual Stepper
        stepperContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(stepperContainer, wrapWithGap(12))

        // In-app voice assistant host used when Accessibility is disabled (e.g. after Bank Mode step-out)
        inAppAssistantContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(inAppAssistantContainer, wrapWithGap(12))

        // Quick link to Lists and Reminders
        root.addView(
            primaryButton("Lists and reminders kholein", 0xFF5C6B73.toInt()) {
                startActivity(Intent(this, ListsActivity::class.java))
            },
            wrapWithGap(14),
        )

        // Collapsible advanced settings for family member / helper
        val advancedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val toggleAdvanced = TextView(this).apply {
            text = "⚙️ Aur settings dikhayein (Battery, Voice, Naam)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF0F5E5C.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener {
                val show = advancedContainer.visibility != View.VISIBLE
                advancedContainer.visibleIf(show)
                text = if (show) {
                    "⚙️ Aur settings chhupayein"
                } else {
                    "⚙️ Aur settings dikhayein (Battery, Voice, Naam)"
                }
            }
        }
        root.addView(toggleAdvanced, wrapWithGap(10))
        root.addView(advancedContainer)

        buildAdvancedSettings(advancedContainer)
        updateOnboardingStep(autoSpeak = true)
    }

    private fun computeActiveStep(): Int {
        val hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasAccessibility = isServiceOn()
        if (hasAccessibility) {
            prefs.pausedForBanking = false
            prefs.onboardingCompleted = true
            inAppAssistant?.shutdown()
            inAppAssistant = null
            if (::inAppAssistantContainer.isInitialized) {
                inAppAssistantContainer.removeAllViews()
                inAppAssistantContainer.visibility = View.GONE
            }
        }
        return when {
            !hasMic -> 1
            // Do not nag the user on app launch if Accessibility was disabled after onboarding or
            // automatically stepped out for a strict banking app.
            !hasAccessibility && !prefs.pausedForBanking && !prefs.onboardingCompleted -> 2
            else -> 3
        }
    }

    private fun ensureInAppAssistant(): Assistant {
        val existing = inAppAssistant
        if (existing != null) return existing
        inAppAssistantContainer.removeAllViews()
        inAppAssistantContainer.visibility = View.VISIBLE
        return Assistant(this).also { created ->
            inAppAssistant = created
            created.startInApp(inAppAssistantContainer)
        }
    }

    private fun updateOnboardingStep(autoSpeak: Boolean) {
        val step = computeActiveStep()
        val stepChanged = step != currentStepIndex
        currentStepIndex = step
        stepperContainer.removeAllViews()

        when (step) {
            1 -> stepperContainer.addView(
                buildStepCard(
                    stepNumber = "Step 1 of 3",
                    iconText = "🎙️",
                    headline = "Bas boliye, main sunungi",
                    spokenHindi = "बस बोलिए, मैं सुनूँगी। नीचे माइक अलाउ करें बटन दबाइए।",
                    ctaLabel = "Mic allow karein",
                    onCtaClick = {
                        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                    },
                )
            )
            2 -> stepperContainer.addView(
                buildStepCard(
                    stepNumber = "Step 2 of 3",
                    iconText = "👆",
                    headline = "Main aapko button dikhaungi",
                    spokenHindi = "मैं आपको बटन दिखाऊँगी। नीचे साथी चालू करें बटन दबाइए।",
                    ctaLabel = "Saathi chalu karein",
                    onCtaClick = {
                        voice.speak(AccessibilitySetupGuide.spokenInstruction())
                        startActivity(AccessibilitySetupGuide.createSettingsIntent(this@MainActivity))
                    },
                    extraFooter = if (shouldShowSideloadHelp()) buildSideloadRestrictedHelp() else null,
                )
            )
            else -> stepperContainer.addView(
                buildStepCard(
                    stepNumber = "Step 3 of 3",
                    iconText = "✨",
                    headline = "Shuru karein! Poochiye: 'Shopping list padho'",
                    spokenHindi = "शुरू करें! पूछिए: शॉपिंग लिस्ट पढ़ो।",
                    ctaLabel = "Shuru karein",
                    onCtaClick = {
                        voice.stopSpeaking()
                        val assistant = SaathiAccessibilityService.instance?.assistant
                        if (assistant != null) {
                            assistant.onBubbleTap()
                            moveTaskToBack(true)
                        } else {
                            // Keep all voice-only features (lists, reminders, alarms, scam checks,
                            // bank_text_help) working in-app even while Accessibility is disabled.
                            ensureInAppAssistant().onBubbleTap()
                        }
                    },
                )
            )
        }

        if (autoSpeak && (stepChanged || lastSpokenStep != step)) {
            lastSpokenStep = step
            val speech = when (step) {
                1 -> "बस बोलिए, मैं सुनूँगी। नीचे माइक अलाउ करें बटन दबाइए।"
                2 -> "मैं आपको बटन दिखाऊँगी। नीचे साथी चालू करें बटन दबाइए।"
                else -> "शुरू करें! पूछिए: शॉपिंग लिस्ट पढ़ो।"
            }
            stepperContainer.postDelayed({
                if (!isDestroyed) voice.speak(speech)
            }, 350)
        }
    }

    private fun buildStepCard(
        stepNumber: String,
        iconText: String,
        headline: String,
        spokenHindi: String,
        ctaLabel: String,
        onCtaClick: () -> Unit,
        extraFooter: View? = null,
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f * resources.displayMetrics.density
                setStroke(dp(2), 0xFFE8730C.toInt())
            }
        }

        // Top row: Step pill + "Dobara sunein" speaker button
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val stepBadge = TextView(this).apply {
            text = stepNumber
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFF6A5D4D.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        val replayBtn = TextView(this).apply {
            text = "🔊 Dobara sunein"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFF0F5E5C.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(0xFFEAF4F3.toInt())
                cornerRadius = 16f * resources.displayMetrics.density
            }
            setOnClickListener { voice.speak(spokenHindi) }
        }
        topRow.addView(stepBadge, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topRow.addView(replayBtn)
        card.addView(topRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // High-contrast visual icon circle
        val iconView = TextView(this).apply {
            text = iconText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFF3E0.toInt())
                setStroke(dp(2), 0xFFE8730C.toInt())
            }
        }
        val iconParams = LinearLayout.LayoutParams(dp(88), dp(88)).apply {
            topMargin = dp(18)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        card.addView(iconView, iconParams)

        // Large 22sp+ senior-friendly headline
        val headlineView = TextView(this).apply {
            text = headline
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            setTextColor(0xFF1F1A14.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }
        card.addView(headlineView, wrapWithGap(18))

        // Single big bottom CTA button
        val bigCta = Button(this, null, 0, 0).apply {
            text = ctaLabel
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(56)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFF0F5E5C.toInt())
                cornerRadius = 28f * resources.displayMetrics.density
            }
            setOnClickListener { onCtaClick() }
        }
        card.addView(bigCta, wrapWithGap(22))

        if (extraFooter != null) {
            card.addView(extraFooter, wrapWithGap(14))
        }
        return card
    }

    /**
     * Shown only on Android 13+ when the app was sideloaded (not installed from Google Play).
     */
    private fun shouldShowSideloadHelp(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val installer = runCatching {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        }.getOrNull()
        return installer != "com.android.vending"
    }

    /**
     * Collapsible "Button disabled dikh raha hai?" 3-step on-phone guide for sideloaded installs.
     * Contains zero computer/ADB references.
     */
    private fun buildSideloadRestrictedHelp(): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val guideBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFFFFF8EF.toInt())
                cornerRadius = 16f * resources.displayMetrics.density
                setStroke(dp(1), 0xFFEADFD0.toInt())
            }
        }

        val step1Text = TextView(this).apply {
            text = "1. Neeche \"App Settings kholein\" button dabaiye."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF1F1A14.toInt())
        }
        val openAppInfoBtn = primaryButton("App Settings kholein", 0xFFE8730C.toInt()) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        val step2Text = TextView(this).apply {
            text = "2. Top-right ke 3 dots (⋮) dabaiye → \"Allow restricted settings\" chuniye."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF1F1A14.toInt())
        }
        val step3Text = TextView(this).apply {
            text = "3. Wapas aakar Saathi on kar dijiye."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF1F1A14.toInt())
        }

        guideBox.addView(step1Text)
        guideBox.addView(openAppInfoBtn, wrapWithGap(8))
        guideBox.addView(step2Text, wrapWithGap(10))
        guideBox.addView(step3Text, wrapWithGap(8))

        val helpLink = TextView(this).apply {
            text = "Button disabled dikh raha hai?"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF0F5E5C.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                val show = guideBox.visibility != View.VISIBLE
                guideBox.visibleIf(show)
                text = if (show) "Madad chhupayein ▲" else "Button disabled dikh raha hai?"
            }
        }

        wrapper.addView(helpLink)
        wrapper.addView(guideBox, wrapWithGap(6))
        return wrapper
    }

    private fun buildAdvancedSettings(root: LinearLayout) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            root.addView(
                step("Notifications", "To show reminders.", "Allow", done = {
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2) }
            )
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
            root.addView(
                step("Alarms and reminders", "So reminders fire at the exact time she asked.", "Allow", done = {
                    getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
                }) { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
            )
        }
        root.addView(soundFinderCard())

        root.addView(heading("Stop the phone from killing Saathi"))
        root.addView(
            step("Battery: Unrestricted", "Phone makers stop background apps to save battery, which would stop reminders and the character.", "Turn off optimisation", done = {
                getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
            }) {
                @Suppress("BatteryLife")
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        )
        val autoStart = autoStartIntents()
        if (autoStart.isNotEmpty()) {
            root.addView(oemAutoStartCard(autoStart))
            root.addView(oemRecentsLockCard())
        }

        root.addView(heading("Voice"))
        root.addView(voiceCard())

        root.addView(heading("About her"))
        root.addView(aboutCard())

        root.addView(heading("Testing (optional)"))
        root.addView(testingCard())
    }

    private fun soundFinderCard(): View {
        val card = step(
            "Sound finder (optional)",
            "Lets Saathi name the app playing a sound. Android requires Notification access for this and will warn that it can read notifications. Saathi never reads them, and conversations (SMS and chats) start switched off. Leave them off.",
            "Open Notification access",
            done = { phone.hasMediaAccess },
        ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } as LinearLayout
        val result = body("")
        card.addView(
            primaryButton("Test: what is playing now?", 0xFF5C6B73.toInt()) {
                result.text = when (val sound = phone.whichAppIsPlaying()) {
                    is PhoneHelp.Sound.FromApp -> "✅ Sound is coming from ${sound.label}."
                    PhoneHelp.Sound.UnknownApp -> "Sound is playing, but Android did not say which app."
                    PhoneHelp.Sound.Silent -> "No sound found. Play a video first, then come back and test."
                }
            },
            wrapWithGap(8),
        )
        card.addView(result, wrapWithGap(6))
        return card
    }

    private fun step(title: String, why: String, action: String, done: (() -> Boolean)?, onAction: () -> Unit): View {
        val card = card()
        val name = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF1F1A14.toInt())
        }
        val button = primaryButton(action, onClick = onAction)
        card.addView(name)
        card.addView(body(why), wrapWithGap(2))
        card.addView(button, wrapWithGap(8))
        val refresh = {
            val finished = done?.invoke()
            name.text = when (finished) {
                true -> "✅  $title"
                false -> "⬜  $title"
                null -> title
            }
            button.visibleIf(finished != true)
        }
        refresh()
        refreshers += refresh
        card.layoutParams = wrapWithGap(8)
        return card
    }

    private fun oemAutoStartCard(intents: List<Intent>): View {
        val card = step(
            "Auto-start (${Build.MANUFACTURER})",
            "This phone may stop Saathi in the background. Allow auto-start or background activity for Saathi. If App info opens instead, look under Battery there.",
            "Open auto-start",
            done = { prefs.oemAutoStartDone },
        ) { openAutoStart(intents) } as LinearLayout
        card.addView(
            CheckBox(this).apply {
                text = "I have enabled Auto-start / Unrestricted background usage"
                isChecked = prefs.oemAutoStartDone
                setOnCheckedChangeListener { _, checked ->
                    prefs.oemAutoStartDone = checked
                    refreshers.forEach { it() }
                }
            },
            wrapWithGap(6),
        )
        return card
    }

    private fun oemRecentsLockCard(): View {
        val card = card()
        val name = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF1F1A14.toInt())
        }
        card.addView(name)
        card.addView(
            body("1. Open Recent Apps.\n2. Tap the menu or hold the Saathi card at the top.\n3. Tap Lock / Pin so Clear All does not close Saathi."),
            wrapWithGap(2),
        )
        card.addView(
            CheckBox(this).apply {
                text = "I have locked Saathi in Recent Apps"
                isChecked = prefs.oemRecentsLockDone
                setOnCheckedChangeListener { _, checked ->
                    prefs.oemRecentsLockDone = checked
                    refreshers.forEach { it() }
                }
            },
            wrapWithGap(6),
        )
        val refresh = {
            name.text = (if (prefs.oemRecentsLockDone) "✅  " else "⬜  ") + "Lock Saathi in Recent Apps"
        }
        refresh()
        refreshers += refresh
        card.layoutParams = wrapWithGap(8)
        return card
    }

    private fun voiceCard(): View {
        val card = card()
        voiceStatus = body("")
        voiceSpinner = Spinner(this)
        card.addView(voiceStatus)
        card.addView(body("Pick a gentle feminine voice:"), wrapWithGap(8))
        card.addView(voiceSpinner)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            primaryButton("Test voice") { voice.speak(Lines.DONE.speech + " " + Lines.WAITING.speech) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) },
        )
        row.addView(
            primaryButton("Install Hindi voice", 0xFF5C6B73.toInt()) {
                runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        card.addView(row, wrapWithGap(10))

        card.addView(heading("Hearing her"))
        packStatus = body("")
        card.addView(packStatus)
        packButton = primaryButton("Download Hindi speech pack (about 66 MB)") {
            voice.downloadHindiPack { started ->
                if (!started) {
                    Toast.makeText(this, "This phone could not start the download.", Toast.LENGTH_LONG).show()
                }
                packChecksLeft = PACK_CHECKS
                refreshPack()
            }
        }
        card.addView(packButton, wrapWithGap(8))
        val followUp = CheckBox(this).apply {
            text = "Listen for a follow-up after each answer, so she need not tap again. The mic closes if she stays silent."
            isChecked = prefs.followUpListening
            setOnCheckedChangeListener { _, checked -> prefs.followUpListening = checked }
        }
        card.addView(followUp, wrapWithGap(6))
        val online = CheckBox(this).apply {
            text = "Allow online speech if the on-device pack is missing (her voice would go to Google's speech service)"
            isChecked = prefs.allowOnlineSpeech
            setOnCheckedChangeListener { _, checked -> prefs.allowOnlineSpeech = checked }
        }
        card.addView(online, wrapWithGap(6))

        refreshers += {
            refreshPack()
            voiceStatus.postDelayed(::refreshVoices, 700)
        }
        return card
    }

    private fun refreshPack() {
        voice.checkHindiPack { state ->
            if (isDestroyed) return@checkHindiPack
            packStatus.text = when (state) {
                VoiceIO.HindiPack.INSTALLED -> "✅ Hindi pack is ready. Her voice stays on this phone."
                VoiceIO.HindiPack.DOWNLOADING -> "⏳ Downloading the Hindi pack. Stay on Wi-Fi. This updates by itself."
                VoiceIO.HindiPack.NOT_INSTALLED -> "⬜ Hindi pack is not installed. Download it now so she never sees a prompt."
                VoiceIO.HindiPack.NOT_SUPPORTED -> "⚠️ This phone offers no on-device Hindi. She can type, or allow online speech below."
                VoiceIO.HindiPack.UNKNOWN -> "This Android version cannot report the pack. Try speaking to Saathi to test it."
            }
            packButton.visibleIf(state == VoiceIO.HindiPack.NOT_INSTALLED || state == VoiceIO.HindiPack.UNKNOWN)
            packStatus.removeCallbacks(packPoll)
            if (state == VoiceIO.HindiPack.INSTALLED) {
                packChecksLeft = 0
            } else if ((state == VoiceIO.HindiPack.DOWNLOADING || packChecksLeft > 0) && packChecksLeft-- > -PACK_CHECKS) {
                packStatus.postDelayed(packPoll, PACK_POLL_MS)
            }
        }
    }

    private fun refreshVoices() {
        if (isDestroyed) return
        voiceStatus.text = if (voice.isHindiReady) "✅ Hindi voice is ready." else "⬜ No Hindi voice found. Tap Install Hindi voice."
        val voices = voice.hindiVoices()
        val names = voices.map { it.name }
        if (names == voiceNames) return
        voiceNames = names
        val labels = voices.mapIndexed { index, item ->
            "Voice ${index + 1}" + if (item.isNetworkConnectionRequired) " (needs internet)" else " (on phone)"
        }
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val saved = names.indexOf(prefs.voiceName)
        if (saved >= 0) voiceSpinner.setSelection(saved)
        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                names.getOrNull(position)?.let(voice::setVoice)
                SaathiAccessibilityService.instance?.assistant?.reloadVoice()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun aboutCard(): View {
        val card = card()
        val her = EditText(this).apply {
            hint = "Her name, for example Mummy"
            setText(prefs.herName)
            setSingleLine(true)
        }
        val son = EditText(this).apply {
            hint = "Son's name, for example Rohith"
            setText(prefs.sonName)
            setSingleLine(true)
        }
        card.addView(her)
        card.addView(son, wrapWithGap(6))
        card.addView(
            primaryButton("Save") {
                prefs.herName = her.text.toString()
                prefs.sonName = son.text.toString()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            },
            wrapWithGap(10),
        )
        return card
    }

    private fun testingCard(): View {
        val card = card()
        val log = EvalLog(this, prefs)
        card.addView(
            body(
                "Save each turn on this phone to build test cases: her words with numbers masked, Saathi's state and reply, and timing. " +
                    "No screen text is saved. Turn this off before handing the phone over."
            )
        )
        card.addView(
            CheckBox(this).apply {
                text = "Save turns for testing"
                isChecked = prefs.evalLog
                setOnCheckedChangeListener { _, checked -> prefs.evalLog = checked }
            },
            wrapWithGap(6),
        )
        card.addView(
            primaryButton("Delete saved turns", 0xFF5C6B73.toInt()) {
                log.clear()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            },
            wrapWithGap(8),
        )
        return card
    }

    private fun isServiceOn(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val mine = ComponentName(this, SaathiAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return manager.isEnabled && enabled.split(':').any { it.equals(mine, ignoreCase = true) }
    }

    private fun openAutoStart(intents: List<Intent>) {
        for (intent in intents) {
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        Toast.makeText(this, "Look under Battery for background or auto-start settings.", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private companion object {
        const val PACK_CHECKS = 40
        const val PACK_POLL_MS = 3_000L
    }

    private fun autoStartIntents(): List<Intent> {
        val components = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
            "oppo", "realme" -> listOf("com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            "vivo", "iqoo" -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            )
            "oneplus" -> listOf("com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            else -> emptyList()
        }
        return components.map { (pkg, activity) -> Intent().setComponent(ComponentName(pkg, activity)) }
    }
}
