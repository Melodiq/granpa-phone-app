package com.melodiq.granpaphone

import java.util.Calendar
import java.util.Locale

object PhrRu : Phrases {
    override val code = "ru"
    override val ttsLocale: Locale = Locale("ru", "RU")
    override val modelAsset = "model-ru"
    override val useGrammar = true

    override fun norm(s: String) = Ru.norm(s)
    override fun words(s: String) = Ru.words(s)
    private fun has(w: List<String>, vararg k: String) = k.any { it in w }

    // ---------- речь ----------
    override fun greet(reminders: String?, userName: String?) =
        (if (userName != null) "Здравствуйте, $userName. " else "Здравствуйте. ") + (reminders?.let { "$it " } ?: "") + "Слушаю."
    override fun wakeGreet(reminders: String?, userName: String?): String {
        val ask = listOf("Чего изволите", "Чего пожелаете").random()
        return (if (userName != null) "$ask, $userName?" else "$ask?") + (reminders?.let { " $it" } ?: "")
    }
    override val notHeardRepeat = "Не расслышал. Повторите."
    override val tapWhenReady = "Коснитесь экрана, когда захотите что-то сказать."
    override val notUnderstoodHint = "Не понял. Скажите, например: позвони дочери. Или: время. Или: погода."
    override val notUnderstoodHelp = "Не понял. Скажите «помощь», чтобы услышать, что я умею."
    override val ok = "Хорошо."
    override val nothingToRepeat = "Пока нечего повторять."
    override val help = "Я умею звонить: скажите, например, позвони дочери. " +
        "Сказать время: скажите время. Сказать дату: какое сегодня число. " +
        "Рассказать погоду: скажите погода, погода на завтра или погода на послезавтра. " +
        "Записать дело: скажите запиши. Рассказать дела: что у меня сегодня, или что у меня завтра. " +
        "Прочитать всё, что на экране: скажите прочитай экран. Остановить чтение: скажите Алёша, стоп, или коснитесь экрана. Сбросить звонок: скажите отбой. " +
        "Удалить запись: скажите удали запись. Повторить последний ответ: скажите повтори. " +
        "Чтобы я отдохнул, скажите: отбой. Чтобы позвать снова, скажите: Алёша. " +
        "Чтобы сказать команду, нажмите в любом месте экрана."
    override val loading = "Секунду, я ещё загружаюсь."
    override val asrBroken = "Распознавание речи не загрузилось. Попросите родных переустановить приложение."
    override fun sleepAnnounce(minutes: Int?) = when (minutes) {
        null -> "Отбой. Чтобы позвать меня, скажите: Алёша."
        30 -> "Отбой на полчаса. Позвать раньше: скажите Алёша."
        60 -> "Отбой на час. Позвать раньше: скажите Алёша."
        else -> if (minutes % 60 == 0) "Отбой на ${Ru.num(minutes / 60)} ${Ru.plural(minutes / 60, "час", "часа", "часов")}. Позвать раньше: скажите Алёша."
        else "Отбой на ${Ru.num(minutes, true)} ${Ru.plural(minutes, "минуту", "минуты", "минут")}. Позвать раньше: скажите Алёша."
    }
    override fun sleepMinutes(text: String): Int? {
        val w = words(text)
        if (has(w, "полчаса", "полчасика")) return 30
        for (i in w.indices) {
            val isMin = w[i].startsWith("минут")
            val isHour = w[i] == "час" || w[i] == "часа" || w[i] == "часов" || w[i] == "часик"
            if (!isMin && !isHour) continue
            // число перед словом: «тридцать минут», «два часа»
            var n: Int? = null
            for (back in 2 downTo 1) {
                val j = i - back
                if (j >= 0) RuDate.number(w, j)?.let { if (j + it.second == i) n = it.first }
            }
            val v = n ?: 1
            return if (isMin) v else v * 60
        }
        return null
    }
    override val whomToCall = "Кому позвонить?"
    override fun confirmCall(name: String) = "Позвонить: $name? Скажите да или нет."
    override fun calling(name: String) = "Звоню: $name"
    override val callFailed = "Не получилось позвонить. Попросите родных проверить разрешения."
    override val okWhomToCall = "Хорошо. Кому позвонить?"
    override val notHeardNoCall = "Не расслышал. Не звоню."
    override val sayYesNo = "Скажите да или нет."

