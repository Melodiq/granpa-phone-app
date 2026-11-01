package com.melodiq.granpaphone

import android.content.Intent
import android.net.Uri
import java.util.Calendar

/** Голосовой диалог: команды, звонки, время, погода, ежедневник. */
class Assistant(private val act: MainActivity) {

    private val sp get() = App.instance.speaker
    private val ears get() = App.instance.ears

    private var people: List<Person> = emptyList()
    private var grammar: List<String>? = null
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
                say("Распознавание речи не загрузилось. Попросите родных переустановить приложение.")
            } else {
                say("Секунду, я ещё загружаюсь.") {
                    ears.whenReady { if (s == session) listen(grammar, timeoutMs, cb) }
                }
            }
            return
        }
        sp.beepListen()
        act.setState(LampState.LISTEN, "Слушаю…")
        ears.listen(grammar, timeoutMs) { text ->
            if (s != session) return@listen
            if (text != null) {
                sp.beepHeard()
                act.setState(LampState.HEARD, "«$text»", "Услышал")
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
        val text = "Здравствуйте. " + (rem?.let { "$it " } ?: "") + "Слушаю."
        if (rem != null) lastAnswer = rem
        say(text) { listenCommand(0) }
    }

    /** Разбудили словом «Алёша» или касанием во сне. */
    fun wakeGreet() {
        stopAll()
        val rem = Reminders.take(act)
        if (rem != null) lastAnswer = rem
        say("Да, слушаю." + (rem?.let { " $it" } ?: "")) { listenCommand(0) }
    }

    /** Нажали на экран: сразу слушаем, без лишних слов. */
    fun onTap() {
        stopAll()
        listenCommand(0)
    }

    private fun listenCommand(attempt: Int) {
        listen(commandGrammar(), 8000) { text ->
            if (text == null) {
                if (attempt == 0) say("Не расслышал. Повторите.") { listenCommand(1) }
                else say("Коснитесь экрана, когда захотите что-то сказать.")
            } else {
                handle(text, attempt)
            }
        }
    }

    // ---------- грамматика: список фраз, которые понимаем ----------

    private val basePhrases = listOf(
        "время", "который час", "сколько времени", "какое время", "скажи время",
        "погода", "какая погода", "какая сейчас погода", "погоду", "что с погодой", "что на улице",
        "запиши", "записать", "напомни", "запись", "ежедневник", "сделай запись", "новая запись",
        "что у меня сегодня", "что у меня завтра", "что сегодня", "что завтра", "мои записи", "какие дела", "мои дела",
        "удали запись", "удалить запись", "удали",
        "какое сегодня число", "какой сегодня день", "какое число", "дата",
        "повтори", "еще раз", "помощь", "что ты умеешь", "отмена", "стоп", "нет", "да",
        "отбой", "спать", "отдыхай", "отдохни", "иди спать",
        "позвонить", "позвони", "набери", "звонок"
    )

    private val callVerbs = listOf("позвони", "позвонить", "набери", "звони", "вызови", "позвоните")

    private fun commandGrammar(): List<String> {
        grammar?.let { return it }
        val out = LinkedHashSet<String>()
        fun addIfKnown(p: String) {
            ears.fixPhrase(p)?.let { out.add(it) }
        }
        basePhrases.forEach { addIfKnown(it) }
        for (p in people) {
            val forms = p.forms + p.nameWords.joinToString(" ")
            for (f in forms) {
                if (f.isBlank()) continue
                addIfKnown(f)
                callVerbs.forEach { v -> addIfKnown("$v $f") }
            }
        }
        val g = out.toList()
        grammar = g
        return g
    }

    // ---------- разбор команды ----------

    private fun has(words: List<String>, vararg keys: String) = keys.any { it in words }

    private fun handle(text: String, attempt: Int) {
        val w = Ru.words(text)
        when {
            w.any { it in Sleep.SLEEP_WORDS } -> say("Отбой. Чтобы позвать меня, скажите: Алёша.") { act.goToSleep() }
            has(w, "отмена", "стоп") -> say("Хорошо.")
            has(w, "повтори") || (has(w, "еще") && has(w, "раз")) -> say(lastAnswer ?: "Пока нечего повторять.")
            has(w, "помощь", "умеешь") -> answer(HELP)
            has(w, "удали", "удалить") -> deleteFlow(Diary.all(act), 0)
            has(w, "запиши", "записать", "напомни", "ежедневник") ||
                (has(w, "запись") && !has(w, "удали", "удалить")) -> noteWhat(0)
            has(w, "записи", "дела") -> listNotes(null)
            has(w, "что") && has(w, "сегодня") -> listNotes(0)
            has(w, "что") && has(w, "завтра") -> listNotes(1)
            has(w, "погода", "погоду", "погодой", "улице") -> weather()
            has(w, "число", "дата") || (has(w, "день") && has(w, "какой")) -> answer(dateText())
            has(w, "время", "времени", "час") -> answer(timeText())
            else -> {
                val cands = Contacts.match(people, text)
                when {
                    cands.isNotEmpty() -> confirmCall(cands, 0)
                    has(w, *callVerbs.toTypedArray(), "звонок") -> say("Кому позвонить?") { listenCommand(0) }
                    attempt == 0 -> say("Не понял. Скажите, например: позвони дочери. Или: время. Или: погода.") { listenCommand(1) }
                    else -> say("Не понял. Скажите «помощь», чтобы услышать, что я умею.")
                }
            }
        }
    }

    // ---------- звонки ----------

    private fun confirmCall(cands: List<Person>, i: Int) {
        if (i >= cands.size) {
            say("Хорошо. Кому позвонить?") { listenCommand(0) }
            return
        }
        val p = cands[i]
        say("Позвонить: ${p.name}? Скажите да или нет.") {
            askYesNo { yes ->
                when (yes) {
                    true -> call(p)
                    false -> confirmCall(cands, i + 1)
                    null -> say("Не расслышал. Не звоню.")
                }
            }
        }
    }

    private fun call(p: Person) {
        say("Звоню: ${p.name}") {
            try {
                act.startActivity(
                    Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(p.number)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                say("Не получилось позвонить. Попросите родных проверить разрешения.")
            }
        }
    }

    private val yesNoGrammar = listOf(
        "да", "нет", "верно", "правильно", "не верно", "неверно", "неправильно", "не правильно",
        "конечно", "давай", "звони", "звоните", "отмена", "стоп", "не надо", "не"
    )

    private fun askYesNo(attempt: Int = 0, cb: (Boolean?) -> Unit) {
        listen(yesNoGrammar.mapNotNull { ears.fixPhrase(it) }, 6000) { text ->
            val w = text?.let { Ru.words(it) } ?: emptyList()
            val ans: Boolean? = when {
                w.isEmpty() -> null
                has(w, "нет", "не", "неверно", "неправильно", "отмена", "стоп") -> false
                has(w, "да", "верно", "правильно", "конечно", "давай", "звони", "звоните") -> true
                else -> null
            }
            if (ans == null && attempt == 0) say("Скажите да или нет.") { askYesNo(1, cb) }
            else cb(ans)
        }
    }

    // ---------- время, дата, погода ----------

    private fun timeText(): String {
        val c = Calendar.getInstance()
        return "Сейчас " + Ru.clock(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) + "."
    }

    private fun dateText(): String {
        val c = Calendar.getInstance()
        return "Сегодня " + Ru.weekdayName(c.get(Calendar.DAY_OF_WEEK)) + ", " +
            Ru.dayMonth(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH)) + "."
    }

    private fun weather() {
        val s = session
        act.show("Узнаю погоду…")
        sp.say("Узнаю погоду.")
        Weather.get(act) { text ->
            if (s != session) return@get
            lastAnswer = text
            act.show(text)
            sp.say(text, flush = false) { if (s == session) act.setState(LampState.IDLE) }
        }
    }

    // ---------- ежедневник ----------

    private fun noteWhat(attempt: Int) {
        val q = if (attempt == 0) "Что записать? Говорите после сигнала." else "Не расслышал. Что записать?"
        say(q) {
            listen(null, 15000) { text ->
                when {
                    text != null -> noteWhen(text, 0)
                    attempt == 0 -> noteWhat(1)
                    else -> say("Запись отменена.")
                }
            }
        }
    }

    private fun noteWhen(what: String, attempt: Int) {
        val q = when (attempt) {
            0 -> "Когда? Скажите день и время."
            else -> "Не понял время. Скажите, например: завтра в десять утра."
        }
        say(q) {
            listen(null, 10000) { text ->
                val time = text?.let { RuDate.parse(it) }
                when {
                    time != null -> noteConfirm(what, time)
                    attempt < 2 -> noteWhen(what, attempt + 1)
                    else -> say("Не получилось понять время. Запись отменена.")
                }
            }
        }
    }

    private fun noteConfirm(what: String, time: Long) {
        say("Записываю: $what, ${RuDate.describe(time)}. Верно?") {
            askYesNo { yes ->
                when (yes) {
                    true -> {
                        val note = Diary.add(act, what, time)
                        val n = Diary.upcomingReminders(note)
                        val tail = if (n == 0) "До этого меньше часа, напомнить заранее не успею."
                        else "Напомню заранее."
                        say("Записал. $tail")
                    }
                    false -> noteWhen(what, 0)
                    null -> say("Не расслышал. Запись отменена.")
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
        val dayWord = when (dayOffset) { 0 -> "сегодня"; 1 -> "завтра"; else -> "" }
        val text = when {
            list.isEmpty() && dayOffset == null -> "Записей нет."
            list.isEmpty() -> "На $dayWord записей нет."
            else -> {
                val head = if (dayOffset == null) "Ближайшие записи. " else "${dayWord.replaceFirstChar { it.uppercase() }}. "
                head + list.joinToString(". ") { Diary.describe(it) } + "."
            }
        }
        answer(text)
    }

    private fun deleteFlow(notes: List<Note>, i: Int) {
        if (notes.isEmpty()) { say("Записей нет."); return }
        if (i >= notes.size) { say("Больше записей нет. Ничего не удалено."); return }
        val n = notes[i]
        say("Удалить: ${Diary.describe(n)}? Скажите да или нет.") {
            askYesNo { yes ->
                when (yes) {
                    true -> { Diary.remove(act, n.id); say("Удалил.") }
                    false -> deleteFlow(notes, i + 1)
                    null -> say("Не расслышал. Ничего не удаляю.")
                }
            }
        }
    }

    companion object {
        const val HELP = "Я умею звонить: скажите, например, позвони дочери. " +
            "Сказать время: скажите время. Сказать дату: какое сегодня число. " +
            "Рассказать погоду: скажите погода. " +
            "Записать дело: скажите запиши. Рассказать дела: что у меня сегодня, или что у меня завтра. " +
            "Удалить запись: скажите удали запись. Повторить последний ответ: скажите повтори. " +
            "Чтобы я отдохнул, скажите: отбой. Чтобы позвать снова, скажите: Алёша. " +
            "Чтобы сказать команду, нажмите в любом месте экрана."
    }
}
