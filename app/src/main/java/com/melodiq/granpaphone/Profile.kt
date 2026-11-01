package com.melodiq.granpaphone

import android.content.Context

/** Как обращаться к пользователю и насколько чутко слушать слово «Алёша». */
object Profile {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("profile", Context.MODE_PRIVATE)

    fun name(ctx: Context): String? = prefs(ctx).getString("name", null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setName(ctx: Context, name: String) {
        prefs(ctx).edit().putString("name", name.trim()).apply()
    }

    /** 0 — высокая чувствительность, 1 — средняя, 2 — низкая. */
    fun sensitivity(ctx: Context): Int = prefs(ctx).getInt("sens", 0)

    fun setSensitivity(ctx: Context, v: Int) {
        prefs(ctx).edit().putInt("sens", v.coerceIn(0, 2)).apply()
    }

    /** Порог уверенности и сколько непонятой речи вокруг допускается. */
    fun wakeThreshold(ctx: Context): Pair<Double, Int> = when (sensitivity(ctx)) {
        0 -> 0.40 to 2
        1 -> 0.60 to 1
        else -> 0.78 to 1
    }

    /** Что услышали последним (для настройки порога). */
    @Volatile var lastWake: String? = null

    /** Почему последний раз остановилось чтение экрана (для диагностики). */
    @Volatile var lastReadStop: String? = null
}
