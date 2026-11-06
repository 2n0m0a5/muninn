// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.Manifest
import android.content.ClipDescription
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.edit
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs

/**
 * Muninn: recent screenshots become clips, like copied text. Screenshots taken in the last [RECENT_MS] are picked up
 * the moment they are saved (while Muninn runs) and whenever the keyboard opens. Off unless the person turns it on,
 * because it needs permission to read the phone's pictures.
 */
object MuninnScreenshots {
    private const val TAG = "MuninnScreenshots"
    private const val PREF_LAST_SEEN = "muninn_screenshots_last_seen"
    /** a screenshot older than this when Muninn first sees it is left alone */
    const val RECENT_MS = 15 * 60 * 1000L
    const val SOURCE = "screenshot"

    fun enabled(context: Context) = context.prefs().getBoolean(Settings.PREF_MUNINN_SCREENSHOTS, Defaults.PREF_MUNINN_SCREENSHOTS)

    fun permission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context) = context.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    private var observer: ContentObserver? = null

    /** while Muninn's process runs, hear about new pictures at once */
    fun watchWhileRunning(context: Context) {
        if (observer != null) return
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val check = Runnable { Thread { collect(app) }.start() }
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(check)
                handler.postDelayed(check, 1500) // the file is still being written right after the change
            }
        }
        try {
            app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer!!)
        } catch (e: Exception) {
            Log.w(TAG, "can't watch pictures", e)
        }
    }

    /** add screenshots saved since the last look (and in the last 15 minutes) to the cache */
    @Synchronized
    fun collect(context: Context) {
        if (!enabled(context) || !hasPermission(context)) return
        val dao = ClipboardDao.getInstance(context) ?: return
        val prefs = context.prefs()
        val now = System.currentTimeMillis()
        val since = maxOf(prefs.getLong(PREF_LAST_SEEN, 0), now - RECENT_MS)
        val columns = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DISPLAY_NAME)
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else @Suppress("DEPRECATION") MediaStore.Images.Media.DATA
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ? AND (LOWER($pathColumn) LIKE '%screenshot%' OR LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE 'screenshot%')"
        var newest = prefs.getLong(PREF_LAST_SEEN, 0)
        try {
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, selection,
                arrayOf((since / 1000).toString()), "${MediaStore.Images.Media.DATE_ADDED} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val added = c.getLong(1) * 1000
                    val mime = c.getString(2) ?: "image/png"
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    dao.addClipUri(added, false, uri, ClipDescription(context.getString(R.string.muninn_screenshot), arrayOf(mime)), context, SOURCE)
                    newest = maxOf(newest, added)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "can't read screenshots", e)
        }
        if (newest > prefs.getLong(PREF_LAST_SEEN, 0)) prefs.edit { putLong(PREF_LAST_SEEN, newest) }
    }
}
