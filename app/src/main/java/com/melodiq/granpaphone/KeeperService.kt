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
import android.os.PowerManager
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
    private var fgType = -1
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Слушать слово «Алёша» по кругу. Работает, когда главный экран не слушает сам:
     * экран выключен, открыто другое приложение или помощник в отбое.
     */
    private fun shouldWakeListen(): Boolean =
        callState == TelephonyManager.CALL_STATE_IDLE &&
            !IncomingActivity.visible &&
            !ScreenReaderService.isReading &&
            (!App.activityVisible || Sleep.isOn(this)) &&
            (Sleep.isOn(this) || Sleep.alwaysListen(this)) &&
            Setup.granted(this, android.Manifest.permission.RECORD_AUDIO)

    private val wakeLoop = object : Runnable {
        override fun run() {
            if (!shouldWakeListen()) {
                setWakeListening(false)
                return
            }
            val ears = App.instance.ears
            ears.whenReady {
                if (!shouldWakeListen()) { setWakeListening(false); return@whenReady }
                val phr = Phrases.of(this@KeeperService)
                // Для китайского слушаем свободно: «阿廖沙» может не быть в словаре модели.
                val grammar = if (phr.useGrammar) {
                    (phr.wakePhrases + phr.readPhrases).mapNotNull { ears.fixPhrase(it) }
                } else null
                if (grammar != null && grammar.isEmpty()) { setWakeListening(false); return@whenReady }
                setWakeListening(true)
                // Строгий отбор: слово должно прозвучать уверенно и без потока посторонней речи —
                // тогда не срабатывает от видео, телевизора и другого языка.
                val accept = { h: Hyp ->
                    h.confident({ phr.isWake(it) || phr.isReadScreen(it) }, 0.75, 1, 6)
                }
                ears.listen(grammar, 10 * 60_000, accept = accept) { text ->
                    if (!shouldWakeListen()) { setWakeListening(false); return@listen }
                    when {
                        text != null && phr.isReadScreen(text) -> readScreenByVoice()
                        text != null && phr.isWake(text) -> wakeByVoice()
                        else -> handler.postDelayed(this, 150)
                    }
                }
            }
        }
    }

    // ---------- «отбой» во время чтения экрана или звонка ----------

    private var stopListening = false

    /** Занят: читает экран или идёт звонок. Тогда слушаем только «отбой». */
    private fun busy(): Boolean =
        (ScreenReaderService.isReading || callState == TelephonyManager.CALL_STATE_OFFHOOK) &&
            Setup.granted(this, android.Manifest.permission.RECORD_AUDIO)

    private val stopLoop = object : Runnable {
        override fun run() {
            if (!busy()) { stopListening = false; return }
            val ears = App.instance.ears
            ears.whenReady {
                if (!busy()) { stopListening = false; return@whenReady }
                val phr = Phrases.of(this@KeeperService)
                val grammar = if (phr.useGrammar) phr.stopPhrases.mapNotNull { ears.fixPhrase(it) }.ifEmpty { null } else null
                stopListening = true
                setWakeListening(true) // тот же wakelock: процессор не должен уснуть
                wakeListening = false
                // Во время чтения микрофон слышит и сам текст — поэтому только уверенное короткое «отбой».
                val accept = { h: Hyp -> h.confident({ phr.isStop(it) }, 0.85, 2, 5) }
                ears.listen(grammar, 10 * 60_000, accept = accept) { text ->
                    stopListening = false
                    if (text != null && phr.isStop(text)) stopEverything()
                    else if (busy()) handler.postDelayed(this, 150)
                    else onBusyChanged()
                }
            }
        }
    }

    /** «Отбой»: замолчать, перестать читать, сбросить звонок. */
    private fun stopEverything() {
        App.instance.speaker.stop()
        ScreenReaderService.stop()
        if (callState == TelephonyManager.CALL_STATE_OFFHOOK || callState == TelephonyManager.CALL_STATE_RINGING) {
            Calls.reject(this)
        }
        App.instance.speaker.beepHeard()
        handler.postDelayed({ onBusyChanged() }, 1500)
    }

    /** Чтение или звонок начались/закончились — переключить, что слушаем. */
    fun onBusyChanged() {
        handler.removeCallbacks(stopLoop)
        if (busy()) {
            handler.removeCallbacks(wakeLoop)
            if (!stopListening) handler.postDelayed(stopLoop, 400)
        } else {
            if (stopListening) {
                stopListening = false
                if (!App.activityVisible) App.instance.ears.stop()
            }
            setWakeListening(false)
            startWakeLoop(800)
        }
    }

    /** «Прочитай экран» без открытия помощника: читаем то, что сейчас на экране. */
    private fun readScreenByVoice() {
        App.instance.speaker.beepHeard()
        if (!ScreenReaderService.read()) {
            App.instance.speaker.say(Phrases.of(this).readerDisabled)
        }
        // Слушать дальше, когда чтение закончится (чтобы не услышать самих себя).
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (App.instance.speaker.isSpeaking) handler.postDelayed(this, 1000) else startWakeLoop(300)
            }
        }, 1500)
    }

    /** Пока слушаем «Алёша», процессор не должен засыпать вместе с экраном. */
    private fun setWakeListening(on: Boolean) {
        wakeListening = on
        try {
            if (on) {
                if (wakeLock == null) {
                    wakeLock = getSystemService(PowerManager::class.java)
                        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alesha:wakeword")
                        ?.apply { setReferenceCounted(false) }
                }
                if (wakeLock?.isHeld != true) wakeLock?.acquire()
            } else {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun startWakeLoop(delay: Long) {
        handler.removeCallbacks(wakeLoop)
        handler.postDelayed(wakeLoop, delay)
    }

    private fun stopWakeLoop() {
        handler.removeCallbacks(wakeLoop)
        if (wakeListening) {
            setWakeListening(false)
            // Если главный экран уже открыт, микрофоном пользуется он — не обрываем.
            if (!App.activityVisible) App.instance.ears.stop()
        }
    }

    private fun wakeByVoice() {
        Sleep.countHeard(this)
        stopWakeLoop()
        Sleep.set(this, false)
        updateNotification()
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
        App.instance.speaker.say(Phrases.of(this).tapWhenReady)
        startWakeLoop(4000)
    }

    private val announce = object : Runnable {
        override fun run() {
            if (callState != TelephonyManager.CALL_STATE_RINGING) return
            // Экран звонка сам говорит и слушает; озвучиваем только если его не видно.
            if (!IncomingActivity.visible) App.instance.speaker.say(Phrases.of(this@KeeperService).ringing(ringName))
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
        instance = this
        startFg()
        startWakeLoop(2500)
        Calls.restoreRinger(this) // на случай, если прошлый раз служба упала во время звонка
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        listenPhone()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground вызываем только когда нужно сменить тип: повторный вызов из фона
        // на новых Android отнимает доступ к микрофону.
        startFg()
        when (intent?.action) {
            ACTION_SLEEP -> {
                updateNotification()
                startWakeLoop(1500) // дать договорить «Отбой…»
            }
            ACTION_WAKE -> {
                updateNotification()
                stopWakeLoop()
            }
            ACTION_FG -> stopWakeLoop()
            ACTION_BG -> startWakeLoop(1200)
            else -> if (!wakeListening) startWakeLoop(2000)
        }
        if (phoneListener == null) listenPhone()
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        stopPhone()
        handler.removeCallbacksAndMessages(null)
        setWakeListening(false)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val sleeping = Sleep.isOn(this)
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, Lang.str(this, R.string.notif_channel), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(Lang.str(this, if (sleeping) R.string.notif_sleep else R.string.notif_on))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        try { getSystemService(NotificationManager::class.java)?.notify(1, buildNotification()) } catch (_: Exception) {}
    }

    /**
     * Тип «микрофон» нужен, чтобы слушать «Алёша» при выключенном экране.
     * Android даёт его только пока приложение на экране, поэтому просим один раз
     * и не трогаем, пока он есть.
     */
    private fun startFg() {
        val base = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val micOk = Build.VERSION.SDK_INT >= 30 && Setup.granted(this, android.Manifest.permission.RECORD_AUDIO)
        val mic = if (micOk) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        val want = base or mic
        if (fgType == want) return
        val n = buildNotification()
        try {
            ServiceCompat.startForeground(this, 1, n, want)
            fgType = want
        } catch (_: Exception) {
            // Из фона (после перезагрузки, из будильника) микрофон не дают — подождём открытия приложения.
            if (fgType < 0) {
                try { ServiceCompat.startForeground(this, 1, n, base); fgType = base } catch (_: Exception) {}
            }
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
                        onBusyChanged() // во время звонка слушаем «отбой»
                    }
                    else -> {
                        stopRinging()
                        if (was != TelephonyManager.CALL_STATE_IDLE) {
                            lastCallEnd = System.currentTimeMillis()
                            handler.removeCallbacks(stopLoop)
                            if (stopListening) { stopListening = false; App.instance.ears.stop() }
                            startWakeLoop(3000)
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
        @Volatile
        var instance: KeeperService? = null
            private set

        private const val CHANNEL = "keeper"
        private const val ACTION_SLEEP = "com.melodiq.granpaphone.SLEEP"
        private const val ACTION_WAKE = "com.melodiq.granpaphone.WAKE"
        private const val ACTION_FG = "com.melodiq.granpaphone.FG"
        private const val ACTION_BG = "com.melodiq.granpaphone.BG"

        /** Главный экран открыт — микрофон его. */
        fun appVisible(ctx: Context) {
            startFgService(ctx, Intent(ctx, KeeperService::class.java).setAction(ACTION_FG))
        }

        /** Главный экран закрыт — снова ждём «Алёша». */
        fun appHidden(ctx: Context) {
            startFgService(ctx, Intent(ctx, KeeperService::class.java).setAction(ACTION_BG))
        }

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
