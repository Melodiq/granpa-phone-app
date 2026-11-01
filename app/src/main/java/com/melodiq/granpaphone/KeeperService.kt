package com.melodiq.granpaphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Постоянная служба:
 *  — при включении экрана открывает помощника с приветствием;
 *  — при входящем звонке повторяет вслух, кто звонит;
 *  — держит в памяти модель распознавания, чтобы не было задержек.
 */
class KeeperService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var callState = TelephonyManager.CALL_STATE_IDLE
    private var ringName = ""
    private var lastCallEnd = 0L
    private var phoneListener: PhoneStateListener? = null
    private var wakeListening = false

    /** Во сне: слушать слово «Алёша» по кругу. */
    private val wakeLoop = object : Runnable {
        override fun run() {
            if (!Sleep.isOn(this@KeeperService) || callState != TelephonyManager.CALL_STATE_IDLE) {
                wakeListening = false
                return
            }
            val ears = App.instance.ears
            ears.whenReady {
                if (!Sleep.isOn(this@KeeperService) || callState != TelephonyManager.CALL_STATE_IDLE) return@whenReady
                val grammar = Sleep.WAKE_PHRASES.mapNotNull { ears.fixPhrase(it) }
                if (grammar.isEmpty()) { wakeListening = false; return@whenReady }
                wakeListening = true
                ears.listen(grammar, 30_000) { text ->
                    if (!Sleep.isOn(this@KeeperService)) { wakeListening = false; return@listen }
                    val heard = Ru.words(text ?: "").any { it in Sleep.WAKE_WORDS }
                    if (heard) wakeByVoice() else handler.postDelayed(this, 150)
                }
            }
        }
    }

    private fun startWakeLoop(delay: Long) {
        handler.removeCallbacks(wakeLoop)
        handler.postDelayed(wakeLoop, delay)
    }

    private fun stopWakeLoop() {
        handler.removeCallbacks(wakeLoop)
        if (wakeListening) {
            wakeListening = false
            // Если главный экран уже открыт, микрофоном пользуется он — не обрываем.
            if (!App.activityVisible) App.instance.ears.stop()
        }
    }

    private fun wakeByVoice() {
        stopWakeLoop()
        Sleep.set(this, false)
        startFg()
        App.instance.speaker.beepHeard()
        val canOpen = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        if (canOpen) {
            try {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .putExtra(MainActivity.EXTRA_WAKE, true)
                )
                return
            } catch (_: Exception) {}
        }
        App.instance.speaker.say("Я проснулся. Коснитесь экрана, чтобы сказать команду.")
    }

    private val announce = object : Runnable {
        override fun run() {
            if (callState != TelephonyManager.CALL_STATE_RINGING) return
            // Экран звонка сам говорит и слушает; озвучиваем только если его не видно.
            if (!IncomingActivity.visible) App.instance.speaker.say("Звонит: $ringName")
            handler.postDelayed(this, 5000)
        }
    }

    /** Открыть экран входящего с ответом голосом. false — нельзя (нет разрешения). */
    private fun openIncoming(): Boolean {
        val canOpen = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        if (!canOpen) return false
        Calls.quietRinger(this)
        return try {
            startActivity(
                Intent(this, IncomingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(IncomingActivity.EXTRA_NAME, ringName)
            )
            true
        } catch (_: Exception) {
            Calls.restoreRinger(this)
            false
        }
    }

    private fun stopRinging() {
        handler.removeCallbacks(announce)
        IncomingActivity.close()
        Calls.restoreRinger(this)
    }

    private val openAssistant = Runnable {
        val afterCall = System.currentTimeMillis() - lastCallEnd < 10_000
        if (callState == TelephonyManager.CALL_STATE_IDLE && !afterCall && !Sleep.isOn(this)) {
            val canOpen = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
            if (canOpen) {
                try {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            .putExtra(MainActivity.EXTRA_GREET, true)
                    )
                } catch (_: Exception) {}
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_ON -> {
                    // Небольшая пауза: если экран включился из-за звонка, приветствие не нужно.
                    handler.removeCallbacks(openAssistant)
                    handler.postDelayed(openAssistant, 700)
                }
                Intent.ACTION_USER_PRESENT -> handler.postDelayed({ Reminders.onUserPresent(this@KeeperService) }, 2500)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startFg()
        if (Sleep.isOn(this)) startWakeLoop(2000)
        Calls.restoreRinger(this) // на случай, если прошлый раз служба упала во время звонка
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        listenPhone()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SLEEP -> {
                startFg()
                startWakeLoop(1500) // дать договорить «Отбой…»
            }
            ACTION_WAKE -> {
                stopWakeLoop()
                startFg()
            }
            else -> {
                startFg()
                if (Sleep.isOn(this) && !wakeListening) startWakeLoop(2000)
            }
        }
        if (phoneListener == null) listenPhone()
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        stopPhone()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Во сне нужен доступ к микрофону из фона — служба объявляет тип «микрофон». */
    private fun startFg() {
        val sleeping = Sleep.isOn(this)
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Помощник Алёша", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val n = builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (sleeping) "Алёша отдыхает — скажите «Алёша»" else "Помощник Алёша работает")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val base = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val mic = if (sleeping && Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, 1, n, base or mic)
        } catch (_: Exception) {
            // Из фона Android может не дать тип «микрофон» (например, после перезагрузки).
            try { ServiceCompat.startForeground(this, 1, n, base) } catch (_: Exception) {}
        }
    }

    @Suppress("DEPRECATION")
    private fun listenPhone() {
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        val l = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                val was = callState
                callState = state
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        handler.removeCallbacks(openAssistant)
                        stopWakeLoop()
                        ringName = Contacts.nameFor(this@KeeperService, phoneNumber)
                        if (was != TelephonyManager.CALL_STATE_RINGING) {
                            handler.removeCallbacks(announce)
                            val opened = openIncoming()
                            // Если экран звонка не открылся или его перекрыли — озвучиваем сами.
                            handler.postDelayed(announce, if (opened) 3000 else 1200)
                        } else {
                            // Номер пришёл вторым событием — обновить имя на экране звонка.
                            IncomingActivity.current?.let { openIncoming() }
                        }
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        stopWakeLoop()
                        stopRinging()
                        handler.removeCallbacks(openAssistant)
                        if (was == TelephonyManager.CALL_STATE_RINGING) App.instance.speaker.stop()
                    }
                    else -> {
                        stopRinging()
                        if (was != TelephonyManager.CALL_STATE_IDLE) {
                            lastCallEnd = System.currentTimeMillis()
                            if (Sleep.isOn(this@KeeperService)) startWakeLoop(3000)
                        }
                    }
                }
            }
        }
        try {
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            phoneListener = l
        } catch (_: SecurityException) {
            // Нет разрешения «Телефон» — включится после настройки.
        }
    }

    @Suppress("DEPRECATION")
    private fun stopPhone() {
        val l = phoneListener ?: return
        try { getSystemService(TelephonyManager::class.java)?.listen(l, PhoneStateListener.LISTEN_NONE) } catch (_: Exception) {}
        phoneListener = null
    }

    companion object {
        private const val CHANNEL = "keeper"
        private const val ACTION_SLEEP = "com.melodiq.granpaphone.SLEEP"
        private const val ACTION_WAKE = "com.melodiq.granpaphone.WAKE"

        fun sleep(ctx: Context) {
            startFgService(ctx, Intent(ctx, KeeperService::class.java).setAction(ACTION_SLEEP))
        }

        fun wake(ctx: Context) {
            startFgService(ctx, Intent(ctx, KeeperService::class.java).setAction(ACTION_WAKE))
        }

        fun start(ctx: Context) {
            startFgService(ctx, Intent(ctx, KeeperService::class.java))
        }
    }
}
