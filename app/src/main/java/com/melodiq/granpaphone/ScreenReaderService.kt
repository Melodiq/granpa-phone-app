package com.melodiq.granpaphone

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Чтение экрана вслух: собирает весь текст текущего окна (любого приложения),
 * подсвечивает края экрана и читает. Кнопка громкости — остановить.
 * Включается в «Специальных возможностях» телефона.
 */
class ScreenReaderService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var glow: GlowView? = null
    private var reading = false
    private var generation = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopReading()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopReading()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopReading()
    }

    /** Во время чтения любая кнопка громкости останавливает его. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val volume = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (reading && volume) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                Profile.lastReadStop = "volume key"
                stopReading()
                App.instance.speaker.stop()
            }
            return true
        }
        return false
    }

    fun readNow() {
        val phr = Phrases.of(this)
        val sp = App.instance.speaker
        val text = collectText()
        if (text.isEmpty()) {
            sp.say(phr.nothingOnScreen)
            return
        }
        val gen = ++generation
        reading = true
        showGlow()
        KeeperService.instance?.onBusyChanged() // пока читаем — слушаем «отбой»
        val chunks = listOf(phr.readingScreen) + chunk(text)
        fun next(i: Int) {
            if (gen != generation || !reading) return
            if (i >= chunks.size) {
                finish(gen)
                return
            }
            sp.say(chunks[i], flush = i == 0) { next(i + 1) }
        }
        next(0)
        watchdog(gen)
    }

    /** Если речь прервали снаружи (звонок, другая фраза), рамку всё равно надо убрать. */
    private fun watchdog(gen: Int) {
        var quiet = 0
        val check = object : Runnable {
            override fun run() {
                if (gen != generation || !reading) return
                quiet = if (App.instance.speaker.isSpeaking) 0 else quiet + 1
                if (quiet >= 3) {
                    Profile.lastReadStop = "speech interrupted"
                    finish(gen)
                } else handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(check, 2500)
    }

    private fun finish(gen: Int) {
        if (gen != generation) return
        reading = false
        hideGlow()
        KeeperService.instance?.onBusyChanged() // снова ждать «Алёша»
    }

    fun stopReading() {
        val was = reading
        generation++
        reading = false
        hideGlow()
        if (was) KeeperService.instance?.onBusyChanged()
    }

    // ---------- сбор текста ----------

    private fun collectText(): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        val roots = ArrayList<AccessibilityNodeInfo>()
        try {
            // Все обычные окна приложений; наше собственное окно пропускаем.
            for (w in windows) {
                if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION ||
                    w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM
                ) {
                    w.root?.let { roots.add(it) }
                }
            }
        } catch (_: Exception) {}
        if (roots.isEmpty()) rootInActiveWindow?.let { roots.add(it) }

        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 60 || out.size > 400) return
            if (n.packageName?.toString() == packageName) return
            if (n.isVisibleToUser) {
                val t = (n.text ?: n.contentDescription)?.toString()?.trim()
                if (!t.isNullOrEmpty() && t.length > 1 && seen.add(t)) out.add(t)
            }
            for (k in 0 until n.childCount) {
                walk(try { n.getChild(k) } catch (e: Exception) { null }, depth + 1)
            }
        }
        // Активное окно — первым.
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() != packageName) walk(active, 0)
        roots.forEach { r -> if (r.windowId != active?.windowId) walk(r, 0) }
        return out
    }

    /** Режем длинный текст на куски, которые синтез речи точно проговорит. */
    private fun chunk(parts: List<String>): List<String> {
        val res = ArrayList<String>()
        val sb = StringBuilder()
        for (p in parts) {
            val piece = if (p.endsWith(".") || p.endsWith("!") || p.endsWith("?") || p.endsWith("。")) p else "$p."
            if (sb.length + piece.length > 700 && sb.isNotEmpty()) {
                res.add(sb.toString()); sb.clear()
            }
            if (piece.length > 700) {
                piece.chunked(700).forEach { res.add(it) }
            } else {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(piece)
            }
        }
        if (sb.isNotEmpty()) res.add(sb.toString())
        return res
    }

    // ---------- светящаяся рамка ----------

    private fun showGlow() {
        if (glow != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Касаемая рамка: любое касание экрана останавливает чтение.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        val v = GlowView(this)
        // Останавливаем только настоящим касанием: короткий тап не у самого края экрана
        // (держа телефон, ладонь задевает края — это не должно обрывать чтение).
        var downX = 0f
        var downY = 0f
        var downAt = 0L
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; downAt = e.eventTime }
                android.view.MotionEvent.ACTION_UP -> {
                    val dp = resources.displayMetrics.density
                    val edge = 36 * dp
                    val moved = Math.hypot((e.rawX - downX).toDouble(), (e.rawY - downY).toDouble()) > 30 * dp
                    val quick = e.eventTime - downAt < 600
                    val nearEdge = downX < edge || downX > view.width - edge || downY < edge || downY > view.height - edge
                    if (quick && !moved && !nearEdge) {
                        Profile.lastReadStop = "tap"
                        stopReading()
                        App.instance.speaker.stop()
                    }
                }
            }
            true
        }
        try {
            wm.addView(v, lp)
            glow = v
        } catch (_: Exception) {}
    }

    private fun hideGlow() {
        val v = glow ?: return
        glow = null
        try { getSystemService(WindowManager::class.java)?.removeView(v) } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        var instance: ScreenReaderService? = null
            private set

        val isOn: Boolean get() = instance != null

        /** Идёт чтение — микрофон в это время не слушает, чтобы не реагировать на свою же речь. */
        val isReading: Boolean get() = instance?.reading == true

        /** Остановить чтение (команда «отбой»). */
        fun stop() {
            val s = instance ?: return
            s.handler.post { s.stopReading() }
        }

        /** Прочитать то, что сейчас на экране. false — служба не включена. */
        fun read(): Boolean {
            val s = instance ?: return false
            s.handler.post { s.readNow() }
            return true
        }
    }
}

/** Пульсирующее свечение по краям экрана, пока идёт чтение. */
class GlowView(ctx: Context) : View(ctx) {
    private val dp = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var phase = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        // Несколько полос с убывающей яркостью дают мягкое свечение внутрь экрана.
        val layers = 6
        for (i in 0 until layers) {
            val w = (4 + i * 5) * dp
            val alpha = ((1f - i / layers.toFloat()) * (150 + 105 * phase)).toInt().coerceIn(0, 255)
            paint.strokeWidth = w
            paint.color = Color.argb(alpha / (i + 1), 0xFF, 0xB6, 0x27)
            rect.set(w / 2, w / 2, width - w / 2, height - w / 2)
            canvas.drawRoundRect(rect, 28 * dp, 28 * dp, paint)
        }
    }
}
