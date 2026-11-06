// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.text.format.DateFormat
import android.widget.RemoteViews
import android.widget.Toast
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
 * Muninn: reminders on saved clips.
 *
 * A reminder keeps coming back, at irregular and growing gaps, until it is marked Done or stopped.
 * Every time it comes back it uses all three ways, each where it fits:
 * - Calm: while any reminder is open, the clipboard button above the keys is gold. Always there, never in the way.
 * - Noticeable: a gold chip with one glow and two soft taps, but only when the keyboard opens, never while typing.
 * - Loud: a phone notification. If a reminder comes due while the keyboard is in use, this is the only one used.
 * Limits: a daily maximum, and nothing during quiet hours. After 3 "Later"s the chip asks whether it's still wanted.
 */
object MuninnReminders {
    /** after this many "Later"s Muninn asks "Still want this?" */
    const val SKIPS_BEFORE_ASKING = 3

    private const val TAG = "MuninnReminders"
    private const val CHANNEL_ID = "muninn_reminders"
    private const val PREF_DAY = "muninn_reminders_day"
    private const val PREF_DAY_COUNT = "muninn_reminders_day_count"
    private const val PREF_PENDING_CHIP = "muninn_reminders_pending_chip"

    const val ACTION_ALARM = "app.muninn.keyboard.REMINDER_ALARM"
    const val ACTION_DONE = "app.muninn.keyboard.REMINDER_DONE"
    const val ACTION_LATER = "app.muninn.keyboard.REMINDER_LATER"
    const val ACTION_COPY = "app.muninn.keyboard.REMINDER_COPY"
    const val EXTRA_CLIP_ID = "clip_id"

    private val random = java.security.SecureRandom() // Muninn: truly unpredictable times and ways

    // ---- settings ----

    fun enabled(context: Context) = context.prefs().getBoolean(Settings.PREF_MUNINN_REMINDERS, Defaults.PREF_MUNINN_REMINDERS)
    private fun perDay(context: Context) = context.prefs().getInt(Settings.PREF_MUNINN_REMIND_PER_DAY, Defaults.PREF_MUNINN_REMIND_PER_DAY)
    private fun quietOn(context: Context) = context.prefs().getBoolean(Settings.PREF_MUNINN_QUIET_HOURS, Defaults.PREF_MUNINN_QUIET_HOURS)
    private fun quietStart(context: Context) = context.prefs().getInt(Settings.PREF_MUNINN_QUIET_START, Defaults.PREF_MUNINN_QUIET_START)
    private fun quietEnd(context: Context) = context.prefs().getInt(Settings.PREF_MUNINN_QUIET_END, Defaults.PREF_MUNINN_QUIET_END)
    fun askIfStillWanted(context: Context) = context.prefs().getBoolean(Settings.PREF_MUNINN_OFFER_LET_GO, Defaults.PREF_MUNINN_OFFER_LET_GO)

    fun isQuietNow(context: Context, now: Long = System.currentTimeMillis()) =
        quietOn(context) && MuninnReminderTimes.isQuiet(now, quietStart(context), quietEnd(context))

    /** Calm: is any reminder open? (the clipboard button turns gold) */
    fun hasOpenReminders(context: Context) = enabled(context) && ClipboardDao.getInstance(context)?.withReminders()?.isNotEmpty() == true

    // ---- setting and ending reminders ----

    fun set(context: Context, clipId: Long, time: Long) {
        val first = MuninnReminderTimes.outsideQuietHours(time, quietStart(context), quietEnd(context), quietOn(context))
        ClipboardDao.getInstance(context)?.setReminder(clipId, first)
        afterChange(context, clipId)
    }

    fun set(context: Context, clipId: Long, preset: MuninnReminderTimes.Preset) =
        set(context, clipId, MuninnReminderTimes.presetTime(preset, System.currentTimeMillis()))

    /** the thing is done: the reminder ends, the clip stays */
    fun done(context: Context, clipId: Long) {
        ClipboardDao.getInstance(context)?.setReminder(clipId, null)
        afterChange(context, clipId)
    }

    /** stop reminding: the reminder ends, the clip stays (same as done, said differently) */
    fun stop(context: Context, clipId: Long) = done(context, clipId)

    /** not now: comes back in about 3 hours (outside quiet hours) */
    fun later(context: Context, clipId: Long) {
        val dao = ClipboardDao.getInstance(context) ?: return
        val entry = dao.withReminders().firstOrNull { it.id == clipId } ?: return
        val time = MuninnReminderTimes.laterTime(System.currentTimeMillis(), random, quietStart(context), quietEnd(context), quietOn(context))
        dao.setReminder(clipId, time, entry.remindShown, entry.remindSkips + 1)
        afterChange(context, clipId)
    }

