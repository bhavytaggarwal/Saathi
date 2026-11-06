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
        root.addView(
            step(
                "Sound finder (optional)",
                "Android only tells apps which app is playing media through Notification access. Saathi's listener exists for that alone and never reads a notification. Without it, she can still close the sound from recent apps.",
                "Open Notification access",
                done = { phone.hasMediaAccess },
            ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        )

        root.addView(heading("2. Stop the phone from killing Saathi"))
        root.addView(
            step("Battery: Unrestricted", "Phone makers stop background apps to save battery, which would stop reminders and the character.", "Turn off optimisation", done = {
                getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
            }) {
                @Suppress("BatteryLife")
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        )
        autoStartIntent()?.let { intent ->
            root.addView(
                step("Auto-start (${Build.MANUFACTURER})", "This phone has its own auto-start list. Turn Saathi on there too.", "Open auto-start", done = null) {
                    runCatching { startActivity(intent) }.onFailure {
                        Toast.makeText(this, "Not found. Look for Autostart in the phone's Settings.", Toast.LENGTH_LONG).show()
                    }
                }
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
        val speechStatus = body("")
        card.addView(speechStatus)
        val download = primaryButton("Download Hindi speech pack", 0xFF5C6B73.toInt()) {
            voice.downloadHindiPack { started ->
                Toast.makeText(
                    this,
                    if (started) "Download requested. It may take a few minutes." else "This phone could not start the download.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        card.addView(download, wrapWithGap(8))
        val online = CheckBox(this).apply {
            text = "Allow online speech if the on-device pack is missing (her voice would go to Google's speech service)"
            isChecked = prefs.allowOnlineSpeech
            setOnCheckedChangeListener { _, checked -> prefs.allowOnlineSpeech = checked }
        }
        card.addView(online, wrapWithGap(6))

        refreshers += {
            speechStatus.text = if (voice.isOnDeviceAvailable) {
                "✅ On-device recognition is available, so her voice stays on the phone. If Hindi fails, download the pack below."
            } else {
                "This phone has no on-device recognition (needs Android 12+ and a speech pack). It can only ask for offline speech."
            }
            download.visibleIf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            // The voice engine starts up slowly, so its state is read a moment later.
            voiceStatus.postDelayed(::refreshVoices, 700)
        }
        return card
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

    private fun autoStartIntent(): Intent? {
        val component = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            "oppo", "realme" -> ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            "vivo", "iqoo" -> ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            "oneplus" -> ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            else -> null
        } ?: return null
        return Intent().setComponent(component)
    }
}
