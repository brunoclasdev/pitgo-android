package com.trustarx.pitgo.features.garage.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.vehicle.model.VehicleField
import com.trustarx.pitgo.core.vehicle.model.VehicleFieldError
import com.trustarx.pitgo.core.vehicle.validation.VehicleValidation
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormFailureReason
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormOperation
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormUiState
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormViewModel
import kotlinx.coroutines.yield

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
            is VehicleFormOperation.Success -> onSaved()
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
    val submitting = state.operation is VehicleFormOperation.Submitting
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) showDiscard = false
    }
    val requestBack = {
        when {
            submitting -> Unit
            !sessionExpired && state.hasDraft -> showDiscard = true
            else -> onBack()
        }
    }
    BackHandler(enabled = !sessionExpired, onBack = requestBack)
    val focusRequesters = remember { VehicleField.entries.associateWith { FocusRequester() } }
    val bringIntoViewRequesters = remember {
        VehicleField.entries.associateWith { BringIntoViewRequester() }
    }
    LaunchedEffect(state.focusRequestId) {
        state.focusField?.let { field ->
            focusRequesters.getValue(field).requestFocus()
            yield()
            bringIntoViewRequesters.getValue(field).bringIntoView()
        }
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
                        enabled = !submitting,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding(),
            contentPadding = PaddingValues(
                start = PitGoSpacing.Large,
                top = PitGoSpacing.Small,
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
                    if (state.operation is VehicleFormOperation.Loading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.vehicle_preparing_form),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    VehicleFailureBanner(state.operation)
                    VehicleField.entries.forEach { field ->
                        VehicleInput(
                            state = state,
                            field = field,
                            value = state.valueOf(field),
                            onFieldChanged = onFieldChanged,
                            focusRequester = focusRequesters.getValue(field),
                            bringIntoViewRequester = bringIntoViewRequesters.getValue(field),
                        )
                    }

                    if (state.existingVehicleCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = state.makePrimary,
                                    enabled = !state.isProcessing,
                                    role = Role.Checkbox,
                                    onValueChange = onMakePrimaryChanged,
                                )
                                .padding(vertical = PitGoSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = state.makePrimary,
                                onCheckedChange = null,
                                enabled = !state.isProcessing,
                            )
                            Text(
                                text = stringResource(R.string.vehicle_make_primary),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = !state.isProcessing,
                        modifier = Modifier.fillMaxWidth().heightIn(
                            min = PitGoSize.PrimaryActionMinHeight,
                        ),
                    ) {
                        if (state.operation is VehicleFormOperation.Submitting ||
                            state.operation is VehicleFormOperation.Loading
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(
                            text = stringResource(
                                if (state.operation is VehicleFormOperation.Submitting) {
                                    R.string.vehicle_saving
                                } else if (state.operation is VehicleFormOperation.Loading) {
                                    R.string.vehicle_preparing_form
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
    focusRequester: FocusRequester,
    bringIntoViewRequester: BringIntoViewRequester,
) {
    val focusManager = LocalFocusManager.current
    val fieldError = state.errors[field]
    val errorText = fieldError?.let { vehicleErrorText(field, it) }
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
            imeAction = if (field == VehicleField.MILEAGE) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester)
            .then(if (errorText != null) Modifier.semantics { error(errorText) } else Modifier),
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun vehicleErrorText(field: VehicleField, error: VehicleFieldError): String = when {
    field == VehicleField.BRAND && error == VehicleFieldError.REQUIRED ->
        stringResource(R.string.vehicle_error_brand_required)
    field == VehicleField.MODEL && error == VehicleFieldError.REQUIRED ->
        stringResource(R.string.vehicle_error_model_required)
    field == VehicleField.PLATE && error == VehicleFieldError.INVALID_FORMAT ->
        stringResource(R.string.vehicle_error_plate)
    field == VehicleField.RENAVAM && error == VehicleFieldError.INVALID_FORMAT ->
        stringResource(R.string.vehicle_error_renavam)
    field == VehicleField.CHASSIS && error == VehicleFieldError.INVALID_FORMAT ->
        stringResource(R.string.vehicle_error_chassis)
    field == VehicleField.MANUFACTURE_YEAR || field == VehicleField.MODEL_YEAR ->
        stringResource(R.string.vehicle_error_year, VehicleValidation.maximumYear())
    field == VehicleField.MILEAGE && error == VehicleFieldError.INVALID_FORMAT ->
        stringResource(R.string.vehicle_error_mileage_format)
    field == VehicleField.MILEAGE && error == VehicleFieldError.OUT_OF_RANGE ->
        stringResource(R.string.vehicle_error_mileage_range)
    error == VehicleFieldError.UNAVAILABLE ->
        stringResource(R.string.vehicle_error_identifier_unavailable)
    error == VehicleFieldError.REQUIRED -> stringResource(R.string.field_required)
    else -> stringResource(R.string.vehicle_error_format)
}

private fun VehicleFormUiState.valueOf(field: VehicleField): String = when (field) {
    VehicleField.BRAND -> brand
    VehicleField.MODEL -> model
    VehicleField.VERSION -> version
    VehicleField.MANUFACTURE_YEAR -> manufactureYear
    VehicleField.MODEL_YEAR -> modelYear
    VehicleField.PLATE -> plate
    VehicleField.RENAVAM -> renavam
    VehicleField.CHASSIS -> chassis
    VehicleField.COLOR -> color
    VehicleField.FUEL_TYPE -> fuelType
    VehicleField.TRANSMISSION -> transmission
    VehicleField.ENGINE -> engine
    VehicleField.MILEAGE -> mileage
}

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
        VehicleFormFailureReason.OPERATION_UNAVAILABLE -> R.string.vehicle_failure_operation_unavailable
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
