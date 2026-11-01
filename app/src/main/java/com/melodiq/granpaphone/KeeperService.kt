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
        if (callState == TelephonyManager.CALL_STATE_IDLE && !afterCall) {
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
        Calls.restoreRinger(this) // на случай, если прошлый раз служба упала во время звонка
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        listenPhone()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
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

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Голосовой помощник", NotificationManager.IMPORTANCE_MIN)
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
            .setContentTitle("Голосовой помощник работает")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, 1, n, type)
        } catch (_: Exception) {}
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
                        stopRinging()
                        handler.removeCallbacks(openAssistant)
                        if (was == TelephonyManager.CALL_STATE_RINGING) App.instance.speaker.stop()
                    }
                    else -> {
                        stopRinging()
                        if (was != TelephonyManager.CALL_STATE_IDLE) lastCallEnd = System.currentTimeMillis()
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

        fun start(ctx: Context) {
            startFgService(ctx, Intent(ctx, KeeperService::class.java))
        }
    }
}
