package com.trustarx.pitgo.features.clientaddress.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.address.model.PostalCodeLookupState
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.clientaddress.model.ClientAddressFailure
import com.trustarx.pitgo.features.clientaddress.model.ClientAddressUiState
import com.trustarx.pitgo.features.clientaddress.viewmodel.ClientAddressViewModel

@Composable
fun ClientAddressRoute(
    viewModel: ClientAddressViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == ClientAddressUiState.Saved) onSaved()
        if (state == ClientAddressUiState.SessionExpired) onSessionExpired()
    }
    ClientAddressScreen(
        state = state,
        onBack = onBack,
        onStreetChanged = viewModel::updateStreet,
        onNumberChanged = viewModel::updateNumber,
        onComplementChanged = viewModel::updateComplement,
        onNeighborhoodChanged = viewModel::updateNeighborhood,
        onPostalCodeChanged = viewModel::updatePostalCode,
        onCityChanged = viewModel::updateCity,
        onStateChanged = viewModel::updateState,
        onLookupPostalCode = viewModel::lookupPostalCode,
        onSave = viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientAddressScreen(
    state: ClientAddressUiState,
    onBack: () -> Unit,
    onStreetChanged: (String) -> Unit,
    onNumberChanged: (String) -> Unit,
    onComplementChanged: (String) -> Unit,
    onNeighborhoodChanged: (String) -> Unit,
    onPostalCodeChanged: (String) -> Unit,
    onCityChanged: (String) -> Unit,
    onStateChanged: (String) -> Unit,
    onLookupPostalCode: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(
        title = { Text(stringResource(R.string.client_address_title)) },
        navigationIcon = { IconButton(onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.map_back)) } },
    ) }) { padding ->
        when (state) {
            ClientAddressUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is ClientAddressUiState.Failure -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(failureText(state.reason), color = MaterialTheme.colorScheme.error) }
            ClientAddressUiState.SessionExpired, ClientAddressUiState.Saved -> Unit
            is ClientAddressUiState.Content -> Column(
                Modifier.fillMaxSize().padding(padding).padding(PitGoSpacing.Large)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = state.draft.postalCode,
                        onValueChange = onPostalCodeChanged,
                        label = { Text(stringResource(R.string.client_address_zip_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = onLookupPostalCode,
                        modifier = Modifier.heightIn(min = PitGoSize.MinimumTouchTarget),
                    ) {
                        if (state.postalCodeLookup is PostalCodeLookupState.Loading) {
                            Text(stringResource(R.string.client_address_zip_searching))
                        } else Text(stringResource(R.string.client_address_zip_search))
                    }
                }
                PostalCodeLookupNotice(state.postalCodeLookup)
                OutlinedTextField(
                    value = state.draft.street, onValueChange = onStreetChanged,
                    label = { Text(stringResource(R.string.client_address_street_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.number, onValueChange = onNumberChanged,
                    label = { Text(stringResource(R.string.client_address_number_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.complement, onValueChange = onComplementChanged,
                    label = { Text(stringResource(R.string.client_address_complement_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.neighborhood, onValueChange = onNeighborhoodChanged,
                    label = { Text(stringResource(R.string.client_address_neighborhood_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.city, onValueChange = onCityChanged,
                    label = { Text(stringResource(R.string.client_address_city_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = state.draft.state, onValueChange = onStateChanged,
                    label = { Text(stringResource(R.string.client_address_state_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                state.failure?.let { Text(failureText(it), color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
                ) {
                    if (state.saving) {
                        Text(stringResource(R.string.client_address_saving))
                    } else Text(stringResource(R.string.client_address_save))
                }
            }
        }
    }
}

@Composable
private fun PostalCodeLookupNotice(lookup: PostalCodeLookupState) {
    val message = when (lookup) {
        is PostalCodeLookupState.NotFound -> stringResource(R.string.client_address_zip_not_found)
        is PostalCodeLookupState.Failure -> stringResource(R.string.client_address_zip_lookup_failed)
        else -> null
    } ?: return
    Text(message, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun failureText(failure: ClientAddressFailure) = when (failure) {
    ClientAddressFailure.SESSION_EXPIRED -> stringResource(R.string.client_address_session_expired)
    else -> stringResource(R.string.client_address_save_failed)
}
