package com.melodiq.granpaphone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Виджет на пол-экрана или весь экран: нажатие открывает Алёшу и сразу слушает. */
class AleshaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val tap = PendingIntent.getActivity(
            ctx, 7,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_LISTEN, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for (id in ids) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_alesha)
            views.setOnClickPendingIntent(R.id.widget_root, tap)
            views.setTextViewText(R.id.widget_name, Lang.str(ctx, R.string.widget_name))
            views.setTextViewText(R.id.widget_tap, Lang.str(ctx, R.string.widget_tap))
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        /** Перерисовать все виджеты (например, после смены языка). */
        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(android.content.ComponentName(ctx, AleshaWidget::class.java))
            if (ids.isNotEmpty()) AleshaWidget().onUpdate(ctx, m, ids)
        }
    }
}
