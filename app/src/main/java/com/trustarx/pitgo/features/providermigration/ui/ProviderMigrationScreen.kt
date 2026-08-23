package com.trustarx.pitgo.features.providermigration.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.providermigration.model.ProviderMigrationState
import com.trustarx.pitgo.features.providermigration.viewmodel.ProviderMigrationViewModel

@Composable
fun ProviderMigrationRoute(
    viewModel: ProviderMigrationViewModel,
    providerSession: Boolean,
    onSwitchAccount: () -> Unit,
    onCompleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(providerSession, state) {
        if (providerSession && state == ProviderMigrationState.Idle) viewModel.claim()
        if (state == ProviderMigrationState.Completed) onCompleted()
    }
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(PitGoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            Text(stringResource(R.string.provider_migration_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.provider_migration_message))
            when (state) {
                ProviderMigrationState.Idle -> Button(viewModel::begin, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.provider_migration_begin))
                }
                ProviderMigrationState.Starting, ProviderMigrationState.Claiming -> CircularProgressIndicator()
                ProviderMigrationState.SwitchAccount -> Button(onSwitchAccount, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.provider_migration_switch_account))
                }
                is ProviderMigrationState.Failure -> Button(
                    onClick = if (providerSession) viewModel::claim else viewModel::begin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_retry)) }
                ProviderMigrationState.Completed -> Text(stringResource(R.string.provider_migration_completed))
            }
        }
    }
}
