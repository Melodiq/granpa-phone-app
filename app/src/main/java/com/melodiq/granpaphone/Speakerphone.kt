package com.melodiq.granpaphone

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Громкая связь в начале каждого звонка (входящего и исходящего).
 * Сначала просим систему, а если звонилка не послушалась — нажимаем её кнопку «Динамик»
 * через службу специальных возможностей.
 */
object Speakerphone {
    private val main = Handler(Looper.getMainLooper())
    private var session = 0
    @Volatile var lastLog = ""
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("speaker", Context.MODE_PRIVATE)
    fun auto(ctx: Context) = prefs(ctx).getBoolean("auto", true)
    fun setAuto(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("auto", on).apply()

    /** Звонок начался (трубка снята или номер набирается). */
    fun onCallStarted(ctx: Context) {
        if (!auto(ctx)) return
        val s = ++session
        lastLog = ""
        // Звонилке нужно время, чтобы открыться; пробуем несколько раз.
        for (delay in longArrayOf(600, 1500, 3000, 5000)) {
            main.postDelayed({ if (s == session) tryEnable(ctx) }, delay)
        }
    }

    fun onCallEnded() { session++ }

    private fun isOn(am: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            if (am.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return true
        }
        @Suppress("DEPRECATION")
        return am.isSpeakerphoneOn
    }

    private fun tryEnable(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        if (isOn(am) || uiSpeakerChecked() == true) { note("✓ громкая связь включена"); session++; return }
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { am.setCommunicationDevice(it) }
            }
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
        } catch (_: Exception) {}
        // Проверим чуть позже; если система не включила — жмём кнопку звонилки.
        main.postDelayed({
            if (isOn(am) || uiSpeakerChecked() == true) { note("✓ включена системой"); session++ }
            else if (clickSpeakerButton()) { note("✓ нажата кнопка «Динамик»"); session++ }
            else note("… кнопка «Динамик» пока не найдена")
        }, 400)
    }

    private val LABELS = listOf("динамик", "громк", "speaker", "扬声器", "免提")

    private fun findSpeaker(): AccessibilityNodeInfo? {
        val svc: AccessibilityService = ScreenReaderService.instance ?: return null
        val roots = ArrayList<AccessibilityNodeInfo>()
        try { svc.windows.forEach { w -> w.root?.let { roots.add(it) } } } catch (_: Exception) {}
        svc.rootInActiveWindow?.let { roots.add(0, it) }
        for (r in roots) {
            if (r.packageName?.toString() == svc.packageName) continue
            var hit: AccessibilityNodeInfo? = null
            fun walk(n: AccessibilityNodeInfo?, d: Int) {
                if (n == null || hit != null || d > 30) return
                val l = ((n.text ?: "").toString() + " " + (n.contentDescription ?: "").toString()).lowercase()
                if (n.isVisibleToUser && l.length < 40 && LABELS.any { l.contains(it) }) { hit = n; return }
                for (i in 0 until n.childCount) walk(try { n.getChild(i) } catch (e: Exception) { null }, d + 1)
            }
            walk(r, 0)
            if (hit != null) return hit
        }
        return null
    }

    /** true/false — состояние кнопки «Динамик», null — кнопки не видно. */
    private fun uiSpeakerChecked(): Boolean? {
        val n = findSpeaker() ?: return null
        var c: AccessibilityNodeInfo? = n
        repeat(3) { if (c?.isCheckable == true) return c!!.isChecked; c = c?.parent }
        if (n.isSelected) return true
        return null
    }

    private fun clickSpeakerButton(): Boolean {
        val n = findSpeaker() ?: return false
        var c: AccessibilityNodeInfo? = n
        repeat(5) {
            if (c?.isClickable == true && c!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            c = c?.parent
        }
        return false
    }

    private fun note(s: String) { lastLog = s }
}
