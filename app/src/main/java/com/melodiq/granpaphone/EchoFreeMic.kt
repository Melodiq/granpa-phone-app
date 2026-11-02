package com.melodiq.granpaphone

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener

/**
 * Микрофон с эхоподавлением для Vosk (вместо стандартного SpeechService).
 * Источник «голосовая связь» + системный AcousticEchoCanceler вычитают из записи
 * то, что сейчас играет динамик телефона, — помощник перестаёт слышать сам себя.
 * Насколько хорошо это работает, зависит от телефона.
 */
class EchoFreeMic(private val rec: Recognizer, private val sampleRate: Int = 16000) {

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    private fun open(source: Int, bufBytes: Int): AudioRecord? = try {
        val ar = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes)
        if (ar.state == AudioRecord.STATE_INITIALIZED) ar else { ar.release(); null }
    } catch (e: Exception) {
        null
    }

    /** false — микрофон не открылся. */
    fun start(listener: RecognitionListener, timeoutMs: Int): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufBytes = maxOf(minBuf, sampleRate / 5 * 2) // ~0,2 секунды
        var echoSource = true
        val ar = open(MediaRecorder.AudioSource.VOICE_COMMUNICATION, bufBytes)
            ?: open(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufBytes).also { echoSource = false }
            ?: return false

        // Эффекты: эхоподавление обязательно, шумодав — по возможности. Усиление выключаем,
        // чтобы распознавание не «раскачивало» тихий фон.
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(ar.audioSessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(ar.audioSessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(ar.audioSessionId)?.apply { enabled = false; release() }
            }
        } catch (_: Exception) {}
        echoActive = echoSource || aec?.enabled == true
        echoDetails = buildString {
            append(if (echoSource) "источник «связь»" else "обычный источник")
            append(", AEC ").append(if (aec?.enabled == true) "вкл" else "нет")
            append(", шумодав ").append(if (ns?.enabled == true) "вкл" else "нет")
        }

        running = true
        val t = Thread {
            val buf = ShortArray(bufBytes / 2)
            val deadline = if (timeoutMs > 0) System.currentTimeMillis() + timeoutMs else Long.MAX_VALUE
            try {
                ar.startRecording()
                while (running) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n < 0) {
                        main.post { listener.onError(Exception("микрофон: $n")) }
                        break
                    }
                    if (n == 0) continue
                    if (rec.acceptWaveForm(buf, n)) {
                        val r = rec.result
                        main.post { listener.onResult(r) }
                    } else {
                        val p = rec.partialResult
                        main.post { listener.onPartialResult(p) }
                    }
                    if (System.currentTimeMillis() >= deadline) {
                        val f = rec.finalResult
                        main.post { listener.onFinalResult(f); listener.onTimeout() }
                        break
                    }
                }
            } catch (e: Exception) {
                main.post { listener.onError(e) }
            } finally {
                try { ar.stop() } catch (_: Exception) {}
                try { aec?.release() } catch (_: Exception) {}
                try { ns?.release() } catch (_: Exception) {}
                try { ar.release() } catch (_: Exception) {}
            }
        }
        thread = t
        t.start()
        return true
    }

    /** Остановить и дождаться потока: распознаватель закрывают сразу после этого. */
    fun stop() {
        running = false
        val t = thread
        thread = null
        if (t != null && t !== Thread.currentThread()) {
            try { t.join(800) } catch (_: Exception) {}
        }
    }

    companion object {
        /** Работает ли эхоподавление на этом телефоне (для экрана настройки). */
        @Volatile var echoActive: Boolean? = null
            private set
        @Volatile var echoDetails: String = ""
            private set
    }
}
