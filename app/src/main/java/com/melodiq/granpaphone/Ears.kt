package com.melodiq.granpaphone

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File

/** Офлайн-распознавание речи через Vosk. */
class Ears(private val ctx: Context) {

    private val main = Handler(Looper.getMainLooper())

    @Volatile var model: Model? = null
        private set
    @Volatile var error: String? = null
        private set
    /** Словарь модели. Слова не из словаря распознать нельзя. null — словарь не найден. */
    @Volatile var vocab: Set<String>? = null
        private set

    private val waiters = mutableListOf<() -> Unit>()
    private var service: SpeechService? = null
    private var recognizer: Recognizer? = null

    fun load() {
        StorageService.unpack(ctx, "model-ru", "model",
            { m ->
                Thread {
                    val v = loadVocab()
                    main.post {
                        vocab = v
                        model = m
                        val w = synchronized(waiters) { waiters.toList().also { waiters.clear() } }
                        w.forEach { it() }
                    }
                }.start()
            },
            { e -> error = e.message ?: e.toString() })
    }

    private fun loadVocab(): Set<String>? {
        return try {
            val root = File(ctx.getExternalFilesDir(null), "model")
            val f = root.walkTopDown().firstOrNull { it.isFile && it.name == "words.txt" } ?: return null
            val set = HashSet<String>(200_000)
            f.forEachLine { line ->
                val w = line.substringBefore(' ').trim()
                if (w.isNotEmpty()) set.add(w)
            }
            set
        } catch (e: Exception) {
            null
        }
    }

    fun whenReady(cb: () -> Unit) {
        if (model != null) {
            cb()
            return
        }
        synchronized(waiters) { waiters.add(cb) }
    }

    fun knows(word: String): Boolean = spell(word) != null

    /**
     * Написание слова так, как его знает модель: пробуем как есть, затем с «ё» вместо «е».
     * null — модель это слово не знает.
     */
    fun spell(word: String): String? {
        val v = vocab ?: return word
        if (word in v) return word
        for (i in word.indices) {
            if (word[i] == 'е') {
                val w2 = word.substring(0, i) + 'ё' + word.substring(i + 1)
                if (w2 in v) return w2
            }
        }
        return null
    }

    /** Фраза в написании модели или null, если хоть одно слово незнакомо. */
    fun fixPhrase(phrase: String): String? {
        val parts = phrase.trim().split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val out = ArrayList<String>(parts.size)
        for (p in parts) out.add(spell(p) ?: return null)
        return out.joinToString(" ")
    }

    /**
     * Слушать одну фразу.
     * grammar — список допустимых фраз (точнее) или null — свободная речь.
     * cb(null) — ничего не сказали или ошибка.
     */
    fun listen(grammar: List<String>?, timeoutMs: Int, cb: (String?) -> Unit) {
        val m = model
        if (m == null) {
            cb(null)
            return
        }
        stop()
        var done = false
        val finish = { text: String? ->
            if (!done) {
                done = true
                main.post {
                    stop()
                    cb(text)
                }
            }
        }
        try {
            val rec = if (grammar != null) {
                Recognizer(m, 16000f, JSONArray(grammar + "[unk]").toString())
            } else {
                Recognizer(m, 16000f)
            }
            recognizer = rec
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}

                override fun onResult(hypothesis: String?) {
                    val t = textOf(hypothesis)
                    if (t.isNotEmpty()) finish(t)
                }

                override fun onFinalResult(hypothesis: String?) {
                    val t = textOf(hypothesis)
                    finish(t.ifEmpty { null })
                }

                override fun onError(exception: Exception?) {
                    finish(null)
                }

                override fun onTimeout() {
                    finish(null)
                }
            }
            val s = SpeechService(rec, 16000f)
            service = s
            s.startListening(listener, timeoutMs)
        } catch (e: Exception) {
            stop()
            cb(null)
        }
    }

    val isListening: Boolean get() = service != null

    fun stop() {
        val s = service
        service = null
        if (s != null) {
            try { s.cancel() } catch (_: Exception) {}
            try { s.shutdown() } catch (_: Exception) {}
        }
        val r = recognizer
        recognizer = null
        try { r?.close() } catch (_: Exception) {}
    }

    private fun textOf(hypothesis: String?): String {
        if (hypothesis.isNullOrBlank()) return ""
        return try {
            JSONObject(hypothesis).optString("text").replace("[unk]", " ").trim()
        } catch (e: Exception) {
            ""
        }
    }
}
