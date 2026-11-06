// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import java.io.File

/**
 * Muninn: an image clip, full screen. Pinch to zoom (up to 6×), drag to move around, double-tap to zoom in where you
 * tapped (or back out), and − / + for one hand. Opened from the cache in the app and from the keyboard's clip menu.
 */
class MuninnImageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(EXTRA_FILE)
        // only a plain file name inside Muninn's own clip folder
        ClipboardDao.getInstance(this) // sets up the clip folder
        val dir = File(filesDir, "clipboard")
        val file = name?.takeIf { it.isNotEmpty() && '/' !in it && '\\' !in it && it != ".." }?.let { File(dir, it) }
        if (file == null || !file.isFile) { finish(); return }
        setContent { MuninnImageViewer(file) { finish() } }
    }

    companion object {
        private const val EXTRA_FILE = "file"
        fun open(context: Context, filename: String) {
            context.startActivity(Intent(context, MuninnImageActivity::class.java).putExtra(EXTRA_FILE, filename)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private const val MAX_ZOOM = 6f

@Composable
fun MuninnImageViewer(file: File, onClose: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // big enough to stay sharp when zoomed in, small enough not to run out of memory
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
                android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (_: Throwable) { null }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun center() = Offset(size.width / 2f, size.height / 2f)

    // the picture may not be dragged past its own edges
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
    // zoom to [newScale] keeping the point [focus] (on screen) where it is
    fun zoomTo(newScale: Float, focus: Offset = center()) {
        val s = newScale.coerceIn(1f, MAX_ZOOM)
        offset = clamp((focus - center()) - (focus - center() - offset) * (s / scale), s)
        scale = s
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bmp = bitmap
        if (bmp == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(0xFFE8B04B))
        else Image(
            bmp.asImageBitmap(), contentDescription = stringResource(R.string.muninn_image_viewer),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { tap ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (scale > 1.05f) { scale = 1f; offset = Offset.Zero } else zoomTo(2.5f, tap)
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val s = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        offset = clamp((centroid - center()) - (centroid - center() - offset) * (s / scale) + pan, s)
                        scale = s
                    }
                }
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
        )

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(48.dp)
                .clip(CircleShape).background(Color(0x99000000))) {
                Icon(painterResource(R.drawable.ic_muninn_close), stringResource(R.string.muninn_image_close), tint = Color.White)
            }
            // − 100% +   (tap the percentage to fit the picture again)
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).clip(RoundedCornerShape(28.dp))
                    .background(Color(0xB3101420)).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); zoomTo(scale / 1.5f) },
                    enabled = scale > 1.01f, modifier = Modifier.size(48.dp)) {
                    Text("−", color = if (scale > 1.01f) Color.White else Color(0x66FFFFFF), fontSize = 26.sp)
                }
                Text("${(scale * 100).toInt()}%", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(64.dp).clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) { detectTapGestures(onTap = { scale = 1f; offset = Offset.Zero }) }
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); zoomTo(scale * 1.5f) },
                    enabled = scale < MAX_ZOOM - 0.01f, modifier = Modifier.size(48.dp)) {
                    Text("+", color = if (scale < MAX_ZOOM - 0.01f) Color.White else Color(0x66FFFFFF), fontSize = 26.sp)
                }
            }
        }
    }
}
