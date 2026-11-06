// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.MuninnReminders
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.MuninnRunic
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.previewDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Muninn: first run, as on the canvas. Welcome → three steps (turn on, choose, let reminders reach you) → the keyboard at a glance.
 * Steps: 0 welcome, 1 turn on, 2 choose, 3 notifications, 4 tour.
 */
@Composable
fun WelcomeWizard(
    close: () -> Unit,
    finish: () -> Unit
) {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    fun determineStep(): Int = when {
        !UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm) -> 1
        !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm) -> 2
        Build.VERSION.SDK_INT >= 33 && !MuninnReminders.canNotify(ctx) -> 3
        else -> 4
    }
    var step by rememberSaveable { mutableIntStateOf(if (!UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm)) 0 else determineStep()) }
    val scope = rememberCoroutineScope { Dispatchers.IO }
    LaunchedEffect(step) {
        if (step == 2) scope.launch {
            while (step == 2 && !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) delay(100)
            step = determineStep()
        }
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { step = determineStep() }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = 4 }

    Surface(Modifier.fillMaxSize()) {
        when (step) {
            0 -> WelcomePage { step = 1 }
            4 -> TourPage(onDone = close)
            else -> SetupPage(
                step = step,
                onTurnOn = {
                    settingsLauncher.launch(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addCategory(Intent.CATEGORY_DEFAULT))
                },
                onChoose = { imm.showInputMethodPicker() },
                onAllow = {
                    if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) else step = 4
                },
                onSkipNotifications = { step = 4 },
            )
        }
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    val spin by rememberInfiniteTransition(label = "ring").animateFloat(0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "spin")
    val sway by rememberInfiniteTransition(label = "feather").animateFloat(14f, 22f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "sway")
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                // a ring of runes turning slowly around the feather
                Box(Modifier.size(200.dp).rotate(spin).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape))
                Box(Modifier.size(158.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape))
                listOf(0f, 60f, 120f, 180f, 240f, 300f).forEachIndexed { i, a ->
                    Text("ᛗᚢᚾᛁᚾᚾ"[i % 6].toString(), fontFamily = MuninnRunic, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.rotate(spin + a).offset(y = (-89).dp))
                }
                Image(painterResource(R.drawable.ic_muninn_feather), null, Modifier.size(width = 52.dp, height = 130.dp).rotate(sway))
            }
            Text("muninn", fontFamily = MuninnSerif, fontSize = 64.sp, lineHeight = 64.sp)
            Text(stringResource(R.string.muninn_welcome_tagline_short), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
        Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Promise("ᚲ", true, R.string.muninn_promise_kept, R.string.muninn_promise_kept_text)
            Promise("ᚱ", true, R.string.muninn_promise_back, R.string.muninn_promise_back_text)
            Promise("ᛉ", false, R.string.muninn_promise_phone, R.string.muninn_promise_phone_text)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldButton(stringResource(R.string.muninn_set_up), height = 56.dp, onClick = onStart)
            Text(stringResource(R.string.muninn_set_up_time), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Promise(rune: String, gold: Boolean, title: Int, text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val ink = if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(ink.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Text(rune, fontFamily = MuninnRunic, fontSize = 17.sp, color = ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(text), fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupPage(step: Int, onTurnOn: () -> Unit, onChoose: () -> Unit, onAllow: () -> Unit, onSkipNotifications: () -> Unit) {
    val ctx = LocalContext.current
    var tryText by remember { mutableStateOf("") }
    val isXiaomi = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..3).forEach { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
                }
            }
            Text(stringResource(R.string.muninn_three_steps), fontFamily = MuninnSerif, fontSize = 40.sp, lineHeight = 42.sp)
            Text(stringResource(R.string.muninn_three_steps_text), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupStep(1, step, R.string.muninn_step_turn_on, R.string.muninn_step_turn_on_text, R.string.muninn_step_turn_on_done,
                R.string.muninn_step_turn_on_action, onTurnOn)
            SetupStep(2, step, R.string.muninn_step_choose, R.string.muninn_step_choose_text, R.string.muninn_step_choose_done,
                R.string.muninn_step_choose_action, onChoose)
            SetupStep(3, step, R.string.muninn_step_notify, R.string.muninn_step_notify_text, R.string.muninn_step_notify_done,
                R.string.muninn_step_notify_action, onAllow, secondary = R.string.muninn_not_now, onSecondary = onSkipNotifications)
        }
        if (isXiaomi) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(painterResource(R.drawable.ic_muninn_shield), null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.muninn_on_your_redmi), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.muninn_on_your_redmi_text), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.muninn_open_autostart), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { openAutostart(ctx) }.padding(vertical = 6.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MuninnLabel(stringResource(R.string.muninn_try_it_here))
            OutlinedTextField(
                value = tryText, onValueChange = { tryText = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.muninn_try_it_hint)) },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            )
        }
    }
}

