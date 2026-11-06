// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils

/** Muninn: small helpers for the cache (clip history): one-time code detection, app names and the line under each clip. */
object MuninnClips {
    /** how long a one-time code stays in the cache */
    const val CODE_LIFETIME_MS = 10 * 60 * 1000L

    // Deliberately strict, because a match makes a clip delete itself:
    //   482913, 48291375        6 to 8 digits on their own
    //   482 913, 482-913        6 digits in two groups of 3
    //   G-482913, AB-4829       up to 3 letters, a dash, then 4 to 8 digits
    // Prices (15000), years (2026), phone numbers and card numbers don't match.
    private val codePatterns = listOf(
        Regex("""\d{6,8}"""),
        Regex("""\d{3}[ -]\d{3}"""),
        Regex("""[A-Za-z]{1,3}-\d{4,8}"""),
    )

    fun isOneTimeCode(text: String): Boolean {
        val t = text.trim()
        if (t.length > 12) return false
        return codePatterns.any { it.matches(t) }
    }

    private val linkPattern = Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE)

    fun isLink(text: String?) = text != null && linkPattern.matches(text.trim())

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
        val expiresAt = entry.expiresAt
        when {
            deletedAt != null -> parts.add(context.getString(R.string.muninn_clip_deleted_ago, relative(deletedAt, now)))
            expiresAt != null -> {
                val minutes = ((expiresAt - now) / 60_000L).coerceAtLeast(1)
                parts.add(0, context.getString(R.string.muninn_clip_code))
                parts.add(context.resources.getQuantityString(R.plurals.muninn_clip_code_expires, minutes.toInt(), minutes.toInt()))
            }
            else -> parts.add(relative(entry.timeStamp, now))
        }
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
