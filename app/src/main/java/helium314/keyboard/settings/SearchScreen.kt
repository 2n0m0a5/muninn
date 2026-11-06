// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.clip
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
        title = { Text(title) },
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
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column {
                    TopAppBar(
                        title = title,
                        windowInsets = WindowInsets(0),
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
 * so a screen never greets you with thirty switches. Search (top right) still finds every setting.
 */
@Composable
private fun MuninnEssentialsAndMore(settings: List<Any?>, essentials: Set<String>) {
    val shown = settings.filterIsInstance<String>().filter { it in essentials }
    val rest = settings.filter { it != null && !(it is String && it in essentials) }
    val restCount = rest.count { it is String }
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    Column(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth()
            .clip(shape).background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 4.dp)
    ) {
        shown.forEach { SettingsActivity.settingsContainer[it]?.Preference() }
    }
    if (restCount == 0) return
    androidx.compose.foundation.layout.Row(
        Modifier.padding(horizontal = 14.dp, vertical = 14.dp).fillMaxWidth()
            .clip(shape)
            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { expanded = !expanded }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                stringResource(if (expanded) helium314.keyboard.latin.R.string.muninn_fewer_options else helium314.keyboard.latin.R.string.muninn_more_options),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
            )
            androidx.compose.material3.Text(
                stringResource(helium314.keyboard.latin.R.string.muninn_more_options_description, restCount),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Text(if (expanded) "−" else "+", style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
    }
    AnimatedVisibility(visible = expanded) {
        Column {
            rest.forEach {
                if (it is Int) PreferenceCategory(stringResource(it))
                else SettingsActivity.settingsContainer[it!!]?.Preference()
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 80.dp))
        }
    }
}
