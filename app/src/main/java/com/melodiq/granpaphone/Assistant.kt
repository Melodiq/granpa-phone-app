package com.melodiq.granpaphone

import android.content.Intent
import android.net.Uri
import java.util.Calendar

/** Голосовой диалог: команды, звонки, время, погода, ежедневник. Язык — из [Phrases]. */
class Assistant(private val act: MainActivity) {

    private val sp get() = App.instance.speaker
    private val ears get() = App.instance.ears
    private val phr get() = Phrases.of(act)

    private var people: List<Person> = emptyList()
    private var grammar: List<String>? = null
    private var grammarLang = ""
    private var lastAnswer: String? = null

    /** Счётчик диалога: всё, что пришло от старого диалога после нажатия на экран, игнорируем. */
    private var session = 0

    // ---------- базовые действия ----------

    private fun say(text: String, then: (() -> Unit)? = null) {
        act.show(text)
        val s = session
        sp.say(text) {
            if (s != session) return@say
            if (then != null) then() else act.setState(LampState.IDLE)
        }
    }

    /** Ответ, который можно повторить командой «повтори». */
    private fun answer(text: String) {
        lastAnswer = text
        say(text)
    }

    private fun listen(grammar: List<String>?, timeoutMs: Int, cb: (String?) -> Unit) {
        val s = session
        val m = ears.model
        if (m == null) {
            if (ears.error != null) {
                say(phr.asrBroken)
            } else {
                say(phr.loading) {
                    ears.whenReady { if (s == session) listen(grammar, timeoutMs, cb) }
                }
            }
            return
        }
        sp.beepListen()
        act.setState(LampState.LISTEN, act.getString(R.string.status_listen))
        ears.listen(grammar, timeoutMs) { text ->
            if (s != session) return@listen
            if (text != null) {
                sp.beepHeard()
                act.setState(LampState.HEARD, "«$text»", act.getString(R.string.status_heard))
            }
            cb(text)
        }
    }

    fun stopAll() {
        session++
        ears.stop()
        sp.stop()
    }

    fun reloadContacts() {
        people = Contacts.load(act)
        grammar = null
    }

    // ---------- вход в диалог ----------

    /** Включили экран: приветствие, напоминания, слушаем. */
    fun greet() {
        stopAll()
        val rem = Reminders.take(act)
        if (rem != null) lastAnswer = rem
        say(phr.greet(rem, Profile.name(act))) { listenCommand(0) }
    }

    /** Разбудили словом «Алёша» или касанием во сне. */
    fun wakeGreet() {
        stopAll()
        val rem = Reminders.take(act)
        if (rem != null) lastAnswer = rem
        say(phr.wakeGreet(rem, Profile.name(act))) { listenCommand(0) }
    }

    /** Отбой на время без голоса (пять касаний по кругу). */
    fun sleepFor(minutes: Int) {
        stopAll()
        sp.beepHeard()
        say(phr.sleepAnnounce(minutes)) { act.goToSleep(minutes) }
    }

    /** Нажали на экран: сразу слушаем, без лишних слов. */
    fun onTap() {
        stopAll()
        listenCommand(0)
    }

    private fun listenCommand(attempt: Int) {
        listen(commandGrammar(), 8000) { text ->
            if (text == null) {
                if (attempt == 0) say(phr.notHeardRepeat) { listenCommand(1) }
                else say(phr.tapWhenReady)
            } else {
                handle(text, attempt)
            }
        }
    }

    // ---------- грамматика ----------

    private fun commandGrammar(): List<String>? {
        val p = phr
        if (!p.useGrammar) return null
        if (grammarLang == p.code) grammar?.let { return it }
        val out = LinkedHashSet<String>()
        fun addIfKnown(s: String) {
            ears.fixPhrase(s)?.let { out.add(it) }
        }
        p.basePhrases.forEach { addIfKnown(it) }
        for (person in people) {
            val forms = person.forms + person.nameWords.joinToString(" ")
            for (f in forms) {
                if (f.isBlank()) continue
                addIfKnown(f)
                p.callVerbs.forEach { v -> addIfKnown("$v $f") }
                if (p.code == "en") addIfKnown("call my $f")
            }
        }
        val g = out.toList()
        grammar = g
        grammarLang = p.code
        return g
    }

    private fun smallGrammar(list: List<String>): List<String>? {
        if (!phr.useGrammar || list.isEmpty()) return null
        return list.mapNotNull { ears.fixPhrase(it) }.ifEmpty { null }
    }

    // ---------- разбор команды ----------

