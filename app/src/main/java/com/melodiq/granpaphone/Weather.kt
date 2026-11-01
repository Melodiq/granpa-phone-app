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

data class WeatherHour(val temp: Int, val code: Int, val prob: Int)

data class WeatherNow(
    val temp: Int,
    val code: Int,
    val wind: Int,
    /** Через час и через два. */
    val next: List<WeatherHour>
) {
    /** В ближайшие часы почти так же, как сейчас. */
    val steady: Boolean
        get() = next.size == 2 && next.all { it.code == code && Math.abs(it.temp - temp) <= 1 && it.prob < 40 }
}

/** offset: 0 — сегодня, 1 — завтра, 2 — послезавтра. */
data class WeatherDay(val offset: Int, val min: Int, val max: Int, val code: Int, val prob: Int, val wind: Int)

enum class WeatherError { NO_LOCATION, NO_INTERNET, SERVER }

class WeatherReport(val now: WeatherNow?, val days: List<WeatherDay>, val error: WeatherError?)

/** Погода только из Open-Meteo (бесплатно, без ключа): сейчас, 1–2 часа и на 3 дня. Нужен интернет. */
object Weather {

    private val main = Handler(Looper.getMainLooper())

    /** Последняя ошибка — показывается на экране настройки, чтобы понять причину. */
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

    fun get(ctx: Context, cb: (WeatherReport) -> Unit) {
        locate(ctx) { loc ->
            if (loc == null) {
                cb(WeatherReport(null, emptyList(), WeatherError.NO_LOCATION))
                return@locate
            }
            Thread {
                val report = try {
                    fetch(loc.first, loc.second).also { lastError = null }
                } catch (e: Exception) {
                    lastError = "open-meteo: " + describeError(e)
                    WeatherReport(null, emptyList(), if (online(ctx)) WeatherError.SERVER else WeatherError.NO_INTERNET)
                }
                main.post { cb(report) }
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

    private fun fetch(lat: Double, lon: Double): WeatherReport {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max" +
                "&forecast_hours=4&forecast_days=3&timezone=auto&wind_speed_unit=ms",
            lat, lon
        )
        val j = JSONObject(httpGet(url))

        val cur = j.getJSONObject("current")
        val hourly = j.getJSONObject("hourly")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val probs = hourly.optJSONArray("precipitation_probability")
        // hourly[0] — текущий час, [1] — через час, [2] — через два
        val next = (1..2).filter { it < temps.length() }.map { k ->
            WeatherHour(temps.getDouble(k).roundToInt(), codes.getInt(k), probs?.optInt(k, 0) ?: 0)
        }
        val now = WeatherNow(
            cur.getDouble("temperature_2m").roundToInt(),
            cur.getInt("weather_code"),
            cur.optDouble("wind_speed_10m", 0.0).roundToInt(),
            next
        )

        val days = ArrayList<WeatherDay>()
        j.optJSONObject("daily")?.let { d ->
            val dc = d.getJSONArray("weather_code")
            val mx = d.getJSONArray("temperature_2m_max")
            val mn = d.getJSONArray("temperature_2m_min")
            val pp = d.optJSONArray("precipitation_probability_max")
            val ww = d.optJSONArray("wind_speed_10m_max")
            for (k in 0 until dc.length()) {
                days.add(
                    WeatherDay(
                        k,
                        mn.getDouble(k).roundToInt(),
                        mx.getDouble(k).roundToInt(),
                        dc.getInt(k),
                        pp?.optInt(k, 0) ?: 0,
                        ww?.optDouble(k, 0.0)?.roundToInt() ?: 0
                    )
                )
            }
        }
        return WeatherReport(now, days, null)
    }
}
