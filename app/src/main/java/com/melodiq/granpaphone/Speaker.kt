package com.melodiq.granpaphone

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Озвучка (встроенный синтез речи Android) плюс звуковые и вибро-сигналы. */
class Speaker(private val ctx: Context) : TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(ctx, this)
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val queued = mutableListOf<Triple<String, Boolean, (() -> Unit)?>>()
    private var counter = 0

    @Volatile var ready = false
        private set
    @Volatile var failed = false
        private set
    @Volatile var languageOk = false
        private set

    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_ALARM, 90)
    } catch (e: Exception) {
        null
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            flushQueued()
            return
        }
        val r = tts.setLanguage(Locale("ru", "RU"))
        languageOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setSpeechRate(0.9f)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                val cb = utteranceId?.let { callbacks.remove(it) } ?: return
                main.post { cb() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onDone(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onDone(utteranceId)
            }
        })
        ready = true
        flushQueued()
    }

    private fun flushQueued() {
        val copy = synchronized(queued) { queued.toList().also { queued.clear() } }
        copy.forEach { say(it.first, it.second, it.third) }
    }

    /**
     * Сказать фразу. flush=true прерывает то, что говорилось до этого.
     * onDone вызывается в главном потоке, когда фраза договорена.
     */
    fun say(text: String, flush: Boolean = true, onDone: (() -> Unit)? = null) {
        if (failed) {
            // Синтез речи не работает: хотя бы не застрять в диалоге.
            if (onDone != null) main.postDelayed({ onDone() }, 300)
            return
        }
        if (!ready) {
            synchronized(queued) { queued.add(Triple(text, flush, onDone)) }
            return
        }
        if (flush) callbacks.clear()
        val id = "u${counter++}"
        if (onDone != null) callbacks[id] = onDone
        tts.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), id)
    }

    fun stop() {
        callbacks.clear()
        synchronized(queued) { queued.clear() }
        if (ready) tts.stop()
    }

    val isSpeaking: Boolean get() = ready && tts.isSpeaking

    /** Сигнал «говорите»: короткий писк и вибрация. */
    fun beepListen() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        vibrate(60)
    }

    /** Сигнал «услышал»: другой звук, чтобы было понятно, что команда принята. */
    fun beepHeard() {
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        vibrate(120)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(ms)
        }
    }
}
