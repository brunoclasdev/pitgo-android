package com.trustarx.pitgo.features.providerdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUiState
import com.trustarx.pitgo.features.providerdashboard.viewmodel.ProviderDashboardViewModel

@Composable
fun ProviderDashboardRoute(
    viewModel: ProviderDashboardViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProviderDashboardScreen(
        state, onLogout, viewModel::setAvailability, viewModel::setMode,
    )
}

@Composable
private fun ProviderDashboardScreen(
    state: ProviderDashboardUiState,
    onLogout: () -> Unit,
    onAvailabilityChanged: (Boolean) -> Unit,
    onModeChanged: (ProviderLocationMode) -> Unit,
) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(PitGoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            Text(
                stringResource(R.string.provider_dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            when (state) {
                ProviderDashboardUiState.Loading -> Text(stringResource(R.string.session_verifying))
                ProviderDashboardUiState.Failure -> Text(stringResource(R.string.signup_failure_service))
                is ProviderDashboardUiState.Content -> Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(PitGoSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                    ) {
                        Text(state.provider.name, style = MaterialTheme.typography.titleLarge)
                        Text(state.provider.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(
                                R.string.provider_dashboard_mode,
                                state.provider.locationMode?.name ?: "—",
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.provider_dashboard_available))
                            Switch(
                                checked = state.provider.available,
                                onCheckedChange = onAvailabilityChanged,
                                enabled = !state.updating,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                            Button(
                                onClick = { onModeChanged(ProviderLocationMode.FIXED) },
                                enabled = !state.updating,
                            ) { Text(stringResource(R.string.provider_mode_fixed)) }
                            Button(
                                onClick = { onModeChanged(ProviderLocationMode.MOBILE) },
                                enabled = !state.updating,
                            ) { Text(stringResource(R.string.provider_mode_mobile)) }
                        }
                    }
                }
            }
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
            ) { Text(stringResource(R.string.action_logout)) }
        }
    }
}
