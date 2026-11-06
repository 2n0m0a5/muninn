// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
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
import helium314.keyboard.latin.MuninnReminderTimes
import helium314.keyboard.latin.MuninnReminders
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
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
    REMINDERS(R.string.muninn_filter_reminders),
    LINKS(R.string.muninn_filter_links),
    CODES(R.string.muninn_filter_codes),
    TRASH(R.string.muninn_tab_trash),
}

@Composable
fun MuninnCacheScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val lockOn = remember { ctx.prefs().getBoolean(Settings.PREF_MUNINN_LOCK_CACHE, Defaults.PREF_MUNINN_LOCK_CACHE) }
    var unlocked by remember { mutableStateOf(!lockOn || MuninnCacheLock.isUnlocked()) }
    if (unlocked) CacheContent(onClickBack)
    else LockedScreen(onClickBack, onUnlocked = { unlocked = true })
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CacheContent(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dao = remember { ClipboardDao.getInstance(ctx) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CacheFilter.ALL) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<Long>() } // not empty = choosing clips to merge or delete
    val selecting = selected.isNotEmpty()
    var version by remember { mutableIntStateOf(0) } // bumped after every change, so the list is read again
    /** every action is felt (vibration), seen (message) and shown (the list redraws) */
    fun changed(@StringRes message: Int? = null) {
        version++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (message != null) Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }
    fun changed(message: String) {
        version++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    // a snapshot of each clip, so the screen redraws when a clip changes (the database changes clips in place)
    val clips: List<ClipView> = remember(version, query, filter, tagFilter) {
        if (dao == null) return@remember emptyList()
        val base = when (filter) {
            CacheFilter.ALL -> dao.getAll()
            CacheFilter.PINNED -> dao.getAll().filter { it.isPinned }
            CacheFilter.REMINDERS -> dao.withReminders()
            CacheFilter.LINKS -> dao.getAll().filter { MuninnClips.isLink(it.text) }
            CacheFilter.CODES -> dao.getAll().filter { it.isCode }
            CacheFilter.TRASH -> dao.getTrash()
        }.toList().let { list -> tagFilter?.let { t -> list.filter { t in it.tags } } ?: list }
        val q = query.trim()
        val matching = if (q.isEmpty()) base
            else base.filter { clip ->
                !clip.isCode && (clip.text?.contains(q, ignoreCase = true) == true
                        || MuninnClips.appLabel(ctx, clip.source)?.contains(q, ignoreCase = true) == true
                        || clip.tags.any { MuninnClips.tagLabel(ctx, it).contains(q, ignoreCase = true) })
            }
        matching.map { ClipView(it, it.isPinned, MuninnClips.metaLine(ctx, it, tags = false), it.remindAt, it.tags.toList()) }
    }
    val tagsInUse = remember(version) { dao?.allTags() ?: emptyList() }
    var actionsFor by remember { mutableStateOf<ClipView?>(null) }
    var editFor by remember { mutableStateOf<ClipView?>(null) }
    var tagsFor by remember { mutableStateOf<ClipView?>(null) }
    // a hint that disappears once the person has held a clip
    var hintSeen by remember { mutableStateOf(ctx.prefs().getBoolean(PREF_HINT_SEEN, false)) }
    fun openActions(cv: ClipView) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        actionsFor = cv
        if (!hintSeen) { hintSeen = true; ctx.prefs().edit().putBoolean(PREF_HINT_SEEN, true).apply() }
    }
    fun toggleSelected(cv: ClipView) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (cv.clip.id in selected) selected.remove(cv.clip.id) else selected.add(cv.clip.id)
    }
    val total = remember(version) { dao?.count() ?: 0 }
    val trashCount = remember(version) { dao?.trashCount() ?: 0 }
    val inTrash = filter == CacheFilter.TRASH
    var remindFor by remember { mutableStateOf<ClipView?>(null) }

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

            val row: @Composable (ClipView) -> Unit = { cv ->
                ClipRow(
                    cv, inTrash,
                    selecting = selecting,
                    isSelected = cv.clip.id in selected,
                    onCopy = { if (selecting) toggleSelected(cv) else { copyClip(ctx, cv.clip); changed() } },
                    onHold = { if (selecting) toggleSelected(cv) else if (!inTrash) openActions(cv) },
                    onRemind = { remindFor = cv },
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
                    onTagClick = { tag -> tagFilter = if (tagFilter == tag) null else tag; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                )
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
                if (!inTrash && !hintSeen && clips.isNotEmpty() && !selecting) item(key = "hint") {
                    Text(
                        stringResource(R.string.muninn_clip_actions_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                    )
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
                    items(pinned, key = { it.clip.id }) { cv -> row(cv) }
                    if (rest.isNotEmpty()) item(key = "h_kept") { SectionHeader(R.string.muninn_section_kept, gold = false) }
                }
                items(rest, key = { it.clip.id }) { cv -> row(cv) }
            }

            if (selecting) SelectionBar(
                count = selected.size,
                onCancel = { selected.clear() },
                onDelete = {
                    selected.toList().forEach { dao?.deleteById(it) }
                    selected.clear()
                    changed(R.string.muninn_moved_to_deleted)
                },
                onMerge = {
                    if (dao?.merge(selected.toList()) == true) {
                        selected.clear()
                        changed(R.string.muninn_merged)
                    } else Toast.makeText(ctx, R.string.muninn_merge_need_two, Toast.LENGTH_SHORT).show()
                },
            ) else Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).imePadding()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CacheFilter.entries.forEach { f ->
                        val label = stringResource(f.label) + if (f == CacheFilter.TRASH && trashCount > 0) " $trashCount" else ""
                        FilterChip(label, f == filter) {
                            filter = f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                // tags in use, as a second row: tap one to see only those clips
                val tagRow = (tagsInUse + listOfNotNull(tagFilter)).distinct()
                if (tagRow.isNotEmpty() && !inTrash) Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.ic_muninn_tag), stringResource(R.string.muninn_tags),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    tagRow.forEach { tag ->
                        TagChip(MuninnClips.tagLabel(ctx, tag), selected = tag == tagFilter, large = true) {
                            tagFilter = if (tagFilter == tag) null else tag
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
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
        actionsFor?.let { cv ->
            ClipActionsDialog(
                cv = cv,
                onDismiss = { actionsFor = null },
                onCopy = { actionsFor = null; copyClip(ctx, cv.clip); changed() },
                onEdit = { actionsFor = null; editFor = cv },
                onTags = { actionsFor = null; tagsFor = cv },
                onSelect = { actionsFor = null; selected.clear(); selected.add(cv.clip.id); changed() },
                onShare = { actionsFor = null; shareClip(ctx, cv.clip) },
            )
        }
        editFor?.let { cv ->
            EditDialog(
                initial = cv.clip.text.orEmpty(),
                onDismiss = { editFor = null },
                onSave = { newText ->
                    if (newText == cv.clip.text) { editFor = null; return@EditDialog }
                    if (dao?.editText(cv.clip.id, newText) == true) {
                        editFor = null
                        changed(R.string.muninn_edit_saved)
                    } else Toast.makeText(ctx, R.string.muninn_edit_duplicate, Toast.LENGTH_SHORT).show()
                },
            )
        }
        tagsFor?.let { cv ->
            TagsDialog(
                initial = cv.tags,
                known = (MuninnClips.BUILT_IN_TAGS + tagsInUse).distinct(),
                onDismiss = { tagsFor = null },
                onToggle = { tag, nowOn ->
                    dao?.toggleTag(cv.clip.id, tag)
                    val label = MuninnClips.tagLabel(ctx, tag)
                    changed(ctx.getString(if (nowOn) R.string.muninn_tag_added else R.string.muninn_tag_removed, label))
                },
            )
        }
        remindFor?.let { cv ->
            ReminderSheet(
                cv = cv,
                onDismiss = { remindFor = null },
                onSet = { preset ->
                    MuninnReminders.set(ctx, cv.clip.id, preset)
                    remindFor = null
                    version++
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cv.clip.remindAt?.let {
                        Toast.makeText(ctx, ctx.getString(R.string.muninn_remind_set, MuninnReminders.describeTime(ctx, it)), Toast.LENGTH_SHORT).show()
                    }
                },
                onRemove = {
                    MuninnReminders.done(ctx, cv.clip.id)
                    remindFor = null
                    changed(R.string.muninn_remind_remove)
                },
            )
        }
    }
}

private const val PREF_HINT_SEEN = "muninn_hint_clip_actions_seen"

/** Muninn: the round filter buttons at the bottom of the cache */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/** Muninn: a small gold-outlined label, under a clip or in the tag row */
@Composable
private fun TagChip(label: String, selected: Boolean = false, large: Boolean = false, onClick: (() -> Unit)? = null) {
    val gold = MaterialTheme.colorScheme.primary
    Text(
        label,
        style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else gold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) gold else gold.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, gold.copy(alpha = if (selected) 1f else 0.45f)), RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (large) 14.dp else 8.dp, vertical = if (large) 9.dp else 2.dp)
    )
}

