package com.trustarx.pitgo.features.garage.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.vehicle.model.VehicleField
import com.trustarx.pitgo.core.vehicle.model.VehicleFieldError
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormFailureReason
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormOperation
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormUiState
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormViewModel

@Composable
fun VehicleFormRoute(
    viewModel: VehicleFormViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.operation) {
        when (val operation = state.operation) {
            VehicleFormOperation.Success -> onSaved()
            is VehicleFormOperation.Failure -> if (
                operation.reason == VehicleFormFailureReason.SESSION_EXPIRED
            ) onSessionExpired()
            else -> Unit
        }
    }
    VehicleFormScreen(
        state = state,
        onFieldChanged = viewModel::onFieldChanged,
        onMakePrimaryChanged = viewModel::onMakePrimaryChanged,
        onSubmit = viewModel::submit,
        onDiscard = {
            viewModel.discardDraft()
            onBack()
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormScreen(
    state: VehicleFormUiState,
    onFieldChanged: (VehicleField, String) -> Unit,
    onMakePrimaryChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val sessionExpired = (state.operation as? VehicleFormOperation.Failure)?.reason ==
        VehicleFormFailureReason.SESSION_EXPIRED
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) showDiscard = false
    }
    val requestBack = {
        if (!sessionExpired && state.hasDraft) showDiscard = true else onBack()
    }
    BackHandler(enabled = !sessionExpired, onBack = requestBack)
    val focusRequesters = remember { VehicleField.entries.associateWith { FocusRequester() } }
    LaunchedEffect(state.focusRequestId) {
        state.focusField?.let { focusRequesters.getValue(it).requestFocus() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vehicle_form_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = requestBack,
                        modifier = Modifier.sizeIn(
                            minWidth = PitGoSize.MinimumTouchTarget,
                            minHeight = PitGoSize.MinimumTouchTarget,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = PitGoSpacing.Large,
                top = padding.calculateTopPadding() + PitGoSpacing.Small,
                end = PitGoSpacing.Large,
                bottom = padding.calculateBottomPadding() + PitGoSpacing.ExtraLarge,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_vehicle_plate),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(PitGoSize.ServiceIconContainer),
                    )
                    Text(
                        text = stringResource(R.string.vehicle_form_heading),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.vehicle_form_optional_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VehicleFailureBanner(state.operation)
                    VehicleInput(state, VehicleField.BRAND, state.brand, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.MODEL, state.model, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.VERSION, state.version, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.MANUFACTURE_YEAR, state.manufactureYear, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.MODEL_YEAR, state.modelYear, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.PLATE, state.plate, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.RENAVAM, state.renavam, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.CHASSIS, state.chassis, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.COLOR, state.color, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.FUEL_TYPE, state.fuelType, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.TRANSMISSION, state.transmission, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.ENGINE, state.engine, onFieldChanged, focusRequesters)
                    VehicleInput(state, VehicleField.MILEAGE, state.mileage, onFieldChanged, focusRequesters)

                    if (state.existingVehicleCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = !state.isProcessing,
                                    onClick = { onMakePrimaryChanged(!state.makePrimary) },
                                )
                                .padding(vertical = PitGoSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = state.makePrimary,
                                onCheckedChange = onMakePrimaryChanged,
                                enabled = !state.isProcessing,
                            )
                            Text(stringResource(R.string.vehicle_make_primary))
                        }
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = !state.isProcessing,
                        modifier = Modifier.fillMaxWidth().heightIn(
                            min = PitGoSize.PrimaryActionMinHeight,
                        ),
                    ) {
                        if (state.operation == VehicleFormOperation.Submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(
                            text = stringResource(
                                if (state.operation == VehicleFormOperation.Submitting) {
                                    R.string.vehicle_saving
                                } else {
                                    R.string.vehicle_save
                                },
                            ),
                            modifier = Modifier.padding(start = PitGoSpacing.Small),
                        )
                    }
                }
            }
        }
    }

    if (showDiscard && !sessionExpired) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.vehicle_discard_title)) },
            text = { Text(stringResource(R.string.vehicle_discard_message)) },
            confirmButton = {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.signup_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.signup_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun VehicleInput(
    state: VehicleFormUiState,
    field: VehicleField,
    value: String,
    onFieldChanged: (VehicleField, String) -> Unit,
    focusRequesters: Map<VehicleField, FocusRequester>,
) {
    val fieldError = state.errors[field]
    val errorText = fieldError?.let { vehicleErrorText(it) }
    val numberField = field in setOf(
        VehicleField.MANUFACTURE_YEAR,
        VehicleField.MODEL_YEAR,
        VehicleField.RENAVAM,
        VehicleField.MILEAGE,
    )
    OutlinedTextField(
        value = value,
        onValueChange = { onFieldChanged(field, it) },
        label = { Text(stringResource(vehicleFieldLabel(field))) },
        singleLine = true,
        enabled = !state.isProcessing,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numberField) KeyboardType.Number else KeyboardType.Text,
            capitalization = if (field == VehicleField.PLATE || field == VehicleField.CHASSIS) {
                KeyboardCapitalization.Characters
            } else {
                KeyboardCapitalization.Sentences
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequesters.getValue(field))
            .then(if (errorText != null) Modifier.semantics { error(errorText) } else Modifier),
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun vehicleErrorText(error: VehicleFieldError): String = stringResource(
    when (error) {
        VehicleFieldError.REQUIRED -> R.string.field_required
        VehicleFieldError.INVALID_FORMAT -> R.string.vehicle_error_format
        VehicleFieldError.OUT_OF_RANGE -> R.string.vehicle_error_range
        VehicleFieldError.UNAVAILABLE -> R.string.vehicle_error_unavailable
    },
)

private fun vehicleFieldLabel(field: VehicleField): Int = when (field) {
    VehicleField.BRAND -> R.string.vehicle_brand
    VehicleField.MODEL -> R.string.vehicle_model
    VehicleField.VERSION -> R.string.vehicle_version
    VehicleField.MANUFACTURE_YEAR -> R.string.vehicle_manufacture_year
    VehicleField.MODEL_YEAR -> R.string.vehicle_model_year
    VehicleField.PLATE -> R.string.vehicle_plate
    VehicleField.RENAVAM -> R.string.vehicle_renavam
    VehicleField.CHASSIS -> R.string.vehicle_chassis
    VehicleField.COLOR -> R.string.vehicle_color
    VehicleField.FUEL_TYPE -> R.string.vehicle_fuel
    VehicleField.TRANSMISSION -> R.string.vehicle_transmission
    VehicleField.ENGINE -> R.string.vehicle_engine
    VehicleField.MILEAGE -> R.string.vehicle_mileage
}

@Composable
private fun VehicleFailureBanner(operation: VehicleFormOperation) {
    val failure = operation as? VehicleFormOperation.Failure ?: return
    if (failure.reason == VehicleFormFailureReason.SESSION_EXPIRED) return
    val message = when (failure.reason) {
        VehicleFormFailureReason.OFFLINE -> R.string.vehicle_failure_offline
        VehicleFormFailureReason.TIMEOUT -> R.string.vehicle_failure_timeout
        VehicleFormFailureReason.SERVICE -> R.string.vehicle_failure_service
        VehicleFormFailureReason.CONTRACT -> R.string.vehicle_failure_contract
        VehicleFormFailureReason.UNKNOWN -> R.string.vehicle_failure_unknown
        VehicleFormFailureReason.SESSION_EXPIRED -> return
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = stringResource(message),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(PitGoSpacing.Medium),
        )
    }
}
