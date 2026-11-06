// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs

/**
 * Muninn: translate inside the keyboard, like Yandex. The Translate button opens a bar above the keys with
 * Muninn's own text box: what you type goes there (with suggestions and corrections as usual), and the translation
 * appears live in the app's text field. The check mark keeps it; the cross takes it away again.
 * Translation runs on the phone (Google ML Kit); each language pair is downloaded once, then works offline.
 */
class MuninnTranslate(private val ime: LatinIME) {
    private var view: View? = null
    private var box: EditText? = null
    private var fromLabel: TextView? = null
    private var toLabel: TextView? = null
    private var picker: android.widget.PopupWindow? = null
    private var boxConnection: InputConnection? = null
    private var translator: Translator? = null
    private var ready = false
    private var source = "en"
    private var target = "ru"
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var request = 0 // only the newest translation is shown
    var active = false
        private set

    fun toggle() = if (active) keep() else open()

    private fun languages(): List<String> {
        val mine = SubtypeSettings.getEnabledSubtypes(true).mapNotNull { TranslateLanguage.fromLanguageTag(it.locale().language) }.distinct()
        return (mine + listOf(TranslateLanguage.ENGLISH, TranslateLanguage.RUSSIAN, TranslateLanguage.ARABIC, TranslateLanguage.FRENCH,
            TranslateLanguage.SPANISH, TranslateLanguage.GERMAN, TranslateLanguage.TURKISH, TranslateLanguage.CHINESE)).distinct()
    }

    private fun open() {
        // the languages last chosen; the first time, from the keyboard's language
        source = ime.prefs().getString(PREF_SOURCE, null)
            ?: TranslateLanguage.fromLanguageTag(RichInputMethodManager.getInstance().currentSubtype.locale.language) ?: TranslateLanguage.ENGLISH
        val saved = ime.prefs().getString(PREF_TARGET, null)
        target = saved?.takeIf { it != source } ?: languages().first { it != source }
        active = true
        showBar()
        // from now on the keys type into Muninn's box
        ime.setMuninnInputRedirect(boxConnection)
        prepareTranslator()
    }

