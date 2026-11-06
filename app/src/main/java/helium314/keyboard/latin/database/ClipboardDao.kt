// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

import android.content.ClipDescription
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File
import kotlin.collections.joinToString

/** Class providing cached access to the clipboard table */
// currently we should not need to worry about synchronizing access (though maybe we could addClip in a coroutine, then it might be relevant)
class ClipboardDao private constructor(private val db: Database) {
    interface Listener {
        fun onClipInserted(position: Int)
        fun onClipsRemoved(position: Int, count: Int)
        fun onClipMoved(oldPosition: Int, newPosition: Int)
    }

    var listener: Listener? = null

    // we clean up old clips when a new clip is added, but not too frequently
    private var lastClearOldClips = 0L

    /** Muninn: clips in Recently deleted, newest deletion first. Kept for [TRASH_DAYS] days, then removed for good. */
    private val trash = mutableListOf<ClipboardHistoryEntry>()

    // cache is loaded at start and never dropped
    private val cache = mutableListOf<ClipboardHistoryEntry>().apply {
        db.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_ID, COLUMN_TIMESTAMP, COLUMN_PINNED, COLUMN_TEXT, COLUMN_FILE, COLUMN_MIME_TYPE,
                COLUMN_SOURCE, COLUMN_DELETED, COLUMN_EXPIRES, COLUMN_REMIND_AT, COLUMN_REMIND_STRENGTH, COLUMN_REMIND_SKIPS),
            null,
            null,
            null,
            null,
            "$COLUMN_PINNED, $COLUMN_TIMESTAMP DESC" // was only relevant in the initial approach of using a cursor instead of a cache
        ).use {
            while (it.moveToNext()) {
                val entry = ClipboardHistoryEntry(
                    it.getLong(0),
                    it.getLong(1),
                    it.getInt(2) != 0,
                    it.getStringOrNull(3),
                    it.getStringOrNull(4),
                    it.getStringOrNull(5)?.split('§')?.filter { it.isNotEmpty() },
                    it.getStringOrNull(6),
                    it.getLongOrNull(7),
                    it.getLongOrNull(8),
                    it.getLongOrNull(9),
                    it.getIntOrNull(10) ?: 1,
                    it.getIntOrNull(11) ?: 0,
                )
                if (entry.deletedAt != null) trash.add(entry) else add(entry)
            }
        }
        sort()
        trash.sortByDescending { it.deletedAt }
    }

    fun addClip(timestamp: Long, pinned: Boolean, text: String, source: String? = null, expiresAt: Long? = null) = synchronized(this) {
        clearOldClips()
        trash.filter { it.text == text && it.filename == null }.takeIf { it.isNotEmpty() }?.let { deleteForever(it) }
        val existingIndex = cache.indexOfFirst { it.text == text }
        if (existingIndex >= 0 && cache[existingIndex].timeStamp == timestamp)
            return@synchronized // nothing to do
        if (existingIndex >= 0) {
            updateTimestampAt(existingIndex, timestamp)
            return@synchronized
        }
        insertNewEntry(timestamp, pinned, text, null, null, null, source, expiresAt)
    }

    fun addClipUri(timestamp: Long, pinned: Boolean, uri: Uri, description: ClipDescription, context: Context, source: String? = null) = synchronized(this) {
        clearOldClips()
        val extension = if (description.mimeTypeCount == 0) ""
            else ".${MimeTypeMap.getSingleton().getExtensionFromMimeType(description.getMimeType(0))}"
        val tempFile = File(context.filesDir, "temp_clip")
        tempFile.delete()
        runCatching { FileUtils.copyContentUriToNewFile(uri, context, tempFile) }.onFailure { return@synchronized }

        // we set the file name to the sha256 of the content to have virtually unique names and an easy way to find duplicates
        val sha256 = ChecksumCalculator.checksum(tempFile)
        val file = File(clipFilesDir, sha256 + extension)

        val existingIndex = cache.indexOfFirst { it.filename == file.name }
        if (existingIndex >= 0) {
            if (cache[existingIndex].timeStamp != timestamp)
                updateTimestampAt(existingIndex, timestamp)
            tempFile.delete()
            return@synchronized
        }
        tempFile.renameTo(file)
        // we could try getting a thumbnail using context.contentResolver.loadThumbnail(uri, Size(a, b), null)
        // but currently we don't cache them anyway, so no use for that
        insertNewEntry(timestamp, pinned, description.label?.toString(), file.name, description.getMimeTypes(), context, source)
    }

    // keep pinned and the first non-pinned, others can be deleted
    private fun deleteIfSizeExceeded(prefs: SharedPreferences) {
        val sizeLimit = prefs.getInt(Settings.PREF_CLIPBOARD_FILES_SIZE_LIMIT, Defaults.PREF_CLIPBOARD_FILES_SIZE_LIMIT) * 1000000
        var size = 0L
        var keepMin = 1
        val toRemove = mutableListOf<ClipboardHistoryEntry>()
        cache.forEach {
            if (it.filename == null) return@forEach
            val file = File(clipFilesDir, it.filename)
            size += file.length()
            if (it.isPinned)
                return@forEach
            if (size > sizeLimit) {
                if (keepMin > 0) --keepMin
                else toRemove.add(it)
            }
        }
        delete(toRemove)
    }

    /** only public for restoring backups */
    fun insertNewEntry(
        timestamp: Long, pinned: Boolean, text: String?, filename: String?, mimeTypes: List<String>?, context: Context?,
        source: String? = null, expiresAt: Long? = null,
    ) {
        val cv = ContentValues(7)
        cv.put(COLUMN_SOURCE, source)
        cv.put(COLUMN_EXPIRES, expiresAt)
        cv.put(COLUMN_TIMESTAMP, timestamp)
        cv.put(COLUMN_PINNED, pinned)
        cv.put(COLUMN_TEXT, text)
        cv.put(COLUMN_FILE, filename)
        // § should be a safe separator, not allowed in mime types: https://datatracker.ietf.org/doc/html/rfc6838#section-4.2
        cv.put(COLUMN_MIME_TYPE, mimeTypes?.joinToString("§"))
        val rowId = db.writableDatabase.insert(TABLE, null, cv)

        val entry = ClipboardHistoryEntry(rowId, timestamp, pinned, text, filename, mimeTypes, source, null, expiresAt)
        if (filename != null && context != null)
            deleteIfSizeExceeded(context.prefs())
        cache.add(entry)
        cache.sort()
        listener?.onClipInserted(cache.indexOf(entry))
    }

    private fun updateTimestampAt(index: Int, timestamp: Long) {
        val entry = cache[index]
        entry.timeStamp = timestamp
        cache.sort()
        listener?.onClipMoved(index, cache.indexOf(entry))
        val cv = ContentValues(1)
        cv.put(COLUMN_TIMESTAMP, timestamp)
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
    }

    fun isPinned(index: Int) = cache[index].isPinned

    fun getAt(index: Int) = cache[index]

    fun get(id: Long) = cache.firstOrNull { it.id == id } ?: trash.first { it.id == id }

    fun getAll(): List<ClipboardHistoryEntry> = cache

    fun count() = cache.size

    fun sort() = cache.sort()

    fun togglePinned(id: Long) = synchronized(this) {
        val entry = cache.first { it.id == id }
        entry.isPinned = !entry.isPinned
        entry.timeStamp = System.currentTimeMillis()
        if (listener != null) {
            val oldPos = cache.indexOf(entry)
            cache.sort()
            val newPos = cache.indexOf(entry)
            listener?.onClipMoved(oldPos, newPos)
        } else {
            cache.sort()
        }
        val cv = ContentValues(2)
        cv.put(COLUMN_PINNED, entry.isPinned)
        cv.put(COLUMN_TIMESTAMP, entry.timeStamp)
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
    }

    // RecyclerView initiates this, so we don't call listener (or we'll get an IndexOutOfRangeException from RecyclerView)
    fun deleteClipAt(index: Int) {
        moveToTrash(listOf(cache[index]))
    }

    // ---- Muninn: Recently deleted ----

    fun getTrash(): List<ClipboardHistoryEntry> = trash

    fun trashCount() = trash.size

    /** Delete clips the way a person deletes: into Recently deleted, unless that is turned off. One-time codes go for good. */
    fun moveToTrash(entries: List<ClipboardHistoryEntry>) = synchronized(this) {
        if (entries.isEmpty()) return@synchronized
        val keepDeleted = Settings.getValues()?.mMuninnKeepDeleted ?: Defaults.PREF_MUNINN_KEEP_DELETED
        val (codes, others) = entries.partition { it.isCode }
        delete(codes)
        if (!keepDeleted) {
            delete(others)
            return@synchronized
        }
        val now = System.currentTimeMillis()
        cache.removeAll(others)
        others.forEach {
            it.deletedAt = now
            it.isPinned = false
        }
        trash.addAll(0, others)
        val cv = ContentValues(2)
        cv.put(COLUMN_DELETED, now)
        cv.put(COLUMN_PINNED, false)
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID IN (${others.joinToString(",") { it.id.toString() }})", null)
    }

    /** Delete a clip by id from wherever it is: live clips go to Recently deleted, deleted clips go for good. */
    fun deleteById(id: Long) {
        val inCache = cache.indexOfFirst { it.id == id }
        if (inCache >= 0) {
            moveToTrash(listOf(cache[inCache]))
            listener?.onClipsRemoved(inCache, 1)
            return
        }
        trash.firstOrNull { it.id == id }?.let { deleteForever(listOf(it)) }
    }

    fun restore(id: Long) = synchronized(this) {
        val entry = trash.firstOrNull { it.id == id } ?: return@synchronized
        trash.remove(entry)
        entry.deletedAt = null
        cache.add(entry)
        cache.sort()
        val cv = ContentValues(1)
        cv.putNull(COLUMN_DELETED)
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
        listener?.onClipInserted(cache.indexOf(entry))
    }

    fun deleteForever(entries: List<ClipboardHistoryEntry>) = synchronized(this) {
        trash.removeAll(entries)
        delete(entries)
    }

    fun emptyTrash() = deleteForever(trash.toList())

    // ---- Muninn: reminders ----

    fun setReminder(id: Long, at: Long?, strength: Int = 1, skips: Int = 0) = synchronized(this) {
        val entry = (cache + trash).firstOrNull { it.id == id } ?: return@synchronized
        entry.remindAt = at
        entry.remindStrength = strength
        entry.remindSkips = if (at == null) 0 else skips
        val cv = ContentValues(3)
        if (at == null) cv.putNull(COLUMN_REMIND_AT) else cv.put(COLUMN_REMIND_AT, at)
        cv.put(COLUMN_REMIND_STRENGTH, strength)
        cv.put(COLUMN_REMIND_SKIPS, entry.remindSkips)
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
    }

    /** live clips with a reminder, soonest first */
    fun withReminders(): List<ClipboardHistoryEntry> = cache.filter { it.remindAt != null }.sortedBy { it.remindAt }

    /** one-time codes past their time, and clips deleted more than [TRASH_DAYS] days ago */
    private fun removeExpired() {
        val now = System.currentTimeMillis()
        val expiredCodes = cache.filter { it.expiresAt != null && it.expiresAt < now }
        if (expiredCodes.isNotEmpty()) delete(expiredCodes)
        val trashLimit = now - TRASH_DAYS * 24 * 60 * 60 * 1000L
        val oldTrash = trash.filter { (it.deletedAt ?: now) < trashLimit }
        if (oldTrash.isNotEmpty()) deleteForever(oldTrash)
    }

    private fun delete(entries: List<ClipboardHistoryEntry>) = synchronized(this) {
        if (entries.isEmpty()) return@synchronized
        cache.removeAll(entries)
        db.writableDatabase.delete(TABLE, "$COLUMN_ID IN (${entries.joinToString(",") { it.id.toString() }})", null)
        entries.forEach { if (it.filename != null) File(clipFilesDir, it.filename).delete() }
    }

    fun clearOldClips(now: Boolean = false) {
        if (listener != null)
            return // never clear when clipboard is visible
        if (!now && lastClearOldClips > SystemClock.elapsedRealtime() - 5 * 1000)
            return

        lastClearOldClips = SystemClock.elapsedRealtime()
        removeExpired()
        val retentionTime = Settings.getValues()?.mClipboardHistoryRetentionTime ?: 121L
        if (retentionTime > 120) return
        val minTime = System.currentTimeMillis() - retentionTime * 60 * 1000L
        val toRemove = cache.filter { it.timeStamp < minTime && !it.isPinned }
        delete(toRemove)
    }

    fun clearNonPinned() {
        val indicesToRemove = mutableListOf<Int>()
        cache.forEachIndexed { idx, clip ->
            if (!clip.isPinned)
                indicesToRemove.add(idx)
        }
        if (indicesToRemove.isEmpty())
            return // nothing to remove
        moveToTrash(cache.filter { !it.isPinned })
        listener?.onClipsRemoved(indicesToRemove[0], indicesToRemove.size)
    }

    fun clear() {
        trash.clear()
        if (count() == 0) {
            db.writableDatabase.delete(TABLE, null, null)
            return
        }
        cache.clear()
        listener?.onClipsRemoved(0, count())
        db.writableDatabase.delete(TABLE, null, null)
    }

    fun cleanupFiles(prefs: SharedPreferences) {
        if (!prefs.getBoolean(Settings.PREF_CLIPBOARD_USE_FILES, Defaults.PREF_CLIPBOARD_USE_FILES)) {
            delete(cache.filter { it.filename != null && !it.isPinned })
            return
        }

        val files = clipFilesDir.listFiles()?.toMutableList() ?: return
        val fnames = files.mapTo(HashSet()) { it.name }
        val entries = (cache + trash).filter { it.filename != null }
        val enames = entries.mapTo(HashSet()) { it.filename }

        val filesToRemove = files.filter { it.name !in enames }
        val entriesToRemove = entries.filter { it.filename!! !in fnames }
        if (filesToRemove.isEmpty() && entriesToRemove.isEmpty()) {
            deleteIfSizeExceeded(prefs)
            return
        }

        Log.w(TAG, "deleting ${filesToRemove.size} files and ${entriesToRemove.size} clipboard entries")
        filesToRemove.forEach { it.delete() }
        trash.removeAll(entriesToRemove)
        delete(entriesToRemove)

        deleteIfSizeExceeded(prefs)
    }

    companion object {
        private const val TAG = "ClipboardDao"

        private const val TABLE = "CLIPBOARD"
        // it's possible timestamp is not unique, so we use a separate ID
        // ID is generated and returned on insert, see https://sqlite.org/rowidtable.html
        private const val COLUMN_ID = "ID"
        private const val COLUMN_TIMESTAMP = "TIMESTAMP"
        private const val COLUMN_PINNED = "PINNED"
        private const val COLUMN_TEXT = "TEXT" // we could enforce unique text, but that's only necessary if we can drop the cache (later)
        private const val COLUMN_FILE = "FILE" // path relative to files dir
        private const val COLUMN_MIME_TYPE = "MIME_TYPE" // for files, actually a list of mime types according to clipboard description
        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_PINNED TINYINT NOT NULL,
                $COLUMN_TEXT TEXT
            )
        """

        const val ADD_FILE_COLUMN = "ALTER TABLE $TABLE ADD COLUMN $COLUMN_FILE TEXT"
        const val ADD_MIME_TYPE_COLUMN = "ALTER TABLE $TABLE ADD COLUMN $COLUMN_MIME_TYPE TEXT"
        // Muninn
        private const val COLUMN_SOURCE = "SOURCE" // package of the app the clip came from
        private const val COLUMN_DELETED = "DELETED" // time the clip went to Recently deleted, null if it didn't
        private const val COLUMN_EXPIRES = "EXPIRES" // time a one-time code deletes itself
        const val ADD_SOURCE_COLUMN = "ALTER TABLE $TABLE ADD COLUMN $COLUMN_SOURCE TEXT"
        const val ADD_DELETED_COLUMN = "ALTER TABLE $TABLE ADD COLUMN $COLUMN_DELETED INTEGER"
        const val ADD_EXPIRES_COLUMN = "ALTER TABLE $TABLE ADD COLUMN $COLUMN_EXPIRES INTEGER"
        const val TRASH_DAYS = 30
        private const val COLUMN_REMIND_AT = "REMIND_AT" // when to bring the clip back
        private const val COLUMN_REMIND_STRENGTH = "REMIND_STRENGTH" // 0 calm, 1 noticeable, 2 loud
        private const val COLUMN_REMIND_SKIPS = "REMIND_SKIPS" // how often "Later" was chosen
        const val ADD_REMIND_AT_COLUMN = "ALTER TABLE $TABLE ADD COLUMN $COLUMN_REMIND_AT INTEGER"
        const val ADD_REMIND_STRENGTH_COLUMN = "ALTER TABLE $TABLE ADD COLUMN $COLUMN_REMIND_STRENGTH INTEGER"
        const val ADD_REMIND_SKIPS_COLUMN = "ALTER TABLE $TABLE ADD COLUMN $COLUMN_REMIND_SKIPS INTEGER"

        private var instance: ClipboardDao? = null
        lateinit var clipFilesDir: File
            private set

        /** Returns the instance or creates a new one. Returns null if instance can't be created (e.g. no access to db due to device being locked) */
        fun getInstance(context: Context): ClipboardDao? {
            if (instance == null)
                try {
                    instance = ClipboardDao(Database.getInstance(context))
                    clipFilesDir = File(context.filesDir, "clipboard")
                    clipFilesDir.mkdirs()
                    instance?.cleanupFiles(context.prefs())
                } catch (e: Throwable) {
                    Log.e(TAG, "can't create ClipboardDao", e)
                }
            return instance
        }

        private fun ClipDescription.getMimeTypes(): List<String> {
            val types = mutableListOf<String>()
            for (i in 0..<mimeTypeCount) {
                types.add(getMimeType(i))
            }
            if (types.isEmpty())
                types.add("*/*")
            return types
        }
    }
}

class ClipboardContentProvider : FileProvider()
