package com.melodiq.granpaphone

import java.util.Calendar
import java.util.Locale

object PhrZh : Phrases {
    override val code = "zh"
    override val ttsLocale: Locale = Locale.SIMPLIFIED_CHINESE
    override val modelAsset = "model-zh"
    /** Китайские имена часто не в словаре модели — слушаем свободно и ищем по подстроке. */
    override val useGrammar = false

    private val notAllowed = Regex("[^\\u4e00-\\u9fffa-z0-9 ]")
    private val spaces = Regex("\\s+")
    override fun norm(s: String) = s.lowercase(Locale.ROOT).replace(notAllowed, " ").replace(spaces, " ").trim()
    override fun words(s: String) = norm(s).split(' ').filter { it.isNotEmpty() }
    /** Без пробелов: распознавание по-китайски режет слова непредсказуемо. */
    private fun compact(s: String) = norm(s).replace(" ", "")
    private fun has(s: String, vararg k: String) = k.any { s.contains(it) }

    private fun clock(h: Int, m: Int) = if (m == 0) "${h}点" else "${h}点${m}分"
    private val weekNames = mapOf(
        Calendar.MONDAY to "星期一", Calendar.TUESDAY to "星期二", Calendar.WEDNESDAY to "星期三",
        Calendar.THURSDAY to "星期四", Calendar.FRIDAY to "星期五", Calendar.SATURDAY to "星期六", Calendar.SUNDAY to "星期日"
    )

    // ---------- 语音 ----------
    override fun greet(reminders: String?, userName: String?) =
        (if (userName != null) "${userName}，您好。" else "您好。") + (reminders ?: "") + "我在听。"
    override fun wakeGreet(reminders: String?, userName: String?): String {
        val ask = listOf("有什么吩咐", "您需要什么").random()
        return (if (userName != null) "${userName}，$ask？" else "$ask？") + (reminders ?: "")
    }
    override val notHeardRepeat = "没听清，请再说一遍。"
    override val tapWhenReady = "想说话的时候，请点击屏幕。"
    override val notUnderstoodHint = "没听懂。您可以说：给女儿打电话，或者：几点了，或者：天气。"
    override val notUnderstoodHelp = "没听懂。说「帮助」，听听我能做什么。"
    override val ok = "好的。"
    override val nothingToRepeat = "还没有可以重复的内容。"
    override val help = "我可以打电话：比如说，给女儿打电话。" +
        "报时：说几点了。报日期：说今天几号。" +
        "报天气：说天气、明天天气或者后天天气。" +
        "记事：说记一下。读安排：说今天有什么安排，或者明天有什么安排。" +
        "朗读屏幕上的内容：说读屏幕。停止朗读或挂断电话：说休息。" +
        "删除记录：说删除记录。重复上一个回答：说重复。" +
        "让我休息：说休息。再叫我：说阿廖沙。" +
        "要说命令，请点击屏幕任意位置。"
    override val loading = "请稍等，我还在加载。"
    override val asrBroken = "语音识别没有加载成功。请家人重新安装应用。"
    override fun sleepAnnounce(minutes: Int?) = when (minutes) {
        null -> "我去休息了。要叫我，请说：阿廖沙。"
        30 -> "我休息半小时。要提前叫我，请说：阿廖沙。"
        60 -> "我休息一小时。要提前叫我，请说：阿廖沙。"
        else -> if (minutes % 60 == 0) "我休息${minutes / 60}小时。要提前叫我，请说：阿廖沙。"
        else "我休息${minutes}分钟。要提前叫我，请说：阿廖沙。"
    }
    override fun sleepMinutes(text: String): Int? {
        val s = compact(text)
        if (has(s, "半小时", "半个小时", "半个钟头")) return 30
        Regex("($NUM)分钟").find(s)?.let { return cnNum(it.groupValues[1]) }
        Regex("($NUM)个?(小时|钟头)").find(s)?.let { m -> return cnNum(m.groupValues[1])?.times(60) }
        return null
    }
    override val whomToCall = "给谁打电话？"
    override fun confirmCall(name: String) = "给${name}打电话吗？请说是或者不是。"
    override fun calling(name: String) = "正在给${name}打电话"
    override val callFailed = "电话没有打出去。请家人检查权限。"
    override val okWhomToCall = "好的。给谁打电话？"
    override val notHeardNoCall = "没听清，不打电话了。"
    override val sayYesNo = "请说是或者不是。"

