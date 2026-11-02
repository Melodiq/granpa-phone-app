package com.melodiq.granpaphone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Отправка сообщения в мессенджер MAX «руками» через службу специальных возможностей:
 * открыть MAX → поиск → имя контакта → открыть чат → вписать текст → «Отправить».
 * У MAX нет публичной ссылки «открыть чат по номеру», поэтому делаем как человек.
 * Каждый шаг пишется в журнал — по нему видно, где сломалось.
 */
object MaxSender {

    enum class Fail { NOT_INSTALLED, NO_READER, NO_SEARCH, NO_CONTACT, NO_INPUT, NO_SEND, BUSY }

    private val main = Handler(Looper.getMainLooper())
    private val log = ArrayList<String>()
    @Volatile var running = false
        private set

    /** Журнал последней попытки (для экрана настройки и «Поделиться отчётом»). */
    @Volatile var lastLog: String = ""
        private set

    private val KNOWN_PACKAGES = listOf("ru.oneme.app", "ru.max.messenger", "ru.vk.max")
    private val LABELS = listOf("max", "макс", "мах")

    fun findPackage(ctx: Context): String? {
        val pm = ctx.packageManager
        KNOWN_PACKAGES.firstOrNull { p ->
            try { pm.getLaunchIntentForPackage(p) != null } catch (e: Exception) { false }
        }?.let { return it }
        // Ищем по названию на рабочем столе: «MAX», «Макс».
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL).firstOrNull { ri ->
                val label = ri.loadLabel(pm).toString().trim().lowercase()
                label in LABELS || label.startsWith("max ") || label.startsWith("макс ")
            }?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun note(s: String) {
        log.add(s)
        lastLog = log.joinToString("\n")
    }

    /**
     * Отправить text контакту с именем contactName. cb(null) — успех, иначе причина.
     */
    fun send(ctx: Context, contactName: String, text: String, cb: (Fail?) -> Unit) {
        if (running) { cb(Fail.BUSY); return }
        val svc = ScreenReaderService.instance ?: run { cb(Fail.NO_READER); return }
        val pkg = findPackage(ctx) ?: run { cb(Fail.NOT_INSTALLED); return }
        running = true
        log.clear()
        note("Пакет MAX: $pkg; контакт: «$contactName»")
        val done = { f: Fail? ->
            running = false
            note(if (f == null) "ГОТОВО" else "ОШИБКА: $f")
            main.post { cb(f) }
            Unit
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launch)
        Run(svc, pkg, contactName, text, done).start()
    }

    /** Снимок экрана MAX (дерево элементов) — для отладки без отправки сообщения. */
    fun snapshot(ctx: Context, cb: (String) -> Unit) {
        val svc = ScreenReaderService.instance ?: run { cb("Чтение экрана выключено"); return }
        val pkg = findPackage(ctx) ?: run { cb("MAX не установлен"); return }
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        main.postDelayed({
            val dump = dump(svc.rootInActiveWindow)
            lastLog = "Снимок экрана MAX ($pkg):\n$dump"
            cb(lastLog)
        }, 3500)
    }

    // ---------- пошаговый сценарий ----------

