package com.melodiq.granpaphone

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PhrEn : Phrases {
    override val code = "en"
    override val ttsLocale: Locale = Locale.US
    override val modelAsset = "model-en"
    override val useGrammar = true

    private val notLetters = Regex("[^a-z0-9' ]")
    private val spaces = Regex("\\s+")
    override fun norm(s: String) = s.lowercase(Locale.US).replace(notLetters, " ").replace(spaces, " ").trim()
    override fun words(s: String) = norm(s).split(' ').filter { it.isNotEmpty() }
    private fun has(w: List<String>, vararg k: String) = k.any { it in w }
    private fun hasSeq(w: List<String>, a: String, b: String) = w.zipWithNext().any { it.first == a && it.second == b }

    private fun clock(c: Calendar) = SimpleDateFormat("h:mm a", Locale.US).format(c.time)

    // ---------- speech ----------
    override fun greet(reminders: String?) = "Hello. " + (reminders?.let { "$it " } ?: "") + "I'm listening."
    override fun wakeGreet(reminders: String?) = "Yes, I'm listening." + (reminders?.let { " $it" } ?: "")
    override val notHeardRepeat = "I didn't catch that. Please repeat."
    override val tapWhenReady = "Tap the screen whenever you want to say something."
    override val notUnderstoodHint = "I didn't understand. Say, for example: call my daughter. Or: time. Or: weather."
    override val notUnderstoodHelp = "I didn't understand. Say help to hear what I can do."
    override val ok = "Okay."
    override val nothingToRepeat = "There's nothing to repeat yet."
    override val help = "I can make calls: say, for example, call my daughter. " +
        "Tell the time: say time. Tell the date: say what's the date. " +
        "Tell the weather: say weather, weather tomorrow, or weather the day after tomorrow. " +
        "Take a note: say take a note. Read your plans: say what do I have today, or what do I have tomorrow. " +
        "Read everything on the screen: say read the screen. " +
        "Delete a note: say delete a note. Repeat the last answer: say repeat. " +
        "To let me rest, say: rest. To call me again, say: Alyosha. " +
        "To give a command, tap anywhere on the screen."
    override val loading = "One moment, I'm still loading."
    override val asrBroken = "Speech recognition didn't load. Ask your family to reinstall the app."
    override val sleepAnnounce = "Resting now. To call me, say: Alyosha."
    override val whomToCall = "Who should I call?"
    override fun confirmCall(name: String) = "Call $name? Say yes or no."
    override fun calling(name: String) = "Calling $name"
    override val callFailed = "I couldn't make the call. Ask your family to check the permissions."
    override val okWhomToCall = "Okay. Who should I call?"
    override val notHeardNoCall = "I didn't catch that. Not calling."
    override val sayYesNo = "Say yes or no."

    override fun timeText(c: Calendar) = "It's " + clock(c) + "."
    override fun dateText(c: Calendar) = "Today is " + SimpleDateFormat("EEEE, MMMM d", Locale.US).format(c.time) + "."

    // ---------- weather ----------
    override val weatherChecking = "Checking the weather."
    override fun weatherError(e: WeatherError) = when (e) {
        WeatherError.NO_LOCATION -> "I can't tell where you are. Ask your family to turn on location."
        WeatherError.NO_INTERNET -> "There's no internet, so I can't get the weather."
        WeatherError.SERVER -> "The weather service isn't responding. Try again later."
    }

    private fun deg(t: Int) = (if (t < 0) "minus ${-t}" else "$t") + if (Math.abs(t) == 1) " degree" else " degrees"

    fun condition(code: Int): String = when (code) {
        0 -> "clear"
        1 -> "mostly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61 -> "light rain"
        63 -> "rain"
        65 -> "heavy rain"
        66, 67 -> "freezing rain, icy roads"
        68, 69 -> "sleet"
        71 -> "light snow"
        73 -> "snow"
        75 -> "heavy snow"
        77 -> "snow grains"
        80 -> "light showers"
        81 -> "showers"
        82 -> "heavy showers"
        85 -> "light snow showers"
        86 -> "heavy snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "no precipitation"
    }

    override fun weatherNow(w: WeatherNow): String {
        val sb = StringBuilder("Right now it's ").append(deg(w.temp)).append(", ").append(condition(w.code))
        if (w.wind >= 1) sb.append(", wind ${w.wind} meters per second")
        sb.append(". ")
        if (w.steady) {
            sb.append("No change in the next two hours.")
        } else {
            val labels = listOf("In an hour", "In two hours")
            sb.append(w.next.mapIndexed { k, h ->
                labels[k] + ": " + deg(h.temp) + ", " + condition(h.code) +
                    (if (h.prob >= 40) ", ${h.prob} percent chance of precipitation" else "")
            }.joinToString(". ")).append(".")
        }
        return sb.toString()
    }

    override fun weatherDay(d: WeatherDay): String {
        val day = when (d.offset) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "The day after tomorrow" }
        val range = if (d.min == d.max) deg(d.max) else "from ${d.min} to ${deg(d.max)}"
        val sb = StringBuilder("$day: $range, ").append(condition(d.code))
        if (d.prob >= 30) sb.append(", ${d.prob} percent chance of precipitation")
        if (d.wind >= 8) sb.append(", strong wind up to ${d.wind} meters per second")
        return sb.append(".").toString()
    }

    // ---------- notes ----------
    override val noteWhat = "What should I write down? Speak after the beep."
    override val noteWhatAgain = "I didn't catch that. What should I write down?"
    override val noteCancelled = "Note cancelled."
    override val noteWhen = "When? Say the day and time."
    override val noteWhenExample = "I didn't get the time. Say, for example: tomorrow at ten a m."
    override val noteTimeFailed = "I couldn't understand the time. Note cancelled."
    override fun noteConfirm(what: String, time: Long) = "Writing down: $what, ${describeTime(time)}. Is that right?"
    override fun noteSaved(willRemind: Boolean) =
        "Saved. " + if (willRemind) "I'll remind you in advance." else "It's less than an hour away, so I can't remind you in advance."
    override val noteNotHeardCancelled = "I didn't catch that. Note cancelled."
    override fun notesList(dayOffset: Int?, notes: List<Note>): String {
        val dayWord = when (dayOffset) { 0 -> "today"; 1 -> "tomorrow"; else -> "" }
        return when {
            notes.isEmpty() && dayOffset == null -> "No notes."
            notes.isEmpty() -> "Nothing for $dayWord."
            else -> {
                val head = if (dayOffset == null) "Upcoming notes. " else "${dayWord.replaceFirstChar { it.uppercase() }}. "
                head + notes.joinToString(". ") { describeNote(it) } + "."
            }
        }
    }
    override fun deleteAsk(note: Note) = "Delete: ${describeNote(note)}? Say yes or no."
    override val deleted = "Deleted."
    override val noNotes = "No notes."
    override val noMoreNotes = "No more notes. Nothing was deleted."
    override val notHeardNoDelete = "I didn't catch that. Not deleting anything."

    override fun describeTime(time: Long): String {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val days = RuDate.daysBetween(now, c)
        val day = when {
            days == 0 -> "today"
            days == 1 -> "tomorrow"
            days == 2 -> "the day after tomorrow"
            days in 3..6 -> "on " + SimpleDateFormat("EEEE", Locale.US).format(c.time)
            else -> "on " + SimpleDateFormat("MMMM d", Locale.US).format(c.time)
        }
        return "$day at ${clock(c)}"
    }
    override fun reminder(notes: List<Note>) = "Reminder. " + notes.joinToString(". ") { describeNote(it) } + "."

    // ---------- calls ----------
    override fun incomingFirst(name: String) = "$name is calling. Say answer. Or: decline."
    override fun incomingRepeat(name: String) = "$name is calling."
    override val answerFailed = "I couldn't answer. Press the green button."
    override val rejecting = "Declining."
    override val unknownNumber = "an unknown number"
    override val strangerNumber = "an unfamiliar number"
    override fun ringing(name: String) = "$name is calling"

    override val readingScreen = "Reading the screen."
    override val nothingOnScreen = "There's no text on the screen."
    override val readerDisabled = "Screen reading is turned off. Ask your family to turn it on in the assistant's setup."
    override val readerDone = "Done."
    override val readPhrases = listOf(
        "read the screen", "read screen", "read it", "read", "what's on the screen", "read the text", "read the message",
        "alyosha read the screen", "alex read the screen", "alyosha read", "alex read"
    )
    override fun isReadScreen(text: String): Boolean {
        val w = words(text)
        return has(w, "read") || (has(w, "screen") && has(w, "what's", "what"))
    }

    override val voiceSample = "Hello. I'm Alyosha, your assistant. This is how my voice sounds."
    override fun voiceTest(c: Calendar) = "Voice test. " + timeText(c)

    // ---------- recognition ----------
    override val basePhrases = listOf(
        "time", "what time is it", "what's the time", "tell me the time",
        "weather", "what's the weather", "what's the weather like", "how's the weather",
        "weather tomorrow", "what's the weather tomorrow", "tomorrow's weather",
        "weather the day after tomorrow", "weather day after tomorrow",
        "take a note", "note", "remind me", "write down", "new note", "make a note",
        "what do i have today", "what do i have tomorrow", "my notes", "my plans", "what's today", "what's tomorrow",
        "delete a note", "delete note", "delete",
        "what's the date", "what day is it", "what's today's date", "date", "day of the week", "what day of the week is it",
        "repeat", "say again", "help", "what can you do", "cancel", "stop", "no", "yes",
        "rest", "sleep", "go to sleep", "goodnight",
        "call", "make a call", "phone",
        "read the screen", "read screen", "what's on the screen", "read"
    )
    override val callVerbs = listOf("call", "phone", "dial", "ring")

    private val synonyms = mapOf(
        "daughter" to listOf("daughter's"),
        "son" to listOf("son's"),
        "mom" to listOf("mum", "mother", "mommy", "mama"),
        "mum" to listOf("mom", "mother"),
        "mother" to listOf("mom", "mum"),
        "dad" to listOf("father", "daddy", "papa"),
        "father" to listOf("dad", "daddy"),
        "wife" to listOf("my wife"),
        "husband" to listOf("my husband"),
        "grandson" to listOf("grandchild"),
        "granddaughter" to listOf("grandchild"),
        "brother" to listOf("bro"),
        "sister" to listOf("sis"),
        "doctor" to listOf("doc")
    )
    override fun contactForms(word: String): Set<String> {
        val s = linkedSetOf(word, "$word's")
        synonyms[word]?.forEach { s.addAll(words(it)) }
        return s
    }
    override fun contactScore(p: Person, heard: String) = words(heard).count { it in p.forms && it !in callVerbs && it != "my" }

    override val yesNoPhrases = listOf("yes", "no", "yeah", "yep", "right", "correct", "sure", "okay", "go ahead", "nope", "not", "wrong", "cancel", "stop", "don't")
    override fun yesNo(text: String): Boolean? {
        val w = words(text)
        return when {
            w.isEmpty() -> null
            has(w, "no", "nope", "not", "wrong", "cancel", "stop", "don't") -> false
            has(w, "yes", "yeah", "yep", "right", "correct", "sure", "okay", "ahead") -> true
            else -> null
        }
    }

    override fun command(text: String): Cmd {
        val w = words(text)
        val weather = has(w, "weather", "outside")
        val afterTomorrow = hasSeq(w, "after", "tomorrow")
        return when {
            isReadScreen(text) -> Cmd.READ_SCREEN
            has(w, "rest", "sleep", "goodnight") -> Cmd.SLEEP
            has(w, "cancel", "stop") -> Cmd.CANCEL
            has(w, "repeat", "again") -> Cmd.REPEAT
            has(w, "help") || hasSeq(w, "can", "you") -> Cmd.HELP
            has(w, "delete", "remove") -> Cmd.DELETE
            has(w, "note", "remind", "write") && !has(w, "notes") -> Cmd.NOTE
            weather && afterTomorrow -> Cmd.WEATHER_AFTER
            weather && has(w, "tomorrow", "tomorrow's") -> Cmd.WEATHER_TOMORROW
            weather -> Cmd.WEATHER_NOW
            has(w, "notes", "plans") -> Cmd.LIST_ALL
            has(w, "have") && has(w, "today") -> Cmd.LIST_TODAY
            has(w, "have") && has(w, "tomorrow") -> Cmd.LIST_TOMORROW
            has(w, "date", "day", "today", "today's") -> Cmd.DATE
            has(w, "time") -> Cmd.TIME
            has(w, *callVerbs.toTypedArray()) -> Cmd.CALL_VERB
            else -> Cmd.NONE
        }
    }

    // ---------- time parsing ----------
    private val units = mapOf(
        "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "a" to 1, "an" to 1
    )
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50)
    private val ordUnits = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7,
        "eighth" to 8, "ninth" to 9, "tenth" to 10, "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13,
        "fourteenth" to 14, "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17, "eighteenth" to 18,
        "nineteenth" to 19, "twentieth" to 20, "thirtieth" to 30
    )
    private val months = listOf("january", "february", "march", "april", "may", "june", "july", "august",
        "september", "october", "november", "december")
    private val weekdays = mapOf(
        "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY, "sunday" to Calendar.SUNDAY
    )

    private fun number(t: List<String>, i: Int): Pair<Int, Int>? {
        val w = t.getOrNull(i) ?: return null
        w.toIntOrNull()?.let { return it to 1 }
        if (w == "a" || w == "an") return null
        units[w]?.let { return it to 1 }
        tens[w]?.let { tv ->
            val u = t.getOrNull(i + 1)?.let { units[it] }
            return if (u != null && u in 1..9) (tv + u) to 2 else tv to 1
        }
        return null
    }

    private fun ordinal(t: List<String>, i: Int): Pair<Int, Int>? {
        val w = t.getOrNull(i) ?: return null
        ordUnits[w]?.let { return it to 1 }
        if (w == "twenty" || w == "thirty") {
            val u = t.getOrNull(i + 1)?.let { ordUnits[it] } ?: return null
            if (u > 9) return null
            return ((if (w == "twenty") 20 else 30) + u) to 2
        }
        return null
    }

    override fun parseTime(text: String): Long? {
        val t = words(text.replace(".", " ").replace("o'clock", " oclock"))
        val now = Calendar.getInstance()
        var dayOffset: Int? = null
        var weekday: Int? = null
        var dateDay: Int? = null
        var dateMonth: Int? = null
        var hour: Int? = null
        var minute: Int? = null
        var pm: Boolean? = null
        var rel: Int? = null

        var i = 0
        while (i < t.size) {
            val w = t[i]
            when {
                w == "today" || w == "tonight" -> { dayOffset = 0; if (w == "tonight") pm = true }
                w == "tomorrow" -> if (t.getOrNull(i - 1) == "after") dayOffset = 2 else dayOffset = 1
                w in weekdays -> weekday = weekdays[w]
                w == "noon" || w == "midday" -> { hour = 12; minute = 0; pm = null }
                w == "midnight" -> { hour = 0; minute = 0 }
                w == "am" || (w == "a" && t.getOrNull(i + 1) == "m") -> { pm = false; if (w == "a") i++ }
                w == "pm" || (w == "p" && t.getOrNull(i + 1) == "m") -> { pm = true; if (w == "p") i++ }
                w == "morning" -> pm = false
                w == "afternoon" || w == "evening" || w == "night" -> pm = true
                w == "in" && (number(t, i + 1) != null || t.getOrNull(i + 1) == "a" || t.getOrNull(i + 1) == "an" || t.getOrNull(i + 1) == "half") -> {
                    var j = i + 1
                    var n = 1
                    if (t.getOrNull(j) == "half") { rel = (rel ?: 0) + 30; i = j + 2; continue }
                    if (t.getOrNull(j) == "a" || t.getOrNull(j) == "an") j++ else number(t, j)?.let { n = it.first; j += it.second }
                    when (t.getOrNull(j)) {
                        "hour", "hours" -> rel = (rel ?: 0) + n * 60
                        "minute", "minutes" -> rel = (rel ?: 0) + n
                        "day", "days" -> dayOffset = n
                    }
                    i = j
                }
                w in months -> {
                    dateMonth = months.indexOf(w)
                    ordinal(t, i + 1)?.let { dateDay = it.first; i += it.second }
                        ?: number(t, i + 1)?.let { dateDay = it.first; i += it.second }
                }
                ordinal(t, i) != null -> {
                    val o = ordinal(t, i)!!
                    dateDay = o.first
                    i += o.second - 1
                    if (t.getOrNull(i + 1) == "of") i++
                    t.getOrNull(i + 1)?.let { m -> if (m in months) { dateMonth = months.indexOf(m); i++ } }
                }
                number(t, i) != null -> {
                    val n = number(t, i)!!
                    if (hour == null) hour = n.first else if (minute == null) minute = n.first
                    i += n.second - 1
                }
            }
            i++
        }

        if (rel != null) {
            val c = now.clone() as Calendar
            c.add(Calendar.MINUTE, rel!!)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        var h = hour ?: return null
        val m = minute ?: 0
        when (pm) {
            true -> if (h in 1..11) h += 12
            false -> if (h == 12) h = 0
            null -> if (h in 1..6) h += 12
        }
        if (h !in 0..23 || m !in 0..59) return null
        return buildTime(now, h, m, dayOffset, weekday, dateDay, dateMonth)
    }

    // ---------- wake word, incoming calls ----------
    override val wakePhrases = listOf("alyosha", "hey alyosha", "alex", "hey alex", "hello alex", "assistant", "hey assistant")
    override fun isWake(text: String) = words(text).any { it == "alyosha" || it == "alex" || it == "assistant" }
    override val answerPhrases = listOf(
        "answer", "answer it", "answer the phone", "pick up", "pick it up", "hello", "yes", "accept", "take it",
        "decline", "reject", "hang up", "don't answer", "no", "later", "ignore"
    )
    override fun answerOrReject(text: String): Boolean? {
        val w = words(text)
        return when {
            has(w, "decline", "reject", "hang", "don't", "no", "later", "ignore") -> false
            has(w, "answer", "pick", "hello", "yes", "accept", "take") -> true
            else -> null
        }
    }
}

