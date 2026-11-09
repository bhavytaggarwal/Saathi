package com.saathi.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.UUID

/** Hindi speech in and out. The microphone is open only between listen() and its result. */
class VoiceIO(context: Context, private val prefs: Prefs) {
    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onHeard(text: String)
        fun onNothingHeard()
        fun onRecognizerUnavailable()
        fun onHindiPackMissing()
    }

    enum class HindiPack { INSTALLED, DOWNLOADING, NOT_INSTALLED, NOT_SUPPORTED, UNKNOWN }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val hindi = Locale.forLanguageTag("hi-IN")
    // Media volume, not the separate assistant volume, which can sit near zero and which the
    // volume buttons she knows do not normally change.
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .build()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsStarting = true

    // A line asked for while the engine was still starting, such as a reminder due at a restart.
    private var queued: Pair<String, (() -> Unit)?>? = null
    private val callbacks = HashMap<String, () -> Unit>()
    private var recognizer: SpeechRecognizer? = null

    // The mic must always close. Recognizers can ignore the silence settings, so these enforce it.
    private var lastPartial = ""
    private var activeListener: Listener? = null
    private var retried = false
    private val noSpeech = Runnable {
        val listener = activeListener
        stopListening()
        listener?.onNothingHeard()
    }
    private val endOfSpeech = Runnable { finishWithWhatWasHeard() }
    private val retry = Runnable { activeListener?.let(::openBestRecognizer) }

    var onSpeakingChanged: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            main.post {
                ttsReady = status == TextToSpeech.SUCCESS
                ttsStarting = false
                if (ttsReady) configureTts()
                queued?.let { (speech, onDone) ->
                    queued = null
                    speak(speech, onDone)
                }
            }
        }
    }

    private fun configureTts() {
        val engine = tts ?: return
        engine.language = hindi
        engine.setAudioAttributes(speechAttributes)
        pickVoice()?.let { engine.voice = it }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                main.post { onSpeakingChanged?.invoke(true) }
            }

            override fun onDone(utteranceId: String) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finished(utteranceId)

            override fun onError(utteranceId: String, errorCode: Int) = finished(utteranceId)

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                main.post {
                    callbacks.remove(utteranceId)
                    onSpeakingChanged?.invoke(false)
                    audio.abandonAudioFocusRequest(focusRequest)
                }
            }
        })
    }

    private fun finished(id: String) {
        main.post {
            onSpeakingChanged?.invoke(false)
            audio.abandonAudioFocusRequest(focusRequest)
            callbacks.remove(id)?.invoke()
        }
    }

    /** Hindi voices installed on this phone, downloaded voices first. */
    fun hindiVoices(): List<Voice> {
        val voices = runCatching { tts?.voices }.getOrNull().orEmpty()
        return voices
            .filter { it.locale.language == "hi" && !it.features.contains("notInstalled") }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
    }

    val isHindiReady: Boolean
        get() {
            val engine = tts ?: return false
            return ttsReady && engine.isLanguageAvailable(hindi) >= TextToSpeech.LANG_AVAILABLE
        }

    private fun pickVoice(): Voice? {
        val voices = hindiVoices()
        prefs.voiceName?.let { saved -> voices.firstOrNull { it.name == saved }?.let { return it } }
        // Prefer a voice that declares itself female, then Google's offline "hia" voice, then any.
        return voices.firstOrNull { "female" in it.name.lowercase() || it.features.contains("female") }
            ?: voices.firstOrNull { "hia" in it.name && !it.isNetworkConnectionRequired }
            ?: voices.firstOrNull()
    }

    fun setVoice(name: String) {
        prefs.voiceName = name
        reloadVoice()
    }

    /** Picks up a voice chosen in setup, which runs a separate VoiceIO. */
    fun reloadVoice() {
        if (ttsReady) pickVoice()?.let { tts?.voice = it }
    }

    /** Speaks one line, replacing whatever was being said. */
    fun speak(speech: String, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (engine != null && !ttsReady && ttsStarting) {
            // Spoken as soon as the engine is ready. A newer line replaces it, as QUEUE_FLUSH would.
            queued = speech to onDone
            return
        }
        if (engine == null || !ttsReady) {
            onDone?.let { main.post(it) }
            return
        }
        val id = UUID.randomUUID().toString()
        onDone?.let { callbacks[id] = it }
        engine.setSpeechRate(prefs.speechRate)
        audio.requestAudioFocus(focusRequest)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        if (engine.speak(speech, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) {
            finished(id)
        }
    }

    fun stopSpeaking() {
        queued = null
        callbacks.clear()
        tts?.stop()
        onSpeakingChanged?.invoke(false)
        audio.abandonAudioFocusRequest(focusRequest)
    }

    val hasMicPermission: Boolean
        get() = appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    val isOnDeviceAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    val isListening: Boolean
        get() = recognizer != null

    /** Opens the mic once. Her voice stays on the phone unless online speech was enabled in setup. */
    fun listen(listener: Listener) {
        stopListening()
        stopSpeaking()
        retried = false
        openBestRecognizer(listener)
    }

    private fun finishWithWhatWasHeard() {
        val listener = activeListener
        val heard = lastPartial.trim()
        stopListening()
        if (heard.isEmpty()) listener?.onNothingHeard() else listener?.onHeard(heard)
    }

    private fun openBestRecognizer(listener: Listener) {
        if (isOnDeviceAvailable) {
            start(createOnDevice(), onDevice = true, listener)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || prefs.allowOnlineSpeech) {
            // Before Android 12 there is no on-device API, so the intent can only prefer offline.
            if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                start(SpeechRecognizer.createSpeechRecognizer(appContext), onDevice = false, listener)
            } else {
                listener.onRecognizerUnavailable()
            }
        } else {
            listener.onRecognizerUnavailable()
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.S)
    private fun createOnDevice() = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !prefs.allowOnlineSpeech)
        // Older speakers pause more while thinking, so she gets longer before the mic closes.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
    }

    private fun start(speech: SpeechRecognizer, onDevice: Boolean, listener: Listener) {
        recognizer = speech
        activeListener = listener
        lastPartial = ""
        main.postDelayed(noSpeech, NO_SPEECH_MS)
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty().ifEmpty { lastPartial.trim() }
                stopListening()
                if (heard.isEmpty()) listener.onNothingHeard() else listener.onHeard(heard)
            }

            override fun onPartialResults(partial: Bundle) {
                val text = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
                main.removeCallbacks(noSpeech)
                if (text != lastPartial) {
                    lastPartial = text
                    listener.onPartial(text)
                    // A pause this long after her last new word means she has finished.
                    main.removeCallbacks(endOfSpeech)
                    main.postDelayed(endOfSpeech, END_OF_SPEECH_MS)
                }
            }

            override fun onError(error: Int) {
                Log.w(TAG, "recognizer error $error onDevice=$onDevice")
                val heardSoFar = lastPartial.trim()
                stopListening()
                activeListener = listener
                if (heardSoFar.isNotEmpty()) {
                    activeListener = null
                    listener.onHeard(heardSoFar)
                    return
                }
                when (error) {
                    // The speech service dropped out. She should not have to tap again for that.
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> {
                        if (retried) {
                            activeListener = null
                            listener.onRecognizerUnavailable()
                        } else {
                            retried = true
                            main.postDelayed(retry, RETRY_MS)
                        }
                    }
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        listener.onNothingHeard()
                    ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                        // The download belongs in setup. A size prompt in the middle of her
                        // question would only confuse her.
                        if (onDevice && prefs.allowOnlineSpeech && SpeechRecognizer.isRecognitionAvailable(appContext)) {
                            start(SpeechRecognizer.createSpeechRecognizer(appContext), onDevice = false, listener)
                        } else {
                            listener.onHindiPackMissing()
                        }
                    }
                    else -> listener.onRecognizerUnavailable()
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = listener.onReady()
            override fun onBeginningOfSpeech() {
                main.removeCallbacks(noSpeech)
                // If no words ever arrive, the mic still closes.
                main.postDelayed(noSpeech, MAX_SPEECH_MS)
            }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        speech.startListening(recognizerIntent())
    }

    /** Asks Android, without opening the mic, whether the on-device Hindi pack is ready. */
    fun checkHindiPack(onResult: (HindiPack) -> Unit) {
        if (!isOnDeviceAvailable) {
            onResult(HindiPack.NOT_SUPPORTED)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(HindiPack.UNKNOWN)
            return
        }
        val checker = createOnDevice()
        val finish = { state: HindiPack ->
            main.post {
                checker.destroy()
                Log.i(TAG, "hindi pack: $state")
                onResult(state)
            }
            Unit
        }
        checker.checkRecognitionSupport(
            recognizerIntent(),
            appContext.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    fun List<String>.hasHindi() = any { it.lowercase().startsWith("hi") }
                    finish(
                        when {
                            support.installedOnDeviceLanguages.hasHindi() -> HindiPack.INSTALLED
                            support.pendingOnDeviceLanguages.hasHindi() -> HindiPack.DOWNLOADING
                            support.supportedOnDeviceLanguages.hasHindi() -> HindiPack.NOT_INSTALLED
                            else -> HindiPack.NOT_SUPPORTED
                        }
                    )
                }

                override fun onError(error: Int) {
                    finish(HindiPack.UNKNOWN)
                }
            },
        )
    }

    /** Asks the phone to fetch the on-device Hindi pack. Only setup calls this. */
    fun downloadHindiPack(onFinished: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isOnDeviceAvailable) {
            onFinished?.invoke(false)
            return
        }
        val downloader = createOnDevice()
        val finish = { ok: Boolean ->
            main.post {
                downloader.destroy()
                onFinished?.invoke(ok)
            }
            Unit
        }
        if (Build.VERSION.SDK_INT >= 34) {
            downloader.triggerModelDownload(
                recognizerIntent(),
                appContext.mainExecutor,
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) = Unit
                    override fun onSuccess() {
                        finish(true)
                    }
                    override fun onScheduled() {
                        finish(true)
                    }
                    override fun onError(error: Int) {
                        finish(false)
                    }
                },
            )
        } else {
            downloader.triggerModelDownload(recognizerIntent())
            finish(true)
        }
    }

    fun stopListening() {
        main.removeCallbacks(noSpeech)
        main.removeCallbacks(endOfSpeech)
        main.removeCallbacks(retry)
        activeListener = null
        lastPartial = ""
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    fun shutdown() {
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val TAG = "SaathiVoice"
        const val NO_SPEECH_MS = 9_000L
        const val END_OF_SPEECH_MS = 2_500L
        const val MAX_SPEECH_MS = 25_000L
        const val RETRY_MS = 400L
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
