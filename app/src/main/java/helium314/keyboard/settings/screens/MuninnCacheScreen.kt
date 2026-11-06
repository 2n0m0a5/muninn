// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.MuninnClips
import helium314.keyboard.latin.MuninnReminderTimes
import helium314.keyboard.latin.MuninnReminders
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.BackButton
import helium314.keyboard.latin.utils.MuninnRunic
import helium314.keyboard.latin.utils.MuninnSerif
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.AnswerBar
import helium314.keyboard.settings.FilterPill
import helium314.keyboard.settings.GoldButton
import helium314.keyboard.settings.MuninnDivider
import helium314.keyboard.settings.MuninnLabel
import helium314.keyboard.settings.MuninnRose
import helium314.keyboard.settings.OutlineButton
import helium314.keyboard.settings.TagPill
import kotlinx.coroutines.delay

/*
 * Muninn: the cache in the app, as on the "App, in depth" canvas.
 * Tap a clip to copy it, hold it for everything else (a bottom sheet). Search and filters sit at the bottom, by the thumb.
 */

private enum class CacheFilter(@StringRes val label: Int) {
    ALL(R.string.muninn_tab_all),
    COMING_BACK(R.string.muninn_filter_coming_back),
    PINNED(R.string.muninn_tab_pinned),
    LINKS(R.string.muninn_filter_links),
    TRASH(R.string.muninn_tab_trash),
}

/** what one row shows, taken at the moment the list was read (the database changes clips in place) */
private class ClipView(val clip: ClipboardHistoryEntry, val pinned: Boolean, val meta: String, val remindAt: Long?, val tags: List<String>)

