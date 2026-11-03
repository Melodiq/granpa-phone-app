package com.melodiq.granpaphone

import android.content.Context
import android.provider.CallLog
import java.util.Calendar

/** Последний пропущенный вызов из журнала звонков. */
data class Missed(val name: String, val number: String, val time: Long, val todayCount: Int)

object CallLogReader {
    fun lastMissed(ctx: Context): Missed? {
        return try {
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            var last: Missed? = null
            var today = 0
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                "${CallLog.Calls.TYPE} = ?", arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val date = c.getLong(2)
                    if (last == null) {
                        val number = c.getString(0) ?: ""
                        val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: Contacts.nameFor(ctx, number)
                        last = Missed(name, number, date, 0)
                    }
                    if (date >= startOfDay) today++ else break
                }
            }
            last?.copy(todayCount = today)
        } catch (e: SecurityException) {
            null
        }
    }
}
