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
    private var pairLabel: TextView? = null
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
        source = TranslateLanguage.fromLanguageTag(RichInputMethodManager.getInstance().currentSubtype.locale.language) ?: TranslateLanguage.ENGLISH
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
        val t = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
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

    /** the next target language among the person's languages and a few common ones */
    private fun nextTarget() {
        val list = languages().filter { it != source }
        target = list[(list.indexOf(target) + 1) % list.size]
        ime.prefs().edit { putString(PREF_TARGET, target) }
        pairLabel?.text = "${short(source)} → ${short(target)}"
        prepareTranslator()
    }

    /** swap: the target becomes the source */
    private fun swap() {
        val old = source
        source = target
        target = old
        pairLabel?.text = "${short(source)} → ${short(target)}"
        prepareTranslator()
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
        t.translate(text).addOnSuccessListener { result ->
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
        if (text.isNotBlank() && t != null && ready) {
            t.translate(text).addOnSuccessListener { result ->
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

    /** [EN → RU] [ the text you type…  ] [✓] [✕] */
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
        val pair = TextView(ime).apply {
            KeyboardTypeface.applyToTextView(this)
            text = "${short(source)} → ${short(target)}"
            setTextColor(gold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 100f * d; setColor(ColorUtils.setAlphaComponent(gold, 0x22)) }
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
            contentDescription = ime.getString(R.string.muninn_translate_pair)
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); nextTarget() }
            setOnLongClickListener { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); swap(); true }
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
        root.addView(pair)
        root.addView(edit)
        root.addView(keepButton)
        root.addView(discardButton)
        view = root
        box = edit
        pairLabel = pair
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
    }
}