/** the answer bar's message and its way back */
private class Answer(val text: String, val undo: (() -> Unit)?)

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
    // several tags can be chosen: only clips that have all of them are shown
    var tagFilter by remember { mutableStateOf(listOf<String>()) }
    fun toggleTag(tag: String) { tagFilter = if (tag in tagFilter) tagFilter - tag else tagFilter + tag }
    val selected = remember { mutableStateListOf<Long>() } // not empty: choosing clips to merge or delete
    val selecting = selected.isNotEmpty()
    var version by remember { mutableIntStateOf(0) } // bumped after every change, so the list is read again
    var answer by remember { mutableStateOf<Answer?>(null) }
    var sheetFor by remember { mutableStateOf<ClipView?>(null) }
    var remindFor by remember { mutableStateOf<ClipView?>(null) }
    var editFor by remember { mutableStateOf<ClipView?>(null) }

    /** every action is felt (vibration), seen (the list redraws) and told (the answer bar) */
    fun changed(message: String? = null, undo: (() -> Unit)? = null) {
        version++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (message != null) answer = Answer(message, undo)
    }
    LaunchedEffect(answer) { if (answer != null) { delay(5000); answer = null } }

    val clips: List<ClipView> = remember(version, query, filter, tagFilter) {
        if (dao == null) return@remember emptyList()
        val base = when (filter) {
            CacheFilter.ALL -> dao.getAll()
            CacheFilter.COMING_BACK -> dao.withReminders()
            CacheFilter.PINNED -> dao.getAll().filter { it.isPinned }
            CacheFilter.LINKS -> dao.getAll().filter { MuninnClips.hasLink(it.text) }
            CacheFilter.TRASH -> dao.getTrash()
        }.toList().let { list -> if (tagFilter.isEmpty()) list else list.filter { clip -> tagFilter.all { it in clip.tags } } }
        val q = query.trim()
        val matching = if (q.isEmpty()) base else base.filter { clip ->
            !clip.isCode && (clip.text?.contains(q, ignoreCase = true) == true
                    || MuninnClips.appLabel(ctx, clip.source)?.contains(q, ignoreCase = true) == true
                    || clip.tags.any { MuninnClips.tagLabel(ctx, it).contains(q, ignoreCase = true) })
        }
        val now = System.currentTimeMillis()
        matching.map {
            val meta = MuninnClips.metaLine(ctx, it, tags = false).let { m ->
                val deletedAt = it.deletedAt
                if (deletedAt == null) m
                else m + " · " + ctx.getString(R.string.muninn_gone_in_days,
                    (ClipboardDao.TRASH_DAYS - ((now - deletedAt) / DateUtils.DAY_IN_MILLIS).toInt()).coerceAtLeast(1))
            }
            ClipView(it, it.isPinned, meta, it.remindAt, it.tags.toList())
        }
    }
    val total = remember(version) { dao?.count() ?: 0 }
    val comingBack = remember(version) { dao?.withReminders()?.size ?: 0 }
    val trashCount = remember(version) { dao?.trashCount() ?: 0 }
    val tagsInUse = remember(version) { dao?.allTags() ?: emptyList() }
    val inTrash = filter == CacheFilter.TRASH

    fun toggleSelected(cv: ClipView) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (cv.clip.id in selected) selected.remove(cv.clip.id) else selected.add(cv.clip.id)
    }
    fun delete(ids: List<Long>) {
        ids.forEach { dao?.deleteById(it) }
        changed(ctx.getString(R.string.muninn_moved_to_deleted)) { ids.forEach { dao?.restore(it) }; version++ }
    }
    fun restore(id: Long) {
        dao?.restore(id)
        changed(ctx.getString(R.string.muninn_back_in_cache)) // no Undo: bringing a clip back is never a mistake to fix
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                // header
                if (selecting) Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).clickable { selected.clear() }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_muninn_close), stringResource(android.R.string.cancel), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(stringResource(R.string.muninn_selected, selected.size), fontFamily = MuninnSerif, fontSize = 34.sp)
                } else {
                    Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BackButton(onClickBack)
                        Text(stringResource(if (inTrash) R.string.muninn_recently_deleted else R.string.muninn_cache_title),
                            fontFamily = MuninnSerif, fontSize = if (inTrash) 34.sp else 42.sp, lineHeight = 44.sp)
                        Spacer(Modifier.weight(1f))
                        if (!inTrash) Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.muninn_cache_kept, total), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (comingBack > 0) Text(stringResource(R.string.muninn_cache_coming_back, comingBack), fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (!inTrash) Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ᛗᚢᚾᛁᚾᚾ", fontFamily = MuninnRunic, fontSize = 12.sp, letterSpacing = 4.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (inTrash) item(key = "trash_note") {
                        Text(stringResource(R.string.muninn_trash_intro, ClipboardDao.TRASH_DAYS), fontSize = 13.5.sp, lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp))
                    } else if (!selecting && clips.isNotEmpty()) item(key = "hint") {
                        Text(stringResource(R.string.muninn_hint_tap_hold), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp))
                    }
                    if (clips.isEmpty()) item(key = "empty") { EmptyState(query, inTrash) }

                    val pinned = if (filter == CacheFilter.ALL) clips.filter { it.pinned } else emptyList()
                    val rest = if (filter == CacheFilter.ALL) clips.filter { !it.pinned } else clips
                    val row: @Composable (ClipView) -> Unit = { cv ->
                        ClipCard(
                            cv, inTrash, selecting, cv.clip.id in selected,
                            onTap = {
                                when {
                                    selecting -> toggleSelected(cv)
                                    inTrash -> restore(cv.clip.id)
                                    else -> { copyClip(ctx, cv.clip); changed(ctx.getString(R.string.muninn_cache_copied)) }
                                }
                            },
                            onHold = { if (selecting) toggleSelected(cv) else if (!inTrash) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sheetFor = cv } },
                            onRestore = { restore(cv.clip.id) },
                            onTag = { tag -> toggleTag(tag); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        )
                    }
                    if (pinned.isNotEmpty()) {
                        item(key = "h_pinned") { MuninnLabel(stringResource(R.string.muninn_section_pinned), gold = true, modifier = Modifier.padding(top = 4.dp)) }
                        items(pinned, key = { it.clip.id }) { row(it) }
                        if (rest.isNotEmpty()) item(key = "h_kept") { MuninnLabel(stringResource(R.string.muninn_section_kept), modifier = Modifier.padding(top = 8.dp)) }
                    }
                    items(rest, key = { it.clip.id }) { row(it) }
                    if (inTrash && trashCount > 0) item(key = "empty_all") {
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.muninn_delete_all_now, trashCount), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MuninnRose,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                    dao?.emptyTrash(); changed(ctx.getString(R.string.muninn_deleted_forever))
                                }.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                }

                if (selecting) SelectionPanel(
                    clips = selected.mapNotNull { id -> clips.firstOrNull { it.clip.id == id }?.clip },
                    onDelete = { val ids = selected.toList(); selected.clear(); delete(ids) },
                    onMerge = {
                        if (dao?.merge(selected.toList()) == true) { selected.clear(); changed(ctx.getString(R.string.muninn_merged)) }
                        else Toast.makeText(ctx, R.string.muninn_merge_need_two, Toast.LENGTH_SHORT).show()
                    },
                ) else Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).imePadding().padding(top = 10.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CacheFilter.entries.forEach { f ->
                            val count = when (f) { CacheFilter.COMING_BACK -> comingBack; CacheFilter.TRASH -> trashCount; else -> 0 }
                            FilterPill(stringResource(f.label) + if (count > 0) " $count" else "", f == filter, gold = f == CacheFilter.COMING_BACK) {
                                filter = f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    val tagRow = (tagFilter + tagsInUse).distinct()
                    if (tagRow.isNotEmpty() && !inTrash) Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(R.drawable.ic_muninn_tag), stringResource(R.string.muninn_tags),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        tagRow.forEach { tag ->
                            TagPill(MuninnClips.tagLabel(ctx, tag), selected = tag in tagFilter, large = true) {
                                toggleTag(tag)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        if (tagFilter.size > 1) Text(stringResource(R.string.muninn_clear_tags), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { tagFilter = emptyList() }.padding(horizontal = 12.dp, vertical = 9.dp))
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.muninn_cache_search_words)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_muninn_search), null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        // leave room on the right for the round keyboard button
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 86.dp)
                    )
                }
            }

            AnimatedVisibility(answer != null, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 150.dp)) {
                answer?.let { a ->
                    AnswerBar(a.text, if (a.undo != null) stringResource(R.string.muninn_undo) else null, onAction = {
                        a.undo?.invoke(); answer = null; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    })
                }
            }
        }

        sheetFor?.let { cv ->
            ClipSheet(
                cv = cv,
                onDismiss = { sheetFor = null },
                onCopy = { sheetFor = null; copyClip(ctx, cv.clip); changed(ctx.getString(R.string.muninn_cache_copied)) },
                onRemind = { sheetFor = null; remindFor = cv },
                onPin = {
                    sheetFor = null
                    dao?.togglePinned(cv.clip.id)
                    changed(ctx.getString(if (cv.pinned) R.string.muninn_unpinned else R.string.muninn_pinned))
                },
                onShare = { sheetFor = null; shareClip(ctx, cv.clip) },
                onEdit = { sheetFor = null; editFor = cv },
                onSelect = { sheetFor = null; selected.clear(); selected.add(cv.clip.id); changed() },
                onDelete = { sheetFor = null; delete(listOf(cv.clip.id)) },
            )
        }
        remindFor?.let { cv ->
            RemindSheet(
                cv = cv,
                onDismiss = { remindFor = null },
                onSet = { preset ->
                    MuninnReminders.set(ctx, cv.clip.id, preset)
                    remindFor = null
                    val at = dao?.get(cv.clip.id)?.remindAt
                    changed(if (at != null) ctx.getString(R.string.muninn_remind_set, MuninnReminders.describeTime(ctx, at)) else null)
                },
                onStop = {
                    MuninnReminders.stop(ctx, cv.clip.id)
                    remindFor = null
                    changed(ctx.getString(R.string.muninn_reminder_stop_feedback))
                },
            )
        }
        editFor?.let { cv ->
            EditClipScreen(
                cv = cv,
                knownTags = (MuninnClips.BUILT_IN_TAGS + tagsInUse).distinct(),
                onDismiss = { editFor = null },
                onSave = { newText, newTags ->
                    if (newText != null && newText != cv.clip.text && dao?.editText(cv.clip.id, newText) != true) {
                        Toast.makeText(ctx, R.string.muninn_edit_duplicate, Toast.LENGTH_SHORT).show()
                        return@EditClipScreen
                    }
                    if (newTags != cv.tags) dao?.setTags(cv.clip.id, newTags)
                    editFor = null
                    changed(ctx.getString(R.string.muninn_edit_saved))
                },
            )
        }
    }
}

