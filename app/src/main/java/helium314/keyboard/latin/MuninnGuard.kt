// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.prefs

/** Muninn, Layer 4: the record of takeovers, kept as plain lines in a preference. Pure, so it can be tested. */
object MuninnGuardLog {
    data class Entry(val time: Long, val keyboard: String, val switchedBack: Boolean)

    const val MAX_ENTRIES = 20

    fun parse(stored: String?): List<Entry> = stored.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t', limit = 3)
        if (parts.size != 3) return@mapNotNull null
        val time = parts[0].toLongOrNull() ?: return@mapNotNull null
        Entry(time, parts[2], parts[1] == "1")
    }

    /** newest first; the same takeover reported twice within two minutes is kept once */
    fun add(stored: String?, entry: Entry): String {
        val old = parse(stored)
        val first = old.firstOrNull()
        val list = if (first != null && first.keyboard == entry.keyboard && entry.time - first.time < 2 * 60_000L && !entry.switchedBack)
            old else listOf(entry) + old
        return list.take(MAX_ENTRIES).joinToString("\n") { "${it.time}\t${if (it.switchedBack) 1 else 0}\t${it.keyboard.replace('\n', ' ').replace('\t', ' ')}" }
    }

    /** always a new line, for a takeover that came after Muninn was back */
    fun addNew(stored: String?, entry: Entry): String = serialize(listOf(entry) + parse(stored))

    private fun serialize(list: List<Entry>) =
        list.take(MAX_ENTRIES).joinToString("\n") { "${it.time}\t${if (it.switchedBack) 1 else 0}\t${it.keyboard.replace('\n', ' ').replace('\t', ' ')}" }

    fun countSince(entries: List<Entry>, since: Long) = entries.count { it.time >= since }
}

/**
 * Muninn, Layer 4: Keyboard Guard. Android tells Muninn when the default keyboard changes (a job that waits on that
 * setting, so nothing has to keep running). If another keyboard took over, Muninn asks with a notification, or, when the
 * person granted WRITE_SECURE_SETTINGS once from a computer, puts itself back right away.
 * Voice keyboards (the mic key uses Google voice typing) are left alone, and "It was me" pauses the guard for an hour.
 */
object MuninnGuard {
    private const val TAG = "MuninnGuard"
    private const val JOB_ID = 0x4d756e // "Mun"
    private const val CHANNEL_ID = "muninn_guard"
    private const val NOTIFICATION_ID = 7301
    private const val PREF_LOG = "muninn_guard_log"
    private const val PREF_PAUSED_UNTIL = "muninn_guard_paused_until"
    private const val PREF_BACK_AT = "muninn_guard_back_at"
    private const val PAUSE_MS = 60 * 60 * 1000L
    const val ACTION_IT_WAS_ME = "app.muninn.keyboard.GUARD_IT_WAS_ME"
    const val ACTION_LET_STAY = "app.muninn.keyboard.GUARD_LET_STAY"
    const val EXTRA_KEYBOARD_ID = "keyboard_id"

    fun enabled(context: Context) = context.prefs().getBoolean(Settings.PREF_MUNINN_GUARD, Defaults.PREF_MUNINN_GUARD)
    fun autoEnabled(context: Context) = context.prefs().getBoolean(Settings.PREF_MUNINN_GUARD_AUTO, Defaults.PREF_MUNINN_GUARD_AUTO)

    fun muninnComponent(context: Context) = ComponentName(context, LatinIME::class.java)
    fun muninnId(context: Context): String = muninnComponent(context).flattenToShortString()

    fun currentId(context: Context): String? = SystemSettings.Secure.getString(context.contentResolver, SystemSettings.Secure.DEFAULT_INPUT_METHOD)

    fun isMuninnCurrent(context: Context) = currentId(context)?.let { ComponentName.unflattenFromString(it) } == muninnComponent(context)

    private fun imm(context: Context) = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    fun isMuninnEnabled(context: Context) = UncachedInputMethodManagerUtils.isThisImeEnabled(context, imm(context))

    /** true once `adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS` was run */
    fun canSwitchBySelf(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun grantCommand(context: Context) = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    /** the keyboard's own name, e.g. "Gboard" */
    fun keyboardLabel(context: Context, id: String?): String {
        if (id == null) return "?"
        return try {
            imm(context).inputMethodList.firstOrNull { it.id == id }?.loadLabel(context.packageManager)?.toString()
        } catch (_: Exception) { null } ?: id.substringBefore('/')
    }

    /** voice typing: switching to it is how the mic key works, so it is never a takeover */
    private fun isVoiceOnly(context: Context, id: String): Boolean {
        val info = try { imm(context).inputMethodList.firstOrNull { it.id == id } } catch (_: Exception) { null } ?: return false
        if (info.subtypeCount == 0) return info.id.contains("voice", ignoreCase = true)
        return (0 until info.subtypeCount).all { info.getSubtypeAt(it).mode == "voice" }
    }

    fun log(context: Context): List<MuninnGuardLog.Entry> = MuninnGuardLog.parse(context.prefs().getString(PREF_LOG, null))

    fun stoppedThisWeek(context: Context) = MuninnGuardLog.countSince(log(context), System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)

    private fun record(context: Context, label: String, switchedBack: Boolean) {
        val prefs = context.prefs()
        val now = System.currentTimeMillis()
        // Muninn was back in between: this is a new takeover, not the same one reported twice
        val stored = prefs.getString(PREF_LOG, null)
        val last = MuninnGuardLog.parse(stored).firstOrNull()
        val sameOne = last != null && prefs.getLong(PREF_BACK_AT, 0) < last.time
        val entry = MuninnGuardLog.Entry(now, label, switchedBack)
        prefs.edit { putString(PREF_LOG, if (sameOne) MuninnGuardLog.add(stored, entry) else MuninnGuardLog.addNew(stored, entry)) }
    }

    /** wait for the next change of the default keyboard; called from the keyboard, the app, after restarts and updates */
    fun schedule(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (!enabled(context)) {
            js.cancel(JOB_ID)
            return
        }
        try {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, MuninnGuardJobService::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(SystemSettings.Secure.getUriFor(SystemSettings.Secure.DEFAULT_INPUT_METHOD), 0))
                .setTriggerContentUpdateDelay(500)
                .setTriggerContentMaxDelay(3000)
                .build()
            js.schedule(job)
        } catch (e: Exception) {
            Log.w(TAG, "can't schedule the guard", e)
        }
    }

