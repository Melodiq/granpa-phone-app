package com.melodiq.granpaphone

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** Погода сейчас и на 1–2 часа вперёд (Open-Meteo, бесплатно, без ключа). Нужен интернет. */
object Weather {

    private val main = Handler(Looper.getMainLooper())

    /** Последняя ошибка погоды — показывается на экране настройки, чтобы понять причину. */
    @Volatile var lastError: String? = null

    private fun describeError(e: Exception): String = e.javaClass.simpleName + (e.message?.let { " — " + it.take(120) } ?: "")

    private fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            true
        }
    }

    /** GET с понятной ошибкой при не-200 ответе. */
    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "PomoshnikAlesha/1.0 github.com/Melodiq/granpa-phone-app")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200) ?: ""
                throw java.io.IOException("HTTP $code $err")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun get(ctx: Context, cb: (String) -> Unit) {
        locate(ctx) { loc ->
            if (loc == null) {
                cb("Не могу определить, где вы находитесь. Попросите родных включить геолокацию.")
                return@locate
            }
            Thread {
                val text = try {
                    fetch(loc.first, loc.second)
                } catch (e1: Exception) {
                    lastError = "open-meteo: " + describeError(e1)
                    try {
                        fetchMetNo(loc.first, loc.second)
                    } catch (e2: Exception) {
                        lastError += "; met.no: " + describeError(e2)
                        if (online(ctx)) "Сервер погоды сейчас не отвечает. Попробуйте позже."
                        else "Нет интернета, погоду узнать не могу."
                    }
                }
                main.post { cb(text) }
            }.start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun locate(ctx: Context, cb: (Pair<Double, Double>?) -> Unit) {
        val prefs = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)
        val lm = ctx.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val last: Location? = providers.mapNotNull { p ->
            try { lm?.getLastKnownLocation(p) } catch (e: Exception) { null }
        }.maxByOrNull { it.time }

        fun remember(l: Location): Pair<Double, Double> {
            prefs.edit().putString("lat", l.latitude.toString()).putString("lon", l.longitude.toString()).apply()
            return l.latitude to l.longitude
        }

        val fresh = last != null && System.currentTimeMillis() - last.time < 3 * 3_600_000L
        if (last != null && fresh) {
            cb(remember(last))
            return
        }
        val cached = prefs.getString("lat", null)?.toDoubleOrNull()?.let { lat ->
            prefs.getString("lon", null)?.toDoubleOrNull()?.let { lon -> lat to lon }
        } ?: last?.let { it.latitude to it.longitude }

        // Попробовать получить свежие координаты, но не дольше 6 секунд.
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { p -> try { lm?.isProviderEnabled(p) == true } catch (e: Exception) { false } }
        if (lm == null || provider == null) {
            cb(cached)
            return
        }
        var done = false
        lateinit var listener: LocationListener
        val timeout = Runnable {
            if (!done) {
                done = true
                try { lm.removeUpdates(listener) } catch (_: Exception) {}
                cb(cached)
            }
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (done) return
                done = true
                main.removeCallbacks(timeout)
                try { lm.removeUpdates(this) } catch (_: Exception) {}
                cb(remember(location))
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            main.postDelayed(timeout, 6000)
        } catch (e: Exception) {
            done = true
            cb(cached)
        }
    }

    private fun fetch(lat: Double, lon: Double): String {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability" +
                "&forecast_hours=4&timezone=auto&wind_speed_unit=ms",
            lat, lon
        )
        val j = JSONObject(httpGet(url))
        val cur = j.getJSONObject("current")
        val t0 = cur.getDouble("temperature_2m").roundToInt()
        val c0 = cur.getInt("weather_code")
        val wind = cur.optDouble("wind_speed_10m", 0.0).roundToInt()

        val sb = StringBuilder()
        sb.append("Сейчас ").append(temp(t0)).append(", ").append(describe(c0))
        if (wind >= 1) sb.append(", ветер ").append(Ru.num(wind)).append(" ").append(Ru.plural(wind, "метр", "метра", "метров")).append(" в секунду")
        sb.append(". ")

        val hourly = j.getJSONObject("hourly")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val probs = hourly.optJSONArray("precipitation_probability")
        // hourly[0] — текущий час, [1] — через час, [2] — через два
        val labels = listOf("Через час", "Через два часа")
        val parts = ArrayList<String>()
        for (k in 1..2) {
            if (k >= temps.length()) break
            val t = temps.getDouble(k).roundToInt()
            val c = codes.getInt(k)
            var s = labels[k - 1] + ": " + temp(t) + ", " + describe(c)
            val p = probs?.optInt(k, -1) ?: -1
            if (p >= 40) s += ", вероятность осадков " + Ru.num(p) + " " + Ru.plural(p, "процент", "процента", "процентов")
            parts.add(s)
        }
        val sameAsNow = parts.size == 2 && (1..2).all {
            codes.getInt(it) == c0 && Math.abs(temps.getDouble(it).roundToInt() - t0) <= 1
        }
        if (sameAsNow) sb.append("В ближайшие два часа без изменений.") else sb.append(parts.joinToString(". ")).append(".")
        lastError = null
        return sb.toString()
    }

    /** Запасной источник — норвежский метеоинститут (тоже бесплатно, без ключа). */
    private fun fetchMetNo(lat: Double, lon: Double): String {
        val url = String.format(
            Locale.US,
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", lat, lon
        )
        val series = JSONObject(httpGet(url)).getJSONObject("properties").getJSONArray("timeseries")
        fun at(k: Int): Triple<Int, String, Int>? {
            if (k >= series.length()) return null
            val data = series.getJSONObject(k).getJSONObject("data")
            val det = data.getJSONObject("instant").getJSONObject("details")
            val t = det.getDouble("air_temperature").roundToInt()
            val wind = det.optDouble("wind_speed", 0.0).roundToInt()
            val sym = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code") ?: ""
            return Triple(t, describeSymbol(sym), wind)
        }
        val now = at(0) ?: throw java.io.IOException("пустой прогноз")
        val sb = StringBuilder("Сейчас ").append(temp(now.first)).append(", ").append(now.second)
        if (now.third >= 1) sb.append(", ветер ").append(Ru.num(now.third)).append(" ").append(Ru.plural(now.third, "метр", "метра", "метров")).append(" в секунду")
        sb.append(". ")
        val labels = listOf("Через час", "Через два часа")
        val parts = (1..2).mapNotNull { k -> at(k)?.let { labels[k - 1] + ": " + temp(it.first) + ", " + it.second } }
        sb.append(parts.joinToString(". ")).append(".")
        return sb.toString()
    }

    private fun describeSymbol(code: String): String {
        val c = code.substringBefore('_')
        return when {
            c.isEmpty() -> "без осадков"
            c == "clearsky" -> "ясно"
            c == "fair" -> "преимущественно ясно"
            c == "partlycloudy" -> "переменная облачность"
            c == "cloudy" -> "пасмурно"
            c == "fog" -> "туман"
            "thunder" in c -> "гроза"
            "sleet" in c -> if (c.startsWith("light")) "небольшой мокрый снег" else "мокрый снег"
            "snow" in c -> when {
                c.startsWith("light") -> "небольшой снег"
                c.startsWith("heavy") -> "сильный снег"
                else -> "снег"
            }
            "rain" in c -> when {
                c.startsWith("light") -> if ("showers" in c) "небольшой ливень" else "небольшой дождь"
                c.startsWith("heavy") -> if ("showers" in c) "сильный ливень" else "сильный дождь"
                else -> if ("showers" in c) "ливень" else "дождь"
            }
            else -> "без осадков"
        }
    }

    private fun temp(t: Int): String = when {
        t == 0 -> "ноль градусов"
        t > 0 -> "плюс " + Ru.num(t) + " " + Ru.plural(t, "градус", "градуса", "градусов")
        else -> "минус " + Ru.num(-t) + " " + Ru.plural(-t, "градус", "градуса", "градусов")
    }

    /** Коды погоды WMO. */
    fun describe(code: Int): String = when (code) {
        0 -> "ясно"
        1 -> "преимущественно ясно"
        2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        51, 53, 55 -> "морось"
        56, 57 -> "ледяная морось"
        61 -> "небольшой дождь"
        63 -> "дождь"
        65 -> "сильный дождь"
        66, 67 -> "ледяной дождь, гололёд"
        71 -> "небольшой снег"
        73 -> "снег"
        75 -> "сильный снег"
        77 -> "снежная крупа"
        80 -> "небольшой ливень"
        81 -> "ливень"
        82 -> "сильный ливень"
        85 -> "небольшой снегопад"
        86 -> "сильный снегопад"
        95 -> "гроза"
        96, 99 -> "гроза с градом"
        else -> "без осадков"
    }
}
