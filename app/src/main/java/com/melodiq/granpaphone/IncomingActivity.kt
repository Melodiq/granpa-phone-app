package com.melodiq.granpaphone

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Экран входящего звонка поверх системного.
 * Говорит, кто звонит, и слушает: «ответить», «подними трубку», «сбросить»…
 * Касание в любом месте экрана — ответить.
 */
class IncomingActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var who: TextView
    private lateinit var lamp: LampView
    private lateinit var hint: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var name = ""
    private var session = 0
    private var rounds = 0

    private val sp get() = App.instance.speaker
    private val ears get() = App.instance.ears

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val dp = resources.displayMetrics.density
        val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val top = TextView(this).apply {
            text = getString(R.string.call_top)
            typeface = medium
            textSize = 26f
            setTextColor(Palette.MILK_DIM)
            gravity = Gravity.CENTER
        }
        who = TextView(this).apply {
            typeface = condensed
            textSize = 56f
            setTextColor(Palette.MILK)
            gravity = Gravity.CENTER
            maxLines = 2
            setLineSpacing(0f, 1.0f)
        }
        lamp = LampView(this).apply {
            state = LampState.CALL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        hint = TextView(this).apply {
            text = getString(R.string.call_hint)
            typeface = condensed
            textSize = 32f
            setTextColor(Palette.MINT)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.08f)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.NIGHT)
            setPadding((24 * dp).toInt(), (40 * dp).toInt(), (24 * dp).toInt(), (40 * dp).toInt())
            addView(top, LinearLayout.LayoutParams(-1, -2))
            addView(who, LinearLayout.LayoutParams(-1, -2))
            addView(lamp, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = (16 * dp).toInt(); bottomMargin = (16 * dp).toInt()
            })
            addView(hint, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(root)
        window.statusBarColor = Palette.NIGHT
        window.navigationBarColor = Palette.NIGHT
        root.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) answer()
            true
        }
        setName(intent.getStringExtra(EXTRA_NAME) ?: "")
        current = this
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_NAME)?.let { setName(it) }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        rounds = 0
        loop()
    }

    override fun onPause() {
        visible = false
        session++
        ears.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (current === this) current = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    private fun setName(n: String) {
        name = n
        who.text = n
        lamp.letter = n.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: ""
        root.contentDescription = "Звонит: $n. Коснитесь экрана, чтобы ответить."
    }

    private fun loop() {
        val s = ++session
        val phr = Phrases.of(this)
        val phrase = if (rounds == 0) phr.incomingFirst(name) else phr.incomingRepeat(name)
        rounds++
        sp.say(phrase) {
            if (s != session) return@say
            if (ears.model == null) {
                handler.postDelayed({ if (s == session) loop() }, 3000)
                return@say
            }
            sp.beepListen()
            val grammar = if (phr.useGrammar) phr.answerPhrases.mapNotNull { ears.fixPhrase(it) }.ifEmpty { null } else null
            ears.listen(grammar, 4500) { text ->
                if (s != session) return@listen
                when (text?.let { phr.answerOrReject(it) }) {
                    false -> reject()
                    true -> answer()
                    null -> loop()
                }
            }
        }
    }

    private fun answer() {
        session++
        ears.stop()
        sp.stop()
        sp.beepHeard()
        if (Calls.answer(this)) {
            finish()
        } else {
            lamp.state = LampState.ERROR
            hint.setTextColor(Palette.ROSE)
            hint.text = getString(R.string.call_fail)
            sp.say(Phrases.of(this).answerFailed)
            handler.postDelayed({ finish() }, 2500)
        }
    }

    private fun reject() {
        session++
        ears.stop()
        sp.beepHeard()
        sp.say(Phrases.of(this).rejecting)
        Calls.reject(this)
        finish()
    }

    companion object {
        const val EXTRA_NAME = "name"

        @Volatile
        var current: IncomingActivity? = null
            private set

        @Volatile
        var visible = false
            private set

        fun close() {
            current?.let { a -> a.runOnUiThread { if (!a.isFinishing) a.finish() } }
        }
    }
}

/** Ответ / сброс звонка и приглушение звонка, пока говорит помощник. */
object Calls {

    @SuppressLint("MissingPermission")
    fun answer(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val tm = ctx.getSystemService(TelecomManager::class.java) ?: return false
        return try {
            @Suppress("DEPRECATION")
            tm.acceptRingingCall()
            true
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun reject(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        val tm = ctx.getSystemService(TelecomManager::class.java) ?: return false
        return try {
            @Suppress("DEPRECATION")
            tm.endCall()
        } catch (e: Exception) {
            false
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ringer", Context.MODE_PRIVATE)

    /** Сделать мелодию звонка тихой, чтобы она не мешала слушать команду. */
    fun quietRinger(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        try {
            val cur = am.getStreamVolume(AudioManager.STREAM_RING)
            if (cur <= 1) return
            if (prefs(ctx).getInt("saved", -1) < 0) prefs(ctx).edit().putInt("saved", cur).apply()
            am.setStreamVolume(AudioManager.STREAM_RING, 1, 0)
        } catch (_: Exception) {}
    }

    /** Вернуть громкость звонка как было. */
    fun restoreRinger(ctx: Context) {
        val saved = prefs(ctx).getInt("saved", -1)
        if (saved < 0) return
        prefs(ctx).edit().remove("saved").apply()
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        try {
            am.setStreamVolume(AudioManager.STREAM_RING, saved, 0)
        } catch (_: Exception) {}
    }
}
