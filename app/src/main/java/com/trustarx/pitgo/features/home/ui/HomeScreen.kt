package com.trustarx.pitgo.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.model.Profile
import com.trustarx.pitgo.core.catalog.model.HomeService
import com.trustarx.pitgo.core.design.theme.PitGoElevation
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.ui.PitGoBottomNavigation
import com.trustarx.pitgo.core.ui.PitGoBottomNavigationItems
import com.trustarx.pitgo.core.vehicle.model.Vehicle
import com.trustarx.pitgo.features.clientaddress.model.ClientAddress
import com.trustarx.pitgo.features.home.model.HomeDiagnosticPrompt
import com.trustarx.pitgo.features.home.model.HomeUiState
import com.trustarx.pitgo.features.home.viewmodel.HomeViewModel

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onServiceClick: (HomeService) -> Unit,
    onOpenGarage: () -> Unit,
    onOpenAddress: () -> Unit,
    onBottomItemClick: (BottomItemId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onDiagnosticClick = {},
        onServiceClick = onServiceClick,
        onOpenGarage = onOpenGarage,
        onOpenAddress = onOpenAddress,
        onBottomItemClick = onBottomItemClick,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onDiagnosticClick: () -> Unit,
    onServiceClick: (HomeService) -> Unit,
    onOpenGarage: () -> Unit,
    onOpenAddress: () -> Unit,
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
                selectedItem = BottomItemId.HOME,
                onItemClick = onBottomItemClick,
            )
        },
    ) { padding ->
        when (state) {
            HomeUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            HomeUiState.Failure -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(PitGoSpacing.Large),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.home_failure)) }
            is HomeUiState.Content -> HomeGrid(
                padding = padding,
                profile = state.profile,
                vehicle = state.vehicle,
                services = state.services,
                address = state.address,
                onDiagnosticClick = onDiagnosticClick,
                onServiceClick = onServiceClick,
                onOpenGarage = onOpenGarage,
                onOpenAddress = onOpenAddress,
            )
            is HomeUiState.NoVehicle -> HomeGrid(
                padding = padding,
                profile = state.profile,
                vehicle = null,
                services = state.services,
                address = state.address,
                onDiagnosticClick = onDiagnosticClick,
                onServiceClick = onServiceClick,
                onOpenGarage = onOpenGarage,
                onOpenAddress = onOpenAddress,
            )
        }
    }
}

@Composable
private fun HomeGrid(
    padding: PaddingValues,
    profile: Profile,
    vehicle: Vehicle?,
    services: List<HomeService>,
    address: ClientAddress?,
    onDiagnosticClick: () -> Unit,
    onServiceClick: (HomeService) -> Unit,
    onOpenGarage: () -> Unit,
    onOpenAddress: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = PitGoSpacing.Large,
            top = padding.calculateTopPadding() + PitGoSpacing.Medium,
            end = PitGoSpacing.Large,
            bottom = padding.calculateBottomPadding() + PitGoSpacing.Large,
        ),
        horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { HomeHeader(profile) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (vehicle == null) NoVehicleCard(onOpenGarage) else VehicleCard(vehicle)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { AddressRow(address, onOpenAddress) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.home_services_question),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = PitGoSpacing.Small).semantics { heading() },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                onClick = onDiagnosticClick,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(PitGoSpacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconContainer(HomeDiagnosticPrompt.iconRes)
                    Column {
                        Text(stringResource(HomeDiagnosticPrompt.titleRes), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(HomeDiagnosticPrompt.descriptionRes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(services, key = { it.id }) { service ->
            ServiceCard(service = service, onClick = { onServiceClick(service) })
        }
        if (services.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.home_services_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeHeader(profile: Profile) {
    val avatarDescription = stringResource(R.string.home_avatar_description_dynamic, profile.displayName)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(stringResource(R.string.home_greeting), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        Box(
            modifier = Modifier
                .size(PitGoSize.Avatar)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .semantics { contentDescription = avatarDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(profile.initials, color = MaterialTheme.colorScheme.onPrimary)
            if (profile.hasValidAvatar) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun VehicleCard(vehicle: Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = PitGoElevation.Card),
    ) {
        Row(
            modifier = Modifier.padding(PitGoSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconContainer(R.drawable.ic_home_vehicle)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_selected_vehicle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(vehicle.displayName, style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(vehicle.modelYear?.toString(), vehicle.plate).joinToString(" · ")
                if (details.isNotBlank()) Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoVehicleCard(onOpenGarage: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(PitGoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            Text(stringResource(R.string.home_no_vehicle_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_no_vehicle_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpenGarage, modifier = Modifier.sizeIn(minHeight = PitGoSize.MinimumTouchTarget)) {
                Text(stringResource(R.string.home_open_garage))
            }
        }
    }
}

@Composable
private fun AddressRow(address: ClientAddress?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = PitGoSize.MinimumTouchTarget)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_home_location),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(PitGoSpacing.Small))
        Text(
            address?.let { stringResource(R.string.home_address_summary, it.street, it.number, it.city) }
                ?: stringResource(R.string.home_address_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ServiceCard(service: HomeService, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().sizeIn(minHeight = PitGoSize.MinimumTouchTarget),
        elevation = CardDefaults.cardElevation(defaultElevation = PitGoElevation.Card),
    ) {
        Column(modifier = Modifier.padding(PitGoSpacing.Medium)) {
            IconContainer(R.drawable.ic_service_mechanic)
            Spacer(Modifier.height(PitGoSpacing.Medium))
            Text(service.name, style = MaterialTheme.typography.titleMedium)
            Text(
                service.minimumPrice?.let { stringResource(R.string.service_map_price, it) }
                    ?: service.categoryName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconContainer(iconRes: Int) {
    Box(
        modifier = Modifier
            .size(PitGoSize.ServiceIconContainer)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(PitGoSize.ServiceIcon),
        )
    }
}
