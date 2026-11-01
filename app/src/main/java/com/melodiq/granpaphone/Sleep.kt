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

    /** Слушать «Алёша» всегда, когда помощник не на экране (а не только в отбое). */
    fun alwaysListen(ctx: Context): Boolean = prefs(ctx).getBoolean("always", true)

    fun setAlwaysListen(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("always", on).apply()
    }

    // Счётчики для диагностики: сколько раз услышали «Алёша» и сколько раз окно открылось.
    fun heardCount(ctx: Context) = prefs(ctx).getInt("heard", 0)
    fun openedCount(ctx: Context) = prefs(ctx).getInt("opened", 0)
    fun countHeard(ctx: Context) = prefs(ctx).edit().putInt("heard", heardCount(ctx) + 1).apply()
    fun countOpened(ctx: Context) = prefs(ctx).edit().putInt("opened", openedCount(ctx) + 1).apply()
    fun resetCounts(ctx: Context) = prefs(ctx).edit().putInt("heard", 0).putInt("opened", 0).apply()
}
