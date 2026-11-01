package com.melodiq.granpaphone

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/**
 * Главный экран: весь экран — одна кнопка.
 * Сверху крупные часы, в центре «лампа» состояния, снизу что сейчас происходит.
 * Касание — сказать команду. Удержание 5 секунд — настройка (для родных).
 */
class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var clock: TextView
    private lateinit var day: TextView
    private lateinit var lamp: LampView
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var assistant: Assistant

    private val handler = Handler(Looper.getMainLooper())
    private var longPressFired = false
    private var pendingGreet = false
    private var pendingWake = false
    private var pendingListen = false

    private val longPress = Runnable {
        longPressFired = true
        assistant.stopAll()
        App.instance.speaker.say("Настройка")
        startActivity(Intent(this, SetupActivity::class.java))
    }

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 60_000 - now % 60_000 + 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        buildUi()

        assistant = Assistant(this)
        KeeperService.start(this)

        if (!Setup.essentialsGranted(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        pendingGreet = (savedInstanceState == null || intent.getBooleanExtra(EXTRA_GREET, false)) && !Sleep.isOn(this)
        pendingWake = intent.getBooleanExtra(EXTRA_WAKE, false)
        pendingListen = intent.getBooleanExtra(EXTRA_LISTEN, false)
    }

    private fun buildUi() {
        val dp = resources.displayMetrics.density
        val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val black = Typeface.create("sans-serif-black", Typeface.NORMAL)

        clock = TextView(this).apply {
            typeface = black
            textSize = 96f
            setTextColor(Palette.MILK)
            gravity = Gravity.CENTER
            includeFontPadding = false
            letterSpacing = -0.02f
        }
        day = TextView(this).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = 30f
            setTextColor(Palette.MILK)
            gravity = Gravity.CENTER
        }
        lamp = LampView(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        status = TextView(this).apply {
            typeface = condensed
            textSize = 36f
            setTextColor(Palette.MILK)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.08f)
            maxLines = 6
        }
        hint = TextView(this).apply {
            typeface = medium
            textSize = 20f
            setTextColor(Palette.MILK_DIM)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.NIGHT)
            setPadding((24 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt(), (32 * dp).toInt())
            addView(clock, LinearLayout.LayoutParams(-1, -2))
            addView(day, LinearLayout.LayoutParams(-1, -2))
            addView(lamp, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = (12 * dp).toInt(); bottomMargin = (12 * dp).toInt()
            })
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (10 * dp).toInt() })
        }
        setContentView(root)
        window.statusBarColor = Palette.NIGHT
        window.navigationBarColor = Palette.NIGHT

        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longPressFired = false
                    handler.postDelayed(longPress, 5000)
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (!longPressFired) onTap()
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            true
        }
        updateClock()
        setState(LampState.IDLE, getString(R.string.main_idle))
    }

    private fun onTap() {
        if (Sleep.isOn(this)) {
            wakeUp()
        } else {
            assistant.onTap()
        }
    }

    /** Выйти из сна: служба перестаёт слушать «Алёша», помощник отвечает «Да, слушаю». */
    private fun wakeUp() {
        App.instance.ears.stop()
        Sleep.set(this, false)
        KeeperService.wake(this)
        applySleepLook()
        assistant.wakeGreet()
    }

    /** «Прочитай экран»: свернуться и прочитать то, что было под помощником. */
    fun readScreenBehind() {
        moveTaskToBack(true)
        handler.postDelayed({ ScreenReaderService.read() }, 900)
    }

    /** Команда «отбой». */
    fun goToSleep() {
        Sleep.set(this, true)
        KeeperService.sleep(this)
        applySleepLook()
        handler.postDelayed({ moveTaskToBack(true) }, 400)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_LISTEN, false)) pendingListen = true
        else if (intent.getBooleanExtra(EXTRA_WAKE, false)) pendingWake = true
        else if (intent.getBooleanExtra(EXTRA_GREET, false) && !Sleep.isOn(this)) pendingGreet = true
    }

    override fun onResume() {
        super.onResume()
        App.activityVisible = true
        KeeperService.start(this)
        KeeperService.appVisible(this)
        handler.post(tick)
        assistant.reloadContacts()
        applySleepLook()
        if (!Setup.essentialsGranted(this)) return
        when {
            pendingListen -> {
                // Нажали на виджет: сразу слушаем (из отбоя — будим).
                pendingListen = false
                pendingGreet = false
                pendingWake = false
                if (Sleep.isOn(this)) wakeUp() else assistant.onTap()
            }
            pendingWake -> {
                pendingWake = false
                pendingGreet = false
                Sleep.countOpened(this)
                assistant.wakeGreet()
            }
            pendingGreet -> {
                pendingGreet = false
                assistant.greet()
            }
        }
    }

    override fun onPause() {
        App.activityVisible = false
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(tick)
        // Во сне микрофоном пользуется служба (ждёт «Алёша») — не мешаем ей.
        if (!Sleep.isOn(this)) App.instance.ears.stop()
        KeeperService.appHidden(this)
        super.onPause()
    }

    private fun applySleepLook() {
        if (Sleep.isOn(this)) {
            root.setBackgroundColor(Palette.NIGHT_DEEP)
            window.statusBarColor = Palette.NIGHT_DEEP
            window.navigationBarColor = Palette.NIGHT_DEEP
            setState(LampState.SLEEP, getString(R.string.sleep_status), getString(R.string.sleep_hint))
        } else {
            root.setBackgroundColor(Palette.NIGHT)
            window.statusBarColor = Palette.NIGHT
            window.navigationBarColor = Palette.NIGHT
            if (lamp.state == LampState.SLEEP) setState(LampState.IDLE, getString(R.string.main_idle))
        }
    }

    private fun updateClock() {
        val c = Calendar.getInstance()
        clock.text = String.format("%d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        day.text = if (Lang.isRu(this)) {
            val wd = Ru.weekdayName(c.get(Calendar.DAY_OF_WEEK)).replaceFirstChar { it.uppercase() }
            wd + ", " + c.get(Calendar.DAY_OF_MONTH) + " " + Ru.monthsGen[c.get(Calendar.MONTH)]
        } else {
            val pattern = if (Lang.code(this) == "zh") "M月d日 EEEE" else "EEEE, d MMMM"
            java.text.SimpleDateFormat(pattern, Lang.locale(this)).format(c.time)
        }
    }

    /** Показать текст (то, что сейчас говорится). */
    fun show(text: String) {
        setState(LampState.SPEAK, if (Lang.isRu(this)) text else getString(R.string.status_speak))
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    fun setState(state: LampState, text: String? = null, hintText: String? = null) {
        lamp.state = state
        if (text != null) status.text = text
        hint.text = hintText ?: when (state) {
            LampState.LISTEN -> getString(R.string.hint_listen)
            LampState.IDLE -> getString(R.string.hint_idle)
            LampState.SLEEP -> getString(R.string.hint_sleep)
            else -> ""
        }
        root.contentDescription = status.text.toString() + ". " + hint.text
    }

    companion object {
        const val EXTRA_GREET = "greet"
        const val EXTRA_WAKE = "wake"
        const val EXTRA_LISTEN = "listen"
    }
}
