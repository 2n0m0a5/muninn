// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.MuninnClips
import helium314.keyboard.latin.MuninnReminderTimes
import helium314.keyboard.latin.MuninnReminders
import helium314.keyboard.latin.MuninnShare
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.Theme
import kotlinx.coroutines.delay

/**
 * Muninn, Layer 5: "Share to Muninn" from any app, and "Keep in Muninn" on selected text.
 * The clip is saved the moment this opens; the sheet only adds a tag and when it should come back.
 * Doing nothing is fine: it closes by itself after a few seconds and the clip stays kept.
 */
class MuninnShareActivity : ComponentActivity() {
    private var clipId = -1L
    private var shared: MuninnShare.Shared? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        }
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)
        val parsed = MuninnShare.parse(text, subject)
        val dao = ClipboardDao.getInstance(this)
        if (parsed == null || dao == null) {
            Toast.makeText(this, R.string.muninn_share_nothing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        shared = parsed
        val source = callingPackage ?: referrer?.takeIf { it.scheme == "android-app" }?.host
        if (savedInstanceState == null) dao.addClip(System.currentTimeMillis(), false, parsed.text, source?.takeIf { it != packageName })
        clipId = dao.getAll().firstOrNull { it.text == parsed.text }?.id ?: -1L
        val sourceLabel = MuninnClips.appLabel(this, source)

        setContent {
            Theme {
                // a see-through surface, so text takes the theme's colours over whatever app is behind
                androidx.compose.material3.Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                    ShareSheet(parsed, sourceLabel, onDone = { tags, preset -> finishWith(tags, preset) })
                }
            }
        }
    }

    private fun finishWith(tags: List<String>, preset: MuninnReminderTimes.Preset?) {
        val dao = ClipboardDao.getInstance(this)
        if (clipId >= 0 && dao != null) {
            val old = dao.get(clipId).tags
            val all = (old + tags).distinct()
            if (all != old) dao.setTags(clipId, all)
            if (preset != null) MuninnReminders.set(this, clipId, preset)
        }
        finish()
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        else @Suppress("DEPRECATION") overridePendingTransition(0, android.R.anim.fade_out)
    }
}

private enum class BackChoice(val label: Int, val preset: MuninnReminderTimes.Preset?) {
    TONIGHT(R.string.muninn_remind_tonight, MuninnReminderTimes.Preset.TONIGHT),
    WEEKEND(R.string.muninn_remind_weekend, MuninnReminderTimes.Preset.WEEKEND),
    KEEP(R.string.muninn_share_just_keep, null),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareSheet(shared: MuninnShare.Shared, sourceLabel: String?, onDone: (List<String>, MuninnReminderTimes.Preset?) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val suggested = remember { MuninnShare.suggestedTag(shared) }
    val tags = remember { mutableStateListOf<String>().apply { suggested?.let { add(it) } } }
    var back by remember { mutableStateOf(if (suggested == "watch") BackChoice.WEEKEND else BackChoice.KEEP) }
    var touched by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(6) }
    val drop = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress) // felt: it is saved
        drop.animateTo(1f, tween(700))
    }
    // closes by itself if the person just wanted it kept
    LaunchedEffect(touched) {
        if (touched) return@LaunchedEffect
        while (secondsLeft > 0) { delay(1000); if (!touched) secondsLeft-- }
        if (!touched) onDone(tags.toList(), back.preset)
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone(tags.toList(), back.preset) }
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { touched = true }
                .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(); touched = true } } }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
            }
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_muninn_feather_small), null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp).offset(y = (-14 * (1 - drop.value)).dp).rotate(-20f + 38f * drop.value))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.muninn_share_kept), fontFamily = MuninnSerif, fontSize = 30.sp, lineHeight = 32.sp)
                    Text(stringResource(R.string.muninn_share_kept_text), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(shared.title ?: shared.url ?: shared.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (shared.url != null && shared.title != null)
                    Text(shared.url, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (sourceLabel != null) stringResource(R.string.muninn_share_from, sourceLabel) else stringResource(R.string.muninn_just_now),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MuninnLabel(stringResource(R.string.muninn_share_tag_it), modifier = Modifier.padding(horizontal = 0.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuninnClips.BUILT_IN_TAGS.forEach { tag ->
                        TagPill(MuninnClips.tagLabel(ctx, tag), selected = tag in tags, large = true) {
                            touched = true
                            if (tag in tags) tags.remove(tag) else tags.add(tag)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MuninnLabel(stringResource(R.string.muninn_share_bring_back), gold = true, modifier = Modifier.padding(horizontal = 0.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackChoice.entries.forEach { c ->
                        val chosen = c == back
                        Box(
                            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
                                .border(if (chosen) 1.5.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .clickable { touched = true; back = c; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(c.label), fontSize = 13.5.sp, textAlign = TextAlign.Center,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                                color = when { chosen -> MaterialTheme.colorScheme.primary; c == BackChoice.KEEP -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.onSurface })
                        }
                    }
                }
            }
            GoldButton(stringResource(R.string.muninn_reminder_done), height = 54.dp) { onDone(tags.toList(), back.preset) }
            Text(
                if (touched) stringResource(R.string.muninn_share_saved_already)
                else stringResource(R.string.muninn_share_closes_in, secondsLeft),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 0.dp)
            )
        }
    }
}

