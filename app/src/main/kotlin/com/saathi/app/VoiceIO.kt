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
import android.os.SystemClock
import android.util.Log
import com.saathi.app.core.Lang
import com.saathi.app.core.Language
import com.saathi.app.core.ListenPolicy
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

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
    // The language in use: Hindi unless another was chosen in setup.
    private val language: Language get() = Lang.current
    private val hindi: Locale get() = language.locale
    private var ttsLanguage: Language? = null
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
    private var queued: Triple<String, Float, (() -> Unit)?>? = null
    private val callbacks = HashMap<String, () -> Unit>()

    // The done callback waits a moment after speech ends. stopSpeaking() cancels it, so a line she
    // interrupted can never reopen the mic later over her own words.
    private val pendingDone = HashMap<String, Runnable>()

    // Some engines never report the end of a line. This ends it anyway, so the mic still reopens.
    private val stuckGuards = HashMap<String, Runnable>()

    /** True from speak() until the line ends, is stopped or fails. */
    var isSpeaking = false
        private set

    /** When speech last ended, from SystemClock.uptimeMillis(). */
    var speechEndedAt = 0L
        private set
    private var recognizer: SpeechRecognizer? = null

    // The mic must always close. Recognizers can ignore the silence settings, so these enforce it.
    private val accumulatedSegments = ArrayList<String>()
    private var currentSegment = ""
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
        activeInstances += this
        tts = TextToSpeech(appContext) { status ->
            main.post {
                ttsReady = status == TextToSpeech.SUCCESS
                ttsStarting = false
                if (ttsReady) configureTts()
                queued?.let { (speech, rate, onDone) ->
                    queued = null
                    isSpeaking = false
                    speak(speech, rate, onDone)
                }
            }
        }
    }

    private fun configureTts() {
        val engine = tts ?: return
        ttsLanguage = language
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
                    stuckGuards.remove(utteranceId)?.let(main::removeCallbacks)
                    markSilent()
                    audio.abandonAudioFocusRequest(focusRequest)
                }
            }
        })
    }

    // onDone and onError both end here, so the mic re-arms after a failed line as well.
    private fun finished(id: String) {
        main.post {
            stuckGuards.remove(id)?.let(main::removeCallbacks)
            markSilent()
            audio.abandonAudioFocusRequest(focusRequest)
            val callback = callbacks.remove(id)
            if (callback != null) {
                // Wait after TTS ends and audio focus is released, so the speaker's tail is not
                // heard as her words.
                val run = Runnable {
                    pendingDone.remove(id)
                    callback.invoke()
                }
                pendingDone[id] = run
                main.postDelayed(run, TTS_DONE_DELAY_MS)
            }
        }
    }

    private fun markSilent() {
        if (isSpeaking) speechEndedAt = SystemClock.uptimeMillis()
        isSpeaking = false
        onSpeakingChanged?.invoke(false)
    }

    /** Voices for her language installed on this phone, downloaded voices first. */
    fun hindiVoices(): List<Voice> {
        val voices = runCatching { tts?.voices }.getOrNull().orEmpty()
        return voices
            .filter { it.locale.language == language.code && !it.features.contains("notInstalled") }
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
            ?: voices.firstOrNull { !it.isNetworkConnectionRequired && it.locale.country == "IN" }
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

    /** Speaks one line with QUEUE_FLUSH, replacing whatever was being said. */
    fun speak(speech: String, onDone: (() -> Unit)? = null) {
        speak(speech, prefs.speechRate, onDone)
    }

    /** Speaks one line at a specific speech [rate] (e.g. 1.08f for brisk guidance steps). */
    fun speak(speech: String, rate: Float, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (engine != null && !ttsReady && ttsStarting) {
            // Spoken as soon as the engine is ready. A newer line replaces it, as QUEUE_FLUSH would.
            queued = Triple(speech, rate, onDone)
            isSpeaking = true
            return
        }
        if (engine == null || !ttsReady) {
            onDone?.let { main.post(it) }
            return
        }
        // The language was changed in setup since the engine was set up.
        if (ttsLanguage != language) configureTts()
        val id = UUID.randomUUID().toString()
        // QUEUE_FLUSH replaces the line being said, and its callback goes with it.
        cancelPendingCallbacks()
        onDone?.let { callbacks[id] = it }
        isSpeaking = true
        val guard = Runnable {
            Log.w(TAG, "tts never reported the end of a line; ending it")
            runCatching { engine.stop() }
            finished(id)
        }
        stuckGuards[id] = guard
        main.postDelayed(guard, ListenPolicy.stuckSpeechMs(speech.length, rate))
        engine.setSpeechRate(rate)
        audio.requestAudioFocus(focusRequest)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        if (engine.speak(speech, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) {
            finished(id)
        }
    }

    fun stopSpeaking() {
        queued = null
        cancelPendingCallbacks()
        tts?.stop()
        markSilent()
        audio.abandonAudioFocusRequest(focusRequest)
    }

    private fun cancelPendingCallbacks() {
        callbacks.clear()
        pendingDone.values.forEach(main::removeCallbacks)
        pendingDone.clear()
        stuckGuards.values.forEach(main::removeCallbacks)
        stuckGuards.clear()
    }

    /**
     * Speaks one final line (such as the banking step-out reassurance) and invokes [onFinished]
     * (e.g. AccessibilityService.disableSelf()) only after UtteranceProgressListener.onDone /
     * onError fires or the hard 4500ms fallback timeout elapses, so onDestroy() never cuts off TTS.
     */
    fun speakAndShutdown(speech: String, onFinished: (() -> Unit)? = null) {
        stopListening()
        var completed = false
        val complete = {
            if (!completed) {
                completed = true
                shutdown()
                onFinished?.invoke()
            }
        }
        val fallbackShutdown = Runnable { complete() }
        main.postDelayed(fallbackShutdown, FINAL_SPEECH_TIMEOUT_MS)
        speak(speech) {
            main.removeCallbacks(fallbackShutdown)
            complete()
        }
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
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.tag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !prefs.allowOnlineSpeech)
        // Older speakers pause more while thinking, so natural pauses do not prematurely end recognition.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
    }

    private fun updatePartialBuffer(rawSegment: String): String {
        val clean = rawSegment.trim()
        if (clean.isEmpty()) return lastPartial
        if (currentSegment.isNotEmpty()) {
            val prevLower = currentSegment.lowercase(hindi)
            val nextLower = clean.lowercase(hindi)
            val prevFirstWord = prevLower.substringBefore(' ')
            // If the recognizer reset its active hypothesis after a pause, keep the previous segment.
            val isResetAfterPause = clean.length < currentSegment.length / 2 &&
                !nextLower.startsWith(prevFirstWord) &&
                !prevLower.startsWith(nextLower)
            if (isResetAfterPause) {
                accumulatedSegments += currentSegment
            }
        }
        currentSegment = clean
        lastPartial = (accumulatedSegments + currentSegment).joinToString(" ").trim()
        return lastPartial
    }

    private fun start(speech: SpeechRecognizer, onDevice: Boolean, listener: Listener) {
        recognizer = speech
        activeListener = listener
        accumulatedSegments.clear()
        currentSegment = ""
        lastPartial = ""
        main.postDelayed(noSpeech, NO_SPEECH_MS)
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val finalSegment = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                val buffered = lastPartial.trim()
                val heard = when {
                    finalSegment.isEmpty() -> buffered
                    accumulatedSegments.isNotEmpty() -> (accumulatedSegments + finalSegment).joinToString(" ").trim()
                    buffered.length > finalSegment.length && buffered.lowercase(hindi).startsWith(finalSegment.lowercase(hindi)) -> buffered
                    else -> finalSegment
                }
                stopListening()
                if (heard.isEmpty()) listener.onNothingHeard() else listener.onHeard(heard)
            }

            override fun onPartialResults(partial: Bundle) {
                val stable = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                val unstable = partial.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
                    ?.firstOrNull()?.trim().orEmpty()
                val combined = listOf(stable, unstable).filter { it.isNotEmpty() }.joinToString(" ").trim()
                if (combined.isEmpty()) return
                main.removeCallbacks(noSpeech)
                val previous = lastPartial
                val updated = updatePartialBuffer(combined)
                if (updated != previous) {
                    listener.onPartial(updated)
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
                    fun List<String>.hasHindi() = any { it.lowercase().startsWith(language.code) }
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

    /** What became of a request to fetch the Hindi pack. */
    enum class PackDownload { READY, SCHEDULED, STARTED, FAILED }

    /**
     * Asks the phone to fetch the on-device Hindi pack. Setup calls this from its button, and the
     * assistant calls it by itself when she taps Bolo and the pack is missing. Android decides when
     * it downloads, so on mobile data it may wait for Wi-Fi (SCHEDULED). On Android 13 the phone
     * does not report back, so STARTED is all that is known. Before Android 13 it always fails.
     */
    fun downloadHindiPack(onFinished: ((PackDownload) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isOnDeviceAvailable) {
            onFinished?.invoke(PackDownload.FAILED)
            return
        }
        val downloader = createOnDevice()
        var done = false
        val finish = { result: PackDownload ->
            main.post {
                if (!done) {
                    done = true
                    downloader.destroy()
                    Log.i(TAG, "hindi pack download: $result")
                    onFinished?.invoke(result)
                }
            }
            Unit
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                downloader.triggerModelDownload(
                    recognizerIntent(),
                    appContext.mainExecutor,
                    object : ModelDownloadListener {
                        override fun onProgress(completedPercent: Int) = Unit
                        override fun onSuccess() = finish(PackDownload.READY)
                        override fun onScheduled() = finish(PackDownload.SCHEDULED)
                        override fun onError(error: Int) {
                            Log.w(TAG, "hindi pack download error $error")
                            finish(PackDownload.FAILED)
                        }
                    },
                )
            } else {
                downloader.triggerModelDownload(recognizerIntent())
                finish(PackDownload.STARTED)
            }
        }.onFailure {
            Log.w(TAG, "hindi pack download could not start: ${it.javaClass.simpleName}")
            finish(PackDownload.FAILED)
        }
    }

    fun stopListening() {
        main.removeCallbacks(noSpeech)
        main.removeCallbacks(endOfSpeech)
        main.removeCallbacks(retry)
        activeListener = null
        accumulatedSegments.clear()
        currentSegment = ""
        lastPartial = ""
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    fun shutdown() {
        activeInstances -= this
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val GUIDE_SPEECH_RATE = 1.08f
        private val activeInstances = CopyOnWriteArraySet<VoiceIO>()

        /** Stops any pending or ongoing TTS across all VoiceIO instances (e.g. setup TTS when service connects). */
        fun stopAllSpeaking() {
            for (instance in activeInstances) {
                runCatching { instance.stopSpeaking() }
            }
        }

        private const val TAG = "SaathiVoice"
        const val NO_SPEECH_MS = 9_000L
        const val END_OF_SPEECH_MS = 3_500L
        const val MAX_SPEECH_MS = 25_000L
        const val RETRY_MS = 400L
        const val TTS_DONE_DELAY_MS = ListenPolicy.ECHO_GAP_MS
        const val FINAL_SPEECH_TIMEOUT_MS = 4_500L
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