@Composable
private fun EmptyState(query: String, inTrash: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painterResource(R.drawable.ic_muninn_feather_small), null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
        Text(stringResource(when { query.isNotBlank() -> R.string.muninn_no_match_title; inTrash -> R.string.muninn_trash_empty_title; else -> R.string.muninn_nothing_kept }),
            fontFamily = MuninnSerif, fontSize = 26.sp, textAlign = TextAlign.Center)
        Text(when {
            query.isNotBlank() -> stringResource(R.string.muninn_no_match_text)
            inTrash -> stringResource(R.string.muninn_trash_empty_text)
            else -> stringResource(R.string.muninn_nothing_kept_text)
        }, fontSize = 13.5.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** one clip: text, gold tags, one line about it. Pinned clips have a gold edge; a chosen clip a stronger one. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ClipCard(
    cv: ClipView,
    inTrash: Boolean,
    selecting: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onHold: () -> Unit,
    onRestore: () -> Unit,
    onTag: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val clip = cv.clip
    val gold = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    val edge = when {
        isSelected -> BorderStroke(1.5.dp, gold)
        cv.pinned -> BorderStroke(1.5.dp, gold.copy(alpha = 0.6f))
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (isSelected) gold.copy(alpha = 0.10f) else if (cv.pinned) gold.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceContainer)
            .then(if (edge != null) Modifier.border(edge, shape) else Modifier)
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(start = 14.dp, end = if (inTrash) 6.dp else 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selecting) Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (isSelected) gold else Color.Transparent)
                .border(2.dp, if (isSelected) gold else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) Icon(painterResource(R.drawable.ic_muninn_check), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val text = when {
                clip.isCode -> MuninnClips.MASKED_CODE
                clip.filename != null -> stringResource(R.string.muninn_cache_file) + (clip.text?.let { " · $it" } ?: "")
                else -> clip.text.orEmpty()
            }
            if (clip.filename != null && clip.mimeTypes?.any { it.startsWith("image/") } != false) ClipImage(clip)
            Text(withLinks(text, !clip.isCode && clip.filename == null, MaterialTheme.colorScheme.tertiary),
                fontSize = 15.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                letterSpacing = if (clip.isCode) 1.8.sp else 0.sp,
                color = if (inTrash) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                cv.tags.forEach { tag -> TagPill(MuninnClips.tagLabel(ctx, tag), onClick = if (selecting) null else ({ onTag(tag) })) }
                Text(cv.meta, fontSize = 12.sp, color = if (cv.pinned || clip.isCode || cv.remindAt != null) gold.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (inTrash) Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(gold.copy(alpha = 0.12f)).clickable(onClick = onRestore),
            contentAlignment = Alignment.Center
        ) { Icon(painterResource(R.drawable.ic_muninn_restore), stringResource(R.string.muninn_cache_restore), tint = gold, modifier = Modifier.size(20.dp)) }
    }
}

/** shown at the bottom while clips are chosen: what the merge will look like, then Delete and Merge */
@Composable
private fun SelectionPanel(clips: List<ClipboardHistoryEntry>, onDelete: () -> Unit, onMerge: () -> Unit) {
    val texts = clips.filter { it.filename == null && !it.text.isNullOrEmpty() }.sortedBy { it.timeStamp }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (texts.size >= 2) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.muninn_merged_preview).uppercase(), fontSize = 11.5.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(texts.joinToString("\n") { it.text.orEmpty() }, fontSize = 13.5.sp, lineHeight = 20.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            Text(stringResource(R.string.muninn_merge_explained, texts.size), fontSize = 12.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 72.dp)) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, MuninnRose.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) { Icon(painterResource(R.drawable.ic_muninn_delete), stringResource(R.string.muninn_cache_delete), tint = MuninnRose) }
            GoldButton(stringResource(R.string.muninn_merge_n, clips.size), Modifier.weight(1f), height = 54.dp, enabled = texts.size >= 2, onClick = onMerge)
        }
    }
}

