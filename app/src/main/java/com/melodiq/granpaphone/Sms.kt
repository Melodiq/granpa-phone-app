package com.melodiq.granpaphone

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/** Обычная СМС: отправляется напрямую, без открытия приложения сообщений. */
object Sms {
    fun send(ctx: Context, number: String, text: String): Boolean = try {
        @Suppress("DEPRECATION")
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        val parts = sm.divideMessage(text)
        if (parts.size > 1) sm.sendMultipartTextMessage(number, null, parts, null, null)
        else sm.sendTextMessage(number, null, text, null, null)
        true
    } catch (e: Exception) {
        false
    }
}