/** Muninn: shown instead of the filters while clips are being chosen */
@Composable
private fun SelectionBar(count: Int, onCancel: () -> Unit, onDelete: () -> Unit, onMerge: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 8.dp, end = 86.dp, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onCancel) {
            Icon(painterResource(R.drawable.ic_muninn_close), stringResource(android.R.string.cancel))
        }
        Text(stringResource(R.string.muninn_selected, count), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_muninn_delete), stringResource(R.string.muninn_cache_delete))
        }
        Button(onClick = onMerge, enabled = count >= 2) {
            Icon(painterResource(R.drawable.ic_muninn_merge), null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.muninn_merge))
        }
    }
}

/** Muninn: what holding a clip offers. Big rows, one line each, so it reads at a glance. */
@Composable
private fun ClipActionsDialog(
    cv: ClipView,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onTags: () -> Unit,
    onSelect: () -> Unit,
    onShare: () -> Unit,
) {
    val isText = cv.clip.filename == null
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = {
            Text(
                if (cv.clip.isCode) MuninnClips.MASKED_CODE else if (!isText) stringResource(R.string.muninn_cache_file) else cv.clip.text.orEmpty(),
                maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionRow(R.drawable.sym_keyboard_copy_rounded, R.string.muninn_copy, onCopy)
                if (isText) ActionRow(R.drawable.ic_muninn_edit, R.string.muninn_edit, onEdit)
                ActionRow(R.drawable.ic_muninn_tag, R.string.muninn_tags, onTags)
                ActionRow(R.drawable.ic_muninn_select, R.string.muninn_select_to_merge, onSelect)
                if (isText) ActionRow(R.drawable.ic_muninn_share, R.string.muninn_share, onShare)
            }
        },
    )
}

