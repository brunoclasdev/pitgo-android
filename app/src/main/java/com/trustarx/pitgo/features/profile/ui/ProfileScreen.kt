package com.trustarx.pitgo.features.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.model.AccountSnapshot
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.preferences.AppearanceMode
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.ui.PitGoBottomNavigation
import com.trustarx.pitgo.core.ui.PitGoBottomNavigationItems
import com.trustarx.pitgo.core.vehicle.model.Vehicle
import com.trustarx.pitgo.features.clientaddress.model.ClientAddress
import com.trustarx.pitgo.features.profile.viewmodel.ProfileUiState
import com.trustarx.pitgo.features.profile.viewmodel.ProfileViewModel

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    account: AccountSnapshot,
    legacyProviderMigrationAvailable: Boolean,
    onMigrateProvider: () -> Unit,
    onOpenAddress: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenPaymentMethods: () -> Unit,
    onOpenPaymentHistory: () -> Unit,
    onOpenGuinchoHistory: () -> Unit,
    onBottomItemClick: (BottomItemId) -> Unit,
    onLogout: () -> Unit,
) {
    val profileState by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(
        account = account,
        profileState = profileState,
        legacyProviderMigrationAvailable = legacyProviderMigrationAvailable,
        onMigrateProvider = onMigrateProvider,
        onOpenAddress = onOpenAddress,
        onOpenGarage = onOpenGarage,
        onOpenPaymentMethods = onOpenPaymentMethods,
        onOpenPaymentHistory = onOpenPaymentHistory,
        onOpenGuinchoHistory = onOpenGuinchoHistory,
        onAppearanceSelected = viewModel::setAppearanceMode,
        onBottomItemClick = onBottomItemClick,
        onLogout = onLogout,
    )
}

@Composable
private fun ProfileScreen(
    account: AccountSnapshot,
    profileState: ProfileUiState,
    legacyProviderMigrationAvailable: Boolean,
    onMigrateProvider: () -> Unit,
    onOpenAddress: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenPaymentMethods: () -> Unit,
    onOpenPaymentHistory: () -> Unit,
    onOpenGuinchoHistory: () -> Unit,
    onAppearanceSelected: (AppearanceMode) -> Unit,
    onBottomItemClick: (BottomItemId) -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        bottomBar = {
            PitGoBottomNavigation(
                items = PitGoBottomNavigationItems,
                selectedItem = BottomItemId.PROFILE,
                onItemClick = onBottomItemClick,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(PitGoSpacing.Large)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(profileState.environmentLabel)
            Text(account.profile.displayName, style = MaterialTheme.typography.titleLarge)
            Text(account.authEmail, color = MaterialTheme.colorScheme.onSurfaceVariant)

            ProfileAddressCard(profileState.address, profileState.addressLoading, onOpenAddress)
            ProfileVehicleCard(account.primaryVehicle, onOpenGarage)
            ProfilePaymentMethodsCard(onOpenPaymentMethods)
            OutlinedButton(onClick = onOpenPaymentHistory, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.payment_history))
            }
            OutlinedButton(onClick = onOpenGuinchoHistory, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guincho_history))
            }
            ProfileAppearanceCard(profileState.appearanceMode, onAppearanceSelected)

            if (legacyProviderMigrationAvailable) {
                OutlinedButton(
                    onClick = onMigrateProvider,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
                ) { Text(stringResource(R.string.provider_migration_action)) }
            }
            Button(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth().heightIn(
                    min = PitGoSize.MinimumTouchTarget,
                ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_profile_logout),
                        contentDescription = null,
                        modifier = Modifier.size(PitGoSize.FeatureIcon),
                    )
                    Text(stringResource(R.string.action_logout))
                }
            }
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.profile_logout_title)) },
            text = { Text(stringResource(R.string.profile_logout_message)) },
            confirmButton = {
                TextButton(onClick = onLogout) { Text(stringResource(R.string.action_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileAddressCard(address: ClientAddress?, loading: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = PitGoSize.MinimumTouchTarget),
    ) {
        Column(Modifier.padding(PitGoSpacing.Medium)) {
            Text(stringResource(R.string.profile_address_label), style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    loading -> stringResource(R.string.client_address_loading)
                    address != null -> stringResource(
                        R.string.home_address_summary, address.street, address.number, address.city,
                    )
                    else -> stringResource(R.string.home_address_empty)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileVehicleCard(vehicle: Vehicle?, onOpenGarage: () -> Unit) {
    Card(
        onClick = onOpenGarage,
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = PitGoSize.MinimumTouchTarget),
    ) {
        Column(Modifier.padding(PitGoSpacing.Medium)) {
            Text(stringResource(R.string.profile_vehicle_label), style = MaterialTheme.typography.titleMedium)
            Text(
                vehicle?.displayName ?: stringResource(R.string.home_no_vehicle_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfilePaymentMethodsCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = PitGoSize.MinimumTouchTarget),
    ) {
        Column(Modifier.padding(PitGoSpacing.Medium)) {
            Text(stringResource(R.string.profile_payment_methods_label), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProfileAppearanceCard(mode: AppearanceMode, onSelected: (AppearanceMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
        Text(stringResource(R.string.profile_appearance_label), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
            AppearanceChip(AppearanceMode.LIGHT, mode, R.string.profile_appearance_light, onSelected)
            AppearanceChip(AppearanceMode.DARK, mode, R.string.profile_appearance_dark, onSelected)
            AppearanceChip(AppearanceMode.SYSTEM, mode, R.string.profile_appearance_system, onSelected)
        }
    }
}

@Composable
private fun AppearanceChip(
    option: AppearanceMode,
    current: AppearanceMode,
    labelRes: Int,
    onSelected: (AppearanceMode) -> Unit,
) {
    FilterChip(
        selected = option == current,
        onClick = { onSelected(option) },
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.sizeIn(minHeight = PitGoSize.MinimumTouchTarget),
    )
}
