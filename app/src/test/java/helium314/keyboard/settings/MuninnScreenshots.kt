// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.screens.MainSettingsScreen
import helium314.keyboard.settings.screens.MuninnCacheScreen
import helium314.keyboard.settings.screens.MuninnLookScreen
import helium314.keyboard.settings.screens.MuninnMemoryScreen
import helium314.keyboard.settings.screens.MuninnTypingScreen
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Muninn: renders the app's screens to PNG files, to compare them with the design canvas.
 * Not part of the normal test run: ./gradlew :app:testDebugUnitTest --tests "*MuninnScreenshots*" -Dmuninn.screenshots=<folder>
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h870dp-night-xxhdpi")
class MuninnScreenshots {
    private val out = System.getProperty("muninn.screenshots")

    private fun shoot(name: String, heightDp: Int = 870, content: @Composable () -> Unit) {
        Assume.assumeTrue(out != null)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { Theme { content() } }
        shadowOf(Looper.getMainLooper()).idle()
        val view = activity.window.decorView
        val density = activity.resources.displayMetrics.density
        val w = (392 * density).toInt()
        val h = (heightDp * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(out!!).mkdirs()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = shoot("home", 1240) { MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    @Test fun typing() = shoot("typing") { MuninnTypingScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun memory() = shoot("memory", 1300) { MuninnMemoryScreen({}, {}) }
    @Test fun look() = shoot("look", 1500) { MuninnLookScreen({}, {}) }
    @Test fun appearance() {
        helium314.keyboard.settings.SettingsActivity.settingsContainer = helium314.keyboard.settings.SettingsContainer(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        shoot("appearance", 1500) { helium314.keyboard.settings.screens.AppearanceScreen {} }
    }
    @Test fun cache() = shoot("cache") { MuninnCacheScreen {} }
    @Test fun cacheWithClips() {
        Assume.assumeTrue(out != null)
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val dao = helium314.keyboard.latin.database.ClipboardDao.getInstance(ctx)!!
        val now = System.currentTimeMillis()
        dao.addClip(now - 3 * 86_400_000L, true, "ul. Pushkina 12, apt 34, entrance 2, code 34K")
        dao.addClip(now - 6 * 86_400_000L, false, "Flight check-in, MN 2135, seat 14C")
        dao.addClip(now - 9 * 86_400_000L, false, "https://youtu.be/plov-in-a-kazan")
        dao.addClip(now - 120_000L, false, "482913", expiresAt = now + 480_000L)
        dao.addClip(now - 86_400_000L, false, "Running shoes, size 43, the grey ones")
        dao.getAll().firstOrNull { it.text?.startsWith("Flight") == true }?.let { dao.toggleTag(it.id, "important"); dao.setReminder(it.id, now + 3_600_000L) }
        dao.getAll().firstOrNull { it.text?.startsWith("https") == true }?.let { dao.toggleTag(it.id, "idea") }
        dao.getAll().firstOrNull { it.text?.startsWith("Running") == true }?.let { dao.toggleTag(it.id, "buy") }
        shoot("cache_clips") { MuninnCacheScreen {} }
        shoot("home_clips", 1240) { MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }
    @Test fun share() {
        Assume.assumeTrue(out != null)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, "10-minute stretch for people who sit all day https://vm.tiktok.com/ZM123/")
        val activity = Robolectric.buildActivity(MuninnShareActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val view = activity.window.decorView
        val density = activity.resources.displayMetrics.density
        val w = (392 * density).toInt(); val h = (870 * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF2B3B58.toInt()) // stands in for the app you shared from
        view.draw(canvas)
        File(out!!).mkdirs()
        File(out, "share.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun homeLight() { light { shoot("home_light", 1240) { MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } } }
    @Test fun lookLight() { light { shoot("look_light", 1500) { MuninnLookScreen({}, {}) } } }
    @Test fun guardLight() { light { shoot("guard_light", 1200) { helium314.keyboard.settings.screens.MuninnGuardScreen {} } } }
    @Test fun cacheLight() { light { shoot("cache_light") { MuninnCacheScreen {} } } }

    private fun light(block: () -> Unit) {
        helium314.keyboard.latin.utils.MuninnAppTheme.mode = 1
        try { block() } finally { helium314.keyboard.latin.utils.MuninnAppTheme.mode = -1 }
    }
    @Test fun imageViewer() {
        val file = java.io.File.createTempFile("clip", ".png")
        val bmp = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(0xFF1C2334.toInt())
        val p = android.graphics.Paint().apply { color = 0xFFE8B04B.toInt(); textSize = 120f; isAntiAlias = true }
        c.drawText("Screenshot", 200f, 1200f, p)
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        shoot("image_viewer") { MuninnImageViewer(file) {} }
    }
    @Test fun welcome() = shoot("welcome") { WelcomeWizard({}, {}) }
    @Test fun guard() = shoot("guard", 1100) { helium314.keyboard.settings.screens.MuninnGuardScreen {} }
}
