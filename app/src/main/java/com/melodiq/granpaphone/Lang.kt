package com.melodiq.granpaphone

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Язык интерфейса (экраны и надписи). Речь и команды — всегда русские. */
object Lang {
    val CODES = listOf("ru", "en", "zh")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun code(ctx: Context): String = prefs(ctx).getString("lang", "ru") ?: "ru"

    fun set(ctx: Context, code: String) {
        prefs(ctx).edit().putString("lang", code).apply()
    }

    fun isRu(ctx: Context) = code(ctx) == "ru"

    fun locale(ctx: Context): Locale = when (code(ctx)) {
        "en" -> Locale.ENGLISH
        "zh" -> Locale.SIMPLIFIED_CHINESE
        else -> Locale("ru", "RU")
    }

    /** Контекст с выбранным языком — для activity (attachBaseContext), службы и виджета. */
    fun wrap(ctx: Context): Context {
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(locale(ctx))
        return ctx.createConfigurationContext(cfg)
    }

    fun str(ctx: Context, id: Int, vararg args: Any): String = wrap(ctx).getString(id, *args)
}
