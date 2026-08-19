package com.trustarx.pitgo.features.garage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoElevation
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.ui.PitGoBottomNavigation
import com.trustarx.pitgo.core.ui.PitGoBottomNavigationItems
import com.trustarx.pitgo.core.vehicle.model.Vehicle
import com.trustarx.pitgo.features.garage.model.GarageFailureReason
import com.trustarx.pitgo.features.garage.model.GarageUiState
import com.trustarx.pitgo.features.garage.viewmodel.GarageViewModel

@Composable
fun GarageRoute(
    viewModel: GarageViewModel,
    onAddVehicle: () -> Unit,
    onBottomItemClick: (BottomItemId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    GarageScreen(
        state = state,
        onRetry = viewModel::refresh,
        onAddVehicle = onAddVehicle,
        onSetPrimary = viewModel::setPrimary,
        onBottomItemClick = onBottomItemClick,
    )
}

@Composable
fun GarageScreen(
    state: GarageUiState,
    onRetry: () -> Unit,
    onAddVehicle: () -> Unit,
    onSetPrimary: (String) -> Unit,
    onBottomItemClick: (BottomItemId) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        bottomBar = {
            PitGoBottomNavigation(
                items = PitGoBottomNavigationItems,
                selectedItem = BottomItemId.GARAGE,
                onItemClick = onBottomItemClick,
            )
        },
    ) { padding ->
        when (state) {
            GarageUiState.Loading -> CenteredGarageState(padding) { CircularProgressIndicator() }
            GarageUiState.Empty -> CenteredGarageState(padding) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_vehicle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(PitGoSize.ServiceIconContainer),
                    )
                    Text(
                        stringResource(R.string.garage_empty_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.garage_empty_message),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onAddVehicle,
                        modifier = Modifier.fillMaxWidth().heightIn(
                            min = PitGoSize.MinimumTouchTarget,
                        ),
                    ) { Text(stringResource(R.string.garage_add_vehicle)) }
                }
            }
            is GarageUiState.Failure -> CenteredGarageState(padding) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
                ) {
                    Text(
                        stringResource(garageFailureMessage(state.reason)),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = PitGoSize.MinimumTouchTarget),
                    ) { Text(stringResource(R.string.action_retry)) }
                }
            }
            is GarageUiState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize().selectableGroup(),
                contentPadding = PaddingValues(
                    start = PitGoSpacing.Large,
                    top = padding.calculateTopPadding() + PitGoSpacing.Medium,
                    end = PitGoSpacing.Large,
                    bottom = padding.calculateBottomPadding() + PitGoSpacing.ExtraLarge,
                ),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.garage_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                item {
                    Button(
                        onClick = onAddVehicle,
                        modifier = Modifier.fillMaxWidth().heightIn(
                            min = PitGoSize.MinimumTouchTarget,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_garage_add),
                            contentDescription = null,
                        )
                        Text(stringResource(R.string.garage_add_vehicle))
                    }
                }
                items(state.vehicles, key = Vehicle::id) { vehicle ->
                    VehicleGarageCard(
                        vehicle = vehicle,
                        changing = state.changingPrimaryVehicleId == vehicle.id,
                        enabled = state.changingPrimaryVehicleId == null,
                        onSelect = { onSetPrimary(vehicle.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredGarageState(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(PitGoSpacing.Large),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun VehicleGarageCard(
    vehicle: Vehicle,
    changing: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = vehicle.isPrimary,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        border = BorderStroke(
            width = if (vehicle.isPrimary) 2.dp else 1.dp,
            color = if (vehicle.isPrimary) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = PitGoElevation.Card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PitGoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_vehicle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(vehicle.displayName, style = MaterialTheme.typography.titleMedium)
                listOfNotNull(vehicle.modelYear?.toString(), vehicle.plate)
                    .joinToString(" · ")
                    .takeIf(String::isNotBlank)
                    ?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (vehicle.isPrimary) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_vehicle_primary),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(PitGoSize.FeatureIcon),
                        )
                        Text(
                            stringResource(R.string.garage_primary),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (changing) CircularProgressIndicator(modifier = Modifier.size(PitGoSize.FeatureIcon))
        }
    }
}

private fun garageFailureMessage(reason: GarageFailureReason): Int = when (reason) {
    GarageFailureReason.OFFLINE -> R.string.garage_failure_offline
    GarageFailureReason.TIMEOUT -> R.string.garage_failure_timeout
    GarageFailureReason.SESSION_EXPIRED -> R.string.garage_failure_session
    GarageFailureReason.SERVICE -> R.string.garage_failure_service
    GarageFailureReason.UNKNOWN -> R.string.garage_failure_unknown
}
