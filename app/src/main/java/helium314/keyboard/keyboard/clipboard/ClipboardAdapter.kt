// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.MuninnClips
import helium314.keyboard.latin.MuninnReminderTimes
import helium314.keyboard.latin.MuninnReminders

import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

class ClipboardAdapter(
       val clipboardLayoutParams: ClipboardLayoutParams,
       val keyEventListener: OnKeyEventListener
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    var clipboardHistoryManager: ClipboardHistoryManager? = null

    /** Muninn: called when the shown list changed in a way the panel can't follow item by item */
    var onCacheChanged: () -> Unit = { notifyDataSetChanged() }

    /** Muninn: called after a clip was pinned (true) or unpinned (false), for haptic, sound and a short message */
    var onPinChanged: (Boolean) -> Unit = { }

    /** Muninn: called after a deleted clip was removed for good */
    var onDeletedForever: () -> Unit = { }

    /** Muninn: called after an action from the long-press menu, with the message to show */
    var onMenuAction: (String) -> Unit = { }

    var pinnedIconResId = 0
    var itemBackgroundId = 0
    var itemTypeFace: Typeface? = null
    var itemTextColor = 0
    var itemTextSize = 0f

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.clipboard_entry_key, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setContent(getItem(position))
    }

    private fun getItem(position: Int) = clipboardHistoryManager?.getHistoryEntry(position)

    override fun getItemCount() = clipboardHistoryManager?.getHistorySize() ?: 0

    inner class ViewHolder(
            view: View
    ) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnTouchListener, View.OnLongClickListener {

        private val pinnedIconView: ImageView
        private val contentTextView: TextView
        private val contentImageView: ImageView
        private val metaTextView: TextView

        init {
            view.apply {
                setOnClickListener(this@ViewHolder)
                setOnTouchListener(this@ViewHolder)
                setOnLongClickListener(this@ViewHolder)
                setBackgroundResource(itemBackgroundId)
                isHapticFeedbackEnabled = false
            }
            Settings.getValues().mColors.setBackground(view, ColorType.KEY_BACKGROUND)
            pinnedIconView = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon).apply {
                visibility = View.GONE
                setImageResource(pinnedIconResId)
            }
            contentTextView = view.findViewById<TextView>(R.id.clipboard_entry_text_content).apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
            }
            contentImageView = view.findViewById(R.id.clipboard_entry_image_content)
            metaTextView = view.findViewById<TextView>(R.id.clipboard_entry_meta).apply {
                typeface = itemTypeFace
                setTextColor(Settings.getValues().mColors.get(ColorType.KEY_HINT_TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize * 0.72f)
            }
            clipboardLayoutParams.setItemProperties(view)
            val colors = Settings.getValues().mColors
            colors.setColor(pinnedIconView, ColorType.CLIPBOARD_PIN)
        }

        fun setContent(historyEntry: ClipboardHistoryEntry?) {
            if (historyEntry == null) return
            itemView.tag = historyEntry.id
            if (historyEntry.filename != null) {
                historyEntry.setImageAndDescription(contentImageView, contentTextView)
            } else if (historyEntry.isCode) {
                contentTextView.text = MuninnClips.MASKED_CODE // Muninn: codes stay hidden, tapping still pastes them
            } else {
                // truncate displayed text for performance reasons; Muninn: links coloured wherever they sit (merged clips too)
                val shown = historyEntry.text?.take(1000).orEmpty()
                val spans = android.text.SpannableString(shown)
                val dark = androidx.core.graphics.ColorUtils.calculateLuminance(Settings.getValues().mColors.get(ColorType.KEY_BACKGROUND)) < 0.4
                MuninnClips.linkRanges(shown).forEach {
                    spans.setSpan(android.text.style.ForegroundColorSpan(if (dark) 0xFF8FB0FF.toInt() else 0xFF2F56B0.toInt()),
                        it.first, it.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                contentTextView.text = spans
            }
            // Muninn: app and time under the clip
            val inTrash = clipboardHistoryManager?.cacheView == ClipboardHistoryManager.CacheView.TRASH
            val meta = MuninnClips.metaLine(itemView.context, historyEntry)
            metaTextView.text = if (inTrash) meta + " · " + itemView.context.getString(R.string.muninn_trash_hint) else meta
            pinnedIconView.visibility = if (historyEntry.isPinned) View.VISIBLE else View.GONE
            // Muninn: pinned clips get a gold outline, so they stand out at a glance
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                itemView.foreground = if (historyEntry.isPinned) GradientDrawable().apply {
                    val density = itemView.resources.displayMetrics.density
                    cornerRadius = 10 * density
                    setStroke((2 * density).toInt(), Settings.getValues().mColors.get(ColorType.CLIPBOARD_PIN))
                } else null
            }
            contentImageView.visibility = if (historyEntry.filename != null) View.VISIBLE else View.GONE
            contentTextView.visibility = if (contentTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                keyEventListener.onKeyDown(view.tag as Long)
            }
            return false
        }

        override fun onClick(view: View) {
            keyEventListener.onKeyUp(view.tag as Long)
        }

        override fun onLongClick(view: View): Boolean {
            val manager = clipboardHistoryManager ?: return true
            if (manager.cacheView == ClipboardHistoryManager.CacheView.TRASH) {
                // Muninn: in Recently deleted, holding a clip deletes it for good
                manager.deleteClipForever(view.tag as Long)
                onCacheChanged()
                onDeletedForever()
            } else {
                showMenu(view, view.tag as Long, manager)
            }
            return true
        }

        /**
         * Muninn: holding a clip opens a sheet in Muninn's look (the "Hold a clip in the keyboard" frame):
         * Paste / Pin / Delete as big buttons, the remind times as a grid, the tags as gold pills.
         */
        private fun showMenu(anchor: View, id: Long, manager: ClipboardHistoryManager) {
            val ctx = anchor.context
            val clip = manager.getHistoryEntryContent(id) ?: return
            anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            val colors = Settings.getValues().mColors
            val gold = colors.get(ColorType.CLIPBOARD_PIN)
            val ink = colors.get(ColorType.KEY_TEXT)
            val keyBg = colors.get(ColorType.KEY_BACKGROUND)
            val rose = 0xFFF2A7A0.toInt()
            val d = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            fun round(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
                setColor(color); cornerRadius = radius * d
                if (stroke != null) setStroke(dp(1), stroke)
            }
            fun label(text: String, color: Int, size: Float, bold: Boolean = false) = TextView(ctx).apply {
                this.text = text; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                typeface = android.graphics.Typeface.create(itemTypeFace ?: android.graphics.Typeface.DEFAULT, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
            fun section(text: String, color: Int) = label(text.uppercase(), color, 11f, bold = true).apply {
                letterSpacing = 0.1f; setPadding(dp(6), dp(12), dp(6), dp(6))
            }
            lateinit var popup: android.widget.PopupWindow
            val card = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                background = round(androidx.core.graphics.ColorUtils.blendARGB(keyBg, 0xFF000000.toInt(), 0.25f), 20, androidx.core.graphics.ColorUtils.setAlphaComponent(gold, 0x55))
                setPadding(dp(10), dp(10), dp(10), dp(12))
            }
            // the clip itself, one line, so you know what you are acting on
            card.addView(label(if (clip.isCode) MuninnClips.MASKED_CODE else clip.text?.replace('\n', ' ') ?: ctx.getString(R.string.muninn_cache_file), ink, 13f).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(6), dp(2), dp(6), dp(10)); alpha = 0.8f
            })
            // Paste · Pin · Delete
            val actions = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            fun bigButton(text: String, bg: Int, fg: Int, onClick: () -> Unit) = label(text, fg, 14f, bold = true).apply {
                gravity = android.view.Gravity.CENTER; background = round(bg, 14)
                layoutParams = android.widget.LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
                setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
            }
            actions.addView(bigButton(ctx.getString(R.string.muninn_paste), gold, 0xFF1A1405.toInt()) {
                popup.dismiss()
                keyEventListener.onKeyDown(id)
                keyEventListener.onKeyUp(id)
            })
            actions.addView(bigButton(ctx.getString(if (clip.isPinned) R.string.muninn_cache_unpin else R.string.muninn_cache_pin), keyBg, ink) {
                popup.dismiss()
                manager.toggleClipPinned(id)
                if (manager.cacheView != ClipboardHistoryManager.CacheView.ALL) onCacheChanged()
                onPinChanged(manager.getHistoryEntryContent(id)?.isPinned == true)
            })
            actions.addView(bigButton(ctx.getString(R.string.muninn_cache_delete), keyBg, rose) {
                popup.dismiss()
                manager.clipboardDaoDelete(id)
                onCacheChanged()
                onMenuAction(ctx.getString(R.string.muninn_moved_to_deleted))
            })
            card.addView(actions)

            if (!clip.isCode) {
                // Remind me: four first times in a grid, with the actual time on each
                card.addView(section(ctx.getString(R.string.muninn_remind_me), gold))
                val presets = listOf(
                    R.string.muninn_remind_in_hour to MuninnReminderTimes.Preset.IN_ONE_HOUR,
                    R.string.muninn_remind_tonight to MuninnReminderTimes.Preset.TONIGHT,
                    R.string.muninn_remind_tomorrow to MuninnReminderTimes.Preset.TOMORROW_MORNING,
                    R.string.muninn_remind_next_week to MuninnReminderTimes.Preset.NEXT_WEEK,
                )
                presets.chunked(2).forEach { pair ->
                    val row = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
                    pair.forEach { (text, preset) ->
                        val time = MuninnReminders.describeTime(ctx, MuninnReminderTimes.presetTime(preset, System.currentTimeMillis()))
                        row.addView(android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            background = round(androidx.core.graphics.ColorUtils.setAlphaComponent(gold, 0x18), 12, androidx.core.graphics.ColorUtils.setAlphaComponent(gold, 0x66))
                            setPadding(dp(12), 0, dp(8), 0)
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                            addView(label(ctx.getString(text), ink, 13.5f, bold = true).apply { maxLines = 1 })
                            addView(label(time, gold, 11.5f).apply { maxLines = 1 })
                            setOnClickListener {
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                popup.dismiss()
                                MuninnReminders.set(ctx, id, preset)
                                val at = manager.getHistoryEntryContent(id)?.remindAt
                                notifyDataSetChanged()
                                if (at != null) onMenuAction(ctx.getString(R.string.muninn_remind_set, MuninnReminders.describeTime(ctx, at)))
                            }
                        })
                    }
                    card.addView(row)
                }
                if (clip.remindAt != null) card.addView(label(ctx.getString(R.string.muninn_reminder_stop), ink, 13.5f).apply {
                    alpha = 0.75f; gravity = android.view.Gravity.CENTER; setPadding(dp(6), dp(10), dp(6), dp(4))
                    setOnClickListener {
                        popup.dismiss()
                        MuninnReminders.stop(ctx, id)
                        notifyDataSetChanged()
                        onMenuAction(ctx.getString(R.string.muninn_reminder_stop_feedback))
                    }
                })

                // Tags: tap to add or remove; the sheet stays open so several can be set
                card.addView(section(ctx.getString(R.string.muninn_tags), ink))
                val dao = helium314.keyboard.latin.database.ClipboardDao.getInstance(ctx)
                val tagRow = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(dp(3), 0, dp(3), 0) }
                (MuninnClips.BUILT_IN_TAGS + (dao?.allTags().orEmpty() - MuninnClips.BUILT_IN_TAGS.toSet())).forEach { tag ->
                    val pill = label(MuninnClips.tagLabel(ctx, tag), gold, 13f).apply {
                        gravity = android.view.Gravity.CENTER; setPadding(dp(14), 0, dp(14), 0)
                        layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply { setMargins(0, 0, dp(6), 0) }
                    }
                    fun paint(on: Boolean) {
                        pill.background = round(if (on) gold else 0, 50, if (on) gold else androidx.core.graphics.ColorUtils.setAlphaComponent(gold, 0x77))
                        pill.setTextColor(if (on) 0xFF1A1405.toInt() else gold)
                        pill.typeface = android.graphics.Typeface.create(itemTypeFace ?: android.graphics.Typeface.DEFAULT, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    }
                    paint(tag in clip.tags)
                    pill.setOnClickListener {
                        pill.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        dao?.toggleTag(id, tag)
                        val on = manager.getHistoryEntryContent(id)?.tags?.contains(tag) == true
                        paint(on)
                        notifyDataSetChanged()
                        onMenuAction(ctx.getString(if (on) R.string.muninn_tag_added else R.string.muninn_tag_removed, MuninnClips.tagLabel(ctx, tag)))
                    }
                    tagRow.addView(pill)
                }
                card.addView(android.widget.HorizontalScrollView(ctx).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(tagRow)
                })
            }

            val width = anchor.rootView.width - dp(16)
            popup = android.widget.PopupWindow(card, width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
                isOutsideTouchable = true
                elevation = 16 * d
                animationStyle = android.R.style.Animation_Dialog
            }
            popup.showAtLocation(anchor.rootView, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL, 0, dp(8))
        }
    }
}
