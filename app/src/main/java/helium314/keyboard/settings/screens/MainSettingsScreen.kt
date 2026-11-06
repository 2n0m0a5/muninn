// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.MuninnClips
import helium314.keyboard.latin.MuninnGuard
import helium314.keyboard.latin.MuninnReminders
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.MuninnRunic
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.GoldButton
import helium314.keyboard.settings.OutlineButton
import helium314.keyboard.settings.initPreview
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Muninn: home. What matters today first (things coming back, the cache), then the four spaces
 * (Typing, Memory, Look, Guard), the one-look guide and three quick switches. Settings stay in the background.
 */
@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickDataGathering: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickBack: () -> Unit,
    onClickCache: () -> Unit = {},
    onClickReminders: () -> Unit = {},
    onClickTyping: () -> Unit = {},
    onClickMemory: () -> Unit = {},
    onClickLook: () -> Unit = {},
    onClickGuard: () -> Unit = {},
) {
    var version by remember { mutableIntStateOf(0) } // bumped after Done / Later, so the lists are read again
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            MuninnHeader()
            OtherKeyboardBanner()
            ComingBackToday(version, onClickMemory) { version++ }
            CacheCard(version, onClickCache)
            Spaces(onClickTyping, onClickMemory, onClickLook, onClickGuard)
            HowMuninnWorks()
            Footer(onClickAbout, onClickAdvanced)
        }
    }
}

@Composable
private fun MuninnHeader() {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    // check again every second, so switching keyboards (even from the picker on this screen) shows right away
    var active by remember { mutableStateOf(UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) }
    LaunchedEffect(Unit) {
        while (true) {
            active = UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)
            delay(1000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(painterResource(R.drawable.ic_muninn_feather), null, Modifier.size(width = 18.dp, height = 44.dp).rotate(18f))
        Text("muninn", fontFamily = MuninnSerif, fontSize = 46.sp, lineHeight = 46.sp)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.height(34.dp).clip(RoundedCornerShape(50))
                .background(if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .clickable { imm.showInputMethodPicker() }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) Color(0xFF6FCF97) else MaterialTheme.colorScheme.primary))
            Text(
                stringResource(if (active) R.string.muninn_your_keyboard else R.string.muninn_not_your_keyboard),
                fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** when another keyboard is in use: say so plainly and offer the way back (the cache is safe either way) */
@Composable
private fun OtherKeyboardBanner() {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    var other by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            other = if (MuninnGuard.isMuninnCurrent(ctx) || !MuninnGuard.isMuninnEnabled(ctx)) null
                else MuninnGuard.keyboardLabel(ctx, MuninnGuard.currentId(ctx))
            delay(1000)
        }
    }
    val name = other ?: return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.muninn_other_keyboard), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.muninn_other_keyboard_text, name), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GoldButton(stringResource(R.string.muninn_guard_switch_back), Modifier.width(124.dp), height = 40.dp) {
            if (MuninnGuard.canSwitchBySelf(ctx) && MuninnGuard.switchBack(ctx)) Unit else imm.showInputMethodPicker()
        }
    }
}

