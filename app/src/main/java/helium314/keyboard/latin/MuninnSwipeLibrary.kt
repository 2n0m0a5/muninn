// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Muninn: swipe typing in one tap. Drawing words across the letters needs a small closed-source piece of Google's
 * keyboard (about 1 MB), which no open-source keyboard may ship inside itself. Muninn fetches it once, at the person's
 * request, from a fixed version on GitHub, and keeps it only if its fingerprint (SHA-256) is exactly the known one.
 * This is the same file HeliBoard's guide points to; Muninn just saves the steps.
 */
object MuninnSwipeLibrary {
    private const val TAG = "MuninnSwipeLibrary"
    private const val COMMIT = "46fdf2b550035ca69299ce312fa158e7ade36967"
    /** fingerprints of the files at that commit, checked by hand */
    private val FINGERPRINTS = mapOf(
        "arm64-v8a" to "b1049983e6ac5cfc6d1c66e38959751044fad213dff0637a6cf1d2a2703e754f",
        "armeabi-v7a" to "442a2a8bfcb25489564bc9433a916fa4dc0dba9000fe6f6f03f5939b985091e6",
        "x86" to "c882e12e6d48dd946e0b644c66868a720bd11ac3fecf152000e21a3d5abd59c9",
        "x86_64" to "bd946d126c957b5a6dea3bafa07fa36a27950b30e2b684dffc60746d0a1c7ad8",
    )
    private const val MAX_BYTES = 8L * 1024 * 1024

    fun available() = JniUtils.sHaveGestureLib

    private fun abi() = Build.SUPPORTED_ABIS.firstOrNull { it in FINGERPRINTS }

    fun canGet() = abi() != null

    private fun url(abi: String) =
        "https://github.com/erkserkserks/openboard/raw/$COMMIT/app/src/main/jniLibs/$abi/libjni_latinimegoogle.so"

    /** downloads, checks and installs the library, and turns swipe typing on. Blocking: call off the main thread. */
    fun install(context: Context): Boolean {
        val abi = abi() ?: return false
        val expected = FINGERPRINTS.getValue(abi)
        val temp = File(context.filesDir, "muninn_swipe_download")
        try {
            temp.delete()
            var connection = URL(url(abi)).openConnection() as HttpURLConnection
            var redirects = 0
            while (true) {
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = false
                val code = connection.responseCode
                if (code in 300..399 && redirects < 5) {
                    val next = connection.getHeaderField("Location") ?: return false
                    connection.disconnect()
                    val target = URL(URL(connection.url, next).toString())
                    if (target.protocol != "https") return false
                    connection = target.openConnection() as HttpURLConnection
                    redirects++
                    continue
                }
                if (code != 200) {
                    Log.w(TAG, "download failed: $code")
                    return false
                }
                break
            }
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return false
                        output.write(buffer, 0, n)
                    }
                }
            }
            connection.disconnect()
            val checksum = ChecksumCalculator.checksum(temp)
            if (checksum != expected) {
                Log.w(TAG, "fingerprint does not match: $checksum")
                return false
            }
            val libFile = File(context.filesDir, JniUtils.JNI_LIB_IMPORT_FILE_NAME)
            libFile.setWritable(true)
            libFile.delete()
            // JniUtils loads the file only if its fingerprint matches the one stored here
            context.protectedPrefs().edit(commit = true) { putString(Settings.PREF_LIBRARY_CHECKSUM, checksum) }
            temp.copyTo(libFile)
            libFile.setReadOnly() // Android 14 refuses to load writable code
            context.prefs().edit(commit = true) { putBoolean(Settings.PREF_GESTURE_INPUT, true) }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "can't get the swipe library", e)
            return false
        } finally {
            temp.delete()
        }
    }

    /** the library is loaded when Muninn starts, so Muninn starts again */
    fun restart() {
        Runtime.getRuntime().exit(0)
    }
}
