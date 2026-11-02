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
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Озвучка (встроенный синтез речи Android) плюс звуковые и вибро-сигналы. */
class Speaker(private val ctx: Context) : TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("voice", Context.MODE_PRIVATE)
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
        val r = tts.setLanguage(Phrases.of(ctx).ttsLocale)
        languageOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        // Спокойная манера: чуть медленнее и чуть ниже обычного.
        tts.setSpeechRate(0.85f)
        tts.setPitch(0.9f)
        applyVoice()
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                talking = true
            }

            override fun onDone(utteranceId: String?) {
                talking = false
                lastSpokeAt = System.currentTimeMillis()
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

    @Volatile private var talking = false
    @Volatile var lastSpokeAt = 0L
        private set

    /** Помощник сам говорит или только что договорил — микрофон слышит его же голос. */
    val selfAudible: Boolean
        get() = talking || isSpeaking || System.currentTimeMillis() - lastSpokeAt < 1200

    // ---------- выбор голоса ----------

    /** Русские голоса, которые работают без интернета и уже скачаны. */
    /** Сменить язык речи (после смены языка интерфейса). */
    fun setLanguage(locale: Locale) {
        if (!ready) return
        try {
            val r = tts.setLanguage(locale)
            languageOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            applyVoice()
        } catch (_: Exception) {}
    }

    private fun lang() = Phrases.of(ctx).ttsLocale.language

    /** Голоса текущего языка, которые работают без интернета и уже скачаны. */
    fun russianVoices(): List<Voice> = languageVoices()

    fun languageVoices(): List<Voice> {
        if (!ready) return emptyList()
        return try {
            tts.voices.orEmpty()
                .filter { v ->
                    v.locale.language == lang() &&
                        !v.isNetworkConnectionRequired &&
                        !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                }
                .sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val currentVoiceName: String?
        get() = if (ready) try { tts.voice?.name } catch (e: Exception) { null } else null

    fun selectVoice(name: String) {
        val v = russianVoices().firstOrNull { it.name == name } ?: return
        try { tts.voice = v } catch (_: Exception) {}
        prefs.edit().putString("name_" + lang(), name).apply()
    }

    private fun applyVoice() {
        val list = russianVoices()
        if (list.isEmpty()) return
        val saved = prefs.getString("name_" + lang(), null) ?: if (lang() == "ru") prefs.getString("name", null) else null
        // По умолчанию для русского — «Голос 6» из списка в настройке (выбран как самый спокойный).
        val default6 = if (lang() == "ru") list.getOrNull(5) else null
        val v = list.firstOrNull { it.name == saved } ?: default6 ?: guessMale(list) ?: return
        try { tts.voice = v } catch (_: Exception) {}
    }

    /** Имена голосов не говорят о поле явно; угадываем по известным мужским голосам Google. */
    private fun guessMale(list: List<Voice>): Voice? {
        fun n(v: Voice) = v.name.lowercase(Locale.ROOT)
        return list.firstOrNull { n(it).contains("male") && !n(it).contains("female") }
            ?: list.firstOrNull { n(it).contains("rud") }
            ?: list.firstOrNull { n(it).contains("ruf") }
    }

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
