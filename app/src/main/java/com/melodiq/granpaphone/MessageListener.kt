package com.melodiq.granpaphone

import android.app.Notification
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/** Одно входящее сообщение: от кого, в каком приложении, текст, когда. */
data class Msg(val sender: String, val pkg: String, val app: String, val text: String, val time: Long)

/**
 * Слушает уведомления о сообщениях (СМС, Макс, Телеграм, WhatsApp…) и запоминает последние —
 * чтобы на «кто написал?» сказать, кто и где. Включается в «Доступе к уведомлениям».
 */
class MessageListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val n = sbn.notification ?: return
            if (sbn.isOngoing) return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            if (sbn.packageName == packageName) return
            if (!isMessaging(this, sbn.packageName, n)) return
            val ex = n.extras ?: return
            // В «переписке» (MessagingStyle) берём автора последнего сообщения.
            var sender = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            var text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (msgs != null && msgs.isNotEmpty()) {
                val last = msgs.last() as? android.os.Bundle
                last?.getCharSequence("text")?.toString()?.let { text = it }
                val who = last?.getCharSequence("sender")?.toString()
                    ?: (if (Build.VERSION.SDK_INT >= 28) (last?.get("sender_person") as? android.app.Person)?.name?.toString() else null)
                if (who != null) sender = if (sender != null && sender != who) "$who ($sender)" else who
            }
            if (sender.isNullOrBlank()) sender = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            if (sender.isNullOrBlank() || text.isNullOrBlank()) return
            store(this, Msg(sender!!.trim(), sbn.packageName, appLabel(this, sbn.packageName), text!!.trim(), sbn.postTime))
        } catch (_: Exception) {}
    }

    companion object {
        private const val MAX_KEEP = 30

        private val KNOWN = setOf(
            "ru.oneme.app", "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
            "com.whatsapp", "com.whatsapp.w4b", "com.viber.voip", "com.vkontakte.android",
            "com.google.android.apps.messaging", "com.android.mms", "com.miui.mms", "com.samsung.android.messaging",
            "com.transsion.message", "com.android.messaging"
        )

        private fun isMessaging(ctx: Context, pkg: String, n: Notification): Boolean {
            if (pkg in KNOWN) return true
            if (pkg == Telephony.Sms.getDefaultSmsPackage(ctx)) return true
            return n.category == Notification.CATEGORY_MESSAGE
        }

        fun isSms(ctx: Context, pkg: String) =
            pkg == Telephony.Sms.getDefaultSmsPackage(ctx) || pkg.contains("mms") || pkg.contains("messaging") || pkg == "com.transsion.message"

        private fun appLabel(ctx: Context, pkg: String): String = try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("messages", Context.MODE_PRIVATE)

        @Synchronized
        private fun store(ctx: Context, m: Msg) {
            val list = all(ctx).toMutableList()
            // Одно и то же уведомление приходит повторно при обновлении — не дублируем.
            if (list.any { it.pkg == m.pkg && it.sender == m.sender && it.text == m.text }) return
            list.add(0, m)
            val arr = JSONArray()
            list.take(MAX_KEEP).forEach {
                arr.put(JSONObject().put("s", it.sender).put("p", it.pkg).put("a", it.app).put("t", it.text).put("w", it.time))
            }
            prefs(ctx).edit().putString("list", arr.toString()).apply()
        }

        /** Последние сообщения, новые первыми. */
        fun all(ctx: Context): List<Msg> = try {
            val arr = JSONArray(prefs(ctx).getString("list", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Msg(o.getString("s"), o.getString("p"), o.getString("a"), o.getString("t"), o.getLong("w"))
            }
        } catch (e: Exception) { emptyList() }

        fun enabled(ctx: Context): Boolean =
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }
}
