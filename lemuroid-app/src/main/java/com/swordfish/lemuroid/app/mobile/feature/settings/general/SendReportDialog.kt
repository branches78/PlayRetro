package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.report.DiagnosticReport
import com.swordfish.lemuroid.app.utils.android.PlayRetroButton
import kotlinx.coroutines.launch

/**
 * Shows the diagnostic report before it leaves the device. On mobile "Send" opens the Android app chooser; on TV
 * ([isTV]), where there is rarely an app to share to, "Export Log" opens the file manager to pick a folder, like the
 * folder of the games.
 */
@Composable
fun SendReportDialog(
    isTV: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        report = DiagnosticReport.build(context)
    }

    val showResult = { folder: String? ->
        val message =
            if (folder != null) {
                context.getString(R.string.send_report_exported, folder)
            } else {
                context.getString(R.string.send_report_export_error)
            }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            val text = report
            if (treeUri != null && text != null) {
                scope.launch {
                    showResult(DiagnosticReport.exportToFolder(context, treeUri, text))
                    onDismiss()
                }
            }
        }

    val onConfirm: () -> Unit = onConfirm@{
        val text = report ?: return@onConfirm
        if (isTV) {
            try {
                folderPicker.launch(null)
            } catch (e: ActivityNotFoundException) {
                // No file manager to pick a folder: the Download folder of the device.
                scope.launch {
                    showResult(DiagnosticReport.exportToDownloads(context, text))
                    onDismiss()
                }
            }
        } else {
            if (DiagnosticReport.share(context, text)) {
                onDismiss()
            } else {
                Toast.makeText(context, R.string.send_report_no_app, Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        title = { Text(stringResource(id = R.string.settings_title_send_report)) },
        text = {
            Column {
                Text(stringResource(id = R.string.send_report_dialog_message))
                ReportPreview(report = report, modifier = Modifier.padding(top = 16.dp))
            }
        },
        onDismissRequest = onDismiss,
        // Both buttons centered, "Send" first.
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                PlayRetroButton(
                    text = stringResource(id = if (isTV) R.string.send_report_export else R.string.send_report_send),
                    enabled = report != null,
                    onClick = onConfirm,
                )
                PlayRetroButton(text = stringResource(id = R.string.cancel), onClick = onDismiss)
            }
        },
    )
}

/** The text of the report, scrollable with touch and, when focused, with the up and down keys of the remote. */
@Composable
private fun ReportPreview(
    report: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val scrollState = rememberLazyListState()
    val lines = remember(report) { report?.lines().orEmpty() }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                .border(1.dp, if (focused) Color.White else Color.Transparent, shape)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val step =
                        when (event.key) {
                            Key.DirectionDown -> SCROLL_STEP
                            Key.DirectionUp -> -SCROLL_STEP
                            else -> return@onPreviewKeyEvent false
                        }
                    // At the edges the key moves the focus out (e.g. down to the buttons).
                    val canScroll = if (step > 0) scrollState.canScrollForward else scrollState.canScrollBackward
                    if (canScroll) scope.launch { scrollState.animateScrollBy(step.toFloat()) }
                    canScroll
                }
                .focusable(enabled = report != null, interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        if (report == null) {
            CircularProgressIndicator()
        } else {
            // One item per line: a single text with the whole log is slow to lay out.
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
        }
    }
}

private const val SCROLL_STEP = 300
