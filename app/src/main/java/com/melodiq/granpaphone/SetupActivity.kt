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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    private fun rebuild() {
        box.removeAllViews()
        title(getString(R.string.setup_title))
        note(getString(R.string.setup_intro))

        languageSection()
        nameSection()

        item(getString(R.string.step_permissions), Setup.allPermissions(this)) {
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(Setup.permissions(), 1)
        }

        if (Build.VERSION.SDK_INT >= 23) {
            item(getString(R.string.step_overlay), Settings.canDrawOverlays(this)) {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            val pm = getSystemService(PowerManager::class.java)
            item(getString(R.string.step_battery), pm?.isIgnoringBatteryOptimizations(packageName) == true) {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            item(getString(R.string.step_alarms), am?.canScheduleExactAlarms() == true) {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }

        val sp = App.instance.speaker
        item(getString(R.string.step_tts), sp.ready && sp.languageOk) {
            open(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        }
        voiceSection()

        val ears = App.instance.ears
        note(when {
            ears.model != null -> getString(R.string.asr_ready)
            ears.error != null -> getString(R.string.asr_error, ears.error ?: "")
            else -> getString(R.string.asr_loading)
        })
        if (ears.model != null) {
            val phr = Phrases.of(this)
            val wakeOk = !phr.useGrammar || phr.wakePhrases.any { ears.fixPhrase(it) != null }
            note(getString(if (wakeOk) R.string.wake_known else R.string.wake_unknown))
        }

        item(getString(R.string.step_reader), ScreenReaderService.isOn) {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        note(getString(R.string.reader_note))
        Profile.lastReadStop?.let { note(getString(R.string.reader_last_stop, it)) }
        if (ScreenReaderService.isOn) {
            button(getString(R.string.reader_test)) {
                android.os.Handler(mainLooper).postDelayed({ ScreenReaderService.read() }, 3000)
            }
        }

        if (isXiaomi()) xiaomiSection()

        button(getString(R.string.step_autostart)) {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        note(getString(R.string.autostart_note))

        button(getString(R.string.step_home)) {
            open(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        note(getString(R.string.lockscreen_tip))

        button(getString(R.string.test_voice)) {
            App.instance.speaker.say(Phrases.of(this).voiceTest(java.util.Calendar.getInstance()))
        }

        weatherSection()
        wakeSection()
        contactsReport()

        button(getString(R.string.done)) { finish() }
    }

    /** Как обращаться к пользователю: «Чего изволите, Иван Петрович?» */
    private fun nameSection() {
        val dp = resources.displayMetrics.density
        title(getString(R.string.name_title))
        val field = android.widget.EditText(this).apply {
            hint = getString(R.string.name_hint)
            setText(Profile.name(this@SetupActivity) ?: "")
            textSize = 20f
            setTextColor(Palette.MILK)
            setHintTextColor(Palette.MILK_DIM)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(Color.parseColor("#1C3A63"))
            }
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
        }
        box.addView(field, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (10 * dp).toInt() })
        button(getString(R.string.name_save)) {
            Profile.setName(this, field.text.toString())
            App.instance.speaker.say(Phrases.of(this).wakeGreet(null, Profile.name(this)))
            rebuild()
        }
        note(getString(R.string.name_note))
    }

    private fun languageSection() {
        title(getString(R.string.lang_title))
        val cur = Lang.code(this)
        val names = mapOf("ru" to "Русский", "en" to "English", "zh" to "中文")
        for (c in Lang.CODES) {
            val label = names[c] ?: c
            if (c == cur) tile("✓  $label", Tile.DONE) {} else button(label) {
                Lang.set(this, c)
                // Сменить и речь, и распознавание: другая модель и другой голос.
                App.instance.ears.load(Phrases.of(c).modelAsset)
                App.instance.speaker.setLanguage(Phrases.of(c).ttsLocale)
                AleshaWidget.refresh(this)
                KeeperService.start(this)
                recreate()
            }
        }
        note(getString(R.string.lang_note))
    }

    private fun isXiaomi(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "redmi", "poco").any { it in m }
    }

    /** На HyperOS/MIUI есть свои разрешения, без которых окно из фона не открывается. */
    private fun xiaomiSection() {
        title(getString(R.string.xiaomi_title))
        button(getString(R.string.xiaomi_perms)) {
            val i = Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", packageName)
            open(i)
        }
        button(getString(R.string.xiaomi_autostart)) {
            open(Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        }
        note(getString(R.string.xiaomi_note))
    }

    private fun wakeSection() {
        title(getString(R.string.wake_title))
        val always = Sleep.alwaysListen(this)
        item(getString(if (always) R.string.wake_always else R.string.wake_sleep_only), always) {
            Sleep.setAlwaysListen(this, !always)
            KeeperService.start(this)
            rebuild()
        }
        note(getString(R.string.wake_battery))
        // Чувствительность к «Алёша».
        note(getString(R.string.sens_title))
        val sens = Profile.sensitivity(this)
        listOf(R.string.sens_high, R.string.sens_mid, R.string.sens_low).forEachIndexed { i, res ->
            if (i == sens) tile("✓  " + getString(res), Tile.DONE) {} else button(getString(res)) {
                Profile.setSensitivity(this, i)
                rebuild()
            }
        }
        note(getString(R.string.sens_note))
        Profile.lastWake?.let { note(getString(R.string.wake_last, it)) }
        note(getString(R.string.wake_stats, Sleep.heardCount(this), Sleep.openedCount(this)))
        note(getString(R.string.wake_stats_note))
        button(getString(R.string.wake_stats_reset)) {
            Sleep.resetCounts(this)
            rebuild()
        }
    }

    /** Погода: только Open-Meteo; здесь — проверка и последняя ошибка. */
    private fun weatherSection() {
        title(getString(R.string.weather_title))
        note(getString(R.string.weather_openmeteo))
        button(getString(R.string.weather_check)) {
            val phr = Phrases.of(this)
            App.instance.speaker.say(phr.weatherChecking)
            Weather.get(this) { r ->
                val text = r.error?.let { phr.weatherError(it) } ?: r.now?.let { phr.weatherNow(it) } ?: ""
                App.instance.speaker.say(text, flush = false)
                rebuild()
            }
        }
        Weather.lastError?.let { note(getString(R.string.weather_error, it)) }
    }

    /** Выбор голоса: каждый можно послушать, нажатие сразу выбирает. */
    private fun voiceSection() {
        val sp = App.instance.speaker
        val voices = sp.russianVoices()
        title(getString(R.string.voice_title))
        if (voices.isEmpty()) {
            note(getString(R.string.voice_none))
            return
        }
        note(getString(R.string.voice_note))
        val current = sp.currentVoiceName
        voices.forEachIndexed { i, v ->
            val label = getString(R.string.voice_item, i + 1)
            if (v.name == current) {
                tile("✓  " + label + getString(R.string.voice_selected), Tile.DONE) { sample(v.name) }
            } else {
                button(label) { sample(v.name) }
            }
        }
    }

    private fun sample(name: String) {
        val sp = App.instance.speaker
        sp.selectVoice(name)
        sp.say(Phrases.of(this).voiceSample)
        rebuild()
    }

    private fun contactsReport() {
        if (!Setup.granted(this, Manifest.permission.READ_CONTACTS)) return
        val people = Contacts.load(this)
        val ears = App.instance.ears
        title(getString(R.string.contacts_title, people.size))
        note(getString(R.string.contacts_note))
        if (ears.model == null) {
            note(getString(R.string.contacts_wait))
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
