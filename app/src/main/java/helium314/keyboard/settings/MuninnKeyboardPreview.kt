// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.getActivity
import kotlinx.coroutines.delay

/**
 * Muninn: a round button in the corner of every settings screen.
 * Tap it to open a small text field with the keyboard, so changes made on the screen can be seen right away.
 * Tap again to hide the keyboard. While it is open, the keyboard reloads shortly after any setting changes.
 */
@Composable
fun KeyboardPreviewToggle() {
    var open by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val softKeyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val activity = LocalContext.current.getActivity() as? SettingsActivity
    val prefChanges = activity?.prefChanged?.collectAsState()

    LaunchedEffect(open) {
        if (open) {
            focusRequester.requestFocus()
            softKeyboard?.show()
        } else {
            hadFocus = false
            focusManager.clearFocus()
            softKeyboard?.hide()
        }
    }
    // reload the keyboard after settings change, so the preview shows them (waits a moment, sliders change often)
    LaunchedEffect(prefChanges?.value) {
        if (!open) return@LaunchedEffect
        delay(350)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (open) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.muninn_preview_hint)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) hadFocus = true
                            else if (hadFocus) open = false // e.g. a setting was tapped: the keyboard closed
                        },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FloatingActionButton(
                    onClick = { open = !open },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        painterResource(if (open) R.drawable.ic_muninn_keyboard_hide else R.drawable.ic_muninn_keyboard),
                        contentDescription = stringResource(if (open) R.string.muninn_preview_hide else R.string.muninn_preview_show),
                    )
                }
            }
        }
    }
}