    private class Run(
        val svc: AccessibilityService,
        val pkg: String,
        val name: String,
        val text: String,
        val done: (Fail?) -> Unit
    ) {
        private val h = Handler(Looper.getMainLooper())
        private var backs = 0

        fun start() = waitFor(10_000, "MAX открылся", { root()?.packageName?.toString() == pkg },
            ok = { h.postDelayed({ openSearch() }, 800) })

        private fun root(): AccessibilityNodeInfo? = svc.rootInActiveWindow

        /** Повторять проверку каждые 300 мс, пока не выполнится или не выйдет время. */
        private fun waitFor(timeout: Long, what: String, test: () -> Boolean, ok: () -> Unit, fail: () -> Unit = { done(Fail.NO_SEARCH) }) {
            val until = System.currentTimeMillis() + timeout
            val r = object : Runnable {
                override fun run() {
                    val good = try { test() } catch (e: Exception) { false }
                    when {
                        good -> { note("✓ $what"); ok() }
                        System.currentTimeMillis() > until -> { note("✗ $what — не дождался\n" + dump(root())); fail() }
                        else -> h.postDelayed(this, 300)
                    }
                }
            }
            h.post(r)
        }

        // 1. Поиск в списке чатов.
        private fun openSearch() {
            val r = root()
            val search = findNode(r) { n ->
                val s = label(n)
                (s.contains("поиск") || s.contains("search") || s.contains("найти")) && n.isVisibleToUser
            }
            if (search == null) {
                // Возможно, открыт чат с прошлого раза — шаг назад.
                if (backs < 3) {
                    backs++
                    note("… поиска нет, назад ($backs)")
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    h.postDelayed({ openSearch() }, 900)
                } else {
                    note("✗ не нашёл поиск\n" + dump(r))
                    done(Fail.NO_SEARCH)
                }
                return
            }
            note("✓ нашёл поиск: ${describe(search)}")
            if (search.className?.toString()?.contains("EditText") == true) {
                typeName(search)
            } else {
                tap(search)
                waitFor(4000, "поле поиска", { editText(root()) != null }, { typeName(editText(root())!!) }, { done(Fail.NO_SEARCH) })
            }
        }

        // 2. Вписать имя контакта.
        private fun typeName(field: AccessibilityNodeInfo) {
            setText(field, name)
            note("✓ вписал имя")
            h.postDelayed({ pickContact(0) }, 1500)
        }

        // 3. Выбрать контакт в результатах.
        private fun pickContact(attempt: Int) {
            val r = root()
            val want = Ru.norm(name)
            // В MAX строка чата — нажимаемый блок с описанием «Имя.Чат закреплён..Последнее сообщение…»
            // и надписью «Имя» внутри. Сначала ищем точное имя, потом начало.
            fun nameOf(n: AccessibilityNodeInfo): String? {
                if (n.className?.toString()?.contains("EditText") == true || !n.isVisibleToUser) return null
                n.text?.toString()?.let { return Ru.norm(it) }
                return n.contentDescription?.toString()?.substringBefore('.')?.let { Ru.norm(it) }
            }
            val hit = findNode(r) { n -> nameOf(n) == want }
                ?: findNode(r) { n -> nameOf(n)?.startsWith("$want ") == true }
                ?: findNode(r) { n -> nameOf(n)?.contains(want) == true }
            if (hit == null) {
                if (attempt < 4) { h.postDelayed({ pickContact(attempt + 1) }, 700); return }
                note("✗ контакт не найден в результатах\n" + dump(r))
                done(Fail.NO_CONTACT)
                return
            }
            note("✓ контакт: ${describe(hit)}")
            tap(hit)
            waitFor(6000, "открылся чат", { messageInput(root()) != null || placeholder(root()) != null },
                { h.postDelayed({ typeMessage(0) }, 500) }, { done(Fail.NO_INPUT) })
        }

        // 4. Вписать текст сообщения.
        private fun typeMessage(attempt: Int) {
            var input = messageInput(root())
            if (input == null) {
                // Поле «Сообщение» ещё не активно — коснуться надписи, чтобы появилось поле ввода.
                val ph = placeholder(root())
                if (ph != null && attempt < 3) {
                    note("… касаюсь «Сообщение»: ${describe(ph)}")
                    tap(ph)
                    h.postDelayed({ typeMessage(attempt + 1) }, 900)
                    return
                }
                input = root()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            }
            if (input == null) {
                note("✗ нет поля ввода\n" + dump(root()))
                done(Fail.NO_INPUT)
                return
            }
            note("✓ поле ввода: ${describe(input)}")
            if (!setText(input, text)) {
                note("… ввод не принят, вставляю через буфер обмена")
                paste(input, text)
            }
            h.postDelayed({
                val now = messageInput(root())?.text?.toString() ?: ""
                note(if (now.contains(text.take(5))) "✓ текст в поле" else "? в поле: «${now.take(40)}»")
                pressSend(0)
            }, 700)
        }

        /** Надпись-подсказка «Сообщение» в нижней половине экрана. */
        private fun placeholder(r: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            val half = svc.resources.displayMetrics.heightPixels / 2
            return findNode(r) { n ->
                val s = label(n).trim()
                (s == "сообщение" || s.startsWith("сообщение") || s == "message") && n.isVisibleToUser &&
                    Rect().also { b -> n.getBoundsInScreen(b) }.top > half
            }
        }

        private fun paste(n: AccessibilityNodeInfo, s: String) {
            try {
                val cm = svc.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("msg", s))
                n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                n.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (_: Exception) {}
        }

        // 5. Нажать «Отправить» — только кнопку в строке поля ввода, а не сообщения в переписке
        //    (у них в описании тоже есть «Отправлено»).
        private fun pressSend(attempt: Int) {
            val r = root()
            val input = messageInput(r)
            if (input == null) {
                if (attempt < 3) { h.postDelayed({ pressSend(attempt + 1) }, 600); return }
                note("✗ пропало поле ввода\n" + dump(r)); done(Fail.NO_SEND); return
            }
            val ib = Rect().also { input.getBoundsInScreen(it) }
            val dp = svc.resources.displayMetrics.density
            // Кандидаты: видимые элементы в той же строке, что и поле ввода, правее его середины.
            val row = ArrayList<AccessibilityNodeInfo>()
            collect(r) { n ->
                if (!n.isVisibleToUser || n.isEditable) return@collect
                val b = Rect().also { n.getBoundsInScreen(it) }
                val cy = b.centerY()
                if (cy < ib.top - 40 * dp || cy > ib.bottom + 40 * dp) return@collect
                if (b.left < ib.centerX()) return@collect
                if (b.width() > 120 * dp || b.height() > 120 * dp) return@collect
                row.add(n)
            }
            fun lab(n: AccessibilityNodeInfo) = label(n).trim() + " " + (n.viewIdResourceName ?: "").lowercase()
            val send = row.firstOrNull { val l = lab(it); (l.contains("отправить") || l.contains("send")) && !l.contains("отправлено") }
                ?: row.firstOrNull { val l = lab(it); l.contains("отправ") && !l.contains("отправлено") }
                // Иначе — самая правая нажимаемая кнопка в строке (в MAX там «Отправить» после ввода текста).
                ?: row.filter { it.isClickable }.maxByOrNull { Rect().also { b -> it.getBoundsInScreen(b) }.right }
            if (send == null) {
                if (attempt < 3) { h.postDelayed({ pressSend(attempt + 1) }, 600); return }
                note("✗ нет кнопки «Отправить» в строке ввода\n" + dump(r))
                done(Fail.NO_SEND)
                return
            }
            note("✓ кнопка: ${describe(send)}; в строке: " + row.joinToString(" | ") { describe(it) }.take(400))
            tap(send)
            // Проверка: поле ввода опустело (или снова показывает подсказку).
            waitFor(4000, "сообщение ушло", {
                val t = messageInput(root())?.text?.toString()?.trim() ?: ""
                t.isEmpty() || t.equals("сообщение", true) || t.equals("message", true)
            }, { done(null) }, { done(Fail.NO_SEND) })
        }

        // ---------- поиск элементов и действия ----------

        private fun editText(r: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
            r?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
                ?: findNode(r) { it.isEditable && it.isVisibleToUser }

        /** Поле «Сообщение» внизу чата. */
        private fun messageInput(r: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            val edits = ArrayList<AccessibilityNodeInfo>()
            collect(r) { if (it.isEditable && it.isVisibleToUser) edits.add(it) }
            if (edits.isEmpty()) {
                val f = r?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
                return f.takeIf { Rect().also { b -> it.getBoundsInScreen(b) }.top > svc.resources.displayMetrics.heightPixels / 2 }
            }
            edits.firstOrNull { val s = label(it); s.contains("сообщ") || s.contains("message") }?.let { return it }
            // Иначе — самое нижнее поле ввода на экране (поле сообщения всегда внизу).
            return edits.maxByOrNull { Rect().also { b -> it.getBoundsInScreen(b) }.bottom }
                ?.takeIf { Rect().also { b -> it.getBoundsInScreen(b) }.top > svc.resources.displayMetrics.heightPixels / 2 }
        }

        private fun setText(n: AccessibilityNodeInfo, s: String): Boolean {
            n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, s)
            }
            return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        /** Нажать: на сам элемент или ближайшего нажимаемого родителя; если не вышло — касание по координатам. */
        private fun tap(n: AccessibilityNodeInfo) {
            var c: AccessibilityNodeInfo? = n
            var depth = 0
            while (c != null && depth < 6) {
                if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                c = c.parent
                depth++
            }
            val b = Rect()
            n.getBoundsInScreen(b)
            val p = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
            svc.dispatchGesture(
                GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(),
                null, null
            )
        }

        private fun note(s: String) = MaxSender.note(s)
    }