    override fun timeText(c: Calendar) = "Сейчас " + Ru.clock(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) + "."
    override fun dateText(c: Calendar) = "Сегодня " + Ru.weekdayName(c.get(Calendar.DAY_OF_WEEK)) + ", " +
        Ru.dayMonth(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH)) + "."

    // ---------- погода ----------
    override val weatherChecking = "Узнаю погоду."
    override fun weatherError(e: WeatherError) = when (e) {
        WeatherError.NO_LOCATION -> "Не могу определить, где вы находитесь. Попросите родных включить геолокацию."
        WeatherError.NO_INTERNET -> "Нет интернета, погоду узнать не могу."
        WeatherError.SERVER -> "Сервер погоды сейчас не отвечает. Попробуйте позже."
    }

    private fun temp(t: Int): String = when {
        t == 0 -> "ноль градусов"
        t > 0 -> "плюс " + Ru.num(t) + " " + Ru.plural(t, "градус", "градуса", "градусов")
        else -> "минус " + Ru.num(-t) + " " + Ru.plural(-t, "градус", "градуса", "градусов")
    }

    /** «от минус одного до плюс пяти градусов» — числа в родительном падеже. */
    private fun tempGen(t: Int): String {
        val n = numGen(Math.abs(t))
        return when {
            t == 0 -> "нуля"
            t > 0 -> "плюс $n"
            else -> "минус $n"
        }
    }

    private fun numGen(n: Int): String {
        val units = listOf("нуля", "одного", "двух", "трёх", "четырёх", "пяти", "шести", "семи", "восьми", "девяти")
        val teens = listOf("десяти", "одиннадцати", "двенадцати", "тринадцати", "четырнадцати", "пятнадцати",
            "шестнадцати", "семнадцати", "восемнадцати", "девятнадцати")
        val tens = mapOf(2 to "двадцати", 3 to "тридцати", 4 to "сорока", 5 to "пятидесяти")
        return when {
            n < 10 -> units[n]
            n < 20 -> teens[n - 10]
            n < 60 -> (tens[n / 10] ?: n.toString()) + (if (n % 10 == 0) "" else " " + units[n % 10])
            else -> n.toString()
        }
    }

    private fun wind(w: Int) = Ru.num(w) + " " + Ru.plural(w, "метр", "метра", "метров") + " в секунду"
    private fun prob(p: Int) = "вероятность осадков " + Ru.num(p) + " " + Ru.plural(p, "процент", "процента", "процентов")

    fun condition(code: Int): String = when (code) {
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
        68, 69 -> "мокрый снег"
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

    override fun weatherNow(w: WeatherNow): String {
        val sb = StringBuilder("Сейчас ").append(temp(w.temp)).append(", ").append(condition(w.code))
        if (w.wind >= 1) sb.append(", ветер ").append(wind(w.wind))
        sb.append(". ")
        if (w.steady) {
            sb.append("В ближайшие два часа без изменений.")
        } else {
            val labels = listOf("Через час", "Через два часа")
            sb.append(w.next.mapIndexed { k, h ->
                labels[k] + ": " + temp(h.temp) + ", " + condition(h.code) + (if (h.prob >= 40) ", " + prob(h.prob) else "")
            }.joinToString(". ")).append(".")
        }
        return sb.toString()
    }

    override fun weatherDay(d: WeatherDay): String {
        val day = when (d.offset) { 0 -> "Сегодня"; 1 -> "Завтра"; else -> "Послезавтра" }
        val range = if (d.min == d.max) temp(d.max) else "от ${tempGen(d.min)} до ${tempGen(d.max)} градусов"
        val sb = StringBuilder("$day $range, ").append(condition(d.code))
        if (d.prob >= 30) sb.append(", ").append(prob(d.prob))
        if (d.wind >= 8) sb.append(", сильный ветер до ").append(numGen(d.wind)).append(" метров в секунду")
        return sb.append(".").toString()
    }

    // ---------- ежедневник ----------
    override val noteWhat = "Что записать? Говорите после сигнала."
    override val noteWhatAgain = "Не расслышал. Что записать?"
    override val noteCancelled = "Запись отменена."
    override val noteWhen = "Когда? Скажите день и время."
    override val noteWhenExample = "Не понял время. Скажите, например: завтра в десять утра."
    override val noteTimeFailed = "Не получилось понять время. Запись отменена."
    override fun noteConfirm(what: String, time: Long) = "Записываю: $what, ${describeTime(time)}. Верно?"
    override fun noteSaved(willRemind: Boolean) =
        "Записал. " + if (willRemind) "Напомню заранее." else "До этого меньше часа, напомнить заранее не успею."
    override val noteNotHeardCancelled = "Не расслышал. Запись отменена."
    override fun notesList(dayOffset: Int?, notes: List<Note>): String {
        val dayWord = when (dayOffset) { 0 -> "сегодня"; 1 -> "завтра"; else -> "" }
        return when {
            notes.isEmpty() && dayOffset == null -> "Записей нет."
            notes.isEmpty() -> "На $dayWord записей нет."
            else -> {
                val head = if (dayOffset == null) "Ближайшие записи. " else "${dayWord.replaceFirstChar { it.uppercase() }}. "
                head + notes.joinToString(". ") { describeNote(it) } + "."
            }
        }
    }
    override fun deleteAsk(note: Note) = "Удалить: ${describeNote(note)}? Скажите да или нет."
    override val deleted = "Удалил."
    override val noNotes = "Записей нет."
    override val noMoreNotes = "Больше записей нет. Ничего не удалено."
    override val notHeardNoDelete = "Не расслышал. Ничего не удаляю."

    override fun describeTime(time: Long) = RuDate.describe(time)
    override fun reminder(notes: List<Note>) = "Напоминание. " + notes.joinToString(". ") { describeNote(it) } + "."

    // ---------- звонки ----------
    override fun incomingFirst(name: String) = "Звонит: $name. Скажите: ответить. Или: сбросить."
    override fun incomingRepeat(name: String) = "Звонит: $name."
    override val answerFailed = "Не получилось ответить сам. Нажмите зелёную кнопку."
    override val rejecting = "Сбрасываю."
    override val unknownNumber = "неизвестный номер"
    override val strangerNumber = "незнакомый номер"
    override fun ringing(name: String) = "Звонит: $name"

    override val readingScreen = "Читаю экран."
    override val nothingOnScreen = "На экране нет текста."
    override val readerDisabled = "Чтение экрана не включено. Попросите родных включить его в настройке помощника."
    override val readerDone = "Готово."
    override val readPhrases = listOf(
        "прочитай экран", "прочитай что на экране", "что на экране", "читай экран", "прочитай", "читай",
        "алеша прочитай экран", "алеша что на экране", "алеша прочитай", "прочитай текст", "прочитай сообщение"
    )
    override fun isReadScreen(text: String): Boolean {
        val w = words(text)
        return has(w, "прочитай", "читай", "прочти") || (has(w, "экране", "экран") && has(w, "что"))
    }

    override val stopPhrases = listOf(
        "отбой", "алеша отбой", "алеша стоп", "стоп алеша", "положи трубку", "сбрось", "сбросить", "хватит читать"
    )
    override val readStopPhrases = listOf(
        "алеша стоп", "алеша отбой", "алеша хватит", "алеша замолчи", "стоп алеша", "отбой алеша", "хватит алеша"
    )
    override fun isReadStop(text: String): Boolean {
        val w = words(text)
        return has(w, "алеша", "алексей", "алешка") && has(w, "стоп", "отбой", "хватит", "замолчи")
    }
    override fun isStop(text: String): Boolean {
        val w = words(text)
        return has(w, "отбой", "положи", "сбрось", "сбросить") ||
            (has(w, "алеша") && has(w, "стоп")) || (has(w, "хватит") && has(w, "читать"))
    }

    override val voiceSample = "Здравствуйте. Я Алёша, ваш помощник. Так звучит мой голос."
    override fun voiceTest(c: Calendar) = "Проверка голоса. " + timeText(c)

    // ---------- распознавание ----------
    override val basePhrases = listOf(
        "время", "который час", "сколько времени", "какое время", "скажи время",
        "погода", "какая погода", "какая сейчас погода", "погоду", "что с погодой", "что на улице",
        "погода на завтра", "какая погода завтра", "погода завтра", "что завтра с погодой",
        "погода на послезавтра", "погода послезавтра", "какая погода послезавтра",
        "запиши", "записать", "напомни", "запись", "ежедневник", "сделай запись", "новая запись",
        "что у меня сегодня", "что у меня завтра", "что сегодня", "что завтра", "мои записи", "какие дела", "мои дела",
        "удали запись", "удалить запись", "удали",
        "какое сегодня число", "какой сегодня день", "какое число", "дата", "какая дата", "какая сегодня дата",
        "какой день недели", "какой сегодня день недели", "день недели", "какой день", "число", "сегодня",
        "какое сегодня", "какое число сегодня", "какой сегодня день и число", "скажи число", "скажи день недели",
        "повтори", "еще раз", "помощь", "что ты умеешь", "отмена", "стоп", "нет", "да",
        "отбой", "спать", "отдыхай", "отдохни", "иди спать",
        "отбой на полчаса", "отбой на тридцать минут", "отбой на час", "отбой на два часа",
        "отдыхай полчаса", "отдыхай час", "отдохни полчаса", "отдохни час", "отдыхай тридцать минут",
        "позвонить", "позвони", "набери", "звонок",
        "прочитай экран", "что на экране", "прочитай", "читай экран"
    )
    override val callVerbs = listOf("позвони", "позвонить", "набери", "звони", "вызови", "позвоните")
    override fun contactForms(word: String) = Contacts.wordForms(word)
    override fun contactScore(p: Person, heard: String) = words(heard).count { it in p.forms }

    override val yesNoPhrases = listOf(
        "да", "нет", "верно", "правильно", "не верно", "неверно", "неправильно", "не правильно",
        "конечно", "давай", "звони", "звоните", "отмена", "стоп", "не надо", "не"
    )
    override fun yesNo(text: String): Boolean? {
        val w = words(text)
        return when {
            w.isEmpty() -> null
            has(w, "нет", "не", "неверно", "неправильно", "отмена", "стоп") -> false
            has(w, "да", "верно", "правильно", "конечно", "давай", "звони", "звоните") -> true
            else -> null
        }
    }

    private val sleepWords = setOf("отбой", "спать", "отдыхай", "отдохни")

    override fun command(text: String): Cmd {
        val w = words(text)
        val weather = has(w, "погода", "погоду", "погодой", "улице")
        return when {
            isReadScreen(text) -> Cmd.READ_SCREEN
            w.any { it in sleepWords } -> Cmd.SLEEP
            has(w, "отмена", "стоп") -> Cmd.CANCEL
            has(w, "повтори") || (has(w, "еще") && has(w, "раз")) -> Cmd.REPEAT
            has(w, "помощь", "умеешь") -> Cmd.HELP
            has(w, "удали", "удалить") -> Cmd.DELETE
            has(w, "запиши", "записать", "напомни", "ежедневник") || has(w, "запись") -> Cmd.NOTE
            weather && has(w, "послезавтра") -> Cmd.WEATHER_AFTER
            weather && has(w, "завтра") -> Cmd.WEATHER_TOMORROW
            weather -> Cmd.WEATHER_NOW
            has(w, "записи", "дела") -> Cmd.LIST_ALL
            has(w, "что") && has(w, "сегодня") -> Cmd.LIST_TODAY
            has(w, "что") && has(w, "завтра") -> Cmd.LIST_TOMORROW
            has(w, "число", "дата", "недели", "числа") ||
                (has(w, "день") && has(w, "какой", "сегодня", "скажи")) ||
                w == listOf("сегодня") || w == listOf("какое", "сегодня") -> Cmd.DATE
            has(w, "время", "времени", "час") -> Cmd.TIME
            has(w, *callVerbs.toTypedArray(), "звонок") -> Cmd.CALL_VERB
            else -> Cmd.NONE
        }
    }

    override fun parseTime(text: String) = RuDate.parse(text)

    override val wakePhrases = listOf("алеша", "алеша привет", "привет алеша", "алешенька", "алешка", "эй алеша", "алексей")
    private val wakeWords = setOf("алеша", "алешенька", "алешка", "алексей")
    override fun isWake(text: String) = words(text).any { it in wakeWords }

    override val answerPhrases = listOf(
        "ответить", "ответь", "отвечу", "отвечай", "алло",
        "подними трубку", "подними", "поднять трубку", "подними телефон",
        "возьми трубку", "взять трубку", "бери трубку", "прими звонок", "принять звонок", "принять", "прими",
        "да", "давай", "соедини",
        "сбросить", "сбрось", "сбросьте", "отклонить", "отклони", "не отвечать", "не отвечай",
        "положи трубку", "не надо", "нет", "потом"
    )
    private val answerWords = setOf(
        "ответить", "ответь", "отвечу", "отвечай", "алло", "подними", "поднять", "возьми", "взять",
        "бери", "прими", "принять", "трубку", "да", "давай", "соедини", "телефон"
    )
    private val rejectWords = setOf("сбросить", "сбрось", "сбросьте", "отклонить", "отклони", "не", "положи", "нет", "потом")
    override fun answerOrReject(text: String): Boolean? {
        val w = words(text)
        return when {
            w.any { it in rejectWords } -> false
            w.any { it in answerWords } -> true
            else -> null
        }
    }
}
