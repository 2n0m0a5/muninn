// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log

/**
 * Muninn: voice typing inside the keyboard. The mic no longer hands you over to another keyboard: Muninn listens,
 * shows the words as they come in the bar above the keys, and types them itself. The words are recognised by the
 * phone's speech service, on the phone itself when it can (Android 12+), in the language Muninn is typing.
 */
class MuninnVoice(private val ime: LatinIME) {
    private var recognizer: SpeechRecognizer? = null
    private var view: View? = null
    private var label: TextView? = null
    private var pulse: ValueAnimator? = null
    var listening = false
        private set

    fun toggle() = if (listening) stop() else start()

    private fun start() {
        if (ime.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // a keyboard can't ask for permission itself, so a see-through screen asks once
            ime.startActivity(Intent(ime, MuninnMicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ime)) {
            Toast.makeText(ime, R.string.muninn_voice_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ime)
        recognizer = try {
            if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(ime) else SpeechRecognizer.createSpeechRecognizer(ime)
        } catch (e: Exception) {
            Log.w(TAG, "can't create recognizer", e)
            SpeechRecognizer.createSpeechRecognizer(ime)
        }
        val language = RichInputMethodManager.getInstance().currentSubtype.locale.toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ime.packageName)
        recognizer?.setRecognitionListener(listener)
        listening = true
        showStrip()
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "can't start listening", e)
            finish(R.string.muninn_voice_error)
        }
    }

    fun stop() {
        // stop listening; the last words still arrive in onResults
        try { recognizer?.stopListening() } catch (_: Exception) { }
        label?.setText(R.string.muninn_voice_finishing)
    }

    /** the keyboard closed: drop everything quietly */
    fun cancel() {
        if (!listening) return
        try { recognizer?.cancel() } catch (_: Exception) { }
        cleanUp()
        ime.setNeutralSuggestionStrip()
    }

    /** while listening the strip shows Muninn's listening bar instead of suggestions */
    fun showIfListening(): Boolean {
        if (!listening) return false
        showStrip()
        return true
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { label?.setText(R.string.muninn_voice_listening) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { label?.setText(R.string.muninn_voice_finishing) }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) label?.text = text
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) type(text)
            finish(null)
        }

        override fun onError(error: Int) {
            finish(when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.muninn_voice_nothing_heard
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.muninn_voice_offline
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.muninn_voice_permission
                else -> R.string.muninn_voice_error
            })
        }
    }

    /** types the words where the cursor is, with a space before them when needed */
    private fun type(text: String) {
        val ic = ime.currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(1, 0)
        val spaced = if (!TextUtils.isEmpty(before) && !before!!.last().isWhitespace()) " $text" else text
        ic.commitText(spaced, 1)
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun finish(message: Int?) {
        cleanUp()
        if (message != null) Toast.makeText(ime, message, Toast.LENGTH_SHORT).show()
        ime.setNeutralSuggestionStrip()
    }

    private fun cleanUp() {
        listening = false
        pulse?.cancel()
        pulse = null
        try { recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
        view = null
        label = null
    }

    /** [● Listening… / the words so far        ✕] in Muninn's gold, in the bar above the keys */
    private fun showStrip() {
        val strip = ime.suggestionStripViewOrNull() ?: return
        val existing = view
        if (existing != null && existing.parent == null) {
            strip.setExternalSuggestionView(existing, false)
            return
        }
        if (existing != null) return
        val colors = Settings.getValues().mColors
        val gold = colors.get(ColorType.CLIPBOARD_PIN)
        val d = ime.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val pill = GradientDrawable().apply {
            cornerRadius = 100f * d
            setColor(ColorUtils.setAlphaComponent(gold, 0x22))
            setStroke(dp(1), ColorUtils.setAlphaComponent(gold, 0xAA))
        }
        val root = LinearLayout(ime).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pill
            setPadding(dp(14), 0, dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { setMargins(dp(4), dp(5), dp(4), dp(5)) }
        }
        val dot = View(ime).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(gold) }
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(10) }
        }
        pulse = ValueAnimator.ofFloat(1f, 0.25f).apply {
            duration = 700; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { dot.alpha = it.animatedValue as Float }
            start()
        }
        val text = TextView(ime).apply {
            KeyboardTypeface.applyToTextView(this)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(gold)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START // the newest words stay visible
            setText(R.string.muninn_voice_starting)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { stop() }
        }
        val done = ImageButton(ime).apply {
            setImageResource(R.drawable.ic_muninn_check)
            setColorFilter(gold)
            background = null
            contentDescription = ime.getString(R.string.muninn_voice_done)
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { stop() }
        }
        val cancel = ImageButton(ime).apply {
            setImageResource(R.drawable.ic_muninn_close)
            setColorFilter(colors.get(ColorType.KEY_TEXT))
            background = null
            contentDescription = ime.getString(android.R.string.cancel)
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { cancel() }
        }
        root.addView(dot)
        root.addView(text)
        root.addView(done)
        root.addView(cancel)
        view = root
        label = text
        strip.setExternalSuggestionView(root, false)
        strip.setToolbarVisibility(false)
        root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    companion object {
        private const val TAG = "MuninnVoice"
    }
}

/** a see-through screen that asks for the microphone once (a keyboard can't ask by itself), then closes */
class MuninnMicPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) finish()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        Toast.makeText(this, if (granted) R.string.muninn_voice_ready else R.string.muninn_voice_permission, Toast.LENGTH_LONG).show()
        finish()
    }
}
