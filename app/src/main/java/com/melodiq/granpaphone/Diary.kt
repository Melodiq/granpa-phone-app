package com.melodiq.granpaphone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class Note(val id: Int, val text: String, val time: Long)

/** Ежедневник: записи, будильники-напоминания и список «ещё не услышанных» напоминаний. */
object Diary {

    private const val HOUR = 3_600_000L
    /** За сутки, за 6 часов, за 3 часа, за час. В сам момент события не напоминаем. */
    val OFFSETS = longArrayOf(24 * HOUR, 6 * HOUR, 3 * HOUR, 1 * HOUR)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("diary", Context.MODE_PRIVATE)

    @Synchronized
    fun all(ctx: Context): List<Note> {
        val raw = prefs(ctx).getString("notes", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val now = System.currentTimeMillis()
        val list = ArrayList<Note>()
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k) ?: continue
            list.add(Note(o.optInt("id"), o.optString("text"), o.optLong("time")))
        }
        val alive = list.filter { it.time > now }
        if (alive.size != list.size) save(ctx, alive)
        return alive.sortedBy { it.time }
    }

    private fun save(ctx: Context, notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("time", it.time)) }
        prefs(ctx).edit().putString("notes", arr.toString()).apply()
    }

    fun get(ctx: Context, id: Int): Note? = all(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun add(ctx: Context, text: String, time: Long): Note {
        val p = prefs(ctx)
        val id = p.getInt("nextId", 1)
        p.edit().putInt("nextId", if (id >= 200_000) 1 else id + 1).apply()
        val note = Note(id, text, time)
        save(ctx, all(ctx) + note)
        schedule(ctx, note)
        return note
    }

    @Synchronized
    fun remove(ctx: Context, id: Int) {
        val note = all(ctx).firstOrNull { it.id == id } ?: return
        cancel(ctx, note)
        save(ctx, all(ctx).filter { it.id != id })
        removePending(ctx, id)
    }

    /** Сколько напоминаний ещё впереди у записи. */
    fun upcomingReminders(note: Note): Int {
        val now = System.currentTimeMillis()
        return OFFSETS.count { note.time - it > now }
    }

    private fun reminderIntent(ctx: Context, note: Note, k: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_REMIND)
            .putExtra("id", note.id)
        return PendingIntent.getBroadcast(
            ctx, note.id * 10 + k, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(ctx: Context, note: Note) {
        val now = System.currentTimeMillis()
        OFFSETS.forEachIndexed { k, off ->
            val at = note.time - off
            if (at > now) setAlarm(ctx, at, reminderIntent(ctx, note, k))
        }
    }

    fun cancel(ctx: Context, note: Note) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        OFFSETS.indices.forEach { k -> am.cancel(reminderIntent(ctx, note, k)) }
    }

    fun rescheduleAll(ctx: Context) {
        all(ctx).forEach { schedule(ctx, it) }
        if (pending(ctx).isNotEmpty()) Reminders.scheduleRepeat(ctx)
    }

    fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactOk) {
                val show = PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** «врач, завтра в пятнадцать часов». */
    fun describe(ctx: Context, note: Note): String = Phrases.of(ctx).describeNote(note)

    // --- Напоминания, которые дед ещё не услышал при разблокированном экране ---

    @Synchronized
    fun addPending(ctx: Context, id: Int) {
        val s = pendingIds(ctx).toMutableSet()
        s.add(id.toString())
        prefs(ctx).edit().putStringSet("pending", s).apply()
    }

    @Synchronized
    private fun removePending(ctx: Context, id: Int) {
        val s = pendingIds(ctx).toMutableSet()
        if (s.remove(id.toString())) prefs(ctx).edit().putStringSet("pending", s).apply()
    }

    private fun pendingIds(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("pending", emptySet())?.toSet() ?: emptySet()

    fun pending(ctx: Context): List<Note> {
        val ids = pendingIds(ctx)
        return all(ctx).filter { it.id.toString() in ids }
    }

    @Synchronized
    fun clearPending(ctx: Context) {
        prefs(ctx).edit().remove("pending").apply()
    }
}
