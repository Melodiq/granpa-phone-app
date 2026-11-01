package com.melodiq.granpaphone

import java.util.Calendar

/**
 * Разбор фраз вида «завтра в три часа дня», «в пятницу в десять тридцать»,
 * «пятнадцатого ноября в девять утра», «через два часа».
 * Распознавание выдаёт числа словами, поэтому разбираем слова.
 */
object RuDate {

    private val units = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "два" to 2, "две" to 2, "три" to 3,
        "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9
    )
    private val teens = mapOf(
        "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14,
        "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19
    )
    private val tens = mapOf("двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50)

    private val ordinalUnits = mapOf(
        "первого" to 1, "второго" to 2, "третьего" to 3, "четвертого" to 4, "пятого" to 5,
        "шестого" to 6, "седьмого" to 7, "восьмого" to 8, "девятого" to 9
    )
    private val ordinalOther = mapOf(
        "десятого" to 10, "одиннадцатого" to 11, "двенадцатого" to 12, "тринадцатого" to 13,
        "четырнадцатого" to 14, "пятнадцатого" to 15, "шестнадцатого" to 16, "семнадцатого" to 17,
        "восемнадцатого" to 18, "девятнадцатого" to 19, "двадцатого" to 20, "тридцатого" to 30
    )

    private val weekdays = mapOf(
        "понедельник" to Calendar.MONDAY, "вторник" to Calendar.TUESDAY,
        "среда" to Calendar.WEDNESDAY, "среду" to Calendar.WEDNESDAY,
        "четверг" to Calendar.THURSDAY,
        "пятница" to Calendar.FRIDAY, "пятницу" to Calendar.FRIDAY,
        "суббота" to Calendar.SATURDAY, "субботу" to Calendar.SATURDAY,
        "воскресенье" to Calendar.SUNDAY
    )

    private val hourWords = setOf("час", "часа", "часов")
    private val minuteWords = setOf("минута", "минуту", "минуты", "минут")
    private val dayWords = setOf("день", "дня", "дней")

    /** Число из слов, начиная с позиции i: (значение, сколько слов занято). */
    fun number(t: List<String>, i: Int): Pair<Int, Int>? {
        val w = t.getOrNull(i) ?: return null
        w.toIntOrNull()?.let { return it to 1 }
        units[w]?.let { return it to 1 }
        teens[w]?.let { return it to 1 }
        tens[w]?.let { tv ->
            val u = t.getOrNull(i + 1)?.let { units[it] }
            return if (u != null && u > 0) (tv + u) to 2 else tv to 1
        }
        return null
    }

    /** Порядковое «пятнадцатого», «двадцать первого». */
    private fun ordinal(t: List<String>, i: Int): Pair<Int, Int>? {
        val w = t.getOrNull(i) ?: return null
        ordinalUnits[w]?.let { return it to 1 }
        ordinalOther[w]?.let { return it to 1 }
        if (w == "двадцать" || w == "тридцать") {
            val u = t.getOrNull(i + 1)?.let { ordinalUnits[it] } ?: return null
            return ((if (w == "двадцать") 20 else 30) + u) to 2
        }
        return null
    }

    private fun monthAt(t: List<String>, i: Int): Int? {
        val w = t.getOrNull(i) ?: return null
        val idx = Ru.monthsGen.indexOf(w)
        return if (idx >= 0) idx else null
    }

    /** Возвращает время в миллисекундах или null, если не понятно (нет времени суток и т.п.). */
    fun parse(text: String, now: Calendar = Calendar.getInstance()): Long? {
        val t = Ru.words(text)
        var dayOffset: Int? = null
        var weekday: Int? = null
        var dateDay: Int? = null
        var dateMonth: Int? = null
        var hour: Int? = null
        var minute: Int? = null
        var period: String? = null
        var relMinutes: Int? = null

        var i = 0
        while (i < t.size) {
            val w = t[i]
            when {
                w == "сегодня" -> dayOffset = 0
                w == "завтра" -> dayOffset = 1
                w == "послезавтра" -> dayOffset = 2
                w in weekdays -> weekday = weekdays[w]
                w == "утра" || w == "утром" -> period = "утро"
                w == "дня" && hour != null -> period = "день"
                w == "днем" -> period = "день"
                w == "вечера" || w == "вечером" -> period = "вечер"
                w == "ночи" || w == "ночью" -> period = "ночь"
                w == "полдень" -> { hour = 12; minute = 0 }
                w == "полночь" -> { hour = 0; minute = 0 }
                w == "через" -> {
                    var n = 1
                    var j = i + 1
                    val num = number(t, j)
                    if (num != null) { n = num.first; j += num.second }
                    if (t.getOrNull(j) == "пол") { j++ } // «через полчаса» — редкость, игнор
                    val unit = t.getOrNull(j) ?: ""
                    when (unit) {
                        in hourWords -> relMinutes = (relMinutes ?: 0) + n * 60
                        in minuteWords -> relMinutes = (relMinutes ?: 0) + n
                        in dayWords -> dayOffset = n
                        "неделю" -> dayOffset = 7
                        "полчаса" -> relMinutes = (relMinutes ?: 0) + 30
                    }
                    i = j
                }
                ordinal(t, i) != null -> {
                    val o = ordinal(t, i)!!
                    dateDay = o.first
                    i += o.second - 1
                    monthAt(t, i + 1)?.let { dateMonth = it; i++ }
                }
                w == "час" && hour == null -> { hour = 1 } // «в час дня»
                w in hourWords || w in minuteWords || w in Ru.monthsGen -> {}
                number(t, i) != null -> {
                    val n = number(t, i)!!
                    val afterNum = i + n.second
                    if (monthAt(t, afterNum) != null) {
                        // «пятнадцать ноября»
                        dateDay = n.first
                        dateMonth = monthAt(t, afterNum)
                        i = afterNum
                    } else if (hour == null) {
                        hour = n.first
                        i = afterNum - 1
                    } else if (minute == null) {
                        minute = n.first
                        i = afterNum - 1
                    } else {
                        i = afterNum - 1
                    }
                }
            }
            i++
        }

        if (relMinutes != null) {
            val c = now.clone() as Calendar
            c.add(Calendar.MINUTE, relMinutes)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        var h = hour ?: return null
        val m = minute ?: 0
        when (period) {
            "день" -> if (h in 1..11) h += 12
            "вечер" -> if (h in 1..11) h += 12
            "ночь" -> if (h == 12) h = 0
            "утро" -> if (h == 12) h = 0
            null -> if (h in 1..6) h += 12 // «в три» почти всегда значит 15:00; переспросим
        }
        if (h !in 0..23 || m !in 0..59) return null

        val c = now.clone() as Calendar
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)

        when {
            dateDay != null -> {
                val d = dateDay!!
                if (d !in 1..31) return null
                val explicitMonth = dateMonth != null
                if (explicitMonth) c.set(Calendar.MONTH, dateMonth!!)
                c.set(Calendar.DAY_OF_MONTH, minOf(d, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                if (c.before(now)) {
                    if (explicitMonth) c.add(Calendar.YEAR, 1) else c.add(Calendar.MONTH, 1)
                }
                return c.timeInMillis
            }
            weekday != null -> {
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                var guard = 0
                while ((c.get(Calendar.DAY_OF_WEEK) != weekday || c.before(now)) && guard < 8) {
                    c.add(Calendar.DAY_OF_MONTH, 1)
                    guard++
                }
                return c.timeInMillis
            }
            dayOffset != null -> {
                c.add(Calendar.DAY_OF_MONTH, dayOffset!!)
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                // «сегодня в девять», сказанное вечером, — это 21:00
                if (c.before(now) && period == null && h < 12) c.add(Calendar.HOUR_OF_DAY, 12)
                return if (c.before(now)) null else c.timeInMillis
            }
            else -> {
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                if (c.before(now)) c.add(Calendar.DAY_OF_MONTH, 1)
                return c.timeInMillis
            }
        }
    }

    /** «сегодня в пятнадцать часов», «завтра в девять часов тридцать минут», «в пятницу…», «пятнадцатое ноября…». */
    fun describe(time: Long, now: Calendar = Calendar.getInstance()): String {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val days = daysBetween(now, c)
        val day = when {
            days == 0 -> "сегодня"
            days == 1 -> "завтра"
            days == 2 -> "послезавтра"
            days in 3..6 -> Ru.weekdayAt(c.get(Calendar.DAY_OF_WEEK))
            else -> Ru.dayMonth(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH))
        }
        return day + " в " + Ru.clockAt(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun daysBetween(a: Calendar, b: Calendar): Int {
        val x = a.clone() as Calendar
        val y = b.clone() as Calendar
        for (c in listOf(x, y)) {
            c.set(Calendar.HOUR_OF_DAY, 12)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
        }
        return Math.round((y.timeInMillis - x.timeInMillis) / 86_400_000.0).toInt()
    }
}
