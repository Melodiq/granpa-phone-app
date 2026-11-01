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

/** Погода: Open-Meteo, при сбое — MET Norway. Сейчас, 1–2 часа и на 3 дня. Нужен интернет. */
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
                } catch (e1: Exception) {
                    val err1 = "open-meteo: " + describeError(e1)
                    try {
                        fetchMetNo(loc.first, loc.second).also { lastError = "$err1 (взято из MET Norway)" }
                    } catch (e2: Exception) {
                        lastError = "$err1; met.no: " + describeError(e2)
                        WeatherReport(null, emptyList(), if (online(ctx)) WeatherError.SERVER else WeatherError.NO_INTERNET)
                    }
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

    private fun org.json.JSONArray.num(i: Int): Double? =
        if (i < 0 || i >= length() || isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }

    /**
     * Open-Meteo. Модель не задаём: «best_match» для Европы сам берёт национальные
     * модели (DWD ICON, Météo-France, MET Nordic) и ECMWF. Все поля читаем осторожно:
     * у части моделей бывают пустые значения, из-за этого раньше падал разбор.
     */
    private fun fetch(lat: Double, lon: Double): WeatherReport {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max" +
                "&forecast_days=3&timezone=auto&wind_speed_unit=ms",
            lat, lon
        )
        val j = JSONObject(httpGet(url))

        val cur = j.getJSONObject("current")
        val curTime = cur.optString("time")
        val hourly = j.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val probs = hourly.optJSONArray("precipitation_probability")
        // Индекс текущего часа: последний час, который не позже текущего времени.
        var i0 = 0
        for (k in 0 until times.length()) if (times.optString(k) <= curTime) i0 = k
        val next = (i0 + 1..i0 + 2).mapNotNull { k ->
            val t = temps.num(k) ?: return@mapNotNull null
            val c = codes.num(k) ?: return@mapNotNull null
            WeatherHour(t.roundToInt(), c.roundToInt(), probs?.num(k)?.roundToInt() ?: 0)
        }
        val now = WeatherNow(
            (cur.optDouble("temperature_2m").takeIf { !it.isNaN() } ?: temps.num(i0) ?: throw java.io.IOException("нет температуры")).roundToInt(),
            cur.optInt("weather_code", codes.num(i0)?.roundToInt() ?: 0),
            cur.optDouble("wind_speed_10m", 0.0).let { if (it.isNaN()) 0 else it.roundToInt() },
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
                val hi = mx.num(k) ?: continue
                val lo = mn.num(k) ?: continue
                days.add(WeatherDay(k, lo.roundToInt(), hi.roundToInt(), dc.num(k)?.roundToInt() ?: 0,
                    pp?.num(k)?.roundToInt() ?: 0, ww?.num(k)?.roundToInt() ?: 0))
            }
        }
        return WeatherReport(now, days, null)
    }

    // ---------- MET Norway (запасной источник, бесплатно, без ключа) ----------

    private fun fetchMetNo(lat: Double, lon: Double): WeatherReport {
        val url = String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", lat, lon)
        val series = JSONObject(httpGet(url)).getJSONObject("properties").getJSONArray("timeseries")
        if (series.length() == 0) throw java.io.IOException("пустой прогноз met.no")

        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        class Pt(val time: java.util.Calendar, val temp: Double, val wind: Double, val symbol: String)
        val pts = ArrayList<Pt>()
        for (k in 0 until series.length()) {
            val o = series.getJSONObject(k)
            val date = try { fmt.parse(o.getString("time")) } catch (e: Exception) { null } ?: continue
            val data = o.getJSONObject("data")
            val det = data.getJSONObject("instant").getJSONObject("details")
            val sym = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
                ?: data.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code") ?: ""
            pts.add(Pt(java.util.Calendar.getInstance().apply { time = date }, det.optDouble("air_temperature"),
                det.optDouble("wind_speed", 0.0), sym))
        }
        val first = pts.first()
        val now = WeatherNow(
            first.temp.roundToInt(), metCode(first.symbol), first.wind.roundToInt(),
            pts.drop(1).take(2).map { WeatherHour(it.temp.roundToInt(), metCode(it.symbol), 0) }
        )
        val today = java.util.Calendar.getInstance()
        val days = (0..2).mapNotNull { off ->
            val dayPts = pts.filter { RuDate.daysBetween(today, it.time) == off }
            if (dayPts.isEmpty()) return@mapNotNull null
            // Погоду дня берём около полудня, если есть.
            val noon = dayPts.minByOrNull { Math.abs(it.time.get(java.util.Calendar.HOUR_OF_DAY) - 13) }!!
            WeatherDay(off, dayPts.minOf { it.temp }.roundToInt(), dayPts.maxOf { it.temp }.roundToInt(),
                metCode(noon.symbol), 0, dayPts.maxOf { it.wind }.roundToInt())
        }
        return WeatherReport(now, days, null)
    }

    /** Символ MET Norway → код WMO, чтобы описывать погоду теми же словами. */
    private fun metCode(symbol: String): Int {
        val c = symbol.substringBefore('_')
        return when {
            c.isEmpty() -> 3
            c == "clearsky" -> 0
            c == "fair" -> 1
            c == "partlycloudy" -> 2
            c == "cloudy" -> 3
            c == "fog" -> 45
            "thunder" in c -> 95
            "sleet" in c -> 68
            "snow" in c -> when {
                "showers" in c -> if (c.startsWith("heavy")) 86 else 85
                c.startsWith("light") -> 71
                c.startsWith("heavy") -> 75
                else -> 73
            }
            "rain" in c -> when {
                "showers" in c -> if (c.startsWith("light")) 80 else if (c.startsWith("heavy")) 82 else 81
                c.startsWith("light") -> 61
                c.startsWith("heavy") -> 65
                else -> 63
            }
            else -> 3
        }
    }
}
