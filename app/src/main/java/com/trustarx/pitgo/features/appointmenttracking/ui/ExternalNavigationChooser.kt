package com.trustarx.pitgo.features.appointmenttracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.navigation.ExternalNavigator
import com.trustarx.pitgo.features.appointmenttracking.model.ExternalNavigationFailure
import com.trustarx.pitgo.features.appointmenttracking.model.ExternalNavigationState

@Composable
internal fun ExternalNavigationChooser(
    state: ExternalNavigationState,
    onChoose: (ExternalNavigator) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = when (state) {
        is ExternalNavigationState.Choosing -> state.options
        is ExternalNavigationState.Preparing -> state.options
        is ExternalNavigationState.Failure -> state.options
        else -> return
    }
    val preparing = state is ExternalNavigationState.Preparing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_navigation_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            ) {
                if (state is ExternalNavigationState.Failure) {
                    Text(
                        stringResource(when (state.reason) {
                            ExternalNavigationFailure.DESTINATION_UNAVAILABLE -> R.string.external_navigation_no_destination
                            ExternalNavigationFailure.CONTEXT_CHANGED -> R.string.external_navigation_context_changed
                            ExternalNavigationFailure.CONTEXT_REFRESH_FAILED -> R.string.external_navigation_refresh_failed
                            ExternalNavigationFailure.APP_UNAVAILABLE -> R.string.external_navigation_app_unavailable
                        }),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else if (options.none { it.available }) {
                    Text(stringResource(R.string.external_navigation_no_apps))
                }
                if (preparing) {
                    Text(stringResource(R.string.external_navigation_preparing))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                options.forEach { option ->
                    val name = stringResource(when (option.navigator) {
                        ExternalNavigator.WAZE -> R.string.external_navigation_waze
                        ExternalNavigator.GOOGLE_MAPS -> R.string.external_navigation_google_maps
                    })
                    OutlinedButton(
                        onClick = { onChoose(option.navigator) },
                        enabled = option.available && !preparing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (option.available) name else stringResource(R.string.external_navigation_unavailable_option, name))
                    }
                }
            }
        },
        confirmButton = {
            if (state is ExternalNavigationState.Failure || options.none { it.available }) {
                TextButton(onClick = onRetry, enabled = !preparing) {
                    Text(stringResource(R.string.external_navigation_retry))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.external_navigation_cancel)) }
        },
    )
}
