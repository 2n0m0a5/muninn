// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import android.text.format.DateUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.MuninnGuard
import helium314.keyboard.latin.MuninnReminders
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.GoldButton
import helium314.keyboard.settings.MuninnCard
import helium314.keyboard.settings.MuninnDivider
import helium314.keyboard.settings.MuninnLabel
import helium314.keyboard.settings.MuninnPrefSwitch
import helium314.keyboard.settings.MuninnRowItem
import helium314.keyboard.settings.MuninnScreen
import helium314.keyboard.settings.OutlineButton
import kotlinx.coroutines.delay

private val Green = Color(0xFF6FCF97)

fun isXiaomiFamily() = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

/** HyperOS / MIUI Autostart list; falls back to the app's own settings page */
fun openAutostartSettings(ctx: Context) {
    val miui = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(miui) } catch (_: Exception) {
        ctx.startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Muninn, Layer 4: is Muninn the keyboard, what to fix so the guard keeps working, and what happened */
@Composable
fun MuninnGuardScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    var tick by remember { mutableIntStateOf(0) } // read the phone's state again every second (settings change outside the app)
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val current = remember(tick) { MuninnGuard.isMuninnCurrent(ctx) }
    val enabledInList = remember(tick) { MuninnGuard.isMuninnEnabled(ctx) }
    val other = remember(tick) { MuninnGuard.keyboardLabel(ctx, MuninnGuard.currentId(ctx)) }
    val canSelf = remember(tick) { MuninnGuard.canSwitchBySelf(ctx) }
    val battery = remember(tick) { MuninnGuard.isIgnoringBatteryOptimizations(ctx) }
    val notify = remember(tick) { MuninnReminders.canNotify(ctx) }
    val log = remember(tick) { MuninnGuard.log(ctx) }
    val stopped = remember(tick) { MuninnGuard.stoppedThisWeek(ctx) }
    var guardOn by remember { mutableStateOf(ctx.prefs().getBoolean(Settings.PREF_MUNINN_GUARD, Defaults.PREF_MUNINN_GUARD)) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    fun switchBack() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when {
            !enabledInList -> ctx.startActivity(Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            canSelf && MuninnGuard.switchBack(ctx) -> Toast.makeText(ctx, R.string.muninn_guard_back, Toast.LENGTH_SHORT).show()
            else -> imm.showInputMethodPicker()
        }
    }

    MuninnScreen(title = stringResource(R.string.muninn_guard), onClickBack = onClickBack, intro = stringResource(R.string.muninn_guard_intro)) {
        // status: one look says whether everything is fine
        val ok = current
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .then(if (!ok) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(22.dp)) else Modifier)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val tint = if (ok) Green else MaterialTheme.colorScheme.primary
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_muninn_shield), null, tint = tint, modifier = Modifier.size(36.dp))
            }
            Text(when {
                    ok -> stringResource(R.string.muninn_guard_ok)
                    !enabledInList -> stringResource(R.string.muninn_guard_switched_off)
                    other == "?" -> stringResource(R.string.muninn_other_keyboard)
                    else -> stringResource(R.string.muninn_guard_not_ok, other)
                },
                fontFamily = MuninnSerif, fontSize = 30.sp, lineHeight = 32.sp, textAlign = TextAlign.Center)
            Text(
                when {
                    ok && stopped > 0 -> ctx.resources.getQuantityString(R.plurals.muninn_guard_stopped_week, stopped, stopped)
                    ok -> stringResource(R.string.muninn_guard_ok_text)
                    !enabledInList -> stringResource(R.string.muninn_guard_off_in_list)
                    else -> stringResource(R.string.muninn_guard_not_ok_text)
                },
                fontSize = 13.5.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!ok) GoldButton(stringResource(if (enabledInList) R.string.muninn_guard_switch_back else R.string.muninn_guard_turn_on),
                Modifier.padding(top = 4.dp)) { switchBack() }
        }

        MuninnCard {
            MuninnPrefSwitch(Settings.PREF_MUNINN_GUARD, Defaults.PREF_MUNINN_GUARD,
                stringResource(R.string.muninn_guard_keep), stringResource(R.string.muninn_guard_keep_sub), divider = false) {
                guardOn = it
                MuninnGuard.schedule(ctx)
            }
        }

        if (guardOn) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MuninnLabel(stringResource(R.string.muninn_guard_by_itself_label))
            MuninnCard {
                if (canSelf) {
                    MuninnPrefSwitch(Settings.PREF_MUNINN_GUARD_AUTO, Defaults.PREF_MUNINN_GUARD_AUTO,
                        stringResource(R.string.muninn_guard_by_itself), stringResource(R.string.muninn_guard_by_itself_sub), divider = false)
                } else Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.muninn_guard_by_itself_how), fontSize = 13.5.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val command = MuninnGuard.grantCommand(ctx)
                    Text(command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlineButton(stringResource(R.string.muninn_copy), height = 42.dp) {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("adb", command))
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, R.string.muninn_cache_copied, Toast.LENGTH_SHORT).show()
                        }
                        Text(stringResource(R.string.muninn_guard_by_itself_without), fontSize = 12.sp, lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (guardOn) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MuninnLabel(stringResource(if (isXiaomiFamily()) R.string.muninn_guard_checks_hyperos else R.string.muninn_guard_checks))
            MuninnCard {
                CheckRow(notify, stringResource(R.string.muninn_guard_check_notifications),
                    stringResource(if (notify) R.string.muninn_guard_check_notifications_ok else R.string.muninn_guard_check_notifications_fix)) {
                    if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                if (isXiaomiFamily()) {
                    // HyperOS keeps its own battery list ("No restrictions") and Autostart list, and no app can read
                    // either. So: open the right page, then the person ticks it off.
                    ManualCheckRow(battery, "muninn_guard_battery_done", stringResource(R.string.muninn_guard_check_battery),
                        stringResource(R.string.muninn_guard_check_battery_hyperos), stringResource(R.string.muninn_guard_check_battery_ok)) {
                        openHyperOsBattery(ctx)
                    }
                    ManualCheckRow(false, "muninn_guard_autostart_done", stringResource(R.string.muninn_guard_check_autostart),
                        stringResource(R.string.muninn_guard_check_autostart_sub), stringResource(R.string.muninn_guard_check_autostart_ok), divider = false) {
                        openAutostartSettings(ctx)
                    }
                } else CheckRow(battery, stringResource(R.string.muninn_guard_check_battery),
                    stringResource(if (battery) R.string.muninn_guard_check_battery_ok else R.string.muninn_guard_check_battery_fix),
                    divider = false) {
                    try {
                        ctx.startActivity(Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        ctx.startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MuninnLabel(stringResource(R.string.muninn_guard_what_happened))
            MuninnCard {
                if (log.isEmpty()) Text(stringResource(R.string.muninn_guard_log_empty), fontSize = 13.5.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
                else Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    log.take(10).forEachIndexed { i, e ->
                        if (i > 0) MuninnDivider()
                        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE).toString(), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(84.dp))
                            Text(stringResource(if (e.switchedBack) R.string.muninn_guard_log_switched else R.string.muninn_guard_log_asked, e.keyboard),
                                fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** HyperOS's battery saver page for Muninn ("No restrictions" lives there); the app's own page if it can't be opened */
fun openHyperOsBattery(ctx: Context) {
    val label = ctx.applicationInfo.loadLabel(ctx.packageManager).toString()
    val miui = Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
        .putExtra("package_name", ctx.packageName).putExtra("package_label", label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(miui) } catch (_: Exception) {
        ctx.startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * A step the phone won't let Muninn check: "Open" takes you there; after that, a "Done" tick you press yourself.
 * [autoOk] marks it done when Android can confirm it after all.
 */
@Composable
private fun ManualCheckRow(autoOk: Boolean, prefKey: String, title: String, todo: String, doneText: String, divider: Boolean = true, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var done by remember { mutableStateOf(ctx.prefs().getBoolean(prefKey, false)) }
    var opened by remember { mutableStateOf(false) }
    val ok = autoOk || done
    MuninnRowItem(title, if (ok) doneText else todo, divider = divider, onClick = { opened = true; onOpen() }) {
        when {
            ok -> Box(Modifier.size(28.dp).clip(CircleShape).background(Green.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_muninn_check), null, tint = Green, modifier = Modifier.size(16.dp))
            }
            opened -> Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary).clickable {
                    done = true
                    ctx.prefs().edit().putBoolean(prefKey, true).apply()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.muninn_guard_did_it), color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            else -> OutlineButton(stringResource(R.string.muninn_open), height = 36.dp) { opened = true; onOpen() }
        }
    }
}

@Composable
private fun CheckRow(ok: Boolean, title: String, subtitle: String, divider: Boolean = true, onFix: () -> Unit) {
    MuninnRowItem(title, subtitle, divider = divider, onClick = if (ok) null else onFix) {
        if (ok) Box(Modifier.size(28.dp).clip(CircleShape).background(Green.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_muninn_check), null, tint = Green, modifier = Modifier.size(16.dp))
        } else Box(
            Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary).clickable(onClick = onFix).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text(stringResource(R.string.muninn_fix), color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