private fun endOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
}.timeInMillis

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** gold cards for what comes back today, each with Done / Later / Copy right there */
@Composable
private fun ComingBackToday(version: Int, onClickAll: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val today = remember(version) {
        val end = endOfToday()
        ClipboardDao.getInstance(ctx)?.withReminders()?.filter { (it.remindAt ?: Long.MAX_VALUE) <= end }?.take(3).orEmpty()
            .map { it to MuninnClips.metaLine(ctx, it) }
    }
    if (today.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.muninn_coming_back_today, today.size).uppercase(), fontSize = 12.sp, letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.muninn_all_reminders), fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClickAll).padding(6.dp))
        }
        today.forEach { (clip, meta) ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(clipText(ctx, clip), fontSize = 15.5.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(meta, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GoldButton(stringResource(R.string.muninn_reminder_done), Modifier.weight(1f), height = 40.dp) {
                        MuninnReminders.done(ctx, clip.id)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(ctx, R.string.muninn_reminder_done_feedback, Toast.LENGTH_SHORT).show()
                        onChanged()
                    }
                    OutlineButton(stringResource(R.string.muninn_reminder_later), Modifier.weight(1f), height = 40.dp) {
                        MuninnReminders.later(ctx, clip.id)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val at = ClipboardDao.getInstance(ctx)?.get(clip.id)?.remindAt
                        if (at != null) Toast.makeText(ctx, ctx.getString(R.string.muninn_reminder_later_feedback,
                            MuninnReminders.describeTime(ctx, at)), Toast.LENGTH_SHORT).show()
                        onChanged()
                    }
                    Text(stringResource(R.string.muninn_copy), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                            copyToClipboard(ctx, clip)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }.padding(horizontal = 10.dp, vertical = 11.dp))
                }
            }
        }
    }
}

private fun clipText(ctx: Context, clip: ClipboardHistoryEntry) = when {
    clip.isCode -> MuninnClips.MASKED_CODE
    clip.filename != null -> ctx.getString(R.string.muninn_cache_file)
    else -> clip.text.orEmpty().replace('\n', ' ')
}

private fun copyToClipboard(ctx: Context, clip: ClipboardHistoryEntry) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("", clip.text.orEmpty()))
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, R.string.muninn_cache_copied, Toast.LENGTH_SHORT).show()
}

/** "The cache": counts, a search field and the three newest clips. Everything opens the cache. */
@Composable
private fun CacheCard(version: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { ClipboardDao.getInstance(ctx) }
    val all = remember(version) { dao?.getAll().orEmpty().toList() }
    val todayCount = remember(version) { val start = startOfToday(); all.count { it.timeStamp >= start } }
    val recent = remember(version) { all.sortedByDescending { it.timeStamp }.take(3).map { it to MuninnClips.metaLine(ctx, it) } }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.muninn_cache_title), fontFamily = MuninnSerif, fontSize = 30.sp, lineHeight = 32.sp)
                Text(stringResource(R.string.muninn_cache_counts, all.size, todayCount), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_arrow_left), null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp).rotate(180f))
        }
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(painterResource(R.drawable.ic_muninn_search), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.muninn_cache_search), fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (recent.isNotEmpty()) Column {
            recent.forEach { (clip, meta) ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
                Column(Modifier.padding(vertical = 9.dp, horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(withLinks(clipText(ctx, clip), !clip.isCode && clip.filename == null, MaterialTheme.colorScheme.tertiary),
                        fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** the four spaces, two by two */
@Composable
private fun Spaces(onTyping: () -> Unit, onMemory: () -> Unit, onLook: () -> Unit, onGuard: () -> Unit) {
    val ctx = LocalContext.current
    val languages = remember {
        SubtypeSettings.getEnabledSubtypes(true).map { it.locale().language.uppercase() }.distinct().joinToString(" · ")
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpaceTile("ᚺ", stringResource(R.string.muninn_space_typing), stringResource(R.string.muninn_space_typing_sub, languages),
                MaterialTheme.colorScheme.secondary, Modifier.weight(1f), onClick = onTyping)
            SpaceTile("ᛗ", stringResource(R.string.muninn_space_memory), stringResource(R.string.muninn_space_memory_sub),
                MaterialTheme.colorScheme.primary, Modifier.weight(1f), gold = true, onClick = onMemory)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpaceTile("ᛚ", stringResource(R.string.muninn_space_look), stringResource(R.string.muninn_space_look_sub),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), Modifier.weight(1f), onClick = onLook)
            val guardSub = remember {
                if (!MuninnGuard.enabled(ctx)) ctx.getString(R.string.muninn_space_guard_off)
                else MuninnGuard.stoppedThisWeek(ctx).let { n ->
                    if (n == 0) ctx.getString(R.string.muninn_space_guard_sub)
                    else ctx.resources.getQuantityString(R.plurals.muninn_guard_stopped_week, n, n)
                }
            }
            SpaceTile("ᛉ", stringResource(R.string.muninn_guard), guardSub,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), Modifier.weight(1f), onClick = onGuard)
        }
    }
}

@Composable
private fun SpaceTile(rune: String, name: String, sub: String, ink: Color, modifier: Modifier, gold: Boolean = false, soon: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.heightIn(min = 132.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (gold) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shape) else Modifier)
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Text(rune, fontFamily = MuninnRunic, fontSize = 19.sp, color = ink)
            }
            Spacer(Modifier.weight(1f))
            if (soon) Text(stringResource(R.string.muninn_soon), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(sub, fontSize = 12.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Footer(onClickAbout: () -> Unit, onClickAdvanced: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ᛗᚢᚾᛁᚾᚾ", fontFamily = MuninnRunic, fontSize = 13.sp, letterSpacing = 6.sp, color = MaterialTheme.colorScheme.outline)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FooterLink(stringResource(R.string.settings_screen_about), onClickAbout)
            FooterLink(stringResource(R.string.muninn_on_github)) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Links.MUNINN_GITHUB)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            FooterLink(stringResource(R.string.settings_screen_advanced), onClickAdvanced)
        }
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp))
}

