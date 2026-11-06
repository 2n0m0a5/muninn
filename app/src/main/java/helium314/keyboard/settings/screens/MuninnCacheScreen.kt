// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import helium314.keyboard.latin.utils.MuninnRunic
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.MuninnClips
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.utils.BackButton
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark

/** Muninn: everything the keyboard has kept, with search. Layer 1 of the plan ("Memory"). */
private enum class CacheFilter(@StringRes val label: Int) {
    ALL(R.string.muninn_tab_all),
    PINNED(R.string.muninn_tab_pinned),
    LINKS(R.string.muninn_filter_links),
    CODES(R.string.muninn_filter_codes),
    TRASH(R.string.muninn_tab_trash),
}

@Composable
fun MuninnCacheScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dao = remember { ClipboardDao.getInstance(ctx) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CacheFilter.ALL) }
    var version by remember { mutableIntStateOf(0) } // bumped after every change, so the list is read again
    /** every action is felt (vibration), seen (message) and shown (the list redraws) */
    fun changed(@StringRes message: Int? = null) {
        version++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (message != null) Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    // a snapshot of each clip, so the screen redraws when a clip changes (the database changes clips in place)
    val clips: List<ClipView> = remember(version, query, filter) {
        if (dao == null) return@remember emptyList()
        val base = when (filter) {
            CacheFilter.ALL -> dao.getAll()
            CacheFilter.PINNED -> dao.getAll().filter { it.isPinned }
            CacheFilter.LINKS -> dao.getAll().filter { MuninnClips.isLink(it.text) }
            CacheFilter.CODES -> dao.getAll().filter { it.isCode }
            CacheFilter.TRASH -> dao.getTrash()
        }.toList()
        val q = query.trim()
        val matching = if (q.isEmpty()) base
            else base.filter { clip ->
                !clip.isCode && (clip.text?.contains(q, ignoreCase = true) == true
                        || MuninnClips.appLabel(ctx, clip.source)?.contains(q, ignoreCase = true) == true)
            }
        matching.map { ClipView(it, it.isPinned, MuninnClips.metaLine(ctx, it)) }
    }
    val total = remember(version) { dao?.count() ?: 0 }
    val trashCount = remember(version) { dao?.trashCount() ?: 0 }
    val inTrash = filter == CacheFilter.TRASH

    Surface {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClickBack)
                Text(stringResource(R.string.muninn_cache_title), fontFamily = MuninnSerif, fontSize = 38.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.muninn_cache_kept, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ᛗᚢᚾᛁᚾᚾ", fontFamily = MuninnRunic, fontSize = 12.sp, letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
            }

            // the list takes the middle; search and filters sit at the bottom, where the thumb is
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (inTrash) item(key = "trash_note") {
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.muninn_cache_trash_note, ClipboardDao.TRASH_DAYS),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (trashCount > 0)
                            TextButton(onClick = { dao?.emptyTrash(); changed(R.string.muninn_deleted_forever) }) {
                                Text(stringResource(R.string.muninn_empty_trash))
                            }
                    }
                }
                if (clips.isEmpty()) item(key = "empty") {
                    Text(
                        stringResource(if (query.isBlank()) R.string.muninn_cache_empty else R.string.muninn_cache_no_match),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                val pinned = if (filter == CacheFilter.ALL) clips.filter { it.pinned } else emptyList()
                val rest = if (filter == CacheFilter.ALL) clips.filter { !it.pinned } else clips
                if (pinned.isNotEmpty()) {
                    item(key = "h_pinned") { SectionHeader(R.string.muninn_section_pinned, gold = true) }
                    items(pinned, key = { it.clip.id }) { cv ->
                        ClipRow(cv, inTrash, onCopy = { copyClip(ctx, cv.clip); changed() },
                            onPin = { dao?.togglePinned(cv.clip.id); changed(R.string.muninn_unpinned) },
                            onDelete = { dao?.deleteById(cv.clip.id); changed(R.string.muninn_moved_to_deleted) },
                            onRestore = { })
                    }
                    if (rest.isNotEmpty()) item(key = "h_kept") { SectionHeader(R.string.muninn_section_kept, gold = false) }
                }
                items(rest, key = { it.clip.id }) { cv ->
                    ClipRow(
                        cv, inTrash,
                        onCopy = { copyClip(ctx, cv.clip); changed() },
                        onPin = {
                            val wasPinned = cv.pinned
                            dao?.togglePinned(cv.clip.id)
                            changed(if (wasPinned) R.string.muninn_unpinned else R.string.muninn_pinned)
                        },
                        onDelete = {
                            dao?.deleteById(cv.clip.id)
                            changed(if (inTrash) R.string.muninn_deleted_forever else R.string.muninn_moved_to_deleted)
                        },
                        onRestore = { dao?.restore(cv.clip.id); changed(R.string.muninn_restored) },
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).imePadding()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CacheFilter.entries.forEach { f ->
                        val selected = f == filter
                        val label = stringResource(f.label) + if (f == CacheFilter.TRASH && trashCount > 0) " $trashCount" else ""
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    filter = f
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.muninn_cache_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_muninn_search), null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    // leave room on the right for the round keyboard button
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 86.dp)
                )
            }
        }
    }
}