    override fun timeText(c: Calendar) = "现在是" + clock(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) + "。"
    override fun dateText(c: Calendar) =
        "今天是${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日，${weekNames[c.get(Calendar.DAY_OF_WEEK)]}。"

    // ---------- 天气 ----------
    override val weatherChecking = "正在查询天气。"
    override fun weatherError(e: WeatherError) = when (e) {
        WeatherError.NO_LOCATION -> "无法确定您的位置。请家人打开定位。"
        WeatherError.NO_INTERNET -> "没有网络，查不到天气。"
        WeatherError.SERVER -> "天气服务暂时没有响应，请稍后再试。"
    }

    private fun deg(t: Int) = if (t < 0) "零下${-t}度" else "${t}度"

    fun condition(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "大致晴朗"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "有雾"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "冻毛毛雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        66, 67 -> "冻雨，路面结冰"
        68, 69 -> "雨夹雪"
        71 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        77 -> "米雪"
        80 -> "小阵雨"
        81 -> "阵雨"
        82 -> "强阵雨"
        85 -> "小阵雪"
        86 -> "强阵雪"
        95 -> "雷雨"
        96, 99 -> "雷雨伴有冰雹"
        else -> "无降水"
    }

    override fun weatherNow(w: WeatherNow): String {
        val sb = StringBuilder("现在").append(deg(w.temp)).append("，").append(condition(w.code))
        if (w.wind >= 1) sb.append("，风速每秒${w.wind}米")
        sb.append("。")
        if (w.steady) {
            sb.append("未来两小时没有变化。")
        } else {
            val labels = listOf("一小时后", "两小时后")
            w.next.forEachIndexed { k, h ->
                sb.append(labels[k]).append("：").append(deg(h.temp)).append("，").append(condition(h.code))
                if (h.prob >= 40) sb.append("，降水概率百分之${h.prob}")
                sb.append("。")
            }
        }
        return sb.toString()
    }

    override fun weatherDay(d: WeatherDay): String {
        val day = when (d.offset) { 0 -> "今天"; 1 -> "明天"; else -> "后天" }
        val range = if (d.min == d.max) deg(d.max) else "${deg(d.min)}到${deg(d.max)}"
        val sb = StringBuilder("$day$range，").append(condition(d.code))
        if (d.prob >= 30) sb.append("，降水概率百分之${d.prob}")
        if (d.wind >= 8) sb.append("，大风，风速最高每秒${d.wind}米")
        return sb.append("。").toString()
    }

    // ---------- 记事 ----------
    override val noteWhat = "要记什么？请在提示音后说。"
    override val noteWhatAgain = "没听清。要记什么？"
    override val noteCancelled = "已取消记录。"
    override val noteWhen = "什么时候？请说日期和时间。"
    override val noteWhenExample = "没听懂时间。您可以说：明天上午十点。"
    override val noteTimeFailed = "没能听懂时间，已取消记录。"
    override fun noteConfirm(what: String, time: Long) = "记下：${what}，${describeTime(time)}。对吗？"
    override fun noteSaved(willRemind: Boolean) =
        "记好了。" + if (willRemind) "我会提前提醒您。" else "离现在不到一小时，来不及提前提醒。"
    override val noteNotHeardCancelled = "没听清，已取消记录。"
    override fun notesList(dayOffset: Int?, notes: List<Note>): String {
        val dayWord = when (dayOffset) { 0 -> "今天"; 1 -> "明天"; else -> "" }
        return when {
            notes.isEmpty() && dayOffset == null -> "没有记录。"
            notes.isEmpty() -> "${dayWord}没有安排。"
            else -> (if (dayOffset == null) "最近的记录：" else "$dayWord：") + notes.joinToString("。") { describeNote(it) } + "。"
        }
    }
    override fun deleteAsk(note: Note) = "删除：${describeNote(note)}吗？请说是或者不是。"
    override val deleted = "已删除。"
    override val noNotes = "没有记录。"
    override val noMoreNotes = "没有更多记录了，什么都没删除。"
    override val notHeardNoDelete = "没听清，不删除。"