/** Общая сборка даты/времени из разобранных частей (для английского и китайского). */
internal fun buildTime(
    now: Calendar, h: Int, m: Int, dayOffset: Int?, weekday: Int?, dateDay: Int?, dateMonth: Int?
): Long? {
    val c = now.clone() as Calendar
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    when {
        dateDay != null -> {
            if (dateDay !in 1..31) return null
            if (dateMonth != null) c.set(Calendar.MONTH, dateMonth)
            c.set(Calendar.DAY_OF_MONTH, minOf(dateDay, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m)
            if (c.before(now)) { if (dateMonth != null) c.add(Calendar.YEAR, 1) else c.add(Calendar.MONTH, 1) }
        }
        weekday != null -> {
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m)
            var guard = 0
            while ((c.get(Calendar.DAY_OF_WEEK) != weekday || c.before(now)) && guard < 8) {
                c.add(Calendar.DAY_OF_MONTH, 1); guard++
            }
        }
        dayOffset != null -> {
            c.add(Calendar.DAY_OF_MONTH, dayOffset)
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m)
            if (c.before(now) && h < 12) c.add(Calendar.HOUR_OF_DAY, 12)
            if (c.before(now)) return null
        }
        else -> {
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m)
            if (c.before(now)) c.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    return c.timeInMillis
}
