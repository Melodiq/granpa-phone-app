package com.melodiq.granpaphone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

/**
 * Главный экран: весь экран — одна кнопка.
 * Касание — сказать команду. Удержание 5 секунд — настройка (для родных).
 */
class MainActivity : Activity() {

    private lateinit var label: TextView
    private lateinit var assistant: Assistant
    private val handler = Handler(Looper.getMainLooper())
    private var longPressFired = false
    private var pendingGreet = false

    private val longPress = Runnable {
        longPressFired = true
        assistant.stopAll()
        App.instance.speaker.say("Настройка")
        startActivity(Intent(this, SetupActivity::class.java))
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

        label = TextView(this).apply {
            textSize = 42f
            setTextColor(Color.YELLOW)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            text = "Нажмите в любом месте экрана и скажите команду"
        }
        setContentView(label)
        label.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longPressFired = false
                    handler.postDelayed(longPress, 5000)
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (!longPressFired) assistant.onTap()
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            true
        }

        assistant = Assistant(this)
        KeeperService.start(this)

        if (!Setup.essentialsGranted(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        pendingGreet = savedInstanceState == null || intent.getBooleanExtra(EXTRA_GREET, false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_GREET, false)) pendingGreet = true
    }

    override fun onResume() {
        super.onResume()
        App.activityVisible = true
        KeeperService.start(this)
        assistant.reloadContacts()
        if (pendingGreet && Setup.essentialsGranted(this)) {
            pendingGreet = false
            assistant.greet()
        }
    }

    override fun onPause() {
        App.activityVisible = false
        handler.removeCallbacks(longPress)
        App.instance.ears.stop()
        super.onPause()
    }

    fun show(text: String) {
        label.text = text
        label.contentDescription = text
    }

    companion object {
        const val EXTRA_GREET = "greet"
    }
}
