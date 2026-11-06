// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.MuninnClips
import helium314.keyboard.latin.MuninnScreenshots
import helium314.keyboard.latin.MuninnReminders
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.settings.findIndexOfDefaultSetting
import helium314.keyboard.latin.utils.BackButton
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Chevron
import helium314.keyboard.settings.MoreOptionsRow
import helium314.keyboard.settings.MuninnCard
import helium314.keyboard.settings.MuninnChrome
import helium314.keyboard.settings.MuninnDivider
import helium314.keyboard.settings.MuninnLabel
import helium314.keyboard.settings.MuninnPrefSwitch
import helium314.keyboard.settings.MuninnRowItem
import helium314.keyboard.settings.MuninnScreen
import helium314.keyboard.settings.MuninnSection
import helium314.keyboard.settings.MuninnSegmented
import helium314.keyboard.settings.MuninnStepper
import helium314.keyboard.settings.MuninnSwitch
import helium314.keyboard.settings.OutlineButton
import helium314.keyboard.settings.TagPill
import helium314.keyboard.latin.utils.MuninnRunic
import helium314.keyboard.latin.utils.MuninnAppTheme
import helium314.keyboard.latin.AudioAndHapticFeedbackManager

/* Muninn: the four spaces on the home screen. Each shows the few settings people change; More options holds the rest. */

// ---------------------------------------------------------------- Typing (Huginn)

