package com.melodiq.granpaphone

import java.util.Calendar
import java.util.Locale

/** Русский текст: нормализация, числа словами, склонение по числу. */
object Ru {
    private val ruLocale = Locale("ru", "RU")
    private val notLetters = Regex("[^а-яa-z0-9 ]")
    private val spaces = Regex("\\s+")

    /** Нижний регистр, ё→е, без знаков. Поэтому «Дочь» и «дочь» — одно и то же. */
    fun norm(s: String): String =
        s.lowercase(ruLocale).replace('ё', 'е').replace(notLetters, " ").replace(spaces, " ").trim()

    fun words(s: String): List<String> = norm(s).split(' ').filter { it.isNotEmpty() }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val a = Math.abs(n) % 100
        val b = a % 10
        return when {
            a in 11..14 -> many
            b == 1 -> one
            b in 2..4 -> few
            else -> many
        }
    }

    private val unitsM = listOf("ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
    private val unitsF = listOf("ноль", "одна", "две", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
    private val teens = listOf(
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    )
    private val tens = listOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")

    /** Число словами (0..999), fem — женский род (одна, две). */
    fun num(n: Int, fem: Boolean = false): String {
        if (n < 0) return "минус " + num(-n, fem)
        if (n >= 1000) return n.toString()
        val units = if (fem) unitsF else unitsM
        if (n < 10) return units[n]
        if (n < 20) return teens[n - 10]
        if (n < 100) {
            val t = tens[n / 10]
            return if (n % 10 == 0) t else t + " " + units[n % 10]
        }
        val hundreds = listOf("", "сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот")
        val rest = n % 100
        return if (rest == 0) hundreds[n / 100] else hundreds[n / 100] + " " + num(rest, fem)
    }

    /** «пятнадцать часов тридцать минут» / «девять часов ровно». */
    fun clock(h: Int, m: Int): String {
        val hs = num(h) + " " + plural(h, "час", "часа", "часов")
        return if (m == 0) "$hs ровно" else hs + " " + num(m, true) + " " + plural(m, "минута", "минуты", "минут")
    }

    /** То же, но для «в 15 часов 30 минут» — без «ровно». */
    fun clockAt(h: Int, m: Int): String {
        val hs = num(h) + " " + plural(h, "час", "часа", "часов")
        return if (m == 0) hs else hs + " " + num(m, true) + " " + plural(m, "минута", "минуты", "минут")
    }

    val monthsGen = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    /** Calendar.DAY_OF_WEEK → именительный падеж. */
    fun weekdayName(dow: Int): String = when (dow) {
        Calendar.MONDAY -> "понедельник"
        Calendar.TUESDAY -> "вторник"
        Calendar.WEDNESDAY -> "среда"
        Calendar.THURSDAY -> "четверг"
        Calendar.FRIDAY -> "пятница"
        Calendar.SATURDAY -> "суббота"
        else -> "воскресенье"
    }

    /** «в понедельник», «во вторник», «в среду»… */
    fun weekdayAt(dow: Int): String = when (dow) {
        Calendar.MONDAY -> "в понедельник"
        Calendar.TUESDAY -> "во вторник"
        Calendar.WEDNESDAY -> "в среду"
        Calendar.THURSDAY -> "в четверг"
        Calendar.FRIDAY -> "в пятницу"
        Calendar.SATURDAY -> "в субботу"
        else -> "в воскресенье"
    }

    private val ordinalsNeuter = listOf(
        "первое", "второе", "третье", "четвертое", "пятое", "шестое", "седьмое", "восьмое", "девятое", "десятое",
        "одиннадцатое", "двенадцатое", "тринадцатое", "четырнадцатое", "пятнадцатое", "шестнадцатое",
        "семнадцатое", "восемнадцатое", "девятнадцатое", "двадцатое"
    )

    /** «первое ноября», «двадцать третье декабря». */
    fun dayMonth(day: Int, month0: Int): String {
        val d = when {
            day <= 20 -> ordinalsNeuter[day - 1]
            day < 30 -> "двадцать " + ordinalsNeuter[day - 21]
            day == 30 -> "тридцатое"
            else -> "тридцать первое"
        }
        return d + " " + monthsGen[month0]
    }
}
