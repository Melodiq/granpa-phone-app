package com.melodiq.granpaphone

import android.content.Context

/**
 * Режим «отбой»: помощник не здоровается при включении экрана и не слушает команды,
 * но звонки и напоминания продолжают озвучиваться. Будится словом «Алёша».
 */
object Sleep {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sleep", Context.MODE_PRIVATE)

    /** Отбой включён. Отбой на время сам заканчивается, когда время вышло. */
    fun isOn(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (!p.getBoolean("on", false)) return false
        val until = p.getLong("until", 0L)
        if (until > 0 && System.currentTimeMillis() >= until) {
            p.edit().putBoolean("on", false).putLong("until", 0L).apply()
            return false
        }
        return true
    }

    /** minutes — отбой на время (30, 60…); null — пока не позовут. */
    fun set(ctx: Context, on: Boolean, minutes: Int? = null) {
        val until = if (on && minutes != null) System.currentTimeMillis() + minutes * 60_000L else 0L
        prefs(ctx).edit().putBoolean("on", on).putLong("until", until).apply()
        val pi = endIntent(ctx)
        val am = ctx.getSystemService(android.app.AlarmManager::class.java)
        if (until > 0) Diary.setAlarm(ctx, until, pi) else am?.cancel(pi)
    }

    /** Время отбоя вышло (будильник): снять отбой, если он был на время. */
    fun endTimed(ctx: Context) {
        if (prefs(ctx).getLong("until", 0L) > 0) prefs(ctx).edit().putBoolean("on", false).putLong("until", 0L).apply()
    }

    private fun endIntent(ctx: Context): android.app.PendingIntent =
        android.app.PendingIntent.getBroadcast(
            ctx, 2,
            android.content.Intent(ctx, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SLEEP_END),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

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