    private fun afterChange(context: Context, clipId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(clipId))
        removePending(context, clipId)
        scheduleNext(context)
    }

    // ---- counting: at most a few times a day ----

    /** Muninn: no daily limit any more; how often things come back is left to chance */
    @Suppress("UNUSED_PARAMETER")
    private fun countOk(context: Context, now: Long): Boolean = true

    /** a reminder came back once: count it and plan when it comes back next */
    private fun recordOccurrence(context: Context, entry: ClipboardHistoryEntry, now: Long) {
        val prefs = context.prefs()
        val today = MuninnReminderTimes.dayKey(now)
        val count = if (prefs.getInt(PREF_DAY, 0) == today) prefs.getInt(PREF_DAY_COUNT, 0) else 0
        prefs.edit { putInt(PREF_DAY, today).putInt(PREF_DAY_COUNT, count + 1) }
        val shown = entry.remindShown + 1
        val next = MuninnReminderTimes.nextOccurrence(shown, now, random, quietStart(context), quietEnd(context), quietOn(context))
        ClipboardDao.getInstance(context)?.setReminder(entry.id, next, shown, entry.remindSkips)
    }

    // ---- chips waiting for the next time the keyboard opens ----

    private fun pending(context: Context) =
        context.prefs().getString(PREF_PENDING_CHIP, "")!!.split(',').filter { it.isNotEmpty() }.toMutableSet()

    private fun addPending(context: Context, clipId: Long) {
        val set = pending(context)
        if (set.add(clipId.toString())) context.prefs().edit { putString(PREF_PENDING_CHIP, set.joinToString(",")) }
    }

    private fun removePending(context: Context, clipId: Long) {
        val set = pending(context)
        if (set.remove(clipId.toString())) context.prefs().edit { putString(PREF_PENDING_CHIP, set.joinToString(",")) }
    }

    // ---- Noticeable: the keyboard ----

    /** The reminder to show as a chip now that the keyboard opened, or null. */
    fun pickForKeyboard(context: Context, now: Long = System.currentTimeMillis()): ClipboardHistoryEntry? {
        if (!enabled(context) || isQuietNow(context, now)) return null
        val dao = ClipboardDao.getInstance(context) ?: return null
        val open = dao.withReminders().filter { !it.isCode }
        // a reminder that already came back as a notification: show it in the keyboard too (already counted)
        val waiting = pending(context)
        open.firstOrNull { it.id.toString() in waiting }?.let {
            removePending(context, it.id)
            return it
        }
        // a reminder due right now: the person is here, so the chip is enough (no notification)
        val due = open.firstOrNull { it.isDue(now) } ?: return null
        if (!countOk(context, now)) return null
        recordOccurrence(context, due, now)
        scheduleNext(context)
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

    // ---- Loud: notifications, planned with an alarm ----

    private fun notificationId(clipId: Long) = (clipId % Int.MAX_VALUE).toInt() + 1000

    /** set an alarm for the next time any reminder is due. Call after every change. */
    fun scheduleNext(context: Context) {
        try {
            val dao = ClipboardDao.getInstance(context) ?: return
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = alarmIntent(context)
            val next = dao.withReminders().filter { !it.isCode }.minOfOrNull { it.remindAt!! }
            if (next == null || !enabled(context)) {
                am.cancel(pi)
                return
            }
            val time = MuninnReminderTimes.outsideQuietHours(
                maxOf(next, System.currentTimeMillis() + 2000), quietStart(context), quietEnd(context), quietOn(context))
            // inexact but allowed in doze; exact alarms would need a special permission Muninn does not ask for
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

    /** the alarm went off: every due reminder comes back as a notification, and as a chip the next time the keyboard opens */
    fun onAlarm(context: Context) {
        val dao = ClipboardDao.getInstance(context) ?: return
        val now = System.currentTimeMillis()
        if (!enabled(context)) return scheduleNext(context)
        if (isQuietNow(context, now)) return scheduleNext(context)
        dao.withReminders().filter { it.isDue(now) && !it.isCode }.forEach { clip ->
            if (!countOk(context, now)) {
                // today's limit is reached: try again tomorrow morning
                val tomorrow = MuninnReminderTimes.presetTime(MuninnReminderTimes.Preset.TOMORROW_MORNING, now)
                dao.setReminder(clip.id, tomorrow, clip.remindShown, clip.remindSkips)
                return@forEach
            }
            // the ways it comes back are chosen at random each time (the gold clipboard button is always on while due)
            val ways = if (canNotify(context)) MuninnReminderTimes.pickWays(random) else MuninnReminderTimes.Ways.CHIP
            if (ways != MuninnReminderTimes.Ways.CHIP) post(context, clip)
            if (ways != MuninnReminderTimes.Ways.NOTIFICATION) addPending(context, clip.id)
            recordOccurrence(context, clip, now)
        }
        scheduleNext(context)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, context.getString(R.string.muninn_reminders), NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.muninn_reminders_description) })
    }

    /** the feather on raven black, used as the notification's picture */
    private fun featherBitmap(context: Context): Bitmap? = try {
        val size = (48 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A28.toInt() })
        ContextCompat.getDrawable(context, R.drawable.ic_muninn_feather)?.let {
            val w = (size * 0.24f).toInt()
            val h = (size * 0.6f).toInt()
            canvas.save()
            canvas.rotate(18f, size / 2f, size / 2f)
            it.setBounds((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2)
            it.draw(canvas)
            canvas.restore()
        }
        bitmap
    } catch (e: Exception) {
        null
    }

    private fun post(context: Context, clip: ClipboardHistoryEntry) {
        ensureChannel(context)
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
        val meta = notificationMeta(context, clip)

        // Muninn's look: gold title, the clip, then where and when it was saved
        fun views(big: Boolean) = RemoteViews(context.packageName, R.layout.muninn_notification).apply {
            setTextViewText(R.id.muninn_notification_title, context.getString(R.string.muninn_reminder_title))
            setTextViewText(R.id.muninn_notification_text, text)
            setInt(R.id.muninn_notification_text, "setMaxLines", if (big) 6 else 2)
            setTextViewText(R.id.muninn_notification_meta, meta)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_muninn_feather_small)
            .setColor(0xFFE8B04B.toInt())
            .setContentTitle(context.getString(R.string.muninn_reminder_title))
            .setContentText(text)
            .setSubText(context.getString(R.string.muninn_reminders))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views(false))
            .setCustomBigContentView(views(true))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(R.drawable.ic_muninn_check, context.getString(R.string.muninn_reminder_done), action(ACTION_DONE))
            .addAction(R.drawable.ic_muninn_clock, context.getString(R.string.muninn_reminder_later), action(ACTION_LATER))
            .addAction(R.drawable.ic_muninn_clipboard, context.getString(R.string.muninn_reminder_copy), action(ACTION_COPY))
        featherBitmap(context)?.let { builder.setLargeIcon(it) }
        try {
            NotificationManagerCompat.from(context).notify(notificationId(clip.id), builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "notification not allowed", e)
        }
    }

    /** "Saved 6 days ago · Telegram · 3rd time" */
    private fun notificationMeta(context: Context, clip: ClipboardHistoryEntry): String {
        val parts = mutableListOf<String>()
        parts.add(context.getString(R.string.muninn_saved_ago,
            android.text.format.DateUtils.getRelativeTimeSpanString(clip.timeStamp, System.currentTimeMillis(),
                android.text.format.DateUtils.MINUTE_IN_MILLIS, android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE)))
        MuninnClips.appLabel(context, clip.source)?.let { parts.add(it) }
        if (clip.remindShown > 0) parts.add(context.resources.getQuantityString(R.plurals.muninn_times_back, clip.remindShown + 1, clip.remindShown + 1))
        return parts.joinToString(" · ")
    }

    fun copy(context: Context, clipId: Long) {
        val clip = ClipboardDao.getInstance(context)?.withReminders()?.firstOrNull { it.id == clipId } ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("", clip.text ?: return))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            Toast.makeText(context, R.string.muninn_cache_copied, Toast.LENGTH_SHORT).show()
    }
}

/** Muninn: the reminder alarm, and the Done / Later / Copy buttons on reminder notifications */
class MuninnReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clipId = intent.getLongExtra(MuninnReminders.EXTRA_CLIP_ID, -1)
        when (intent.action) {
            MuninnReminders.ACTION_ALARM -> MuninnReminders.onAlarm(context)
            MuninnReminders.ACTION_DONE -> if (clipId >= 0) MuninnReminders.done(context, clipId)
            MuninnReminders.ACTION_LATER -> if (clipId >= 0) MuninnReminders.later(context, clipId)
            MuninnReminders.ACTION_COPY -> if (clipId >= 0) MuninnReminders.copy(context, clipId)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                MuninnReminders.scheduleNext(context)
                // Layer 4: a restart or an update is when other keyboards most often take over
                MuninnGuard.schedule(context)
                MuninnGuard.check(context)
            }
        }
    }
}
