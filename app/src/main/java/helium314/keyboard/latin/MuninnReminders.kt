// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import java.util.Calendar

/**
 * Muninn, Layer 3: reminders on saved clips.
 *
 * Rules (from the design):
 * - shown when the keyboard opens, never while typing; one per opening
 * - at most [Settings.PREF_MUNINN_REMIND_PER_DAY] different reminders a day
 * - nothing during quiet hours
 * - Calm and Noticeable reminders appear in the keyboard; Loud ones also send a notification
 * - Done clears the reminder, Later brings it back in 3 hours, Let go moves the clip to Deleted
 */
object MuninnReminders {
    const val CALM = 0
    const val NOTICEABLE = 1
    const val LOUD = 2
    /** after this many "Later"s Muninn asks whether to let the clip go */
    const val SKIPS_BEFORE_ASKING = 3

    private const val TAG = "MuninnReminders"
    private const val CHANNEL_ID = "muninn_reminders"
    private const val PREF_SHOWN_DAY = "muninn_reminders_shown_day"
    private const val PREF_SHOWN_IDS = "muninn_reminders_shown_ids"
    private const val PREF_NOTIFIED = "muninn_reminders_notified"

    const val ACTION_ALARM = "app.muninn.keyboard.REMINDER_ALARM"
    const val ACTION_DONE = "app.muninn.keyboard.REMINDER_DONE"
    const val ACTION_LATER = "app.muninn.keyboard.REMINDER_LATER"
    const val EXTRA_CLIP_ID = "clip_id"

    // ---- settings ----

    fun enabled(context: Context) = context.prefs().getBoolean(Settings.PREF_MUNINN_REMINDERS, Defaults.PREF_MUNINN_REMINDERS)
    fun defaultStrength(context: Context) = context.prefs().getInt(Settings.PREF_MUNINN_REMIND_STRENGTH, Defaults.PREF_MUNINN_REMIND_STRENGTH)
    private fun perDay(context: Context) = context.prefs().getInt(Settings.PREF_MUNINN_REMIND_PER_DAY, Defaults.PREF_MUNINN_REMIND_PER_DAY)
    private fun quietOn(context: Context) = context.prefs().getBoolean(Settings.PREF_MUNINN_QUIET_HOURS, Defaults.PREF_MUNINN_QUIET_HOURS)
    private fun quietStart(context: Context) = context.prefs().getInt(Settings.PREF_MUNINN_QUIET_START, Defaults.PREF_MUNINN_QUIET_START)
    private fun quietEnd(context: Context) = context.prefs().getInt(Settings.PREF_MUNINN_QUIET_END, Defaults.PREF_MUNINN_QUIET_END)
    fun offerLetGo(context: Context) = context.prefs().getBoolean(Settings.PREF_MUNINN_OFFER_LET_GO, Defaults.PREF_MUNINN_OFFER_LET_GO)

    fun isQuietNow(context: Context, now: Long = System.currentTimeMillis()) =
        quietOn(context) && MuninnReminderTimes.isQuiet(now, quietStart(context), quietEnd(context))

    // ---- setting and acting on reminders ----

    fun set(context: Context, clipId: Long, time: Long, strength: Int = defaultStrength(context)) {
        ClipboardDao.getInstance(context)?.setReminder(clipId, time, strength)
        clearNotified(context, clipId)
        scheduleLoud(context)
    }

    fun set(context: Context, clipId: Long, preset: MuninnReminderTimes.Preset, strength: Int = defaultStrength(context)) =
        set(context, clipId, MuninnReminderTimes.presetTime(preset, System.currentTimeMillis()), strength)

    fun done(context: Context, clipId: Long) {
        ClipboardDao.getInstance(context)?.setReminder(clipId, null)
        afterAction(context, clipId)
    }

    fun later(context: Context, clipId: Long) {
        val dao = ClipboardDao.getInstance(context) ?: return
        val entry = dao.withReminders().firstOrNull { it.id == clipId } ?: return
        val time = MuninnReminderTimes.laterTime(System.currentTimeMillis(), quietStart(context), quietEnd(context), quietOn(context))
        dao.setReminder(clipId, time, entry.remindStrength, entry.remindSkips + 1)
        afterAction(context, clipId)
    }

    /** let it go: the reminder ends and the clip moves to Deleted (it can still be restored for 30 days) */
    fun letGo(context: Context, clipId: Long) {
        val dao = ClipboardDao.getInstance(context) ?: return
        dao.setReminder(clipId, null)
        dao.deleteById(clipId)
        afterAction(context, clipId)
    }