private fun openAutostart(ctx: Context) {
    val miui = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(miui) } catch (_: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** a step card: done (green tick), the current one (gold edge and button) or still to come (quiet) */
@Composable
private fun SetupStep(number: Int, current: Int, title: Int, text: Int, doneText: Int, action: Int, onAction: () -> Unit,
                      secondary: Int? = null, onSecondary: () -> Unit = {}) {
    val done = number < current
    val active = number == current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
            .then(if (active) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = if (active) 16.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = if (active) Alignment.Top else Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(
                    when { done -> Color(0xFF6FCF97).copy(alpha = 0.16f); active -> MaterialTheme.colorScheme.primary; else -> Color.Transparent })
                    .then(if (!done && !active) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (done) Icon(painterResource(R.drawable.ic_muninn_check), null, tint = Color(0xFF6FCF97), modifier = Modifier.size(18.dp))
                else Text(number.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(title), fontSize = if (active) 16.sp else 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (!done && !active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurface)
                Text(stringResource(if (done) doneText else text), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (active) {
            GoldButton(stringResource(action), height = 50.dp, onClick = onAction)
            if (secondary != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(secondary), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onSecondary).padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

/** the keyboard drawn once, with six gold marks, and what each one does */
@Composable
private fun TourPage(onDone: () -> Unit) {
    val marks = listOf(
        Triple("ᚠ", R.string.muninn_tour_chip, R.string.muninn_tour_chip_text),
        Triple("ᚢ", R.string.muninn_tour_clipboard, R.string.muninn_tour_clipboard_text),
        Triple("ᚦ", R.string.muninn_tour_feather, R.string.muninn_tour_feather_text),
        Triple("ᚨ", R.string.muninn_how_swipe_space, R.string.muninn_tour_space_text),
        Triple("ᚱ", R.string.muninn_tour_hold_letter, R.string.muninn_tour_hold_letter_text),
        Triple("ᚲ", R.string.muninn_tour_delete, R.string.muninn_tour_delete_text),
    )
    // where each mark sits on the drawing, as a fraction of its width and height
    val spots = listOf(0.62f to 0.0f, 0.93f to 0.0f, 0.08f to 0.0f, 0.55f to 0.80f, 0.24f to 0.20f, 0.92f to 0.58f)
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.muninn_tour_title), fontFamily = MuninnSerif, fontSize = 40.sp, lineHeight = 42.sp)
            Text(stringResource(R.string.muninn_tour_text), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            val h = 244.dp
            Column(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_muninn_feather_small), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Text(stringResource(R.string.muninn_tour_chip_example), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_muninn_clipboard), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { r, keys ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = if (r == 1) 16.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (r == 2) TourKey("⇧", 1.45f)
                        keys.forEach { TourKey(it.toString()) }
                        if (r == 2) TourKey("⌫", 1.45f)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TourKey("?123", 1.45f); TourKey("⊕"); TourKey(","); TourKey("‹ English ›", 4.4f, gold = true); TourKey("."); TourKey("↵", 1.45f)
                }
            }
            spots.forEachIndexed { i, (x, y) ->
                Box(
                    Modifier.offset(x = w * x - 10.dp, y = h * y - 6.dp).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(marks[i].first, fontFamily = MuninnRunic, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            marks.forEach { (rune, action, result) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(top = 1.dp).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text(rune, fontFamily = MuninnRunic, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(stringResource(action), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(result), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton(stringResource(R.string.muninn_how_got_it), onClick = onDone)
            Text(stringResource(R.string.muninn_tour_again), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TourKey(label: String, weight: Float = 1f, gold: Boolean = false) {
    Box(
        Modifier.weight(weight).height(40.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (gold) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(7.dp)) else Modifier),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = if (label.length > 1) 11.sp else 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (label.length > 1) 0.75f else 1f), maxLines = 1) }
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) { Surface { WelcomeWizard({}) { } } }
}

