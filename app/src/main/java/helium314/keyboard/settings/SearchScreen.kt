// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.BackButton
import helium314.keyboard.latin.utils.CloseIcon
import helium314.keyboard.latin.utils.SearchIcon
import helium314.keyboard.settings.preferences.PreferenceCategory

@Composable
fun SearchSettingsScreen(
    onClickBack: () -> Unit,
    title: String,
    settings: List<Any?>,
    /** Muninn: one plain sentence at the top, saying what this screen is for */
    intro: String? = null,
    /** Muninn: the few settings most people need. Everything else folds away under "More options". */
    essentials: Set<String>? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null // overrides settings if not null
) {
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(title, fontFamily = helium314.keyboard.latin.utils.MuninnSerif, fontSize = 34.sp, lineHeight = 36.sp) },
        content = {
            if (content != null) content()
            else {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                ) { innerPadding ->
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).then(Modifier.padding(innerPadding))
                    ) {
                        if (intro != null) MuninnScreenIntro(intro)
                        if (essentials != null) {
                            MuninnEssentialsAndMore(settings, essentials)
                            return@Column
                        }
                        settings.forEach {
                            if (it is Int) {
                                PreferenceCategory(stringResource(it))
                            } else {
                                // this only animates appearing prefs
                                // a solution would be using a list(visible to key)
                                AnimatedVisibility(visible = it != null) {
                                    if (it != null)
                                        SettingsActivity.settingsContainer[it]?.Preference()
                                }
                            }
                        }
                    }
                    // lazyColumn has janky scroll for a while (not sure why compose gets smoother after a while)
                    // maybe related to unnecessary recompositions? but even for just displaying text it's there
                    // didn't manage to improve things with @Immutable list wrapper and other lazy list hints
                    // so for now: just use "normal" Column
                    //  even though it takes up to ~50% longer to load it's much better UX
                    //  and the missing appear animations could be added
    //                LazyColumn {
    //                    items(prefs.filterNotNull(), key = { it }) {
    //                        Box(Modifier.animateItem()) {
    //                            if (it is Int)
    //                                PreferenceCategory(stringResource(it))
    //                            else
    //                                SettingsActivity.settingsContainer[it]!!.Preference()
    //                        }
    //                    }
    //                }
                }
            }
        },
        filteredItems = { SettingsActivity.settingsContainer.filter(it) },
        itemContent = { it.Preference() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T: Any?> SearchScreen(
    onClickBack: () -> Unit,
    title: @Composable () -> Unit,
    filteredItems: (String) -> List<T>,
    itemContent: @Composable (T) -> Unit,
    icon: @Composable (() -> Unit)? = null,
    menu: List<Pair<String, () -> Unit>>? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    // searchText and showSearch should have the same remember or rememberSaveable
    // saveable survives orientation changes and switching between screens, but shows the
    // keyboard in unexpected situations such as going back from another screen, which is rather annoying
    var searchText by remember { mutableStateOf(TextFieldValue()) }
    var showSearch by remember { mutableStateOf(false) }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
    { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {

            fun setShowSearch(value: Boolean) {
                showSearch = value
                if (!value) searchText = TextFieldValue()
            }
            BackHandler {
                if (showSearch || searchText.text.isNotEmpty()) setShowSearch(false)
                else onClickBack()
            }
            Surface(
                color = MaterialTheme.colorScheme.surface, // Muninn: the header sits on the same raven black as the page
            ) {
                Column {
                    TopAppBar(
                        // Muninn: every screen title in the serif, like the mockups
                        title = { CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(
                            fontFamily = helium314.keyboard.latin.utils.MuninnSerif, fontSize = 34.sp, lineHeight = 36.sp)) { title() } },
                        windowInsets = WindowInsets(0),
                        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        navigationIcon = {
                            BackButton {
                                if (showSearch) setShowSearch(false)
                                else onClickBack()
                            }
                        },
                        actions = {
                            if (icon == null)
                                IconButton(onClick = { setShowSearch(!showSearch) }) { SearchIcon() }
                            else
                                icon()
                            if (menu != null)
                                Box {
                                    var showMenu by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = { showMenu = true }
                                    ) { Icon(painterResource(R.drawable.ic_arrow_left), "menu", Modifier.rotate(-90f)) }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        menu.forEach {
                                            DropdownMenuItem(
                                                text = { Text(it.first) },
                                                onClick = { showMenu = false; it.second() }
                                            )
                                        }
                                    }
                                }
                        },
                    )
                    ExpandableSearchField(
                        expanded = showSearch,
                        onDismiss = { setShowSearch(false) },
                        search = searchText,
                        onSearchChange = { searchText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                if (searchText.text.isBlank() && content != null) {
                    Column {
                        content()
                    }
                } else {
                    val items = filteredItems(searchText.text)
                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                    ) { innerPadding ->
                        LazyColumn(contentPadding = innerPadding) {
                            items(items) {
                                itemContent(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

// from StreetComplete
/** Expandable text field that can be dismissed and requests focus when it is expanded */
@Composable
fun ExpandableSearchField(
    expanded: Boolean,
    onDismiss: () -> Unit,
    search: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) focusRequester.requestFocus()
    }
    AnimatedVisibility(visible = expanded, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = modifier.focusRequester(focusRequester),
            leadingIcon = { SearchIcon() },
            trailingIcon = { IconButton(onClick = {
                if (search.text.isBlank()) onDismiss()
                else onSearchChange(TextFieldValue())
            }) { CloseIcon(android.R.string.cancel) } },
            singleLine = true,
            colors = colors,
            textStyle = contentTextDirectionStyle,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
    }
}

/** Muninn: the sentence at the top of a settings screen */
@Composable
private fun MuninnScreenIntro(text: String) {
    androidx.compose.material3.Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
    )
}

/**
 * Muninn: progressive disclosure. The essentials sit in one card; the rest waits behind "More options",
 * grouped into small cards, so a screen never greets you with thirty switches. Search (top right) still finds every setting.
 * A gold dot marks what you changed; "Reset this screen" puts this screen back the way it came.
 */
@Composable
private fun MuninnEssentialsAndMore(settings: List<Any?>, essentials: Set<String>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = ctx.prefs()
    val changes = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    @Suppress("UNUSED_VARIABLE") val trigger = changes?.value // read so the dots update after any change
    val keys = settings.filterIsInstance<String>()
    fun changed(key: String): Boolean = prefs.contains(key) ||
            prefs.all.keys.any { it.startsWith(key + "_") && it !in keys }
    val shown = keys.filter { it in essentials }
    val rest = settings.filter { it != null && !(it is String && it in essentials) }
    val restCount = rest.count { it is String }
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)

    @Composable
    fun Item(key: String) {
        Box {
            SettingsActivity.settingsContainer[key]?.Preference()
            if (changed(key)) Box(
                Modifier.padding(start = 6.dp).size(7.dp).align(androidx.compose.ui.Alignment.CenterStart)
                    .clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary)
            )
        }
    }

    Column(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        shown.forEach { Item(it) }
        if (restCount > 0) MoreOptionsRow(
            stringResource(R.string.muninn_more_options_description, restCount), expanded) { expanded = !expanded }
    }
    AnimatedVisibility(visible = expanded) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            // a card per group: a category title starts a new one
            val groups = mutableListOf<Pair<Int?, MutableList<String>>>()
            rest.forEach {
                if (it is Int) groups.add(it to mutableListOf())
                else if (it is String) {
                    if (groups.isEmpty()) groups.add(null to mutableListOf())
                    groups.last().second.add(it)
                }
            }
            groups.filter { it.second.isNotEmpty() }.forEach { (title, items) ->
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    if (title != null) MuninnLabel(stringResource(title))
                    Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)) {
                        items.forEach { Item(it) }
                    }
                }
            }
        }
    }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 110.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary))
        Text(stringResource(R.string.muninn_gold_dot_changed), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp).weight(1f))
        OutlineButton(stringResource(R.string.muninn_reset_screen), height = 40.dp) { askReset = true }
    }
    if (askReset) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askReset = false },
        title = { Text(stringResource(R.string.muninn_reset_screen)) },
        text = { Text(stringResource(R.string.muninn_reset_screen_text)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                askReset = false
                prefs.edit().apply {
                    keys.forEach { key ->
                        remove(key)
                        prefs.all.keys.filter { it.startsWith(key + "_") && it !in keys }.forEach { remove(it) }
                    }
                }.apply()
                helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }) { Text(stringResource(R.string.muninn_reset)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askReset = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
}