    // ---------- общие помощники ----------

    private fun label(n: AccessibilityNodeInfo): String {
        val hint = if (android.os.Build.VERSION.SDK_INT >= 26) n.hintText?.toString() ?: "" else ""
        return ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "") + " " + hint).lowercase()
    }

    private fun describe(n: AccessibilityNodeInfo): String =
        "${n.className?.toString()?.substringAfterLast('.')} «${n.text ?: n.contentDescription ?: ""}» id=${n.viewIdResourceName ?: "-"}"

    private fun collect(r: AccessibilityNodeInfo?, depth: Int = 0, f: (AccessibilityNodeInfo) -> Unit) {
        if (r == null || depth > 40) return
        f(r)
        for (i in 0 until r.childCount) collect(try { r.getChild(i) } catch (e: Exception) { null }, depth + 1, f)
    }

    private fun findNode(r: AccessibilityNodeInfo?, test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        collect(r) { if (found == null && test(it)) found = it }
        return found
    }

    /** Текстовый снимок дерева элементов экрана. */
    fun dump(r: AccessibilityNodeInfo?): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || d > 25 || sb.length > 6000) return
            val t = n.text?.toString()?.take(40) ?: ""
            val cd = n.contentDescription?.toString()?.take(40) ?: ""
            val id = n.viewIdResourceName?.substringAfter(":id/") ?: ""
            val flags = (if (n.isClickable) "c" else "") + (if (n.isEditable) "e" else "")
            if (t.isNotEmpty() || cd.isNotEmpty() || id.isNotEmpty() || flags.isNotEmpty()) {
                sb.append("  ".repeat(minOf(d, 10))).append(n.className?.toString()?.substringAfterLast('.'))
                if (id.isNotEmpty()) sb.append(" #").append(id)
                if (t.isNotEmpty()) sb.append(" «").append(t).append("»")
                if (cd.isNotEmpty()) sb.append(" [").append(cd).append("]")
                if (flags.isNotEmpty()) sb.append(" {").append(flags).append("}")
                sb.append('\n')
            }
            for (i in 0 until n.childCount) walk(try { n.getChild(i) } catch (e: Exception) { null }, d + 1)
        }
        walk(r, 0)
        return sb.toString()
    }
}