    private fun handle(text: String, attempt: Int) {
        val p = phr
        when (p.command(text)) {
            Cmd.SLEEP -> {
                val minutes = p.sleepMinutes(text)
                say(p.sleepAnnounce(minutes)) { act.goToSleep(minutes) }
            }
            Cmd.CANCEL -> say(p.ok)
            Cmd.REPEAT -> say(lastAnswer ?: p.nothingToRepeat)
            Cmd.HELP -> answer(p.help)
            Cmd.DELETE -> deleteFlow(Diary.all(act), 0)
            Cmd.NOTE -> noteWhat(0)
            Cmd.LIST_ALL -> listNotes(null)
            Cmd.LIST_TODAY -> listNotes(0)
            Cmd.LIST_TOMORROW -> listNotes(1)
            Cmd.WEATHER_NOW -> weather(null)
            Cmd.WEATHER_TOMORROW -> weather(1)
            Cmd.WEATHER_AFTER -> weather(2)
            Cmd.DATE -> answer(p.dateText(Calendar.getInstance()))
            Cmd.TIME -> answer(p.timeText(Calendar.getInstance()))
            Cmd.READ_SCREEN -> {
                if (!ScreenReaderService.isOn) {
                    say(p.readerDisabled)
                } else {
                    // Помощник уходит в сторону, и читается то, что под ним.
                    act.setState(LampState.IDLE)
                    act.readScreenBehind()
                }
            }
            Cmd.CALL_VERB, Cmd.NONE -> {
                val cands = Contacts.match(people, text, p)
                when {
                    cands.isNotEmpty() -> confirmCall(cands, 0)
                    p.command(text) == Cmd.CALL_VERB -> say(p.whomToCall) { listenCommand(0) }
                    attempt == 0 -> say(p.notUnderstoodHint) { listenCommand(1) }
                    else -> say(p.notUnderstoodHelp)
                }
            }
        }
    }

    // ---------- звонки ----------

    private fun confirmCall(cands: List<Person>, i: Int) {
        if (i >= cands.size) {
            say(phr.okWhomToCall) { listenCommand(0) }
            return
        }
        val person = cands[i]
        say(phr.confirmCall(person.name)) {
            askYesNo { yes ->
                when (yes) {
                    true -> call(person)
                    false -> confirmCall(cands, i + 1)
                    null -> say(phr.notHeardNoCall)
                }
            }
        }
    }

    private fun call(person: Person) {
        say(phr.calling(person.name)) {
            try {
                act.startActivity(
                    Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(person.number)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                say(phr.callFailed)
            }
        }
    }

    private fun askYesNo(attempt: Int = 0, cb: (Boolean?) -> Unit) {
        listen(smallGrammar(phr.yesNoPhrases), 6000) { text ->
            val ans = text?.let { phr.yesNo(it) }
            if (ans == null && attempt == 0) say(phr.sayYesNo) { askYesNo(1, cb) }
            else cb(ans)
        }
    }

    // ---------- погода ----------

    /** dayOffset: null — сейчас и ближайшие часы, 1 — завтра, 2 — послезавтра. */
    private fun weather(dayOffset: Int?) {
        val s = session
        val p = phr
        act.show(p.weatherChecking)
        sp.say(p.weatherChecking)
        Weather.get(act) { report ->
            if (s != session) return@get
            val text = when {
                report.error != null -> p.weatherError(report.error)
                dayOffset == null -> report.now?.let { p.weatherNow(it) } ?: p.weatherError(WeatherError.SERVER)
                else -> report.days.firstOrNull { it.offset == dayOffset }?.let { p.weatherDay(it) }
                    ?: p.weatherError(WeatherError.SERVER)
            }
            lastAnswer = text
            act.show(text)
            sp.say(text, flush = false) { if (s == session) act.setState(LampState.IDLE) }
        }
    }

    // ---------- ежедневник ----------

    private fun noteWhat(attempt: Int) {
        say(if (attempt == 0) phr.noteWhat else phr.noteWhatAgain) {
            listen(null, 15000) { text ->
                when {
                    text != null -> noteWhen(text, 0)
                    attempt == 0 -> noteWhat(1)
                    else -> say(phr.noteCancelled)
                }
            }
        }
    }

    private fun noteWhen(what: String, attempt: Int) {
        say(if (attempt == 0) phr.noteWhen else phr.noteWhenExample) {
            listen(null, 10000) { text ->
                val time = text?.let { phr.parseTime(it) }
                when {
                    time != null -> noteConfirm(what, time)
                    attempt < 2 -> noteWhen(what, attempt + 1)
                    else -> say(phr.noteTimeFailed)
                }
            }
        }
    }

    private fun noteConfirm(what: String, time: Long) {
        say(phr.noteConfirm(what, time)) {
            askYesNo { yes ->
                when (yes) {
                    true -> {
                        val note = Diary.add(act, what, time)
                        say(phr.noteSaved(Diary.upcomingReminders(note) > 0))
                    }
                    false -> noteWhen(what, 0)
                    null -> say(phr.noteNotHeardCancelled)
                }
            }
        }
    }

    /** dayOffset: 0 — сегодня, 1 — завтра, null — ближайшие записи. */
    private fun listNotes(dayOffset: Int?) {
        val now = Calendar.getInstance()
        val all = Diary.all(act)
        val list = if (dayOffset == null) all.take(5) else all.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.time }
            RuDate.daysBetween(now, c) == dayOffset
        }
        answer(phr.notesList(dayOffset, list))
    }

    private fun deleteFlow(notes: List<Note>, i: Int) {
        if (notes.isEmpty()) { say(phr.noNotes); return }
        if (i >= notes.size) { say(phr.noMoreNotes); return }
        val n = notes[i]
        say(phr.deleteAsk(n)) {
            askYesNo { yes ->
                when (yes) {
                    true -> { Diary.remove(act, n.id); say(phr.deleted) }
                    false -> deleteFlow(notes, i + 1)
                    null -> say(phr.notHeardNoDelete)
                }
            }
        }
    }
}
