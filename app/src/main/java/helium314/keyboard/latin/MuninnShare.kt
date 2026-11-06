// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

/** Muninn, Layer 5: making sense of what other apps share. Pure, so it can be tested. */
object MuninnShare {
    private val urlPattern = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    class Shared(val text: String, val title: String?, val url: String?)

    /**
     * Apps share a subject and a text that often holds a link (TikTok: "caption https://…", YouTube: just the link).
     * The clip keeps everything; [Shared.title] and [Shared.url] are what the sheet shows.
     */
    fun parse(text: String?, subject: String?): Shared? {
        val body = text?.trim().orEmpty()
        val subj = subject?.trim().orEmpty()
        if (body.isEmpty() && subj.isEmpty()) return null
        val url = urlPattern.find(body)?.value?.trimEnd('.', ',', ')', '"', '\'')
        val rest = (if (url != null) body.replace(url, "") else body).trim().trim('-', '|', ':', '·').trim()
        val title = rest.ifEmpty { null } ?: subj.ifEmpty { null }
        val full = when {
            body.isEmpty() -> subj
            subj.isNotEmpty() && !body.contains(subj) && url != null && rest.isEmpty() -> "$subj\n$body"
            else -> body
        }
        return Shared(full, title, url)
    }

    /** a shared link (a video, a post, an article) gets the "Watch & read" tag already chosen; plain text gets none */
    fun suggestedTag(shared: Shared): String? = if (shared.url != null) "watch" else null
}