@Composable
private fun ActionRow(icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EditDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.muninn_edit), fontFamily = MuninnSerif, fontSize = 30.sp) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3, maxLines = 10,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.muninn_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsDialog(initial: List<String>, known: List<String>, onDismiss: () -> Unit, onToggle: (String, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val on = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    val all = remember { mutableStateListOf<String>().apply { addAll((known + initial).distinct()) } }
    var newTag by remember { mutableStateOf("") }
    fun toggle(tag: String) {
        val nowOn = tag !in on
        if (nowOn) on.add(tag) else on.remove(tag)
        onToggle(tag, nowOn)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.muninn_tags), fontFamily = MuninnSerif, fontSize = 30.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    all.forEach { tag -> TagChip(MuninnClips.tagLabel(ctx, tag), selected = tag in on, large = true) { toggle(tag) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag, onValueChange = { newTag = it.replace(ClipboardDao.TAG_SEPARATOR, "").take(24) },
                        placeholder = { Text(stringResource(R.string.muninn_tag_new)) }, singleLine = true,
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    )
                    TextButton(enabled = newTag.isNotBlank(), onClick = {
                        val tag = newTag.trim()
                        newTag = ""
                        if (tag !in all) all.add(tag)
                        if (tag !in on) toggle(tag)
                    }) { Text(stringResource(R.string.muninn_tag_add)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

/** Muninn: the cache behind a fingerprint or the screen lock */
@Composable
private fun LockedScreen(onClickBack: () -> Unit, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    fun ask() = MuninnCacheLock.ask(ctx, onUnlocked)
    LaunchedEffect(Unit) { ask() }
    Surface {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClickBack)
                Text(stringResource(R.string.muninn_cache_title), fontFamily = MuninnSerif, fontSize = 38.sp)
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_muninn_lock), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
                Text(stringResource(R.string.muninn_locked), fontFamily = MuninnSerif, fontSize = 28.sp, textAlign = TextAlign.Center)
                if (android.os.Build.VERSION.SDK_INT < 28)
                    Text(stringResource(R.string.muninn_lock_needs_android9), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Button(onClick = { ask() }) { Text(stringResource(R.string.muninn_unlock)) }
            }
        }
    }
}

/** Muninn: choose when a clip first comes back. After that it keeps coming back until Done or stopped. */
@Composable
private fun ReminderSheet(
    cv: ClipView,
    onDismiss: () -> Unit,
    onSet: (MuninnReminderTimes.Preset) -> Unit,
    onRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    var pendingPreset by remember { mutableStateOf<MuninnReminderTimes.Preset?>(null) }
    // notifications need permission on Android 13+; without it reminders still show in the keyboard
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val preset = pendingPreset ?: return@rememberLauncherForActivityResult
        onSet(preset)
        if (!granted) Toast.makeText(ctx, R.string.muninn_notifications_denied, Toast.LENGTH_LONG).show()
    }
    fun choose(preset: MuninnReminderTimes.Preset) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !MuninnReminders.canNotify(ctx)) {
            pendingPreset = preset
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else onSet(preset)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            Row {
                if (cv.remindAt != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.muninn_reminder_stop)) }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
        title = { Text(stringResource(R.string.muninn_remind_me), fontFamily = MuninnSerif, fontSize = 30.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (cv.clip.isCode) MuninnClips.MASKED_CODE else cv.clip.text.orEmpty(),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.muninn_remind_first_time).uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    R.string.muninn_remind_in_hour to MuninnReminderTimes.Preset.IN_ONE_HOUR,
                    R.string.muninn_remind_tonight to MuninnReminderTimes.Preset.TONIGHT,
                    R.string.muninn_remind_tomorrow to MuninnReminderTimes.Preset.TOMORROW_MORNING,
                    R.string.muninn_remind_next_week to MuninnReminderTimes.Preset.NEXT_WEEK,
                ).forEach { (label, preset) ->
                    val time = MuninnReminders.describeTime(ctx, MuninnReminderTimes.presetTime(preset, System.currentTimeMillis()))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { choose(preset) }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.muninn_remind_explained), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        },
    )
}