/**
 * Muninn: every gesture and signal on one card, so one look is enough to know the whole keyboard.
 * Open the first time; after "Got it" it folds into one line that can be opened again.
 */
@Composable
private fun HowMuninnWorks() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(!ctx.prefs().getBoolean(PREF_HOW_SEEN, false)) }
    val gold = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .animateContentSize()
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ᚱ", fontFamily = MuninnRunic, fontSize = 20.sp, color = gold, modifier = Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.muninn_how_title), style = MaterialTheme.typography.titleMedium)
                if (!open) Text(stringResource(R.string.muninn_how_subtitle), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_arrow_left), null, tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp).rotate(if (open) 90f else 270f))
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HowRow(R.drawable.ic_muninn_feather_small, R.string.muninn_how_copy, stringResource(R.string.muninn_how_copy_does), gold = true)
                HowRow(R.drawable.ic_muninn_share, R.string.muninn_how_share, stringResource(R.string.muninn_how_share_does), gold = true)
                HowRow(R.drawable.ic_muninn_translate, R.string.muninn_how_translate, stringResource(R.string.muninn_how_translate_does))
                HowRow(R.drawable.ic_arrow_horizontal, R.string.muninn_how_swipe_space, stringResource(R.string.muninn_how_swipe_space_does))
                HowRow(R.drawable.ic_settings_gesture, R.string.muninn_how_hold_space, stringResource(R.string.muninn_how_hold_space_does))
                HowRow(R.drawable.ic_muninn_pin, R.string.muninn_how_hold_clip, stringResource(R.string.muninn_how_hold_clip_does))
                HowRow(R.drawable.ic_muninn_clipboard, R.string.muninn_how_gold, stringResource(R.string.muninn_how_gold_does), gold = true)
                HowRow(R.drawable.ic_muninn_bell, R.string.muninn_how_chip, stringResource(R.string.muninn_how_chip_does), gold = true)
                HowRow(R.drawable.ic_muninn_restore, R.string.muninn_how_deleted,
                    stringResource(R.string.muninn_how_deleted_does, helium314.keyboard.latin.database.ClipboardDao.TRASH_DAYS))
                HowRow(R.drawable.ic_muninn_keyboard, R.string.muninn_how_preview, stringResource(R.string.muninn_how_preview_does))
            }
            Row(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = {
                    open = false
                    ctx.prefs().edit { putBoolean(PREF_HOW_SEEN, true) }
                }) { Text(stringResource(R.string.muninn_how_got_it)) }
            }
        }
    }
}

private const val PREF_HOW_SEEN = "muninn_how_it_works_seen"

@Composable
private fun HowRow(@DrawableRes icon: Int, @StringRes action: Int, result: String, gold: Boolean = false) {
    val color = if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(action), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
