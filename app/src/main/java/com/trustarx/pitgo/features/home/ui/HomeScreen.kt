package com.trustarx.pitgo.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.common.model.DemoService
import com.trustarx.pitgo.core.common.model.DemoServiceId
import com.trustarx.pitgo.core.common.model.DemoVehicle
import com.trustarx.pitgo.core.design.theme.PitGoElevation
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.ui.PitGoBottomNavigation
import com.trustarx.pitgo.features.home.model.DemoProfile
import com.trustarx.pitgo.features.home.model.DiagnosticPrompt
import com.trustarx.pitgo.features.home.viewmodel.HomeUiState
import com.trustarx.pitgo.features.home.viewmodel.HomeViewModel

@Composable
fun HomeRoute(
    onServiceClick: (DemoServiceId) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onDiagnosticClick = {},
        onServiceClick = onServiceClick,
        onBottomItemClick = {},
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onDiagnosticClick: () -> Unit,
    onServiceClick: (DemoServiceId) -> Unit,
    onBottomItemClick: (BottomItemId) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        bottomBar = {
            PitGoBottomNavigation(
                items = uiState.bottomNavigationItems,
                selectedItem = uiState.selectedBottomItem,
                onItemClick = onBottomItemClick,
            )
        },
    ) { innerPadding ->
        val startInset = if (layoutDirection == LayoutDirection.Ltr) {
            innerPadding.calculateLeftPadding(layoutDirection)
        } else {
            innerPadding.calculateRightPadding(layoutDirection)
        }
        val endInset = if (layoutDirection == LayoutDirection.Ltr) {
            innerPadding.calculateRightPadding(layoutDirection)
        } else {
            innerPadding.calculateLeftPadding(layoutDirection)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyGridState(),
            contentPadding = PaddingValues(
                start = startInset + PitGoSpacing.Large,
                top = innerPadding.calculateTopPadding() + PitGoSpacing.Medium,
                end = endInset + PitGoSpacing.Large,
                bottom = innerPadding.calculateBottomPadding() + PitGoSpacing.Large,
            ),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeHeader(profile = uiState.profile)
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                VehicleCard(vehicle = uiState.vehicle)
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                LocationRow(vehicle = uiState.vehicle)
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.home_services_question),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(top = PitGoSpacing.Small)
                        .semantics { heading() },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                DiagnosticCard(
                    prompt = uiState.diagnosticPrompt,
                    onClick = onDiagnosticClick,
                )
            }

            items(
                items = uiState.services,
                key = { service -> service.id },
            ) { service ->
                ServiceCard(
                    service = service,
                    onClick = { onServiceClick(service.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(profile: DemoProfile) {
    val avatarDescription = stringResource(R.string.home_avatar_description)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(profile.greetingRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(profile.nameRes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }

        Box(
            modifier = Modifier
                .size(PitGoSize.Avatar)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .semantics {
                    contentDescription = avatarDescription
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(profile.avatarInitialRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun VehicleCard(vehicle: DemoVehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = PitGoElevation.Card),
    ) {
        Row(
            modifier = Modifier.padding(PitGoSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconContainer(iconRes = vehicle.iconRes)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_selected_vehicle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(vehicle.modelRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.home_vehicle_details,
                        vehicle.year,
                        stringResource(vehicle.plateRes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LocationRow(vehicle: DemoVehicle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PitGoSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_home_location),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(PitGoSize.FeatureIcon),
        )
        Spacer(Modifier.width(PitGoSpacing.Small))
        Text(
            text = stringResource(vehicle.addressRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiagnosticCard(
    prompt: DiagnosticPrompt,
    onClick: () -> Unit,
) {
    val actionDescription = stringResource(R.string.diagnostic_action_description)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = PitGoSize.MinimumTouchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = actionDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(PitGoSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconContainer(iconRes = prompt.iconRes)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(prompt.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(prompt.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ServiceCard(
    service: DemoService,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val serviceName = stringResource(service.nameRes)
    val actionDescription = stringResource(
        R.string.service_action_description,
        serviceName,
        service.startingPrice,
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = PitGoSize.MinimumTouchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = actionDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = PitGoElevation.Card),
    ) {
        Column(modifier = Modifier.padding(PitGoSpacing.Medium)) {
            IconContainer(iconRes = service.iconRes)
            Spacer(Modifier.height(PitGoSpacing.Medium))
            Text(
                text = serviceName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.service_price, service.startingPrice),
                style = MaterialTheme.typography.bodyMedium,
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