/** hold a clip: everything it can do. Four big buttons for the common things, rows for the rest. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ClipSheet(
    cv: ClipView,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onRemind: () -> Unit,
    onPin: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val clip = cv.clip
    val isText = clip.filename == null
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (clip.isCode) MuninnClips.MASKED_CODE else if (!isText) stringResource(R.string.muninn_cache_file) else clip.text.orEmpty(),
                    fontSize = 16.sp, lineHeight = 23.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    cv.tags.forEach { TagPill(MuninnClips.tagLabel(ctx, it)) }
                    Text(cv.meta, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigAction(R.drawable.sym_keyboard_copy_rounded, R.string.muninn_copy, Modifier.weight(1f), main = true, onClick = onCopy)
                if (!clip.isCode) BigAction(R.drawable.ic_muninn_bell, R.string.muninn_remind, Modifier.weight(1f), gold = true, onClick = onRemind)
                BigAction(R.drawable.ic_muninn_pin, if (cv.pinned) R.string.muninn_cache_unpin else R.string.muninn_cache_pin, Modifier.weight(1f), onClick = onPin)
                if (isText) BigAction(R.drawable.ic_muninn_share, R.string.muninn_share, Modifier.weight(1f), onClick = onShare)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                SheetRow(R.drawable.ic_muninn_edit, stringResource(if (isText) R.string.muninn_edit_text else R.string.muninn_tags), null, onClick = onEdit)
                if (isText) SheetRow(R.drawable.ic_muninn_tag, stringResource(R.string.muninn_tags),
                    cv.tags.joinToString(", ") { MuninnClips.tagLabel(ctx, it) }.ifEmpty { null }, valueGold = true, onClick = onEdit)
                SheetRow(R.drawable.ic_muninn_select, stringResource(R.string.muninn_select_several), stringResource(R.string.muninn_select_several_sub), onClick = onSelect)
                SheetRow(R.drawable.ic_muninn_delete, stringResource(R.string.muninn_cache_delete), stringResource(R.string.muninn_delete_waits),
                    color = MuninnRose, divider = false, onClick = onDelete)
            }
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.muninn_about_clip).uppercase(), fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(aboutClip(ctx, cv), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun aboutClip(ctx: Context, cv: ClipView): String {
    val date = DateUtils.formatDateTime(ctx, cv.clip.timeStamp, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
    val app = MuninnClips.appLabel(ctx, cv.clip.source)
    val copied = if (app != null) ctx.getString(R.string.muninn_copied_in, app, date) else ctx.getString(R.string.muninn_copied_on, date)
    val remind = cv.remindAt?.let { ctx.getString(R.string.muninn_comes_back_at, MuninnReminders.describeTime(ctx, it)) }
    return listOfNotNull(copied, remind).joinToString(" · ")
}

@Composable
private fun BigAction(@DrawableRes icon: Int, @StringRes label: Int, modifier: Modifier, main: Boolean = false, gold: Boolean = false, onClick: () -> Unit) {
    val bg = when { main -> MaterialTheme.colorScheme.primary; gold -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f); else -> MaterialTheme.colorScheme.surfaceContainerHighest }
    val fg = when { main -> MaterialTheme.colorScheme.onPrimary; gold -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface }
    Column(
        modifier.height(76.dp).clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(22.dp))
        Text(stringResource(label), fontSize = 12.5.sp, fontWeight = if (main) FontWeight.Bold else FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun SheetRow(@DrawableRes icon: Int, label: String, value: String?, valueGold: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface,
                     divider: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(painterResource(icon), null, tint = if (color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) else color,
            modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, color = color, modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (valueGold) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (divider) MuninnDivider()
}

/** "Bring this back": choose the first time, see how it keeps returning, one gold button */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindSheet(cv: ClipView, onDismiss: () -> Unit, onSet: (MuninnReminderTimes.Preset) -> Unit, onStop: () -> Unit) {
    val ctx = LocalContext.current
    var choice by remember { mutableStateOf(MuninnReminderTimes.Preset.TONIGHT) }
    var pendingPreset by remember { mutableStateOf<MuninnReminderTimes.Preset?>(null) }
    // notifications need permission on Android 13+; without it reminders still show in the keyboard
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val preset = pendingPreset ?: return@rememberLauncherForActivityResult
        onSet(preset)
        if (!granted) Toast.makeText(ctx, R.string.muninn_notifications_denied, Toast.LENGTH_LONG).show()
    }
    fun confirm() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !MuninnReminders.canNotify(ctx)) {
            pendingPreset = choice
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else onSet(choice)
    }
    val presets = listOf(
        R.string.muninn_remind_in_hour to MuninnReminderTimes.Preset.IN_ONE_HOUR,
        R.string.muninn_remind_tonight to MuninnReminderTimes.Preset.TONIGHT,
        R.string.muninn_remind_tomorrow to MuninnReminderTimes.Preset.TOMORROW_MORNING,
        R.string.muninn_remind_next_week to MuninnReminderTimes.Preset.NEXT_WEEK,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.muninn_bring_back), fontFamily = MuninnSerif, fontSize = 34.sp, lineHeight = 36.sp, color = MaterialTheme.colorScheme.primary)
                Text(cv.clip.text.orEmpty().replace('\n', ' '), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MuninnLabel(stringResource(R.string.muninn_remind_first_time), modifier = Modifier.padding(horizontal = 0.dp))
                presets.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (label, preset) ->
                            val chosen = preset == choice
                            val time = MuninnReminders.describeTime(ctx, MuninnReminderTimes.presetTime(preset, System.currentTimeMillis()))
                            Column(
                                Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
                                    .border(if (chosen) 1.5.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .clickable { choice = preset }.padding(horizontal = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                            ) {
                                Text(stringResource(label), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(time, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.muninn_then_keeps), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                ReturnTimeline()
                Text(stringResource(R.string.muninn_remind_explained_short), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GoldButton(stringResource(R.string.muninn_remind_me_x, stringResource(presets.first { it.second == choice }.first).lowercase()), height = 54.dp) { confirm() }
            if (cv.remindAt != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.muninn_reminder_stop), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onStop).padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

/** dots on a line at uneven, unknowable gaps: after the first time, when it comes back is left to chance */
@Composable
private fun ReturnTimeline() {
    val gold = MaterialTheme.colorScheme.primary
    val labels = listOf("1st", "?", "?", "?", "?", "?")
    val weights = listOf(0f, 2.3f, 0.6f, 3.1f, 1.2f, 2.2f) // no pattern on purpose
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            labels.indices.forEach { i ->
                if (i > 0) Box(Modifier.weight(weights[i]).height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i == 0) gold else gold.copy(alpha = 0.55f)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.indices.forEach { i ->
                if (i > 0) Spacer(Modifier.weight(weights[i]))
                Text(labels[i], fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(14.dp), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** edit a clip's text and its tags on one full screen */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditClipScreen(cv: ClipView, knownTags: List<String>, onDismiss: () -> Unit, onSave: (String?, List<String>) -> Unit) {
    val ctx = LocalContext.current
    val isText = cv.clip.filename == null && !cv.clip.isCode
    var text by remember { mutableStateOf(cv.clip.text.orEmpty()) }
    val on = remember { mutableStateListOf<String>().apply { addAll(cv.tags) } }
    val all = remember { mutableStateListOf<String>().apply { addAll((knownTags + cv.tags).distinct()) } }
    var newTag by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(android.R.string.cancel), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss).padding(horizontal = 8.dp, vertical = 12.dp))
                    Text(stringResource(R.string.muninn_edit_clip), fontFamily = MuninnSerif, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary)
                            .clickable(enabled = !isText || text.isNotBlank()) { onSave(if (isText) text else null, on.toList()) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.muninn_save), color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold) }
                }
                if (isText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuninnLabel(stringResource(R.string.muninn_text_label))
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, minLines = 6, maxLines = 14,
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    )
                    Text(stringResource(R.string.muninn_edit_keeps), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MuninnLabel(stringResource(R.string.muninn_tags_tap))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        all.forEach { tag ->
                            TagPill(MuninnClips.tagLabel(ctx, tag), selected = tag in on, large = true) { if (tag in on) on.remove(tag) else on.add(tag) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newTag, onValueChange = { newTag = it.replace(ClipboardDao.TAG_SEPARATOR, "").take(24) },
                            placeholder = { Text(stringResource(R.string.muninn_tag_new)) }, singleLine = true,
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedBorderColor = Color.Transparent,
                            )
                        )
                        OutlineButton(stringResource(R.string.muninn_tag_add), height = 52.dp) {
                            val tag = newTag.trim()
                            if (tag.isNotEmpty()) {
                                if (tag !in all) all.add(tag)
                                if (tag !in on) on.add(tag)
                                newTag = ""
                            }
                        }
                    }
                }
            }
        }
    }
}