/** what one row shows, taken at the moment the list was read */
private class ClipView(val clip: ClipboardHistoryEntry, val pinned: Boolean, val meta: String)

@Composable
private fun SectionHeader(@StringRes title: Int, gold: Boolean) {
    Text(
        stringResource(title).uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        color = if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun ClipRow(
    cv: ClipView,
    inTrash: Boolean,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    val clip = cv.clip
    val gold = MaterialTheme.colorScheme.primary
    val highlighted = cv.pinned || clip.isCode
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (cv.pinned) gold.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer)
            .then(if (highlighted) Modifier.border(BorderStroke(1.5.dp, gold.copy(alpha = 0.7f)), RoundedCornerShape(16.dp)) else Modifier)
            .clickable(enabled = !inTrash) { onCopy() }
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val text = when {
                clip.isCode -> MuninnClips.MASKED_CODE
                clip.filename != null -> stringResource(R.string.muninn_cache_file) + (clip.text?.let { " · $it" } ?: "")
                else -> clip.text.orEmpty()
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = if (MuninnClips.isLink(clip.text)) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                cv.meta,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) gold else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (inTrash) {
            IconButton(onClick = onRestore) {
                Icon(painterResource(R.drawable.ic_muninn_restore), stringResource(R.string.muninn_cache_restore), tint = gold)
            }
        } else if (!clip.isCode) {
            IconButton(onClick = onPin) {
                Icon(
                    painterResource(R.drawable.ic_muninn_pin),
                    stringResource(if (cv.pinned) R.string.muninn_cache_unpin else R.string.muninn_cache_pin),
                    tint = if (cv.pinned) gold else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(if (cv.pinned) 22.dp else 20.dp)
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                painterResource(R.drawable.ic_muninn_delete),
                stringResource(if (inTrash) R.string.muninn_cache_delete_forever else R.string.muninn_cache_delete),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun copyClip(context: Context, clip: ClipboardHistoryEntry) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val data = if (clip.filename != null) clip.getContentUri(context)?.let { ClipData.newUri(context.contentResolver, clip.text ?: "", it) }
        else ClipData.newPlainText("", clip.text ?: "")
    if (data == null) return
    cm.setPrimaryClip(data)
    // Android 13+ shows its own "copied" message; older versions get ours
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU)
        Toast.makeText(context, R.string.muninn_cache_copied, Toast.LENGTH_SHORT).show()
}

@Preview
@Composable
private fun PreviewCache() {
    Theme(previewDark) { Surface { MuninnCacheScreen {} } }
}
