package com.melodiq.granpaphone

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * Напоминание звучит в своё время, а потом повторяется каждые 5 минут,
 * пока дед не включит и не разблокирует экран.
 */
object Reminders {

    private const val REPEAT_MS = 5 * 60_000L

    /** С 00:00 до 06:00 о делах не напоминаем совсем. */
    fun isNight(): Boolean = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 6

    /** Ближайшие 06:00 — с этого времени снова можно повторять неуслышанные напоминания. */
    private fun morning(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 6); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun onFire(ctx: Context, id: Int) {
        if (Diary.get(ctx, id) == null) return
        // Ночное напоминание просто пропускаем: впереди будут следующие (за 3 часа, за час).
        if (isNight()) return
        Diary.addPending(ctx, id)
        if (userPresent(ctx)) {
            speakPending(ctx, clear = true)
        } else {
            speakPending(ctx, clear = false)
            scheduleRepeat(ctx)
        }
    }

    fun onRepeat(ctx: Context) {
        if (Diary.pending(ctx).isEmpty()) return
        if (isNight()) {
            Diary.setAlarm(ctx, morning(), repeatIntent(ctx))
            return
        }
        if (userPresent(ctx)) {
            speakPending(ctx, clear = true)
        } else {
            speakPending(ctx, clear = false)
            scheduleRepeat(ctx)
        }
    }

    /** Экран разблокировали. Если главный экран сам не открылся — озвучить здесь. */
    fun onUserPresent(ctx: Context) {
        if (isNight()) return
        cancelRepeat(ctx)
        if (!App.activityVisible && Diary.pending(ctx).isNotEmpty()) speakPending(ctx, clear = true)
    }

    /** Забрать текст напоминаний для приветствия (и считать их услышанными). */
    fun take(ctx: Context): String? {
        if (isNight()) return null
        val p = Diary.pending(ctx)
        Diary.clearPending(ctx)
        cancelRepeat(ctx)
        return if (p.isEmpty()) null else text(p)
    }

    private fun text(notes: List<Note>): String = Phrases.of(App.instance).reminder(notes)

    private fun speakPending(ctx: Context, clear: Boolean) {
        val p = Diary.pending(ctx)
        if (p.isEmpty()) return
        App.instance.speaker.say(text(p), flush = false)
        if (clear) {
            Diary.clearPending(ctx)
            cancelRepeat(ctx)
        }
    }

    private fun repeatIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_REPEAT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun scheduleRepeat(ctx: Context) {
        val next = System.currentTimeMillis() + REPEAT_MS
        val at = if (isNight() || java.util.Calendar.getInstance().apply { timeInMillis = next }
                .get(java.util.Calendar.HOUR_OF_DAY) < 6) maxOf(next, morning()) else next
        Diary.setAlarm(ctx, at, repeatIntent(ctx))
    }

    fun cancelRepeat(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(repeatIntent(ctx))
    }

    fun userPresent(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val km = ctx.getSystemService(KeyguardManager::class.java)
        return (pm?.isInteractive == true) && (km?.isKeyguardLocked != true)
    }
}