@Composable
fun MuninnTypingScreen(
    onClickBack: () -> Unit,
    onClickLanguages: () -> Unit,
    onClickCorrections: () -> Unit,
    onClickGesture: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickToolbar: () -> Unit,
) {
    val languages = remember { SubtypeSettings.getEnabledSubtypes(true) }
    var more by remember { mutableStateOf(false) }
    MuninnScreen(
        title = stringResource(R.string.muninn_space_typing),
        onClickBack = onClickBack,
        intro = stringResource(R.string.muninn_typing_intro),
        runes = "ᚺᚢᚷᛁᚾᚾ",
    ) {
        MuninnSection(stringResource(R.string.muninn_typing_languages)) {
            languages.forEach { subtype ->
                val layout = SubtypeLocaleUtils.getMainLayoutDisplayName(subtype.mainLayoutNameOrQwerty()) ?: subtype.mainLayoutNameOrQwerty()
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onClickLanguages).padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center) {
                        Text(subtype.locale().language.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(subtype.locale().getDisplayName(subtype.locale()).replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(layout, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Chevron()
                }
                MuninnDivider()
            }
            Row(
                Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onClickLanguages).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("+", fontSize = 20.sp, color = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.muninn_add_language), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MuninnLabel(stringResource(R.string.muninn_typing_help))
            MuninnCard {
                MuninnPrefSwitch(Settings.PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION,
                    stringResource(R.string.muninn_typing_autocorrect), stringResource(R.string.muninn_typing_autocorrect_sub)) { reload() }
                MuninnPrefSwitch(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS,
                    stringResource(R.string.muninn_typing_suggestions), stringResource(R.string.muninn_typing_suggestions_sub)) { reload() }
                if (JniUtils.sHaveGestureLib)
                    MuninnPrefSwitch(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT,
                        stringResource(R.string.muninn_typing_swipe), stringResource(R.string.muninn_typing_swipe_sub)) { reload() }
                MuninnPrefSwitch(Settings.PREF_AUTO_CAP, Defaults.PREF_AUTO_CAP,
                    stringResource(R.string.muninn_typing_autocap), stringResource(R.string.muninn_typing_autocap_sub)) { reload() }
                MoreOptionsRow(stringResource(R.string.muninn_typing_more), more) { more = !more }
                AnimatedVisibility(more) {
                    Column {
                        MuninnRowItem(stringResource(R.string.settings_screen_correction), stringResource(R.string.muninn_correction_description),
                            icon = R.drawable.ic_settings_correction, onClick = onClickCorrections) { Chevron() }
                        if (JniUtils.sHaveGestureLib)
                            MuninnRowItem(stringResource(R.string.settings_screen_gesture), null,
                                icon = R.drawable.ic_settings_gesture, onClick = onClickGesture) { Chevron() }
                        MuninnRowItem(stringResource(R.string.dictionary_settings_category), stringResource(R.string.muninn_dictionaries_description),
                            icon = R.drawable.ic_dictionary, onClick = onClickDictionaries) { Chevron() }
                        MuninnRowItem(stringResource(R.string.settings_screen_secondary_layouts), stringResource(R.string.muninn_secondary_layouts_description),
                            icon = R.drawable.ic_settings_layout, onClick = onClickLayouts) { Chevron() }
                        MuninnRowItem(stringResource(R.string.settings_screen_toolbar), stringResource(R.string.muninn_toolbar_description),
                            icon = R.drawable.ic_settings_toolbar, divider = false, onClick = onClickToolbar) { Chevron() }
                    }
                }
            }
        }
    }
}

private fun reload() = KeyboardSwitcher.getInstance().setThemeNeedsReload()

// ---------------------------------------------------------------- Memory (Muninn)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuninnMemoryScreen(onClickBack: () -> Unit, onClickClipboardSettings: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val haptic = LocalHapticFeedback.current
    fun saved() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        MuninnReminders.scheduleNext(ctx)
    }
    var keepDays by remember { mutableIntStateOf(prefs.getInt(Settings.PREF_MUNINN_KEEP_DAYS, Defaults.PREF_MUNINN_KEEP_DAYS)) }
    var on by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_MUNINN_REMINDERS, Defaults.PREF_MUNINN_REMINDERS)) }
    var perDay by remember { mutableIntStateOf(prefs.getInt(Settings.PREF_MUNINN_REMIND_PER_DAY, Defaults.PREF_MUNINN_REMIND_PER_DAY)) }
    var quiet by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_MUNINN_QUIET_HOURS, Defaults.PREF_MUNINN_QUIET_HOURS)) }
    var quietStart by remember { mutableIntStateOf(prefs.getInt(Settings.PREF_MUNINN_QUIET_START, Defaults.PREF_MUNINN_QUIET_START)) }
    var quietEnd by remember { mutableIntStateOf(prefs.getInt(Settings.PREF_MUNINN_QUIET_END, Defaults.PREF_MUNINN_QUIET_END)) }
    var askQuiet by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val tags = remember {
        ClipboardDao.getInstance(ctx)?.getAll().orEmpty().flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    }

    MuninnScreen(
        title = stringResource(R.string.muninn_space_memory),
        onClickBack = onClickBack,
        intro = stringResource(R.string.muninn_memory_intro),
        gold = true,
        runes = "ᛗᚢᚾᛁᚾᚾ",
        runesColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MuninnLabel(stringResource(R.string.muninn_memory_keeping))
            MuninnCard {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.muninn_keep_for), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.muninn_keep_for_sub), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val options = listOf(0, 365, 90)
                    MuninnSegmented(
                        listOf(stringResource(R.string.muninn_keep_forever), stringResource(R.string.muninn_keep_year), stringResource(R.string.muninn_keep_3_months)),
                        options.indexOf(keepDays).coerceAtLeast(0)
                    ) {
                        keepDays = options[it]
                        prefs.edit { putInt(Settings.PREF_MUNINN_KEEP_DAYS, keepDays) }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                MuninnDivider()
                MuninnPrefSwitch(Settings.PREF_MUNINN_SKIP_SENSITIVE, Defaults.PREF_MUNINN_SKIP_SENSITIVE,
                    stringResource(R.string.muninn_skip_passwords), stringResource(R.string.muninn_skip_passwords_sub))
                MuninnPrefSwitch(Settings.PREF_MUNINN_EXPIRE_CODES, Defaults.PREF_MUNINN_EXPIRE_CODES,
                    stringResource(R.string.muninn_codes_expire), stringResource(R.string.muninn_codes_expire_sub))
                MuninnPrefSwitch(Settings.PREF_MUNINN_KEEP_DELETED, Defaults.PREF_MUNINN_KEEP_DELETED,
                    stringResource(R.string.muninn_keep_deleted_30), stringResource(R.string.muninn_keep_deleted_30_sub))
                ScreenshotsSwitch()
                MuninnPrefSwitch(Settings.PREF_MUNINN_LOCK_CACHE, Defaults.PREF_MUNINN_LOCK_CACHE,
                    stringResource(R.string.muninn_lock_cache), stringResource(R.string.muninn_lock_cache_short), divider = false)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MuninnLabel(stringResource(R.string.muninn_memory_bringing_back), gold = true)
            MuninnCard(gold = true) {
                MuninnRowItem(stringResource(R.string.muninn_reminders), stringResource(R.string.muninn_reminders_on_sub),
                    onClick = { on = !on; prefs.edit { putBoolean(Settings.PREF_MUNINN_REMINDERS, on) }; saved() }) {
                    MuninnSwitch(on) { on = it; prefs.edit { putBoolean(Settings.PREF_MUNINN_REMINDERS, it) }; saved() }
                }
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.muninn_three_ways), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WayTile(R.drawable.ic_muninn_clipboard, R.string.muninn_way_gold, R.string.muninn_way_gold_when, Modifier.weight(1f))
                        WayTile(null, R.string.muninn_way_chip, R.string.muninn_way_chip_when, Modifier.weight(1f))
                        WayTile(R.drawable.ic_muninn_bell, R.string.muninn_way_notification, R.string.muninn_way_notification_when, Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.muninn_three_ways_explained), fontSize = 12.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MuninnDivider()
                MuninnRowItem(stringResource(R.string.muninn_quiet_hours), stringResource(R.string.muninn_quiet_hours_sub), onClick = { askQuiet = true }) {
                    Box(Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { askQuiet = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(if (quiet) "%02d:00 – %02d:00".format(quietStart, quietEnd) else stringResource(R.string.muninn_off),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                MuninnPrefSwitch(Settings.PREF_MUNINN_OFFER_LET_GO, Defaults.PREF_MUNINN_OFFER_LET_GO,
                    stringResource(R.string.muninn_ask_still_need), stringResource(R.string.muninn_ask_still_need_sub), divider = false) { saved() }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MuninnLabel(stringResource(R.string.muninn_tags))
            MuninnCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { (tag, count) -> TagPill("${MuninnClips.tagLabel(ctx, tag)}  $count", large = true) }
                    }
                    Text(stringResource(R.string.muninn_tags_how), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        MuninnCard {
            MoreOptionsRow(stringResource(R.string.muninn_memory_more), more) { more = !more }
            AnimatedVisibility(more) {
                MuninnRowItem(stringResource(R.string.muninn_clipboard_settings), stringResource(R.string.muninn_clipboard_settings_description),
                    icon = R.drawable.ic_muninn_clipboard, divider = false, onClick = onClickClipboardSettings) { Chevron() }
            }
        }

        OutlineButton(stringResource(R.string.muninn_remind_try_button), Modifier.fillMaxWidth(), height = 50.dp) {
            val latest = ClipboardDao.getInstance(ctx)?.getAll()?.filter { !it.isCode }?.maxByOrNull { it.timeStamp }
            if (latest == null) Toast.makeText(ctx, R.string.muninn_cache_empty, Toast.LENGTH_SHORT).show()
            else {
                MuninnReminders.set(ctx, latest.id, System.currentTimeMillis() - 1000)
                MuninnReminders.onAlarm(ctx) // comes back right away: notification now, chip when the keyboard opens
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(ctx, R.string.muninn_remind_try_done, Toast.LENGTH_LONG).show()
            }
        }
    }

    if (askQuiet) AlertDialog(
        onDismissRequest = { askQuiet = false },
        confirmButton = { TextButton(onClick = { askQuiet = false }) { Text(stringResource(android.R.string.ok)) } },
        title = { Text(stringResource(R.string.muninn_quiet_hours), fontFamily = MuninnSerif, fontSize = 30.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.muninn_quiet_hours_sub), Modifier.weight(1f), fontSize = 14.sp)
                    MuninnSwitch(quiet) { quiet = it; prefs.edit { putBoolean(Settings.PREF_MUNINN_QUIET_HOURS, it) }; saved() }
                }
                if (quiet) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.muninn_quiet_from), Modifier.weight(1f), fontSize = 15.sp)
                        MuninnStepper("%02d:00".format(quietStart),
                            onMinus = { quietStart = (quietStart + 23) % 24; prefs.edit { putInt(Settings.PREF_MUNINN_QUIET_START, quietStart) }; saved() },
                            onPlus = { quietStart = (quietStart + 1) % 24; prefs.edit { putInt(Settings.PREF_MUNINN_QUIET_START, quietStart) }; saved() })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.muninn_quiet_until), Modifier.weight(1f), fontSize = 15.sp)
                        MuninnStepper("%02d:00".format(quietEnd),
                            onMinus = { quietEnd = (quietEnd + 23) % 24; prefs.edit { putInt(Settings.PREF_MUNINN_QUIET_END, quietEnd) }; saved() },
                            onPlus = { quietEnd = (quietEnd + 1) % 24; prefs.edit { putInt(Settings.PREF_MUNINN_QUIET_END, quietEnd) }; saved() })
                    }
                }
            }
        }
    )
}

