package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.utils.android.PlayRetroButton


@Composable
fun CustomNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.game_context_menu_custom_name_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            PlayRetroButton(
                text = stringResource(id = R.string.ok),
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text.trim())
                    }
                },
            )
        },
        dismissButton = {
            PlayRetroButton(text = stringResource(id = R.string.cancel), onClick = onDismiss)
        },
    )
}