    private fun prepareTranslator() {
        translator?.close()
        ready = false
        box?.hint = ime.getString(R.string.muninn_translate_getting, name(source), name(target))
        // never let the translation library take the keyboard down: if it fails, the bar says so and typing goes on
        val t = try {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        } catch (e: Throwable) {
            Log.w(TAG, "can't create translator", e)
            translator = null
            box?.hint = ime.getString(R.string.muninn_translate_unavailable)
            return
        }
        translator = t
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                if (translator !== t) return@addOnSuccessListener
                ready = true
                box?.hint = ime.getString(R.string.muninn_translate_type_in, name(source))
                translateSoon(0)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "can't get translation model", e)
                if (translator !== t) return@addOnFailureListener
                box?.hint = ime.getString(R.string.muninn_translate_no_model)
            }
    }

    private fun name(language: String) = java.util.Locale.forLanguageTag(language).let { it.getDisplayLanguage(it) }
        .replaceFirstChar { it.uppercase() }

    private fun short(language: String) = language.uppercase().take(2)

    private fun showPair() {
        fromLabel?.text = short(source)
        toLabel?.text = short(target)
        fromLabel?.contentDescription = ime.getString(R.string.muninn_translate_from, name(source))
        toLabel?.contentDescription = ime.getString(R.string.muninn_translate_into, name(target))
    }

    private fun save() = ime.prefs().edit { putString(PREF_SOURCE, source); putString(PREF_TARGET, target) }

    /** ⇄: the two languages change places */
    private fun swap() {
        val old = source
        source = target
        target = old
        save()
        showPair()
        prepareTranslator()
    }

    /** a language was picked for one side; picking the other side's language swaps them */
    private fun choose(from: Boolean, language: String) {
        if (from) {
            if (language == target) target = source
            source = language
        } else {
            if (language == source) source = target
            target = language
        }
        save()
        showPair()
        prepareTranslator()
    }

    /** every language translation knows: the person's own first, then all others by name; ✓ marks the chosen one */
    private fun openPicker(from: Boolean, anchor: View) {
        picker?.dismiss()
        val colors = Settings.getValues().mColors
        val gold = colors.get(ColorType.CLIPBOARD_PIN)
        val ink = colors.get(ColorType.KEY_TEXT)
        val ground = ColorUtils.setAlphaComponent(colors.get(ColorType.MAIN_BACKGROUND), 0xFF)
        val d = ime.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val chosen = if (from) source else target
        val mine = SubtypeSettings.getEnabledSubtypes(true).mapNotNull { TranslateLanguage.fromLanguageTag(it.locale().language) }.distinct()
        val others = TranslateLanguage.getAllLanguages().filter { it !in mine }.sortedBy { name(it).lowercase() }
        val offline = mutableSetOf(TranslateLanguage.ENGLISH)
        val list = LinearLayout(ime).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(8)) }
        val rows = mutableMapOf<String, TextView>()
        fun header(text: String) = TextView(ime).apply {
            KeyboardTypeface.applyToTextView(this)
            this.text = text.uppercase()
            letterSpacing = 0.1f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(ColorUtils.setAlphaComponent(ink, 0x99))
            setPadding(dp(18), dp(14), dp(18), dp(6))
        }
        fun row(language: String): View {
            val isChosen = language == chosen
            val line = LinearLayout(ime).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(18), 0, dp(14), 0)
                isClickable = true
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(gold, 0x33)), null,
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    picker?.dismiss()
                    choose(from, language)
                }
            }
            val nameView = TextView(ime).apply {
                KeyboardTypeface.applyToTextView(this)
                text = name(language)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (isChosen) gold else ink)
                if (isChosen) setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val note = TextView(ime).apply {
                KeyboardTypeface.applyToTextView(this)
                text = java.util.Locale.forLanguageTag(language).displayLanguage.takeIf { it != name(language) } ?: ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(ColorUtils.setAlphaComponent(ink, 0x88))
                setPadding(dp(8), 0, dp(8), 0)
            }
            rows[language] = note
            line.addView(nameView)
            line.addView(note)
            if (isChosen) line.addView(android.widget.ImageView(ime).apply {
                setImageResource(R.drawable.ic_muninn_check)
                setColorFilter(gold)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
            return line
        }
        list.addView(TextView(ime).apply {
            KeyboardTypeface.applyToTextView(this)
            text = ime.getString(if (from) R.string.muninn_translate_pick_from else R.string.muninn_translate_pick_into)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTextColor(ink)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(18), dp(16), dp(18), dp(2))
        })
        list.addView(TextView(ime).apply {
            KeyboardTypeface.applyToTextView(this)
            text = ime.getString(R.string.muninn_translate_pick_note)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(ColorUtils.setAlphaComponent(ink, 0x99))
            setPadding(dp(18), 0, dp(18), dp(4))
        })
        if (mine.isNotEmpty()) {
            list.addView(header(ime.getString(R.string.muninn_translate_yours)))
            mine.forEach { list.addView(row(it)) }
        }
        list.addView(header(ime.getString(R.string.muninn_translate_all)))
        others.forEach { list.addView(row(it)) }
        // mark the languages that already work without internet
        try {
            com.google.mlkit.common.model.RemoteModelManager.getInstance()
                .getDownloadedModels(com.google.mlkit.nl.translate.TranslateRemoteModel::class.java)
                .addOnSuccessListener { models ->
                    offline += models.map { it.language }
                    offline.forEach { lang -> rows[lang]?.let { v ->
                        v.text = listOf(v.text.toString(), ime.getString(R.string.muninn_translate_offline)).filter { it.isNotEmpty() }.joinToString(" · ")
                    } }
                }
        } catch (e: Throwable) { Log.w(TAG, "can't list downloaded languages", e) }

        val scroll = android.widget.ScrollView(ime).apply {
            addView(list)
            background = GradientDrawable().apply {
                cornerRadius = 22f * d
                setColor(ground)
                setStroke(dp(1), ColorUtils.setAlphaComponent(ink, 0x22))
            }
            clipToOutline = true
        }
        val height = (ime.resources.displayMetrics.heightPixels * 0.45f).toInt()
        val width = anchor.rootView.width - dp(16)
        picker = android.widget.PopupWindow(scroll, width, height, true).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
            isOutsideTouchable = true
            elevation = 16 * d
            showAtLocation(anchor.rootView, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(8))
        }
    }

    private fun translateSoon(delay: Long = 350) {
        pending?.let { handler.removeCallbacks(it) }
        val r = Runnable { translateNow() }
        pending = r
        handler.postDelayed(r, delay)
    }

    private fun translateNow() {
        val text = box?.text?.toString().orEmpty()
        val real = ime.realInputConnection() ?: return
        if (text.isBlank()) {
            real.setComposingText("", 1)
            return
        }
        val t = translator ?: return
        if (!ready) return
        val id = ++request
        val task = try { t.translate(text) } catch (e: Throwable) { Log.w(TAG, "translation failed", e); return }
        task.addOnSuccessListener { result ->
            if (id != request || !active) return@addOnSuccessListener
            // the translation sits in the app's field as composing text, replaced each time, until kept
            ime.realInputConnection()?.setComposingText(result, 1)
        }.addOnFailureListener { e -> Log.w(TAG, "translation failed", e) }
    }

    /** ✓: keep the translation in the app and close the bar */
    fun keep() {
        if (!active) return
        pending?.let { handler.removeCallbacks(it) }
        // translate what is in the box one last time before keeping it
        val text = box?.text?.toString().orEmpty()
        val t = translator
        val task = if (text.isNotBlank() && t != null && ready) try { t.translate(text) } catch (e: Throwable) { null } else null
        if (task != null) {
            task.addOnSuccessListener { result ->
                ime.realInputConnection()?.apply { setComposingText(result, 1); finishComposingText() }
                close()
            }.addOnFailureListener { close() }
        } else {
            ime.realInputConnection()?.finishComposingText()
            close()
        }
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** ✕: take the translation away again and close */
    fun discard() {
        if (!active) return
        ime.realInputConnection()?.apply { setComposingText("", 1); finishComposingText() }
        close()
    }

    /** the keyboard closed: keep what is there, quietly */
    fun cancel() {
        if (!active) return
        ime.realInputConnection()?.finishComposingText()
        close()
    }

    private fun close() {
        picker?.dismiss()
        picker = null
        active = false
        request++
        pending?.let { handler.removeCallbacks(it) }
        ime.setMuninnInputRedirect(null)
        translator?.close()
        translator = null
        view = null
        box = null
        boxConnection = null
        ime.setNeutralSuggestionStrip()
    }

    /** while translating, the bar stays in place of suggestions */
    fun showIfActive(): Boolean {
        if (!active) return false
        val strip = ime.suggestionStripViewOrNull() ?: return true
        view?.let { if (it.parent == null) strip.setExternalSuggestionView(it, false) }
        return true
    }

    /** [EN] ⇄ [RU] [ the text you type…  ] [✓] [✕]: tap a language to choose it from all of them, ⇄ swaps */
    private fun showBar() {
        val strip = ime.suggestionStripViewOrNull() ?: return
        val colors = Settings.getValues().mColors
        val gold = colors.get(ColorType.CLIPBOARD_PIN)
        val ink = colors.get(ColorType.KEY_TEXT)
        val d = ime.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(ime).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            setPadding(dp(4), dp(4), dp(2), dp(4))
        }
        fun pill(text: String, from: Boolean) = TextView(ime).apply {
            KeyboardTypeface.applyToTextView(this)
            this.text = text
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 100f * d; setColor(ColorUtils.setAlphaComponent(ink, 0x18)) }
            minWidth = dp(44)
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); openPicker(from, this) }
        }
        val from = pill(short(source), true)
        val to = pill(short(target), false)
        val swapButton = ImageButton(ime).apply {
            setImageResource(R.drawable.ic_muninn_swap)
            setColorFilter(ColorUtils.setAlphaComponent(ink, 0xCC))
            background = null
            contentDescription = ime.getString(R.string.muninn_translate_swap)
            layoutParams = LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); swap() }
        }
        val edit = EditText(ime).apply {
            KeyboardTypeface.applyToTextView(this)
            setTextColor(ink)
            setHintTextColor(ColorUtils.setAlphaComponent(ink, 0x88))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.START
            // it shows what the keys type; it never takes focus (that would ask for a keyboard inside the keyboard)
            isFocusable = false
            isFocusableInTouchMode = false
            showSoftInputOnFocus = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            background = GradientDrawable().apply {
                cornerRadius = 12f * d
                setColor(ColorUtils.setAlphaComponent(ink, 0x14))
                setStroke(dp(1), ColorUtils.setAlphaComponent(gold, 0x99))
            }
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(6), 0, dp(4), 0) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { translateSoon() }
            })
        }
        val keepButton = ImageButton(ime).apply {
            setImageResource(R.drawable.ic_muninn_check)
            setColorFilter(gold)
            background = null
            contentDescription = ime.getString(R.string.muninn_translate_keep)
            layoutParams = LinearLayout.LayoutParams(dp(42), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { keep() }
        }
        val discardButton = ImageButton(ime).apply {
            setImageResource(R.drawable.ic_muninn_close)
            setColorFilter(ink)
            background = null
            contentDescription = ime.getString(R.string.muninn_translate_discard)
            layoutParams = LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { discard() }
        }
        root.addView(from)
        root.addView(swapButton)
        root.addView(to)
        root.addView(edit)
        root.addView(keepButton)
        root.addView(discardButton)
        view = root
        box = edit
        fromLabel = from
        toLabel = to
        showPair()
        val info = EditorInfo().apply { inputType = edit.inputType; imeOptions = EditorInfo.IME_ACTION_DONE }
        boxConnection = edit.onCreateInputConnection(info)
        strip.setExternalSuggestionView(root, false)
        strip.setToolbarVisibility(false)
        root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (languages().size < 2) Toast.makeText(ime, R.string.muninn_translate_no_model, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MuninnTranslate"
        private const val PREF_TARGET = "muninn_translate_target"
        private const val PREF_SOURCE = "muninn_translate_source"
    }
}
