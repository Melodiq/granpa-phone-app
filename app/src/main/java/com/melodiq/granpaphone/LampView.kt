package com.melodiq.granpaphone

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

/** Палитра «вечерняя лампа»: глубокий синий фон, молочный текст, цветные состояния. */
object Palette {
    val NIGHT = Color.parseColor("#0F2340")      // фон: глубокий синий, не чёрный — мягче для глаз
    val NIGHT_DEEP = Color.parseColor("#0A1830") // фон в режиме сна
    val MILK = Color.parseColor("#FFF4DC")       // основной текст, контраст ~14:1
    val MILK_DIM = Color.parseColor("#C9C0AE")   // подсказки
    val AMBER = Color.parseColor("#FFB627")      // «слушаю»
    val MINT = Color.parseColor("#5EE6A8")       // «услышал», входящий звонок
    val MOON = Color.parseColor("#8E9BD6")       // сон
    val ROSE = Color.parseColor("#FF8A80")       // ошибка, сброс
}

enum class LampState { IDLE, LISTEN, HEARD, SPEAK, SLEEP, CALL, ERROR }

/**
 * Огромный круг-«лампа» в центре экрана. Для почти незрячего важен не текст,
 * а большое цветное пятно: жёлтое дышит — говорите; зелёное — услышал; луна — сплю.
 */
class LampView(ctx: Context) : View(ctx) {

    var state: LampState = LampState.IDLE
        set(value) {
            if (field == value) return
            field = value
            updateAnimation()
            invalidate()
        }

    /** Буква в круге (первая буква имени звонящего). */
    var letter: String = ""
        set(value) { field = value; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val moonPath = Path()

    private var breath = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            breath = it.animatedValue as Float
            invalidate()
        }
    }

    private fun updateAnimation() {
        val shouldBreathe = state == LampState.LISTEN || state == LampState.CALL
        if (shouldBreathe && isAttachedToWindow) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
            breath = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun colorFor(s: LampState) = when (s) {
        LampState.IDLE -> Palette.MILK
        LampState.LISTEN -> Palette.AMBER
        LampState.HEARD -> Palette.MINT
        LampState.SPEAK -> Palette.MILK
        LampState.SLEEP -> Palette.MOON
        LampState.CALL -> Palette.MINT
        LampState.ERROR -> Palette.ROSE
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f * 0.86f
        val c = colorFor(state)

        when (state) {
            LampState.IDLE -> {
                // Спокойное кольцо: «я здесь, коснитесь, чтобы говорить».
                ring.color = c
                ring.alpha = 230
                ring.strokeWidth = maxR * 0.09f
                canvas.drawCircle(cx, cy, maxR * 0.78f, ring)
                fill.color = c
                fill.alpha = 255
                canvas.drawCircle(cx, cy, maxR * 0.16f, fill)
            }
            LampState.SLEEP -> {
                // Полумесяц.
                val r = maxR * 0.62f
                moonPath.reset()
                moonPath.addCircle(cx, cy, r, Path.Direction.CW)
                val cut = Path().apply { addCircle(cx + r * 0.45f, cy - r * 0.32f, r * 0.86f, Path.Direction.CW) }
                moonPath.op(cut, Path.Op.DIFFERENCE)
                fill.color = c
                fill.alpha = 255
                canvas.drawPath(moonPath, fill)
            }
            else -> {
                val pulse = if (state == LampState.LISTEN || state == LampState.CALL) breath else 0f
                val r = maxR * (0.78f + 0.12f * pulse)
                // Мягкое свечение вокруг лампы.
                glow.shader = RadialGradient(
                    cx, cy, maxR,
                    intArrayOf(withAlpha(c, 150), withAlpha(c, 0)),
                    floatArrayOf(r / maxR * 0.85f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, maxR, glow)
                fill.shader = null
                fill.color = c
                fill.alpha = 255
                canvas.drawCircle(cx, cy, r, fill)
                if (letter.isNotEmpty() && state == LampState.CALL) {
                    text.color = Palette.NIGHT
                    text.textSize = r * 1.15f
                    val y = cy - (text.descent() + text.ascent()) / 2f
                    canvas.drawText(letter, cx, y, text)
                }
            }
        }
    }

    private fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}
