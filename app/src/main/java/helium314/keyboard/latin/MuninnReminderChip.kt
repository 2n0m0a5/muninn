// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * Muninn: the reminder chip shown in the suggestion bar when the keyboard opens.
 *
 *   [ (bell) Flight check-in closes 21:40      (check) (clock) (leaf) ]
 *
 * Tapping the text pastes the clip and marks the reminder done. The three buttons are Done, Later and Let go.
 * Calm: quiet outline. Noticeable and Loud: gold, one slow glow and two soft taps when it first appears.
 */
object MuninnReminderChip {
    interface Actions {
        fun onPaste(clip: ClipboardHistoryEntry)
        fun onDone(clip: ClipboardHistoryEntry)
        fun onLater(clip: ClipboardHistoryEntry)
        fun onLetGo(clip: ClipboardHistoryEntry)
    }

    fun create(context: Context, clip: ClipboardHistoryEntry, firstTime: Boolean, actions: Actions): View {
        val colors = Settings.getValues().mColors
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val gold = colors.get(ColorType.CLIPBOARD_PIN)
        val calm = clip.remindStrength == MuninnReminders.CALM
        val askToLetGo = clip.remindSkips >= MuninnReminders.SKIPS_BEFORE_ASKING && MuninnReminders.offerLetGo(context)
        val textColor = if (calm) colors.get(ColorType.KEY_TEXT) else gold

        val pill = GradientDrawable().apply {
            cornerRadius = 100f * density
            setColor(if (calm) 0 else ColorUtils.setAlphaComponent(gold, 0x22))
            setStroke(dp(1), if (calm) colors.get(ColorType.KEY_HINT_TEXT) else ColorUtils.setAlphaComponent(gold, 0xAA))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pill
            setPadding(dp(10), 0, dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(4), dp(5), dp(4), dp(5))
            }
        }

        val label = TextView(context).apply {
            KeyboardTypeface.applyToTextView(this)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(textColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            val prefix = if (askToLetGo) context.getString(R.string.muninn_reminder_still_want) + " " else ""
            val body = if (clip.filename != null) context.getString(R.string.muninn_cache_file) else clip.text.orEmpty().replace('\n', ' ')
            text = prefix + body
            ContextCompat.getDrawable(context, R.drawable.ic_muninn_bell)?.mutate()?.let {
                it.setTint(textColor)
                it.setBounds(0, 0, dp(16), dp(16))
                setCompoundDrawablesRelative(it, null, null, null)
                compoundDrawablePadding = dp(6)
            }
            contentDescription = context.getString(R.string.muninn_reminder_title) + ": " + body
            setOnClickListener { actions.onPaste(clip) }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(label)

        fun button(icon: Int, description: Int, highlighted: Boolean, onClick: () -> Unit) = ImageButton(context).apply {
            setImageResource(icon)
            setColorFilter(if (highlighted) gold else colors.get(ColorType.KEY_TEXT))
            background = null
            contentDescription = context.getString(description)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
        root.addView(button(R.drawable.ic_muninn_check, R.string.muninn_reminder_done, !askToLetGo) { actions.onDone(clip) })
        root.addView(button(R.drawable.ic_muninn_clock, R.string.muninn_reminder_later, false) { actions.onLater(clip) })
        root.addView(button(R.drawable.ic_muninn_leaf, R.string.muninn_reminder_let_go, askToLetGo) { actions.onLetGo(clip) })

        if (firstTime && !calm) {
            // two soft taps, like wingbeats
            root.post { root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            root.postDelayed({ root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, 140)
            // one slow glow, then it stays still
            ValueAnimator.ofInt(0x22, 0x77, 0x22).apply {
                duration = 1600
                startDelay = 150
                addUpdateListener { pill.setColor(ColorUtils.setAlphaComponent(gold, it.animatedValue as Int)) }
                start()
            }
        }
        return root
    }
}
