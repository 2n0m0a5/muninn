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
    val dao = remember { ClipboardDao.getInstance(ctx) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CacheFilter.ALL) }
    var version by remember { mutableIntStateOf(0) } // bumped after every change, so the list is read again
    fun changed() { version++ }

    val clips: List<ClipboardHistoryEntry> = remember(version, query, filter) {
        if (dao == null) return@remember emptyList()
        val base = when (filter) {
            CacheFilter.ALL -> dao.getAll()
            CacheFilter.PINNED -> dao.getAll().filter { it.isPinned }
            CacheFilter.LINKS -> dao.getAll().filter { MuninnClips.isLink(it.text) }
            CacheFilter.CODES -> dao.getAll().filter { it.isCode }
            CacheFilter.TRASH -> dao.getTrash()
        }.toList()
        val q = query.trim()
        if (q.isEmpty()) base
        else base.filter { clip ->
            !clip.isCode && (clip.text?.contains(q, ignoreCase = true) == true
                    || MuninnClips.appLabel(ctx, clip.source)?.contains(q, ignoreCase = true) == true)
        }
    }
    val total = remember(version) { dao?.count() ?: 0 }
    val trashCount = remember(version) { dao?.trashCount() ?: 0 }

    Surface {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClickBack)
                Text(stringResource(R.string.muninn_cache_title), fontFamily = MuninnSerif, fontSize = 38.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.muninn_cache_kept, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            )
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
                            .clickable { filter = f }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            if (filter == CacheFilter.TRASH) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.muninn_cache_trash_note, ClipboardDao.TRASH_DAYS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (trashCount > 0)
                        TextButton(onClick = { dao?.emptyTrash(); changed() }) { Text(stringResource(R.string.muninn_empty_trash)) }
                }
            }
            if (clips.isEmpty()) {
                Text(
                    stringResource(if (query.isBlank()) R.string.muninn_cache_empty else R.string.muninn_cache_no_match),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(clips, key = { it.id }) { clip ->
                    ClipRow(
                        clip = clip,
                        inTrash = filter == CacheFilter.TRASH,
                        onCopy = { copyClip(ctx, clip); changed() },
                        onPin = { dao?.togglePinned(clip.id); changed() },
                        onDelete = { dao?.deleteById(clip.id); changed() },
                        onRestore = { dao?.restore(clip.id); changed() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipRow(
    clip: ClipboardHistoryEntry,
    inTrash: Boolean,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    val ctx = LocalContext.current
    val gold = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (clip.isPinned || clip.isCode) Modifier.border(BorderStroke(1.dp, gold.copy(alpha = 0.5f)), RoundedCornerShape(16.dp)) else Modifier)
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
                MuninnClips.metaLine(ctx, clip),
                style = MaterialTheme.typography.bodySmall,
                color = if (clip.isCode) gold else MaterialTheme.colorScheme.onSurfaceVariant
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
                    stringResource(if (clip.isPinned) R.string.muninn_cache_unpin else R.string.muninn_cache_pin),
                    tint = if (clip.isPinned) gold else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
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
    Toast.makeText(context, R.string.muninn_cache_copied, Toast.LENGTH_SHORT).show()
}

@Preview
@Composable
private fun PreviewCache() {
    Theme(previewDark) { Surface { MuninnCacheScreen {} } }
}
