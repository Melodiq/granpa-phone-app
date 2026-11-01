package com.melodiq.granpaphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        KeeperService.start(ctx)
        when (intent.action) {
            ACTION_REMIND -> Reminders.onFire(ctx, intent.getIntExtra("id", -1))
            ACTION_REPEAT -> Reminders.onRepeat(ctx)
            // Отбой на время закончился: помощник снова здоровается при включении экрана.
            ACTION_SLEEP_END -> {
                Sleep.endTimed(ctx)
                KeeperService.wake(ctx)   // обновить уведомление
                KeeperService.start(ctx)  // и снова ждать «Алёша»
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.melodiq.granpaphone.REMIND"
        const val ACTION_REPEAT = "com.melodiq.granpaphone.REPEAT"
        const val ACTION_SLEEP_END = "com.melodiq.granpaphone.SLEEP_END"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        KeeperService.start(ctx)
        Diary.rescheduleAll(ctx)
    }
}

internal fun startFgService(ctx: Context, intent: Intent) {
    try {
        ContextCompat.startForegroundService(ctx, intent)
    } catch (e: Exception) {
        // Android может запретить запуск из фона — служба поднимется при следующем открытии приложения.
    }
}
