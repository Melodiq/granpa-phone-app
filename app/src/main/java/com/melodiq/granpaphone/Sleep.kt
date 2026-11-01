package com.melodiq.granpaphone

import android.content.Context

/**
 * Режим «отбой»: помощник не здоровается при включении экрана и не слушает команды,
 * но звонки и напоминания продолжают озвучиваться. Будится словом «Алёша».
 */
object Sleep {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sleep", Context.MODE_PRIVATE)

    fun isOn(ctx: Context): Boolean = prefs(ctx).getBoolean("on", false)

    fun set(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("on", on).apply()
    }

    /** Фразы-пробуждения (в е-написании; Ears сам подберёт ё, если модель пишет через ё). */
    val WAKE_PHRASES = listOf(
        "алеша", "алеша привет", "привет алеша", "алешенька", "алешка", "эй алеша", "алексей"
    )
    val WAKE_WORDS = setOf("алеша", "алешенька", "алешка", "алексей")

    val SLEEP_WORDS = setOf("отбой", "спать", "отдыхай", "отдохни", "уйди", "сверни", "свернись")
}