/** the cache behind a fingerprint or the screen lock */
@Composable
private fun LockedScreen(onClickBack: () -> Unit, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    fun ask() = MuninnCacheLock.ask(ctx, onUnlocked)
    LaunchedEffect(Unit) { ask() }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "ring")
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClickBack)
                Text(stringResource(R.string.muninn_cache_title), fontFamily = MuninnSerif, fontSize = 42.sp)
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
            ) {
                Box(
                    Modifier.size(92.dp + (14 * pulse).dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f * (1 - pulse))),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape).clickable { ask() },
                        contentAlignment = Alignment.Center
                    ) { Icon(painterResource(R.drawable.ic_muninn_lock), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp)) }
                }
                Text(stringResource(R.string.muninn_locked), fontFamily = MuninnSerif, fontSize = 32.sp, textAlign = TextAlign.Center)
                if (android.os.Build.VERSION.SDK_INT < 28)
                    Text(stringResource(R.string.muninn_lock_needs_android9), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Text(stringResource(R.string.muninn_locked_text), fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GoldButton(stringResource(R.string.muninn_unlock), Modifier.width(200.dp)) { ask() }
                }
            }
            Text(stringResource(R.string.muninn_keyboard_still_pastes), fontSize = 12.5.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp))
        }
    }
}

/** a picture clip (a screenshot, a copied image): a small preview, read once in the background */
@Composable
private fun ClipImage(clip: ClipboardHistoryEntry) {
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, clip.filename) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(ClipboardDao.clipFilesDir, clip.filename!!)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 600) sample *= 2
                android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (_: Exception) { null }
        }
    }
    bitmap?.let {
        androidx.compose.foundation.Image(it.asImageBitmap(), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)))
    }
}

/** the clip's text with every link in it coloured, wherever it sits (merged clips keep their links) */
fun withLinks(text: String, colour: Boolean, linkColor: Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        if (colour) MuninnClips.linkRanges(text).forEach {
            addStyle(androidx.compose.ui.text.SpanStyle(color = linkColor), it.first, it.last + 1)
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
}

@Preview
@Composable
private fun PreviewCache() {
    Theme(previewDark) { Surface { MuninnCacheScreen {} } }
}
