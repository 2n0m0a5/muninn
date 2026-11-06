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
import android.view.MenuItem
import android.widget.PopupMenu
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
                contentTextView.text = historyEntry.text?.take(1000) // truncate displayed text for performance reasons
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

        /** Muninn: the long-press menu: Pin, Remind me, Remove reminder, Delete */
        private fun showMenu(anchor: View, id: Long, manager: ClipboardHistoryManager) {
            val ctx = anchor.context
            val clip = manager.getHistoryEntryContent(id) ?: return
            anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            val menu = PopupMenu(ctx, anchor)
            val pinItem = menu.menu.add(if (clip.isPinned) R.string.muninn_cache_unpin else R.string.muninn_cache_pin)
            val presets = mutableMapOf<MenuItem, MuninnReminderTimes.Preset>()
            if (!clip.isCode) {
                val remind = menu.menu.addSubMenu(R.string.muninn_remind_me)
                listOf(
                    R.string.muninn_remind_in_hour to MuninnReminderTimes.Preset.IN_ONE_HOUR,
                    R.string.muninn_remind_tonight to MuninnReminderTimes.Preset.TONIGHT,
                    R.string.muninn_remind_tomorrow to MuninnReminderTimes.Preset.TOMORROW_MORNING,
                    R.string.muninn_remind_next_week to MuninnReminderTimes.Preset.NEXT_WEEK,
                ).forEach { (label, preset) -> presets[remind.add(label)] = preset }
            }
            val removeReminder = if (clip.remindAt != null) menu.menu.add(R.string.muninn_remind_remove) else null
            val deleteItem = if (!clip.isPinned) menu.menu.add(R.string.muninn_cache_delete) else null
            menu.setOnMenuItemClickListener { item ->
                when {
                    item == pinItem -> {
                        manager.toggleClipPinned(id)
                        if (manager.cacheView != ClipboardHistoryManager.CacheView.ALL) onCacheChanged()
                        onPinChanged(manager.getHistoryEntryContent(id)?.isPinned == true)
                    }
                    item in presets -> {
                        MuninnReminders.set(ctx, id, presets[item]!!)
                        val at = manager.getHistoryEntryContent(id)?.remindAt
                        notifyDataSetChanged()
                        if (at != null) onMenuAction(ctx.getString(R.string.muninn_remind_set, MuninnReminders.describeTime(ctx, at)))
                    }
                    item == removeReminder -> {
                        MuninnReminders.done(ctx, id)
                        notifyDataSetChanged()
                        onMenuAction(ctx.getString(R.string.muninn_remind_remove))
                    }
                    item == deleteItem -> {
                        manager.clipboardDaoDelete(id)
                        onCacheChanged()
                        onMenuAction(ctx.getString(R.string.muninn_moved_to_deleted))
                    }
                    else -> return@setOnMenuItemClickListener false // a submenu opening
                }
                true
            }
            menu.show()
        }
    }
}