    private fun afterAction(context: Context, clipId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(clipId))
        clearNotified(context, clipId)
        scheduleLoud(context)
    }

    // ---- the keyboard ----

    /** The reminder to show now that the keyboard opened, or null. Counts it towards today's limit. */
    fun pickForKeyboard(context: Context, now: Long = System.currentTimeMillis()): ClipboardHistoryEntry? {
        if (!enabled(context) || isQuietNow(context, now)) return null
        val dao = ClipboardDao.getInstance(context) ?: return null
        val due = dao.withReminders().firstOrNull { it.isDue(now) && !it.isCode } ?: return null
        val prefs = context.prefs()
        val today = MuninnReminderTimes.dayKey(now)
        val shown = if (prefs.getInt(PREF_SHOWN_DAY, 0) == today)
            prefs.getString(PREF_SHOWN_IDS, "")!!.split(',').filter { it.isNotEmpty() }.toMutableSet()
            else mutableSetOf()
        val key = due.id.toString()
        if (key !in shown) {
            if (shown.size >= perDay(context)) return null // today's limit reached
            shown.add(key)
            prefs.edit { putInt(PREF_SHOWN_DAY, today).putString(PREF_SHOWN_IDS, shown.joinToString(",")) }
        }
        return due
    }

    /** "today 19:00", "tomorrow 09:00", or "Thu 13 Nov 09:00" */
    fun describeTime(context: Context, time: Long, now: Long = System.currentTimeMillis()): String {
        val clock = DateFormat.getTimeFormat(context).format(time)
        val day = MuninnReminderTimes.dayKey(time)
        val today = MuninnReminderTimes.dayKey(now)
        val tomorrow = MuninnReminderTimes.dayKey(now + 24 * MuninnReminderTimes.HOUR)
        return when (day) {
            today -> context.getString(R.string.muninn_remind_today_at, clock)
            tomorrow -> context.getString(R.string.muninn_remind_tomorrow_at, clock)
            else -> DateFormat.format("EEE d MMM", Calendar.getInstance().apply { timeInMillis = time }).toString() + " " + clock
        }
    }

    // ---- loud reminders: notifications ----

    private fun notificationId(clipId: Long) = (clipId % Int.MAX_VALUE).toInt() + 1000

    private fun notifiedSet(context: Context) =
        context.prefs().getString(PREF_NOTIFIED, "")!!.split(',').filter { it.isNotEmpty() }.toMutableSet()

    private fun clearNotified(context: Context, clipId: Long) {
        val set = notifiedSet(context)
        if (set.removeAll { it.startsWith("$clipId:") })
            context.prefs().edit { putString(PREF_NOTIFIED, set.joinToString(",")) }
    }

    /** Set an alarm for the next loud reminder that has not been sent yet. Call after any reminder change. */
    fun scheduleLoud(context: Context) {
        try {
            val dao = ClipboardDao.getInstance(context) ?: return
            val notified = notifiedSet(context)
            val next = dao.withReminders()
                .filter { it.remindStrength == LOUD && !it.isCode && "${it.id}:${it.remindAt}" !in notified }
                .minOfOrNull { it.remindAt!! }
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = alarmIntent(context)
            if (next == null || !enabled(context)) {
                am.cancel(pi)
                return
            }
            val time = MuninnReminderTimes.outsideQuietHours(
                maxOf(next, System.currentTimeMillis() + 2000), quietStart(context), quietEnd(context), quietOn(context))
            // inexact but allowed in doze; exact alarms need a special permission that Muninn does not ask for
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        } catch (e: Exception) {
            Log.w(TAG, "could not schedule reminder", e)
        }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, MuninnReminderReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** send notifications for loud reminders that are due now */
    fun notifyDue(context: Context) {
        val dao = ClipboardDao.getInstance(context) ?: return
        val now = System.currentTimeMillis()
        if (!enabled(context) || isQuietNow(context, now)) {
            scheduleLoud(context)
            return
        }
        val notified = notifiedSet(context)
        val due = dao.withReminders().filter { it.remindStrength == LOUD && it.isDue(now) && !it.isCode && "${it.id}:${it.remindAt}" !in notified }
        if (due.isNotEmpty() && canNotify(context)) {
            ensureChannel(context)
            due.forEach { post(context, it) }
        }
        due.forEach { notified.add("${it.id}:${it.remindAt}") }
        context.prefs().edit { putString(PREF_NOTIFIED, notified.joinToString(",")) }
        scheduleLoud(context)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, context.getString(R.string.muninn_reminders), NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.muninn_reminders_description) })
    }

    private fun post(context: Context, clip: ClipboardHistoryEntry) {
        fun action(action: String) = PendingIntent.getBroadcast(
            context, (clip.id * 10 + action.length).toInt(),
            Intent(context, MuninnReminderReceiver::class.java).setAction(action).putExtra(EXTRA_CLIP_ID, clip.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = clip.text?.take(300) ?: context.getString(R.string.muninn_cache_file)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_muninn_feather_small)
            .setColor(0xFFE8B04B.toInt())
            .setContentTitle(context.getString(R.string.muninn_reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.muninn_reminder_done), action(ACTION_DONE))
            .addAction(0, context.getString(R.string.muninn_reminder_later), action(ACTION_LATER))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(clip.id), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notification not allowed", e)
        }
    }
}

/** Muninn: the alarm for loud reminders, and the Done / Later buttons on their notifications */
class MuninnReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clipId = intent.getLongExtra(MuninnReminders.EXTRA_CLIP_ID, -1)
        when (intent.action) {
            MuninnReminders.ACTION_ALARM -> MuninnReminders.notifyDue(context)
            MuninnReminders.ACTION_DONE -> if (clipId >= 0) MuninnReminders.done(context, clipId)
            MuninnReminders.ACTION_LATER -> if (clipId >= 0) MuninnReminders.later(context, clipId)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> MuninnReminders.scheduleLoud(context)
        }
    }
}
