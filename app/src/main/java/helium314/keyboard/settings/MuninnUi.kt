// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.BackButton
import helium314.keyboard.latin.utils.MuninnRunic
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.prefs

/*
 * Muninn: the building blocks of every screen, as drawn on the "App, in depth" canvas.
 * Gold is only for memory and the one main action of a screen. Rows have one title line and at most one line under it.
 */

/** small state shared with the settings frame, e.g. the Look screen hides the floating keyboard button */
object MuninnChrome {
    var hidePreviewButton by mutableStateOf(false)
}

val MuninnRose = Color(0xFFF2A7A0)

/** A screen: back arrow, a serif title (gold for Memory), optional runes, one sentence, then the content. */
@Composable
fun MuninnScreen(
    title: String,
    onClickBack: () -> Unit,
    intro: String? = null,
    gold: Boolean = false,
    runes: String? = null,
    runesColor: Color = MaterialTheme.colorScheme.secondary,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = if (scroll) 110.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackButton(onClickBack)
                    Text(title, fontFamily = MuninnSerif, fontSize = 38.sp, lineHeight = 40.sp,
                        color = if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (runes != null) Text(runes, fontFamily = MuninnRunic, fontSize = 13.sp, color = runesColor,
                        modifier = Modifier.padding(start = 10.dp, top = 10.dp))
                    Spacer(Modifier.weight(1f))
                    actions()
                }
                if (intro != null) Text(intro, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
            }
            content()
        }
    }
}

/** "KEEPING", "BRINGING BACK": small caps above a card */
@Composable
fun MuninnLabel(text: String, gold: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
        color = if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 8.dp)
    )
}

/** A rounded card holding rows. A gold edge marks the Muninn (memory) parts. */
@Composable
fun MuninnCard(gold: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (gold) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape) else Modifier)
    ) { content() }
}

@Composable
fun MuninnSection(label: String, gold: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MuninnLabel(label, gold)
        MuninnCard(gold) { content() }
    }
}

@Composable
fun MuninnDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
}

/** a row: optional icon tile, title, one line under it, and whatever sits on the right */
@Composable
fun MuninnRowItem(
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    iconGold: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) IconTile(icon, iconGold)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = titleColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
    if (divider) MuninnDivider()
}

@Composable
fun IconTile(@DrawableRes icon: Int, gold: Boolean = false, size: Dp = 38.dp) {
    val tint = if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(11.dp))
            .background(if (gold) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) { Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun Chevron(down: Boolean = false, up: Boolean = false) {
    Icon(painterResource(R.drawable.ic_arrow_left), null, tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(18.dp).rotate(if (up) 90f else if (down) 270f else 180f))
}

/** the gold switch: 46 × 28, gold track when on */
@Composable
fun MuninnSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)? = null) {
    val track by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, label = "track")
    val knob by animateColorAsState(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "knob")
    val x by animateDpAsState(if (checked) 21.dp else 3.dp, label = "knobX")
    Box(
        Modifier.size(46.dp, 28.dp).clip(RoundedCornerShape(50)).background(track)
            .then(if (onChange != null) Modifier.clickable { onChange(!checked) } else Modifier)
    ) {
        Box(Modifier.offset(x = x, y = 3.dp).size(22.dp).clip(CircleShape).background(knob))
    }
}

/** a switch row bound to a preference; feels a tap when changed */
@Composable
fun MuninnPrefSwitch(
    key: String,
    default: Boolean,
    title: String,
    subtitle: String? = null,
    divider: Boolean = true,
    onChanged: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var on by remember(key) { mutableStateOf(ctx.prefs().getBoolean(key, default)) }
    fun set(v: Boolean) {
        on = v
        ctx.prefs().edit { putBoolean(key, v) }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onChanged(v)
    }
    MuninnRowItem(title, subtitle, divider = divider, onClick = { set(!on) }) { MuninnSwitch(on) { set(it) } }
}

/** two to four choices side by side; the chosen one is gold */
@Composable
fun MuninnSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val chosen = i == selected
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** − 3 + */
@Composable
fun MuninnStepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StepButton("−", onMinus)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 18.sp) }
}

/** the one main action of a screen */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, height: Dp = 52.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold) }
}

/** the second choice */
@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, height: Dp = 48.dp, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = color, fontSize = 14.5.sp, fontWeight = FontWeight.Medium) }
}

/** filter chips: white when chosen; [gold] outlines the "Coming back" one */
@Composable
fun FilterPill(label: String, selected: Boolean, gold: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.height(40.dp).clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.onSurface else if (gold) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (gold && !selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.surface else if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
    }
}

/** a tag: small, gold, under a clip. [large] for the tappable ones in sheets and filter rows. */
@Composable
fun TagPill(label: String, selected: Boolean = false, large: Boolean = false, onClick: (() -> Unit)? = null) {
    val gold = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.then(if (large) Modifier.height(38.dp) else Modifier).clip(shape)
            .background(if (selected) gold else gold.copy(alpha = 0.08f))
            .border(1.dp, gold.copy(alpha = if (selected) 1f else 0.45f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (large) 13.dp else 8.dp, vertical = if (large) 0.dp else 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = if (large) 13.5.sp else 11.5.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else gold)
    }
}

/** the answer bar: says what happened and offers the way back */
@Composable
fun AnswerBar(text: String, actionLabel: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.inverseSurface)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (actionLabel != null) Box(
            Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text(actionLabel, color = MaterialTheme.colorScheme.inversePrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold) }
    }
}

/** the folded "More options" row */
@Composable
fun MoreOptionsRow(subtitle: String, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(androidx.compose.ui.res.stringResource(R.string.muninn_more_options), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Chevron(down = !open, up = open)
    }
}
