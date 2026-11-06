// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.MuninnRunic
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

// Muninn: the settings home, laid out like the Rune & Feather design:
// wordmark header, a grid of popular switches, then settings grouped into rounded cards.
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
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = "",
        settings = emptyList(),
    ) {
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).then(Modifier.padding(innerPadding))
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MuninnHeader()
                PopularSettings()

                Group(R.string.muninn_group_typing, R.string.muninn_group_typing_raven) {
                    Preference(
                        name = stringResource(R.string.language_and_layouts_title),
                        description = enabledSubtypes.joinToString(", ") { it.displayName() },
                        onClick = onClickLanguage,
                        icon = R.drawable.ic_settings_languages
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_screen_correction),
                        onClick = onClickTextCorrection,
                        icon = R.drawable.ic_settings_correction
                    ) { NextScreenIcon() }
                    if (JniUtils.sHaveGestureLib)
                        Preference(
                            name = stringResource(R.string.settings_screen_gesture),
                            onClick = onClickGestureTyping,
                            icon = R.drawable.ic_settings_gesture
                        ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.dictionary_settings_category),
                        onClick = onClickDictionaries,
                        icon = R.drawable.ic_dictionary
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_screen_secondary_layouts),
                        description = stringResource(R.string.muninn_secondary_layouts_description),
                        onClick = onClickLayouts,
                        icon = R.drawable.ic_settings_layout
                    ) { NextScreenIcon() }
                }

                Group(R.string.muninn_group_look) {
                    Preference(
                        name = stringResource(R.string.settings_screen_appearance),
                        description = stringResource(R.string.muninn_appearance_description),
                        onClick = onClickAppearance,
                        icon = R.drawable.ic_settings_appearance
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_screen_preferences),
                        description = stringResource(R.string.muninn_preferences_description),
                        onClick = onClickPreferences,
                        icon = R.drawable.ic_settings_preferences
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_screen_toolbar),
                        onClick = onClickToolbar,
                        icon = R.drawable.ic_settings_toolbar
                    ) { NextScreenIcon() }
                }

                Group(R.string.muninn_group_more) {
                    Preference(
                        name = stringResource(R.string.settings_screen_advanced),
                        onClick = onClickAdvanced,
                        icon = R.drawable.ic_settings_advanced
                    ) { NextScreenIcon() }
                    // we don't even show the menu if data gathering phase ended more than 2 weeks ago
                    if (JniUtils.sHaveGestureLib && System.currentTimeMillis() < END_DATE_EPOCH_MILLIS + TWO_WEEKS_IN_MILLIS)
                        Preference(
                            name = stringResource(R.string.gesture_data_screen),
                            onClick = onClickDataGathering,
                            icon = R.drawable.ic_settings_gesture
                        ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.settings_screen_about),
                        onClick = onClickAbout,
                        icon = R.drawable.ic_settings_about
                    ) { NextScreenIcon() }
                }

                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 96.dp), horizontalArrangement = Arrangement.Center) {
                    Text("ᛗᚢᚾᛁᚾᚾ", fontFamily = MuninnRunic, fontSize = 13.sp, letterSpacing = 5.sp,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun MuninnHeader() {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val active = UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(painterResource(R.drawable.ic_muninn_feather), null, Modifier.size(width = 18.dp, height = 44.dp).rotate(18f))
        Text("muninn", fontFamily = MuninnSerif, fontSize = 46.sp, lineHeight = 46.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) Color(0xFF6FCF97) else MaterialTheme.colorScheme.outline))
            Text(
                stringResource(if (active) R.string.muninn_status_active else R.string.muninn_status_not_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Big switches with small pictures, like Yandex's "Popular settings" */
@Composable
private fun PopularSettings() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.muninn_popular_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PopularCard(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW, R.string.muninn_popular_number_row, Modifier.weight(1f)) { NumberRowPicture() }
            PopularCard(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS, R.string.muninn_popular_hints, Modifier.weight(1f)) { HintsPicture() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PopularCard(Settings.PREF_POPUP_ON, Defaults.PREF_POPUP_ON, R.string.muninn_popular_key_preview, Modifier.weight(1f)) { KeyPreviewPicture() }
            PopularCard(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY, R.string.muninn_popular_clipboard, Modifier.weight(1f)) { ClipboardPicture() }
        }
    }
}

@Composable
private fun PopularCard(key: String, default: Boolean, @StringRes title: Int, modifier: Modifier, picture: @Composable () -> Unit) {
    val prefs = LocalContext.current.prefs()
    var on by remember { mutableStateOf(prefs.getBoolean(key, default)) }
    val gold = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(BorderStroke(1.5.dp, if (on) gold else MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(18.dp))
            .clickable {
                on = !on
                prefs.edit { putBoolean(key, on) }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            Box(
                Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) { picture() }
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape)
                    .background(if (on) gold else Color.Transparent)
                    .border(2.dp, if (on) gold else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (on) Icon(painterResource(R.drawable.ic_setup_check), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Text(stringResource(title), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun KeyBlocks(count: Int, height: Int = 14) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) {
            Box(Modifier.weight(1f).height(height.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
private fun NumberRowPicture() {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) { listOf("1", "2", "3", "4", "5").forEach { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }
        KeyBlocks(5)
        KeyBlocks(5)
    }
}

@Composable
private fun HintsPicture() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("a" to "@", "s" to "#").forEachIndexed { i, (letter, hint) ->
            Box(Modifier.size(width = 40.dp, height = 48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                Text(letter, fontSize = 22.sp, modifier = Modifier.align(Alignment.Center))
                Text(hint, fontSize = 11.sp, color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp))
            }
        }
    }
}

@Composable
private fun KeyPreviewPicture() {
    Box(Modifier.fillMaxWidth().height(80.dp).padding(8.dp)) {
        Column(Modifier.align(Alignment.BottomCenter)) { KeyBlocks(5, 16) }
        Box(
            Modifier.align(Alignment.TopCenter).size(width = 36.dp, height = 42.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) { Text("b", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ClipboardPicture() {
    Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(true, false, false).forEach { gold ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .then(if (gold) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                Box(Modifier.width(if (gold) 70.dp else 50.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outline))
            }
        }
    }
}

/** A rounded card holding a group of settings, with a small heading above it */
@Composable
private fun Group(@StringRes title: Int, @StringRes raven: Int? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(title).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (raven != null)
                Text(stringResource(raven), fontFamily = MuninnSerif, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 4.dp),
            content = content
        )
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
