package com.melodiq.granpaphone

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Setup {
    fun permissions(): Array<String> {
        val p = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 26) p.add(Manifest.permission.ANSWER_PHONE_CALLS)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        return p.toTypedArray()
    }

    fun granted(ctx: Context, perm: String) =
        Build.VERSION.SDK_INT < 23 || ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    fun allPermissions(ctx: Context) = permissions().all { granted(ctx, it) }

    /** Без этого помощник не может работать вообще. */
    fun essentialsGranted(ctx: Context) =
        granted(ctx, Manifest.permission.RECORD_AUDIO) &&
            granted(ctx, Manifest.permission.READ_CONTACTS) &&
            granted(ctx, Manifest.permission.CALL_PHONE)
}

/** Экран настройки для родственников. Открывается удержанием пальца на главном экране 5 секунд. */
class SetupActivity : Activity() {

    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 48)
            setBackgroundColor(Palette.NIGHT)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Palette.NIGHT)
            addView(box)
        })
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        KeeperService.start(this)
        rebuild()
    }

    private fun rebuild() {
        box.removeAllViews()
        title("Настройка помощника")
        note("Для родственников. Нажмите на каждую жёлтую плашку и разрешите. Зелёная рамка с галочкой — готово.")

        item("1. Разрешения: микрофон, контакты, звонки, ответ на звонки, геолокация", Setup.allPermissions(this)) {
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(Setup.permissions(), 1)
        }

        if (Build.VERSION.SDK_INT >= 23) {
            item("2. Показ поверх других окон (приветствие при нажатии боковой кнопки и ответ на звонок голосом)", Settings.canDrawOverlays(this)) {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            val pm = getSystemService(PowerManager::class.java)
            item("3. Батарея: без ограничений (иначе система выключит помощника)", pm?.isIgnoringBatteryOptimizations(packageName) == true) {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            item("4. Точные напоминания", am?.canScheduleExactAlarms() == true) {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }

        val sp = App.instance.speaker
        item(
            "5. Русский голос. Если галочки нет — установите голос в настройках синтеза речи (Google)",
            sp.ready && sp.languageOk
        ) {
            open(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        }
        voiceSection()

        val ears = App.instance.ears
        val earsText = when {
            ears.model != null -> "✓ 6. Распознавание речи готово (работает без интернета)"
            ears.error != null -> "✗ 6. Распознавание речи: ошибка — " + ears.error
            else -> "… 6. Распознавание речи загружается… (первый запуск до минуты)"
        }
        note(earsText)
        if (ears.model != null) {
            val wakeOk = Sleep.WAKE_PHRASES.any { ears.fixPhrase(it) != null }
            note(if (wakeOk) "✓ Слово «Алёша» распознаётся: «отбой» — помощник отдыхает, «Алёша» — просыпается"
                 else "✗ Слово «Алёша» распознавание не знает — будить можно касанием экрана")
        }

        button("7. Автозапуск (Tecno/Infinix): откроются настройки приложения → включите «Автозапуск» и разрешите работу в фоне") {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        note("Также на Tecno: Настройки → Батарея → Управление приложениями → «Помощник Алёша» → Без ограничений. И закрепите приложение в списке недавних (замок), чтобы его не закрывала очистка.")

        button("8. Необязательно: сделать главным экраном (тогда при нажатии кнопки сразу открывается помощник)") {
            open(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        note("Совет: отключите блокировку экрана (Настройки → Безопасность → Блокировка экрана → Нет), чтобы дедушке не нужно было ничего смахивать.")

        button("Проверить голос") {
            App.instance.speaker.say("Проверка голоса. Сейчас " + java.util.Calendar.getInstance().let {
                Ru.clock(it.get(java.util.Calendar.HOUR_OF_DAY), it.get(java.util.Calendar.MINUTE))
            })
        }

        Weather.lastError?.let { note("Погода, последняя ошибка: $it") }

        contactsReport()

        button("Готово — вернуться к помощнику") { finish() }
    }

    /** Выбор голоса: каждый можно послушать, нажатие сразу выбирает. */
    private fun voiceSection() {
        val sp = App.instance.speaker
        val voices = sp.russianVoices()
        title("Голос помощника")
        if (voices.isEmpty()) {
            note("Скачанных русских голосов нет. Нажмите пункт 5 и скачайте русский голос в настройках Google.")
            return
        }
        note("Нажмите на голос — он скажет фразу и станет голосом помощника. Выберите спокойный мужской. Если мужского нет: пункт 5 → Русский → скачайте ещё голоса.")
        val current = sp.currentVoiceName
        voices.forEachIndexed { i, v ->
            val label = "Голос ${i + 1}" + (if (v.name == current) " — выбран" else "")
            if (v.name == current) {
                tile("✓  $label", Tile.DONE) { sample(v.name) }
            } else {
                button(label) { sample(v.name) }
            }
        }
    }

    private fun sample(name: String) {
        val sp = App.instance.speaker
        sp.selectVoice(name)
        sp.say("Здравствуйте. Я Алёша, ваш помощник. Так звучит мой голос.")
        rebuild()
    }

    private fun contactsReport() {
        if (!Setup.granted(this, Manifest.permission.READ_CONTACTS)) return
        val people = Contacts.load(this)
        val ears = App.instance.ears
        title("Контакты (${people.size})")
        note("Помощник узнаёт контакт по словам из его имени. Лучше всего подписывать коротко: «Дочь», «Сын Андрей», «Соседка Валя». Крестик ✗ — слово не знает распознавание, переименуйте контакт.")
        if (ears.model == null) {
            note("Проверка станет доступна после загрузки распознавания.")
            return
        }
        for (p in people.take(60)) {
            val ok = p.nameWords.any { w -> ears.knows(w) }
            note((if (ok) "✓ " else "✗ ") + p.name)
        }
    }

    // ---------- простые элементы интерфейса ----------

    private fun open(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        }
    }

    private fun title(t: String) {
        box.addView(TextView(this).apply {
            text = t
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Palette.MILK)
            setPadding(0, 32, 0, 16)
        })
    }

    private fun note(t: String) {
        box.addView(TextView(this).apply {
            text = t
            textSize = 17f
            setTextColor(Palette.MILK_DIM)
            setPadding(0, 8, 0, 16)
        })
    }

    /** Шаг настройки: не готово — янтарная плашка (надо нажать), готово — спокойная с галочкой. */
    private fun item(t: String, ok: Boolean, onClick: () -> Unit) {
        tile((if (ok) "✓  " else "") + t, if (ok) Tile.DONE else Tile.TODO, onClick)
    }

    private fun button(t: String, onClick: () -> Unit) {
        tile(t, Tile.NEUTRAL, onClick)
    }

    private enum class Tile { TODO, DONE, NEUTRAL }

    private fun tile(t: String, kind: Tile, onClick: () -> Unit) {
        val dp = resources.displayMetrics.density
        val bg = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 14 * dp
            when (kind) {
                Tile.TODO -> setColor(Palette.AMBER)
                Tile.DONE -> { setColor(Palette.NIGHT); setStroke((2 * dp).toInt(), Palette.MINT) }
                Tile.NEUTRAL -> setColor(Color.parseColor("#1C3A63"))
            }
        }
        box.addView(TextView(this).apply {
            text = t
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(when (kind) { Tile.TODO -> Palette.NIGHT; Tile.DONE -> Palette.MINT; Tile.NEUTRAL -> Palette.MILK })
            background = bg
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (12 * dp).toInt()
        })
    }
}
