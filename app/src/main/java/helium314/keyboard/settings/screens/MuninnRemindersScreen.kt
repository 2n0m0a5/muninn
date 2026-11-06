// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import helium314.keyboard.latin.MuninnReminders
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.BackButton
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.prefs

/** Muninn, Layer 3: how and when reminders come back */
@Composable
fun MuninnRemindersScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val haptic = LocalHapticFeedback.current
    var on by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_MUNINN_REMINDERS, Defaults.PREF_MUNINN_REMINDERS)) }
    var strength by remember { mutableIntStateOf(prefs.getInt(Settings.PREF_MUNINN_REMIND_STRENGTH, Defaults.PREF_MUNINN_REMIND_STRENGTH)) }
    var perDay by remember { mutableIntStateOf(prefs.getInt(Settings.PREF_MUNINN_REMIND_PER_DAY, Defaults.PREF_MUNINN_REMIND_PER_DAY)) }
    var quiet by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_MUNINN_QUIET_HOURS, Defaults.PREF_MUNINN_QUIET_HOURS)) }
    var quietStart by remember { mutableIntStateOf(prefs.getInt(Settings.PREF_MUNINN_QUIET_START, Defaults.PREF_MUNINN_QUIET_START)) }
    var quietEnd by remember { mutableIntStateOf(prefs.getInt(Settings.PREF_MUNINN_QUIET_END, Defaults.PREF_MUNINN_QUIET_END)) }
    var letGo by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_MUNINN_OFFER_LET_GO, Defaults.PREF_MUNINN_OFFER_LET_GO)) }
    fun saved() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        MuninnReminders.scheduleLoud(ctx)
    }

    Surface {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClickBack)
                Text(stringResource(R.string.muninn_reminders), fontFamily = MuninnSerif, fontSize = 38.sp, color = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card {
                    SwitchRow(R.string.muninn_reminders, R.string.muninn_reminders_on_description, on) {
                        on = it; prefs.edit { putBoolean(Settings.PREF_MUNINN_REMINDERS, it) }; saved()
                    }
                }
                Column(Modifier.alpha(if (on) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Heading(R.string.muninn_remind_default_strength)
                    Card {
                        listOf(
                            Triple(MuninnReminders.CALM, R.string.muninn_strength_calm, R.string.muninn_strength_calm_description),
                            Triple(MuninnReminders.NOTICEABLE, R.string.muninn_strength_noticeable, R.string.muninn_strength_noticeable_description),
                            Triple(MuninnReminders.LOUD, R.string.muninn_strength_loud, R.string.muninn_strength_loud_description),
                        ).forEach { (value, name, description) ->
                            val selected = strength == value
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = on) {
                                    strength = value; prefs.edit { putInt(Settings.PREF_MUNINN_REMIND_STRENGTH, value) }; saved()
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(name), style = MaterialTheme.typography.bodyLarge,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    Text(stringResource(description), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(12.dp))
                                Spacer(Modifier.size(20.dp).clip(RoundedCornerShape(50))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                    .border(BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), RoundedCornerShape(50)))
                            }
                        }
                    }

                    Heading(R.string.muninn_remind_limits)
                    Card {
                        StepperRow(R.string.muninn_remind_per_day, R.string.muninn_remind_per_day_description, perDay.toString(),
                            enabled = on,
                            onMinus = { if (perDay > 1) { perDay--; prefs.edit { putInt(Settings.PREF_MUNINN_REMIND_PER_DAY, perDay) }; saved() } },
                            onPlus = { if (perDay < 10) { perDay++; prefs.edit { putInt(Settings.PREF_MUNINN_REMIND_PER_DAY, perDay) }; saved() } })
                        SwitchRow(R.string.muninn_quiet_hours, R.string.muninn_quiet_hours_description, quiet, enabled = on) {
                            quiet = it; prefs.edit { putBoolean(Settings.PREF_MUNINN_QUIET_HOURS, it) }; saved()
                        }
                        if (quiet) {
                            StepperRow(R.string.muninn_quiet_from, null, "%02d:00".format(quietStart), enabled = on,
                                onMinus = { quietStart = (quietStart + 23) % 24; prefs.edit { putInt(Settings.PREF_MUNINN_QUIET_START, quietStart) }; saved() },
                                onPlus = { quietStart = (quietStart + 1) % 24; prefs.edit { putInt(Settings.PREF_MUNINN_QUIET_START, quietStart) }; saved() })
                            StepperRow(R.string.muninn_quiet_until, null, "%02d:00".format(quietEnd), enabled = on,
                                onMinus = { quietEnd = (quietEnd + 23) % 24; prefs.edit { putInt(Settings.PREF_MUNINN_QUIET_END, quietEnd) }; saved() },
                                onPlus = { quietEnd = (quietEnd + 1) % 24; prefs.edit { putInt(Settings.PREF_MUNINN_QUIET_END, quietEnd) }; saved() })
                        }
                        SwitchRow(R.string.muninn_offer_let_go, R.string.muninn_offer_let_go_description, letGo, enabled = on) {
                            letGo = it; prefs.edit { putBoolean(Settings.PREF_MUNINN_OFFER_LET_GO, it) }; saved()
                        }
                    }

                    Heading(R.string.muninn_remind_try)
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.muninn_remind_try_description), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                enabled = on,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                onClick = {
                                    val latest = ClipboardDao.getInstance(ctx)?.getAll()?.filter { !it.isCode }?.maxByOrNull { it.timeStamp }
                                    if (latest == null) {
                                        Toast.makeText(ctx, R.string.muninn_cache_empty, Toast.LENGTH_SHORT).show()
                                    } else {
                                        MuninnReminders.set(ctx, latest.id, System.currentTimeMillis() - 1000, strength)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        Toast.makeText(ctx, R.string.muninn_remind_try_done, Toast.LENGTH_LONG).show()
                                    }
                                }
                            ) { Text(stringResource(R.string.muninn_remind_try_button), fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun Heading(@StringRes text: Int) {
    Text(stringResource(text).uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(vertical = 4.dp), content = content)
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes description: Int?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (description != null)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun StepperRow(@StringRes title: Int, @StringRes description: Int?, value: String, enabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (description != null)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            verticalAlignment = Alignment.CenterVertically) {
            Text("−", fontSize = 22.sp, modifier = Modifier.clickable(enabled = enabled) { onMinus() }.padding(horizontal = 16.dp, vertical = 8.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 4.dp))
            Text("+", fontSize = 22.sp, modifier = Modifier.clickable(enabled = enabled) { onPlus() }.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}