/** "Keep recent screenshots": asks for the pictures permission the first time it is turned on */
@Composable
private fun ScreenshotsSwitch() {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var on by remember { mutableStateOf(MuninnScreenshots.enabled(ctx) && MuninnScreenshots.hasPermission(ctx)) }
    fun set(v: Boolean) {
        on = v
        ctx.prefs().edit { putBoolean(Settings.PREF_MUNINN_SCREENSHOTS, v) }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (v) Thread { MuninnScreenshots.collect(ctx) }.start()
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) set(true) else Toast.makeText(ctx, R.string.muninn_screenshots_denied, Toast.LENGTH_LONG).show()
    }
    fun toggle(v: Boolean) {
        if (v && !MuninnScreenshots.hasPermission(ctx)) permission.launch(MuninnScreenshots.permission()) else set(v)
    }
    MuninnRowItem(stringResource(R.string.muninn_screenshots), stringResource(R.string.muninn_screenshots_sub), onClick = { toggle(!on) }) {
        MuninnSwitch(on) { toggle(it) }
    }
}

@Composable
private fun WayTile(icon: Int?, name: Int, whenText: Int, modifier: Modifier) {
    val gold = MaterialTheme.colorScheme.primary
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp)).background(gold.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(painterResource(icon), null, tint = gold, modifier = Modifier.size(18.dp))
            else Box(Modifier.size(34.dp, 14.dp).clip(RoundedCornerShape(50)).border(1.5.dp, gold, RoundedCornerShape(50)))
        }
        Text(stringResource(name), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(whenText), fontSize = 11.5.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Look

private class ThemeChoice(val id: String, val name: Int, val bg: Color, val key: Color, val accent: Color)

private val themeChoices = listOf(
    ThemeChoice(KeyboardTheme.THEME_RUNE_NIGHT, R.string.muninn_theme_night_raven, Color(0xFF0B0E16), Color(0xFF1C2334), Color(0xFFE8B04B)),
    ThemeChoice(KeyboardTheme.THEME_LIGHT, R.string.muninn_theme_daylight, Color(0xFFF2E8DA), Color(0xFFFFFFFF), Color(0xFF6E9BFF)),
    ThemeChoice(KeyboardTheme.THEME_DARK, R.string.muninn_theme_ash, Color(0xFF263238), Color(0xFF3B474D), Color(0xFF80CBC4)),
)

/** Look: themes and keys. The live keyboard sits at the bottom, where the real keyboard appears. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MuninnLookScreen(onClickBack: () -> Unit, onClickAppearance: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val haptic = LocalHapticFeedback.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val heightKey = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, findIndexOfDefaultSetting(landscape, false), 2)
    var version by remember { mutableIntStateOf(0) } // redraws the preview after every change
    var follow by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT)) }
    var height by remember { mutableFloatStateOf(prefs.getFloat(heightKey, 1f)) }
    var more by remember { mutableStateOf(false) }
    fun changed() {
        version++
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        reload()
    }
    val night = ResourceUtils.isNight(ctx.resources)
    val current = remember(version) {
        if (follow && night) prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT)
        else prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS)
    }

    DisposableEffect(Unit) {
        MuninnChrome.hidePreviewButton = true
        onDispose { MuninnChrome.hidePreviewButton = false }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BackButton(onClickBack)
                        Text(stringResource(R.string.muninn_space_look), fontFamily = MuninnSerif, fontSize = 38.sp, lineHeight = 40.sp)
                    }
                    Text(stringResource(R.string.muninn_look_intro), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuninnLabel(stringResource(R.string.muninn_look_theme))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        themeChoices.forEach { t ->
                            val chosen = t.id == current
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(2.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
                                    .clickable {
                                        prefs.edit {
                                            putString(Settings.PREF_THEME_COLORS, t.id)
                                            putString(Settings.PREF_THEME_COLORS_NIGHT, t.id)
                                            putBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
                                        }
                                        follow = false
                                        changed()
                                    }
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)).background(t.bg).padding(8.dp),
                                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    repeat(3) { Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(t.key)) }
                                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(t.accent))
                                }
                                Text(stringResource(t.name), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 2.dp))
                            }
                        }
                    }
                    MuninnCard {
                        MuninnRowItem(stringResource(R.string.muninn_follow_phone), stringResource(R.string.muninn_follow_phone_sub), divider = false,
                            onClick = { follow = !follow; setFollow(ctx, follow); changed() }) {
                            MuninnSwitch(follow) { follow = it; setFollow(ctx, it); changed() }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuninnLabel(stringResource(R.string.muninn_look_keys))
                    MuninnCard {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.muninn_key_height), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text("${(height * 100).toInt()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(
                                value = height,
                                onValueChange = { height = (it * 20).toInt() / 20f; version++ },
                                onValueChangeFinished = { prefs.edit { putFloat(heightKey, height) }; changed() },
                                valueRange = 0.5f..1.5f,
                                thumb = { Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary)) },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            )
                        }
                        MuninnDivider()
                        MuninnPrefSwitch(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS,
                            stringResource(R.string.muninn_key_borders), stringResource(R.string.muninn_key_borders_sub)) { changed() }
                        MuninnPrefSwitch(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW,
                            stringResource(R.string.muninn_popular_number_row), stringResource(R.string.muninn_number_row_sub)) { changed() }
                        MoreOptionsRow(stringResource(R.string.muninn_look_more), more) { more = !more }
                        AnimatedVisibility(more) {
                            MuninnRowItem(stringResource(R.string.settings_screen_appearance), stringResource(R.string.muninn_appearance_description),
                                icon = R.drawable.ic_settings_appearance, divider = false, onClick = onClickAppearance) { Chevron() }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuninnLabel(stringResource(R.string.muninn_look_touch_sound))
                    MuninnCard {
                        if (AudioAndHapticFeedbackManager.getInstance().hasVibrator())
                            MuninnPrefSwitch(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON,
                                stringResource(R.string.muninn_quick_vibration), stringResource(R.string.muninn_vibration_sub)) { reload() }
                        MuninnPrefSwitch(Settings.PREF_SOUND_ON, Defaults.PREF_SOUND_ON,
                            stringResource(R.string.muninn_quick_sounds), stringResource(R.string.muninn_sounds_sub), divider = false) { reload() }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuninnLabel(stringResource(R.string.muninn_look_app))
                    MuninnCard {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.muninn_app_theme), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(stringResource(R.string.muninn_app_theme_sub), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            var appTheme by remember { mutableIntStateOf(prefs.getInt(MuninnAppTheme.PREF, 0)) }
                            MuninnSegmented(
                                listOf(stringResource(R.string.muninn_app_theme_phone), stringResource(R.string.muninn_app_theme_light), stringResource(R.string.muninn_app_theme_dark)),
                                appTheme
                            ) {
                                appTheme = it
                                prefs.edit { putInt(MuninnAppTheme.PREF, it) }
                                MuninnAppTheme.mode = it
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                }
            }
            LivePreview(version, height)
        }
    }
}

private fun setFollow(ctx: android.content.Context, on: Boolean) {
    ctx.prefs().edit {
        putBoolean(Settings.PREF_THEME_DAY_NIGHT, on)
        if (on) {
            putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT)
            putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_RUNE_NIGHT)
        }
    }
}

/** a small keyboard drawn with the current theme's own colours; changes show the moment they are made */
@Composable
private fun LivePreview(version: Int, height: Float) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val colors = remember(version) { KeyboardTheme.getColorsForCurrentTheme(ctx) }
    val borders = remember(version) { prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS) }
    val numberRow = remember(version) { prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW) }
    val bg = Color(colors.get(ColorType.MAIN_BACKGROUND))
    val key = if (borders) Color(colors.get(ColorType.KEY_BACKGROUND)) else Color.Transparent
    val functional = if (borders) Color(colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)) else Color.Transparent
    val space = Color(colors.get(ColorType.SPACE_BAR_BACKGROUND))
    val text = Color(colors.get(ColorType.KEY_TEXT))
    val action = Color(colors.get(ColorType.ACTION_KEY_BACKGROUND))
    val keyH = (34 * height).dp
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp)).background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)).padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.padding(horizontal = 6.dp)) {
            Text(stringResource(R.string.muninn_live_preview).uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
                color = text.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        }
        @Composable
        fun KeyRow(keys: String, pad: Int = 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = pad.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                keys.forEach { c ->
                    Box(Modifier.weight(1f).height(keyH).clip(RoundedCornerShape(7.dp)).background(key), contentAlignment = Alignment.Center) {
                        Text(c.toString(), color = text, fontSize = (15 * height.coerceIn(0.8f, 1.2f)).sp)
                    }
                }
            }
        }
        if (numberRow) KeyRow("1234567890")
        KeyRow("qwertyuiop")
        KeyRow("asdfghjkl", 15)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1.45f).height(keyH).clip(RoundedCornerShape(7.dp)).background(functional), contentAlignment = Alignment.Center) {
                Text("?123", color = text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.weight(5.4f).height(keyH).clip(RoundedCornerShape(7.dp)).background(space), contentAlignment = Alignment.Center) {
                Text("ᛗᚢᚾᛁᚾᚾ", fontFamily = MuninnRunic, color = text.copy(alpha = 0.5f), fontSize = 10.sp, letterSpacing = 4.sp)
            }
            Box(Modifier.weight(1.45f).height(keyH).clip(RoundedCornerShape(7.dp)).background(action))
        }
        Spacer(Modifier.height(0.dp))
    }
}
