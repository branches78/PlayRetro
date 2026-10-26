package com.swordfish.lemuroid.app.mobile.feature.settings.smb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.utils.android.PlayRetroButton
import com.swordfish.lemuroid.app.utils.android.focusBorder

/**
 * Network share (SMB) with the games library: name, server, path from the share name and, optionally, user and
 * password. "Test" lists the folder; "Save" makes it the source of the library (mobile and TV). On TV the title is
 * shown above the form (there is no top bar) and the keyboard only opens when a field is chosen with OK.
 */
@Composable
fun SmbSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SmbSettingsViewModel,
    isTV: Boolean = false,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val config = state.config
    var showPassword by remember { mutableStateOf(false) }
    // Explicit color: on TV the content is not inside a Surface, so the default text color is black.
    val textColor = MaterialTheme.colorScheme.onSurface
    val testing = state.testResult == SmbSettingsViewModel.TestResult.Testing

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isTV) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.smb_title),
                style = MaterialTheme.typography.titleLarge,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
        SmbTextField(
            value = config.displayName,
            onValueChange = { viewModel.update(config.copy(displayName = it)) },
            label = stringResource(R.string.smb_display_name),
            isTV = isTV,
        )
        SmbTextField(
            value = config.server,
            onValueChange = { viewModel.update(config.copy(server = it)) },
            label = stringResource(R.string.smb_server),
            isTV = isTV,
            keyboardType = KeyboardType.Uri,
        )
        SmbTextField(
            value = config.path,
            onValueChange = { viewModel.update(config.copy(path = it)) },
            label = stringResource(R.string.smb_path),
            supportingText = stringResource(R.string.smb_path_hint),
            isTV = isTV,
            keyboardType = KeyboardType.Uri,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusBorder()
                    .clickable { viewModel.update(config.copy(useAuthentication = !config.useAuthentication)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = config.useAuthentication, onCheckedChange = null)
            Text(text = stringResource(R.string.smb_use_authentication), color = textColor)
        }
        if (config.useAuthentication) {
            SmbTextField(
                value = config.user,
                onValueChange = { viewModel.update(config.copy(user = it)) },
                label = stringResource(R.string.smb_user),
                isTV = isTV,
            )
            SmbTextField(
                value = config.password,
                onValueChange = { viewModel.update(config.copy(password = it)) },
                label = stringResource(R.string.smb_password),
                isTV = isTV,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.smb_show_password),
                        )
                    }
                },
            )
        }

        TestResultText(state.testResult)

        // Test, Save and Cancel, centered. Test stays enabled while testing: a disabled button loses the focus, which
        // on TV jumped to the search button; a second press meanwhile does nothing.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayRetroButton(
                text = stringResource(R.string.smb_test_connection),
                enabled = config.isValid(),
                onClick = { if (!testing) viewModel.testConnection() },
            )
            PlayRetroButton(
                text = stringResource(R.string.smb_save),
                enabled = config.isValid(),
                onClick = {
                    viewModel.save()
                    onClose()
                },
            )
            PlayRetroButton(text = stringResource(R.string.cancel), onClick = onClose)
        }
    }
}

/**
 * Text field of the form. The keyboard's action key is "next" and moves to the following field. On TV the field is
 * only edited after pressing OK on it, so moving through the form with the remote doesn't open the keyboard each time.
 */
@Composable
private fun SmbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isTV: Boolean,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(!isTV) }

    LaunchedEffect(editing) {
        if (isTV && editing) keyboard?.show()
    }

    OutlinedTextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { if (isTV && !it.isFocused) editing = false }
                .onPreviewKeyEvent { event ->
                    val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (isTV && !editing && isOk) {
                        if (event.type == KeyEventType.KeyUp) editing = true
                        true
                    } else {
                        false
                    }
                },
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        readOnly = !editing,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions =
            KeyboardActions(
                onNext = {
                    if (isTV) {
                        editing = false
                        keyboard?.hide()
                    }
                    focusManager.moveFocus(FocusDirection.Down)
                },
            ),
        singleLine = true,
    )
}

@Composable
private fun TestResultText(result: SmbSettingsViewModel.TestResult?) {
    val text =
        when (result) {
            null -> return
            SmbSettingsViewModel.TestResult.Testing -> stringResource(R.string.smb_testing)
            is SmbSettingsViewModel.TestResult.Success -> stringResource(R.string.smb_test_success, result.entries)
            is SmbSettingsViewModel.TestResult.Failure -> stringResource(R.string.smb_test_error, result.message)
        }
    val color =
        if (result is SmbSettingsViewModel.TestResult.Failure) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}
