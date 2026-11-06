// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils

/** Muninn: small helpers for the cache (clip history): link detection, app names and the line under each clip. */
object MuninnClips {
    private val linkPattern = Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE)
    private val linkInText = Regex("""(https?://|www\.)[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /** the whole clip is one link */
    fun isLink(text: String?) = text != null && linkPattern.matches(text.trim())

    /** the clip has a link somewhere in it (also after merging a link with other text) */
    fun hasLink(text: String?) = text != null && linkInText.containsMatchIn(text)

    /** where the links are, to colour them */
    fun linkRanges(text: String): List<IntRange> = linkInText.findAll(text).map { it.range }.toList()

    // ---- tags (Layer 2) ----

    /** tags every Muninn has; people can add their own names too */
    val BUILT_IN_TAGS = listOf("important", "watch", "buy", "idea")

    fun tagLabel(context: Context, tag: String): String = when (tag) {
        "important" -> context.getString(R.string.muninn_tag_important)
        "watch" -> context.getString(R.string.muninn_tag_watch)
        "buy" -> context.getString(R.string.muninn_tag_buy)
        "idea" -> context.getString(R.string.muninn_tag_idea)
        else -> tag
    }

    /** what is shown instead of a code, so it can't be read over someone's shoulder */
    const val MASKED_CODE = "••• •••"

    private val labelCache = HashMap<String, String?>()

    /** the app's name for a package, or null if it can't be found */
    fun appLabel(context: Context, packageName: String?): String? {
        if (packageName.isNullOrEmpty()) return null
        if (packageName == MuninnScreenshots.SOURCE) return context.getString(R.string.muninn_screenshot)
        return labelCache.getOrPut(packageName) {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    /** "Telegram · 5 min. ago", or for codes "Code · deletes itself in 8 min." */
    fun metaLine(context: Context, entry: ClipboardHistoryEntry, now: Long = System.currentTimeMillis(), tags: Boolean = true): String {
        justNow = context.getString(R.string.muninn_just_now)
        val parts = mutableListOf<String>()
        if (entry.isPinned) parts.add(context.getString(R.string.muninn_tab_pinned))
        if (tags) entry.tags.forEach { parts.add(tagLabel(context, it)) }
        appLabel(context, entry.source)?.let { parts.add(it) }
        val deletedAt = entry.deletedAt
        if (deletedAt != null) parts.add(context.getString(R.string.muninn_clip_deleted_ago, relative(deletedAt, now)))
        else parts.add(relative(entry.timeStamp, now))
        val remindAt = entry.remindAt
        if (remindAt != null && deletedAt == null)
            parts.add(context.getString(R.string.muninn_reminder_meta, MuninnReminders.describeTime(context, remindAt, now)))
        return parts.joinToString(" · ")
    }

    private var justNow: String = "just now"

    private fun relative(time: Long, now: Long): String =
        if (now - time < DateUtils.MINUTE_IN_MILLIS) justNow
        else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}
