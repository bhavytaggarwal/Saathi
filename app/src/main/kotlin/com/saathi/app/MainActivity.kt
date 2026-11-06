package com.saathi.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.saathi.app.core.Lines

/** One-time setup, done by her son. Each step shows whether it is finished. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var voice: VoiceIO
    private lateinit var phone: PhoneHelp
    private val refreshers = ArrayList<() -> Unit>()

    private lateinit var voiceSpinner: Spinner
    private lateinit var geminiStatus: TextView
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
        refreshers.forEach { it() }
    }

    override fun onDestroy() {
        packStatus.removeCallbacks(packPoll)
        voice.shutdown()
        super.onDestroy()
    }

    private fun build() {
        val (scroll, root) = page()
        setContentView(scroll)

        root.addView(title("Saathi setup"))
        root.addView(body("Finish these steps once, then hand the phone to her. Saathi only reads and points. It never taps, types or swipes."))

        root.addView(heading("1. Allow Saathi on this phone"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            root.addView(
                step(
                    "Allow restricted settings",
                    "Directly installed apps need this before accessibility can be turned on. Open App info, tap ⋮ at the top right, then Allow restricted settings. If you see no such option, try the next step first: the option appears after Android blocks it once.",
                    "Open App info",
                    done = null,
                ) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            )
        }
        root.addView(
            step(
                "Turn on the Saathi accessibility service",
                "This lets the character appear and read the allowed apps. Find Saathi under Downloaded apps.",
                "Open Accessibility",
                done = ::isServiceOn,
            ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )
        root.addView(
            step("Microphone", "To hear her questions. The mic opens only after she taps the character.", "Allow", done = {
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            }) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        )
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

        root.addView(heading("2. Stop the phone from killing Saathi"))
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
            root.addView(
                step(
                    "Auto-start (${Build.MANUFACTURER})",
                    "This phone may stop Saathi in the background. Allow auto-start or background activity for Saathi. If App info opens instead, look under Battery there. If the phone has no such setting, the Battery step above is enough.",
                    "Open auto-start",
                    done = null,
                ) { openAutoStart(autoStart) }
            )
        }

        root.addView(heading("3. Gemini"))
        root.addView(geminiCard())

        root.addView(heading("4. Voice"))
        root.addView(voiceCard())

        root.addView(heading("5. About her"))
        root.addView(aboutCard())

        root.addView(heading("Lists and reminders"))
        root.addView(
            primaryButton("Open lists and reminders") { startActivity(Intent(this, ListsActivity::class.java)) },
            wrapWithGap(4),
        )
    }

    private fun soundFinderCard(): View {
        val card = step(
            "Sound finder (optional)",
            "Lets Saathi name the app playing a sound. Android requires Notification access for this and will warn that it can read notifications. Saathi never reads them, and conversations (SMS and chats) start switched off. Leave them off. Turning them on in Android's settings is at your own risk and adds nothing to Saathi.",
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

    private fun geminiCard(): View {
        val card = card()
        card.addView(body("Paste a paid Gemini API key with a spending limit. It is stored encrypted on this phone. Saathi asks Google not to store each request. Also turn off Generate Content logging in AI Studio."))
        val key = EditText(this).apply {
            hint = if (prefs.apiKey.isBlank()) "Gemini API key" else "Key saved. Paste to replace."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val model = EditText(this).apply {
            setText(prefs.model)
            hint = Prefs.DEFAULT_MODEL
            setSingleLine(true)
        }
        geminiStatus = body("")
        card.addView(key, wrapWithGap(8))
        card.addView(body("Model"), wrapWithGap(8))
        card.addView(model)
        card.addView(
            primaryButton("Save and test") {
                val typed = key.text.toString().trim()
                if (typed.isNotEmpty()) prefs.apiKey = typed
                prefs.model = model.text.toString().ifBlank { Prefs.DEFAULT_MODEL }
                key.text.clear()
                key.hint = "Key saved. Paste to replace."
                testGemini()
            },
            wrapWithGap(10),
        )
        card.addView(geminiStatus, wrapWithGap(8))
        return card
    }

    private fun testGemini() {
        geminiStatus.text = "Testing..."
        GeminiClient(prefs).ask(
            "Current time: test\nScreen access: none\nShe said: \"Namaste, aap kaun ho?\"",
            emptyList(),
            null,
        ) { result ->
            geminiStatus.text = when (result) {
                is GeminiClient.Result.Ok -> "✅ Working. Saathi says: ${result.reply.line.text}"
                is GeminiClient.Result.Failed -> when (result.failure) {
                    GeminiClient.Failure.NO_KEY -> "Paste a key first."
                    GeminiClient.Failure.BAD_KEY -> "❌ Google refused this key or model. Check both."
                    GeminiClient.Failure.NETWORK -> "❌ No internet."
                    GeminiClient.Failure.QUOTA -> "❌ Quota or spending limit reached."
                    else -> "❌ Gemini gave an unexpected reply: ${result.failure}. Try again."
                }
            }
        }
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
            // The voice engine starts up slowly, so its state is read a moment later.
            voiceStatus.postDelayed(::refreshVoices, 700)
        }
        return card
    }

    /** Shows the real state of the Hindi pack, and keeps checking while a download runs. */
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

    private fun isServiceOn(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val mine = ComponentName(this, SaathiAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return manager.isEnabled && enabled.split(':').any { it.equals(mine, ignoreCase = true) }
    }

    /** The screen differs by phone and OS version, so each known one is tried, then App info. */
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