    private var observer: android.database.ContentObserver? = null

    /** listens directly while the process runs; Android's job scheduler can hold jobs back for minutes on HyperOS */
    fun watchWhileRunning(context: Context) {
        if (observer != null) return
        val app = context.applicationContext
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val check = Runnable { check(app) }
        observer = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                // wait a moment: Android sets the new keyboard, then its subtype
                handler.removeCallbacks(check)
                handler.postDelayed(check, 400)
            }
        }
        try {
            app.contentResolver.registerContentObserver(
                SystemSettings.Secure.getUriFor(SystemSettings.Secure.DEFAULT_INPUT_METHOD), false, observer!!)
        } catch (e: Exception) {
            Log.w(TAG, "can't watch the keyboard setting", e)
        }
    }

    /** look at the default keyboard now and act if another one took over */
    fun check(context: Context) {
        if (!enabled(context)) return
        if (isMuninnCurrent(context)) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            context.prefs().edit { putLong(PREF_BACK_AT, System.currentTimeMillis()) }
            return
        }
        if (!isMuninnEnabled(context)) return // the person turned Muninn off in the system list: respect that
        if (System.currentTimeMillis() < context.prefs().getLong(PREF_PAUSED_UNTIL, 0)) return
        val id = currentId(context) ?: return
        if (isVoiceOnly(context, id)) return
        val label = keyboardLabel(context, id)
        if (autoEnabled(context) && canSwitchBySelf(context) && switchBack(context)) {
            record(context, label, switchedBack = true)
            notify(context, label, id, switchedBack = true)
        } else {
            record(context, label, switchedBack = false)
            notify(context, label, id, switchedBack = false)
        }
    }

    /** needs WRITE_SECURE_SETTINGS */
    fun switchBack(context: Context): Boolean = try {
        SystemSettings.Secure.putString(context.contentResolver, SystemSettings.Secure.DEFAULT_INPUT_METHOD, muninnId(context))
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "no permission to switch back", e)
        false
    }

    fun pause(context: Context) = context.prefs().edit { putLong(PREF_PAUSED_UNTIL, System.currentTimeMillis() + PAUSE_MS) }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.muninn_guard), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.muninn_guard_channel_description) })
    }

    private fun notify(context: Context, label: String, keyboardId: String, switchedBack: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val switchIntent = PendingIntent.getActivity(context, 1,
            Intent(context, MuninnSwitchBackActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_muninn_feather_small)
            .setColor(0xFFE8B04B.toInt())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (switchedBack) {
            val letStay = PendingIntent.getBroadcast(context, 3, Intent(context, MuninnGuardReceiver::class.java)
                .setAction(ACTION_LET_STAY).putExtra(EXTRA_KEYBOARD_ID, keyboardId), flags)
            builder.setContentTitle(context.getString(R.string.muninn_guard_switched_title))
                .setContentText(context.getString(R.string.muninn_guard_switched_text, label))
                .setSilent(true)
                .addAction(0, context.getString(R.string.muninn_guard_let_stay, label), letStay)
        } else {
            val itWasMe = PendingIntent.getBroadcast(context, 2, Intent(context, MuninnGuardReceiver::class.java).setAction(ACTION_IT_WAS_ME), flags)
            builder.setContentTitle(context.getString(R.string.muninn_guard_took_over_title, label))
                .setContentText(context.getString(R.string.muninn_guard_took_over_text))
                .setContentIntent(switchIntent)
                .addAction(0, context.getString(R.string.muninn_guard_switch_back), switchIntent)
                .addAction(0, context.getString(R.string.muninn_guard_it_was_me), itWasMe)
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "can't post notification", e)
        }
    }

    fun cancelNotification(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}

/** runs when the default keyboard setting changed, then waits for the next change */
class MuninnGuardJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        MuninnGuard.check(this)
        MuninnGuard.schedule(this)
        return false
    }

    override fun onStopJob(params: JobParameters?) = false
}

/** "It was me" and "Let Gboard stay" from the notification */
class MuninnGuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MuninnGuard.ACTION_IT_WAS_ME -> {
                MuninnGuard.pause(context)
                MuninnGuard.cancelNotification(context)
            }
            MuninnGuard.ACTION_LET_STAY -> {
                MuninnGuard.pause(context)
                MuninnGuard.cancelNotification(context)
                val id = intent.getStringExtra(MuninnGuard.EXTRA_KEYBOARD_ID) ?: return
                try {
                    SystemSettings.Secure.putString(context.contentResolver, SystemSettings.Secure.DEFAULT_INPUT_METHOD, id)
                } catch (_: SecurityException) { }
            }
        }
    }
}

/** a see-through screen that opens Android's keyboard picker (it only opens from a visible window), then closes */
class MuninnSwitchBackActivity : Activity() {
    private var shown = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (shown) {
            finish()
            return
        }
        shown = true
        MuninnGuard.cancelNotification(this)
        if (!MuninnGuard.isMuninnEnabled(this)) {
            startActivity(Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        window.decorView.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, 150)
    }
}
