package com.trustarx.pitgo.features.guincho.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.trustarx.pitgo.R

@Composable
fun GuinchoInspectionAlert(opening: Boolean, error: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!opening) onDismiss() },
        title = { Text(stringResource(R.string.inspection_alert_title)) },
        text = {
            Column {
                Text(stringResource(R.string.inspection_alert_message))
                if (opening) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error) Text(stringResource(R.string.inspection_alert_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen, enabled = !opening) { Text(stringResource(R.string.inspection_alert_review)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !opening) { Text(stringResource(R.string.inspection_alert_dismiss)) }
        },
    )
}
