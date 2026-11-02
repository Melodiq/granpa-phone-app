package com.melodiq.granpaphone

import android.content.Context
import java.util.Calendar
import java.util.Locale

/** Что пользователь попросил. */
enum class Cmd {
    SLEEP, CANCEL, REPEAT, HELP, DELETE, NOTE, LIST_ALL, LIST_TODAY, LIST_TOMORROW,
    WEATHER_NOW, WEATHER_TOMORROW, WEATHER_AFTER, DATE, TIME, CALL_VERB, READ_SCREEN, MESSAGE, NONE
}

/**
 * Всё, что зависит от языка: что помощник говорит, какие фразы распознаёт и как их понимает.
 * Реализации: [PhrRu], [PhrEn], [PhrZh].
 */
interface Phrases {
    val code: String
    val ttsLocale: Locale
    /** Папка модели Vosk в assets. */
    val modelAsset: String
    /** Слушать команды по списку фраз (точнее) или свободно (для китайского: имена вне словаря). */
    val useGrammar: Boolean

    // ---------- текст и слова ----------
    fun norm(s: String): String
    fun words(s: String): List<String>

    // ---------- что говорит помощник ----------
    fun greet(reminders: String?, userName: String?): String
    fun wakeGreet(reminders: String?, userName: String?): String
    val notHeardRepeat: String
    val tapWhenReady: String
    val notUnderstoodHint: String
    val notUnderstoodHelp: String
    val ok: String
    val nothingToRepeat: String
    val help: String
    val loading: String
    val asrBroken: String
    fun sleepAnnounce(minutes: Int?): String
    /** «отбой на 30 минут / на час» → минуты; просто «отбой» → null. */
    fun sleepMinutes(text: String): Int?
    val whomToCall: String
    fun confirmCall(name: String): String
    fun calling(name: String): String
    val callFailed: String
    val okWhomToCall: String
    val notHeardNoCall: String
    val sayYesNo: String

    fun timeText(c: Calendar): String
    fun dateText(c: Calendar): String

    val weatherChecking: String
    fun weatherError(e: WeatherError): String
    fun weatherNow(w: WeatherNow): String
    fun weatherDay(d: WeatherDay): String

    val noteWhat: String
    val noteWhatAgain: String
    val noteCancelled: String
    val noteWhen: String
    val noteWhenExample: String
    val noteTimeFailed: String
    fun noteConfirm(what: String, time: Long): String
    fun noteSaved(willRemind: Boolean): String
    val noteNotHeardCancelled: String
    fun notesList(dayOffset: Int?, notes: List<Note>): String
    fun deleteAsk(note: Note): String
    val deleted: String
    val noNotes: String
    val noMoreNotes: String
    val notHeardNoDelete: String

    fun describeTime(time: Long): String
    fun describeNote(note: Note): String = note.text + ", " + describeTime(note.time)
    fun reminder(notes: List<Note>): String

    fun incomingFirst(name: String): String
    fun incomingRepeat(name: String): String
    val answerFailed: String
    val rejecting: String
    val unknownNumber: String
    val strangerNumber: String
    fun ringing(name: String): String

    // ---------- чтение экрана ----------
    val readingScreen: String
    val nothingOnScreen: String
    val readerDisabled: String
    val readerDone: String
    /** Фразы «прочитай экран» для списка распознавания (вместе со словом пробуждения). */
    val readPhrases: List<String>
    fun isReadScreen(text: String): Boolean

    // ---------- «отбой» во время чтения или звонка ----------
    /** Фразы для списка распознавания, пока идёт чтение экрана или звонок. */
    val stopPhrases: List<String>
    fun isStop(text: String): Boolean
    /** Во время чтения — только вместе со словом пробуждения («Алёша, стоп»): читаемый текст его почти не содержит. */
    val readStopPhrases: List<String>
    fun isReadStop(text: String): Boolean

    // ---------- сообщения в MAX ----------
    /** «напиши», «отправь сообщение» — для списка распознавания вместе с именами. */
    val msgVerbs: List<String>
    fun isMessage(text: String): Boolean
    val msgWhom: String
    fun msgWhat(name: String): String
    val msgWhatAgain: String
    fun msgConfirm(name: String, text: String): String
    fun msgSending(name: String): String
    fun msgSent(name: String): String
    fun msgFailed(f: MaxSender.Fail): String
    val msgCancelled: String

    val voiceSample: String
    fun voiceTest(c: Calendar): String

    // ---------- распознавание ----------
    val basePhrases: List<String>
    val callVerbs: List<String>
    fun contactForms(word: String): Set<String>
    /** Насколько фраза похожа на этот контакт (0 — не он). */
    fun contactScore(p: Person, heard: String): Int
    val yesNoPhrases: List<String>
    /** true — да, false — нет, null — непонятно. */
    fun yesNo(text: String): Boolean?
    fun command(text: String): Cmd
    fun parseTime(text: String): Long?

    val wakePhrases: List<String>
    fun isWake(text: String): Boolean
    val answerPhrases: List<String>
    /** true — ответить, false — сбросить, null — непонятно. */
    fun answerOrReject(text: String): Boolean?

    companion object {
        fun of(code: String): Phrases = when (code) {
            "en" -> PhrEn
            "zh" -> PhrZh
            else -> PhrRu
        }

        fun of(ctx: Context): Phrases = of(Lang.code(ctx))
    }
}