/** what one row shows, taken at the moment the list was read */
private class ClipView(val clip: ClipboardHistoryEntry, val pinned: Boolean, val meta: String, val remindAt: Long?, val tags: List<String>)

@Composable
private fun SectionHeader(@StringRes title: Int, gold: Boolean) {
    Text(
        stringResource(title).uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        color = if (gold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ClipRow(
    cv: ClipView,
    inTrash: Boolean,
    selecting: Boolean,
    isSelected: Boolean,
    onCopy: () -> Unit,
    onHold: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onRemind: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val clip = cv.clip
    val gold = MaterialTheme.colorScheme.primary
    val highlighted = cv.pinned || clip.isCode || isSelected
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (cv.pinned || isSelected) gold.copy(alpha = if (isSelected) 0.16f else 0.08f) else MaterialTheme.colorScheme.surfaceContainer)
            .then(if (highlighted) Modifier.border(BorderStroke(if (isSelected) 2.dp else 1.5.dp, gold.copy(alpha = if (isSelected) 1f else 0.7f)), RoundedCornerShape(16.dp)) else Modifier)
            .combinedClickable(enabled = !inTrash, onClick = onCopy, onLongClick = onHold)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Box(
                Modifier.padding(end = 12.dp).size(22.dp).clip(RoundedCornerShape(50))
                    .background(if (isSelected) gold else Color.Transparent)
                    .border(BorderStroke(2.dp, if (isSelected) gold else MaterialTheme.colorScheme.outline), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Icon(painterResource(R.drawable.ic_muninn_check), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            if (cv.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cv.tags.forEach { tag -> TagChip(MuninnClips.tagLabel(ctx, tag), onClick = if (selecting) null else ({ onTagClick(tag) })) }
            }
            Text(
                cv.meta,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) gold else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selecting) Unit
        else if (inTrash) {
            IconButton(onClick = onRestore) {
                Icon(painterResource(R.drawable.ic_muninn_restore), stringResource(R.string.muninn_cache_restore), tint = gold)
            }
        } else if (!clip.isCode) {
            IconButton(onClick = onRemind) {
                Icon(
                    painterResource(R.drawable.ic_muninn_bell),
                    stringResource(R.string.muninn_remind_me),
                    tint = if (cv.remindAt != null) gold else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onPin) {
                Icon(
                    painterResource(R.drawable.ic_muninn_pin),
                    stringResource(if (cv.pinned) R.string.muninn_cache_unpin else R.string.muninn_cache_pin),
                    tint = if (cv.pinned) gold else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(if (cv.pinned) 22.dp else 20.dp)
                )
            }
        }
        if (!selecting) IconButton(onClick = onDelete) {
            Icon(
                painterResource(R.drawable.ic_muninn_delete),
                stringResource(if (inTrash) R.string.muninn_cache_delete_forever else R.string.muninn_cache_delete),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun shareClip(context: Context, clip: ClipboardHistoryEntry) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, clip.text.orEmpty())
    context.startActivity(Intent.createChooser(send, context.getString(R.string.muninn_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
