package com.example.auraplay.ui

import com.example.auraplay.ui.MotionIconButton as IconButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        requester.requestFocus()
        keyboard?.show()
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Songs, albums, artists") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            IconButton(onClick = { focus.clearFocus(); keyboard?.hide(); onClose() }) {
                Icon(Icons.Rounded.Close, "Close search")
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(requester)
    )
}