    override fun describeTime(time: Long): String {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val days = RuDate.daysBetween(now, c)
        val day = when {
            days == 0 -> "今天"
            days == 1 -> "明天"
            days == 2 -> "后天"
            days in 3..6 -> weekNames[c.get(Calendar.DAY_OF_WEEK)] ?: ""
            else -> "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
        }
        return day + clock(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
    override fun describeNote(note: Note) = note.text + "，" + describeTime(note.time)
    override fun reminder(notes: List<Note>) = "提醒：" + notes.joinToString("。") { describeNote(it) } + "。"

    // ---------- 来电 ----------
    override fun incomingFirst(name: String) = "${name}来电。说「接听」，或者说「挂断」。"
    override fun incomingRepeat(name: String) = "${name}来电。"
    override val answerFailed = "没能自动接听，请按绿色按钮。"
    override val rejecting = "正在挂断。"
    override val unknownNumber = "未知号码"
    override val strangerNumber = "陌生号码"
    override fun ringing(name: String) = "${name}来电"

    override val readingScreen = "正在读屏幕。"
    override val nothingOnScreen = "屏幕上没有文字。"
    override val readerDisabled = "读屏功能没有开启。请家人在助手设置中开启。"
    override val readerDone = "读完了。"
    override val readPhrases: List<String> = emptyList()
    override fun isReadScreen(text: String) = has(compact(text), "读屏", "读一下", "读屏幕", "念一下", "屏幕上有什么", "读给我听", "朗读")

    override val stopPhrases: List<String> = emptyList()
    override fun isStop(text: String) = has(compact(text), "休息", "挂断", "挂掉", "别念了", "停止朗读", "阿廖沙停", "阿廖沙别")

    override val voiceSample = "您好，我是阿廖沙，您的助手。这是我的声音。"
    override fun voiceTest(c: Calendar) = "语音测试。" + timeText(c)

    // ---------- 识别 ----------
    override val basePhrases: List<String> = emptyList()
    override val callVerbs = listOf("打电话", "打给", "拨打", "呼叫", "给")

    private val synonyms = mapOf(
        "女儿" to listOf("闺女", "姑娘"),
        "儿子" to listOf("儿"),
        "妈妈" to listOf("妈", "母亲", "老妈"),
        "爸爸" to listOf("爸", "父亲", "老爸"),
        "老婆" to listOf("妻子", "媳妇", "爱人"),
        "妻子" to listOf("老婆", "媳妇", "爱人"),
        "老公" to listOf("丈夫", "爱人"),
        "丈夫" to listOf("老公", "爱人"),
        "孙子" to listOf("孙儿"),
        "孙女" to listOf("孙女儿"),
        "医生" to listOf("大夫")
    )
    override fun contactForms(word: String): Set<String> {
        val s = linkedSetOf(word)
        synonyms[word]?.let { s.addAll(it) }
        return s
    }
    override fun contactScore(p: Person, heard: String): Int {
        val h = compact(heard)
        return p.forms.filter { it.length >= 1 && h.contains(it) }.maxOfOrNull { it.length } ?: 0
    }

    override val yesNoPhrases: List<String> = emptyList()
    override fun yesNo(text: String): Boolean? {
        val s = compact(text)
        return when {
            s.isEmpty() -> null
            has(s, "不", "没", "取消", "错", "算了") -> false
            has(s, "是", "对", "好", "可以", "行", "嗯", "打吧") -> true
            else -> null
        }
    }

    override fun command(text: String): Cmd {
        val s = compact(text)
        val weather = has(s, "天气", "气温", "温度")
        return when {
            isReadScreen(text) -> Cmd.READ_SCREEN
            has(s, "休息", "睡觉", "晚安", "退下") -> Cmd.SLEEP
            has(s, "取消", "停止", "算了") -> Cmd.CANCEL
            has(s, "重复", "再说一遍", "再说一次") -> Cmd.REPEAT
            has(s, "帮助", "你会什么", "你能做什么") -> Cmd.HELP
            has(s, "删除", "删掉") -> Cmd.DELETE
            has(s, "记一下", "记下", "记录一下", "提醒我", "备忘", "记住") -> Cmd.NOTE
            weather && has(s, "后天") -> Cmd.WEATHER_AFTER
            weather && has(s, "明天") -> Cmd.WEATHER_TOMORROW
            weather -> Cmd.WEATHER_NOW
            has(s, "今天") && has(s, "安排", "日程", "有什么事") -> Cmd.LIST_TODAY
            has(s, "明天") && has(s, "安排", "日程", "有什么事") -> Cmd.LIST_TOMORROW
            has(s, "记录", "安排", "日程") -> Cmd.LIST_ALL
            has(s, "几号", "日期", "星期几", "礼拜几", "周几", "几月") -> Cmd.DATE
            has(s, "几点", "时间", "钟点") -> Cmd.TIME
            has(s, "打电话", "打给", "拨打", "呼叫") -> Cmd.CALL_VERB
            else -> Cmd.NONE
        }
    }

    // ---------- 时间解析 ----------
    private const val NUM = "[零〇一二两三四五六七八九十0-9]+"

    /** 中文数字 0..99 → Int. */
    fun cnNum(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        val d = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (s.isEmpty()) return null
        val ten = s.indexOf('十')
        if (ten < 0) {
            var v = 0
            for (ch in s) v = v * 10 + (d[ch] ?: return null)
            return v
        }
        val tens = if (ten == 0) 1 else d[s[0]] ?: return null
        val unit = if (ten == s.length - 1) 0 else d[s[ten + 1]] ?: return null
        return tens * 10 + unit
    }

    override fun parseTime(text: String): Long? {
        val s = compact(text)
        val now = Calendar.getInstance()

        Regex("($NUM|半)个?(小时|钟头)(以后|之后|后)").find(s)?.let { m ->
            val n = if (m.groupValues[1] == "半") 0 else cnNum(m.groupValues[1]) ?: return null
            val c = now.clone() as Calendar
            c.add(Calendar.MINUTE, if (m.groupValues[1] == "半") 30 else n * 60)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        Regex("($NUM)分钟(以后|之后|后)").find(s)?.let { m ->
            val n = cnNum(m.groupValues[1]) ?: return null
            val c = now.clone() as Calendar
            c.add(Calendar.MINUTE, n)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        var dayOffset: Int? = when {
            s.contains("大后天") -> 3
            s.contains("后天") -> 2
            s.contains("明天") || s.contains("明早") || s.contains("明晚") -> 1
            s.contains("今天") || s.contains("今晚") || s.contains("今早") -> 0
            else -> null
        }
        var weekday: Int? = null
        Regex("(星期|周|礼拜)([一二三四五六日天])").find(s)?.let { m ->
            weekday = when (m.groupValues[2]) {
                "一" -> Calendar.MONDAY; "二" -> Calendar.TUESDAY; "三" -> Calendar.WEDNESDAY
                "四" -> Calendar.THURSDAY; "五" -> Calendar.FRIDAY; "六" -> Calendar.SATURDAY
                else -> Calendar.SUNDAY
            }
        }
        var dateDay: Int? = null
        var dateMonth: Int? = null
        Regex("(?:($NUM)月)?($NUM)(日|号)").find(s)?.let { m ->
            dateDay = cnNum(m.groupValues[2])
            if (m.groupValues[1].isNotEmpty()) dateMonth = cnNum(m.groupValues[1])?.minus(1)
        }

        val tm = Regex("($NUM)点(钟)?(半|($NUM)分?)?").find(s)
        var h: Int
        var min = 0
        if (tm != null) {
            h = cnNum(tm.groupValues[1]) ?: return null
            if (tm.groupValues[3] == "半") min = 30
            else if (tm.groupValues[4].isNotEmpty()) min = cnNum(tm.groupValues[4]) ?: 0
        } else if (s.contains("中午")) {
            h = 12
        } else {
            return null
        }
        val pm = when {
            has(s, "下午", "晚上", "傍晚", "今晚", "明晚", "晚") -> true
            has(s, "上午", "早上", "早晨", "凌晨", "明早", "今早") -> false
            has(s, "中午") -> { if (h in 1..5) h += 12; null }
            else -> null
        }
        when (pm) {
            true -> if (h in 1..11) h += 12
            false -> if (h == 12) h = 0
            null -> if (h in 1..6 && !s.contains("中午")) h += 12
        }
        if (h !in 0..23 || min !in 0..59) return null
        return buildTime(now, h, min, dayOffset, weekday, dateDay, dateMonth)
    }

    // ---------- 唤醒词、来电 ----------
    override val wakePhrases: List<String> = emptyList()
    override fun isWake(text: String) = has(compact(text), "阿廖沙", "廖沙", "小助手", "助手")
    override val answerPhrases: List<String> = emptyList()
    override fun answerOrReject(text: String): Boolean? {
        val s = compact(text)
        return when {
            has(s, "挂断", "拒接", "不接", "挂了", "挂掉", "不要", "等会") -> false
            has(s, "接听", "接电话", "接", "喂", "好", "是") -> true
            else -> null
        }
    }
}
