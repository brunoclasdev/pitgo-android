package com.trustarx.pitgo.features.providerdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.layout.Row
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUiState
import com.trustarx.pitgo.features.providerdashboard.model.ProviderAvailabilityIssue
import com.trustarx.pitgo.features.providerdashboard.viewmodel.ProviderDashboardViewModel

@Composable
fun ProviderDashboardRoute(
    viewModel: ProviderDashboardViewModel,
    onLogout: () -> Unit,
    onOpenIncomingRequest: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Confirmed live (see specs/014 investigation): Android's Doze/App Standby kills
    // ProviderLocationTrackingService after a few minutes idle unless the app is exempted from
    // battery optimization — it's a proper foreground service, but that alone isn't enough on
    // this OS version/OEM. Re-checked on every onStart so returning from the system settings
    // screen (or the app resuming after being backgrounded) reflects the current state.
    var ignoringBatteryOptimizations by remember { mutableStateOf(true) }
    LifecycleStartEffect(Unit) {
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
        onStopOrDispose {}
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context) }
    var pendingMode by remember { mutableStateOf(ProviderLocationMode.MOBILE) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Re-run the preflight only after Android has resolved the permission request. When it is
        // denied, isReady() maps the result to the actionable notifications-required message.
        viewModel.enableTrackedAvailability(pendingMode)
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (pendingMode == ProviderLocationMode.PITTEC && Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.enableTrackedAvailability(pendingMode)
            }
        } else viewModel.onPrecisePermissionDenied()
    }
    ProviderDashboardScreen(
        state, onLogout,
        onAvailabilityChanged = { available ->
            val content = state as? ProviderDashboardUiState.Content
            val mode = content?.provider?.locationMode
            if (available && mode in TRACKED_MODES) {
                pendingMode = requireNotNull(mode)
                locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            } else viewModel.setAvailability(available)
        },
        onModeChanged = viewModel::setMode,
        onOpenIncomingRequest = onOpenIncomingRequest,
        onOpenLocationSettings = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            viewModel.clearAvailabilityIssue()
        },
        onOpenNotificationSettings = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
            }
            context.startActivity(intent)
            viewModel.clearAvailabilityIssue()
        },
        showBatteryOptimizationBanner = !ignoringBatteryOptimizations,
        onRequestBatteryExemption = {
            batteryOptimizationLauncher.launch(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        },
    )
}

private val TRACKED_MODES = setOf(ProviderLocationMode.MOBILE, ProviderLocationMode.PITTEC)

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
        ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun ProviderDashboardScreen(
    state: ProviderDashboardUiState,
    onLogout: () -> Unit,
    onAvailabilityChanged: (Boolean) -> Unit,
    onModeChanged: (ProviderLocationMode) -> Unit,
    onOpenIncomingRequest: (String) -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    showBatteryOptimizationBanner: Boolean,
    onRequestBatteryExemption: () -> Unit,
) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(PitGoSpacing.Large)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            Text(
                stringResource(R.string.provider_dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            val content = state as? ProviderDashboardUiState.Content
            if (showBatteryOptimizationBanner && content?.provider?.locationMode in TRACKED_MODES) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(PitGoSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                    ) {
                        Text(
                            stringResource(R.string.provider_battery_optimization_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(stringResource(R.string.provider_battery_optimization_message))
                        Button(onClick = onRequestBatteryExemption) {
                            Text(stringResource(R.string.provider_battery_optimization_action))
                        }
                    }
                }
            }
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
                        state.provider.pendingLocationMode?.let {
                            Text(
                                stringResource(R.string.provider_mode_pending),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val isAvailable = state.provider.available
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = PitGoSize.PrimaryActionMinHeight)
                                .toggleable(
                                    value = isAvailable,
                                    enabled = !state.updating,
                                    role = Role.Switch,
                                    onValueChange = onAvailabilityChanged,
                                ),
                            shape = MaterialTheme.shapes.medium,
                            color = if (isAvailable) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                width = 2.dp,
                                color = if (isAvailable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(PitGoSpacing.Medium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall),
                                ) {
                                    Text(
                                        text = stringResource(R.string.provider_dashboard_available),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = stringResource(
                                            if (isAvailable) R.string.provider_dashboard_available_on
                                            else R.string.provider_dashboard_available_off,
                                        ),
                                        color = if (isAvailable) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Switch(
                                    checked = isAvailable,
                                    onCheckedChange = null,
                                    enabled = !state.updating,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
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
                            Button(
                                onClick = { onModeChanged(ProviderLocationMode.PITTEC) },
                                enabled = !state.updating,
                            ) { Text(stringResource(R.string.provider_mode_pittec)) }
                        }
                        Text(
                            when (state.tracking) {
                                com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState.Stopped -> stringResource(R.string.tracking_stopped)
                                com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState.Starting -> stringResource(R.string.tracking_starting)
                                is com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState.Publishing -> stringResource(R.string.tracking_live)
                                is com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState.Degraded -> stringResource(R.string.tracking_degraded)
                                com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState.Stopping -> stringResource(R.string.tracking_stopping)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.availabilityIssue?.let { issue ->
                            Text(
                                text = stringResource(
                                    when (issue) {
                                        ProviderAvailabilityIssue.PRECISE_PERMISSION_REQUIRED -> R.string.provider_availability_precise_required
                                        ProviderAvailabilityIssue.LOCATION_SERVICES_DISABLED -> R.string.provider_availability_location_disabled
                                        ProviderAvailabilityIssue.PRECISE_FIX_UNAVAILABLE -> R.string.provider_availability_fix_unavailable
                                        ProviderAvailabilityIssue.NOTIFICATIONS_REQUIRED -> R.string.pittec_notifications_required
                                        ProviderAvailabilityIssue.UPDATE_FAILED -> R.string.provider_availability_update_failed
                                    },
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (issue == ProviderAvailabilityIssue.LOCATION_SERVICES_DISABLED) {
                                Button(onClick = onOpenLocationSettings) {
                                    Text(stringResource(R.string.provider_availability_open_location_settings))
                                }
                            }
                            if (issue == ProviderAvailabilityIssue.NOTIFICATIONS_REQUIRED) {
                                Button(onClick = onOpenNotificationSettings) {
                                    Text(stringResource(R.string.pittec_open_notification_settings))
                                }
                            }
                        }
                    }
                }
            }
            (state as? ProviderDashboardUiState.Content)?.incomingAppointmentId?.let { appointmentId ->
                Button(
                    onClick = { onOpenIncomingRequest(appointmentId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
                ) { Text(stringResource(R.string.mobile_dispatch_provider_title)) }
            }
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
            ) { Text(stringResource(R.string.action_logout)) }
        }
    }
}
