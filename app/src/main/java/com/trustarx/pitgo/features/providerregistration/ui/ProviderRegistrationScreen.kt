package com.trustarx.pitgo.features.providerregistration.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.common.validation.BrazilianInputValidation
import com.trustarx.pitgo.core.design.theme.PitGoElevation
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.ui.CepVisualTransformation
import com.trustarx.pitgo.core.ui.CnpjVisualTransformation
import com.trustarx.pitgo.core.ui.PhoneVisualTransformation
import com.trustarx.pitgo.features.providerregistration.model.CatalogService
import com.trustarx.pitgo.features.providerregistration.model.AddressVerificationState
import com.trustarx.pitgo.features.providerregistration.model.ProviderLocationMode
import com.trustarx.pitgo.features.providerregistration.model.LoadState
import com.trustarx.pitgo.features.providerregistration.model.OfferDraft
import com.trustarx.pitgo.features.providerregistration.model.OfferField
import com.trustarx.pitgo.features.providerregistration.model.ProviderEditingContent
import com.trustarx.pitgo.features.providerregistration.model.ProviderFailure
import com.trustarx.pitgo.features.providerregistration.model.ProviderField
import com.trustarx.pitgo.features.providerregistration.model.ProviderFieldError
import com.trustarx.pitgo.features.providerregistration.model.ProviderRegistrationUiState
import com.trustarx.pitgo.features.providerregistration.model.ProviderSubmissionState
import com.trustarx.pitgo.features.providerregistration.model.ProviderSummary
import com.trustarx.pitgo.core.address.model.PostalCodeLookupFailure
import com.trustarx.pitgo.core.address.model.PostalCodeLookupState
import com.trustarx.pitgo.features.providerregistration.model.ServiceCategory
import com.trustarx.pitgo.features.providerregistration.viewmodel.ProviderRegistrationViewModel
import com.trustarx.pitgo.features.providerregistration.validation.ProviderRegistrationValidation
import kotlinx.coroutines.yield

@Composable
fun ProviderRegistrationRoute(
    viewModel: ProviderRegistrationViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onFinished: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestAddressVerification: () -> Unit = viewModel::verifyAddress
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showProcessingMessage by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    val editing = (state as? ProviderRegistrationUiState.Editing)?.content
    val isSubmitting = editing?.submission == ProviderSubmissionState.Submitting

    fun handleBack() {
        when {
            isSubmitting -> showProcessingMessage = true
            editing?.hasDraft == true -> showDiscardDialog = true
            else -> onBack()
        }
    }

    BackHandler(onBack = ::handleBack)
    LaunchedEffect(state) {
        if (state == ProviderRegistrationUiState.SessionExpired) onSessionExpired()
    }

    ProviderRegistrationScreen(
        state = state,
        onBack = ::handleBack,
        onLogout = {
            if (isSubmitting) showProcessingMessage = true else showLogoutDialog = true
        },
        onRetryStartup = viewModel::retryStartup,
        onFieldChanged = viewModel::onFieldChanged,
        onLookupPostalCode = viewModel::lookupPostalCode,
        onLocationModeSelected = viewModel::onLocationModeSelected,
        onVerifyAddress = requestAddressVerification,
        onConfirmDivergence = viewModel::verifyAddress,
        onOfferFieldChanged = viewModel::onOfferFieldChanged,
        onCategorySelected = viewModel::onCategorySelected,
        onServiceSelected = viewModel::onServiceSelected,
        onFreeChanged = viewModel::onFreeChanged,
        onAddOffer = viewModel::addOffer,
        onRemoveOffer = viewModel::removeOffer,
        onRetryCatalog = viewModel::retryCatalog,
        onRetryImplicitService = viewModel::retryImplicitService,
        onSubmit = viewModel::submit,
        onFinished = onFinished,
    )

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.profile_logout_title)) },
            text = { Text(stringResource(R.string.profile_logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text(stringResource(R.string.action_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.provider_discard_title)) },
            text = { Text(stringResource(R.string.provider_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    viewModel.discardDraft()
                    onBack()
                }) { Text(stringResource(R.string.provider_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.provider_keep_editing))
                }
            },
        )
    }
    if (showProcessingMessage) {
        AlertDialog(
            onDismissRequest = { showProcessingMessage = false },
            text = { Text(stringResource(R.string.provider_processing_back_message)) },
            confirmButton = {
                TextButton(onClick = { showProcessingMessage = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderRegistrationScreen(
    state: ProviderRegistrationUiState,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onRetryStartup: () -> Unit,
    onFieldChanged: (ProviderField, String) -> Unit,
    onLookupPostalCode: () -> Unit,
    onLocationModeSelected: (ProviderLocationMode) -> Unit,
    onVerifyAddress: () -> Unit,
    onConfirmDivergence: () -> Unit,
    onOfferFieldChanged: (Long, OfferField, String) -> Unit,
    onCategorySelected: (Long, String) -> Unit,
    onServiceSelected: (Long, String) -> Unit,
    onFreeChanged: (Long, Boolean) -> Unit,
    onAddOffer: () -> Unit,
    onRemoveOffer: (Long) -> Unit,
    onRetryCatalog: (String?) -> Unit,
    onRetryImplicitService: () -> Unit,
    onSubmit: () -> Unit,
    onFinished: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.provider_registration_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text(stringResource(R.string.action_logout))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                ProviderRegistrationUiState.Loading -> StatusContent(
                    message = stringResource(R.string.provider_catalog_loading),
                    loading = true,
                )
                is ProviderRegistrationUiState.StartupFailure -> StatusContent(
                    message = failureMessage(state.reason),
                    onRetry = onRetryStartup,
                )
                is ProviderRegistrationUiState.Editing -> RegistrationForm(
                    content = state.content,
                    onFieldChanged = onFieldChanged,
                    onLookupPostalCode = onLookupPostalCode,
                    onLocationModeSelected = onLocationModeSelected,
                    onVerifyAddress = onVerifyAddress,
                    onConfirmDivergence = onConfirmDivergence,
                    onOfferFieldChanged = onOfferFieldChanged,
                    onCategorySelected = onCategorySelected,
                    onServiceSelected = onServiceSelected,
                    onFreeChanged = onFreeChanged,
                    onAddOffer = onAddOffer,
                    onRemoveOffer = onRemoveOffer,
                    onRetryCatalog = onRetryCatalog,
                    onRetryImplicitService = onRetryImplicitService,
                    onSubmit = onSubmit,
                )
                is ProviderRegistrationUiState.AlreadyRegistered -> SummaryContent(
                    summary = state.summary,
                    title = stringResource(R.string.provider_existing_title),
                    message = stringResource(R.string.provider_existing_message),
                    actionLabel = stringResource(R.string.provider_back_to_profile),
                    onFinished = onFinished,
                )
                is ProviderRegistrationUiState.Success -> SummaryContent(
                    summary = state.summary,
                    title = stringResource(R.string.provider_success_title),
                    message = stringResource(R.string.provider_success_message),
                    actionLabel = stringResource(R.string.provider_finish),
                    onFinished = onFinished,
                )
                ProviderRegistrationUiState.SessionExpired -> StatusContent(
                    message = stringResource(R.string.provider_failure_session),
                )
            }
        }
    }
}

@Composable
private fun RegistrationForm(
    content: ProviderEditingContent,
    onFieldChanged: (ProviderField, String) -> Unit,
    onLookupPostalCode: () -> Unit,
    onLocationModeSelected: (ProviderLocationMode) -> Unit,
    onVerifyAddress: () -> Unit,
    onConfirmDivergence: () -> Unit,
    onOfferFieldChanged: (Long, OfferField, String) -> Unit,
    onCategorySelected: (Long, String) -> Unit,
    onServiceSelected: (Long, String) -> Unit,
    onFreeChanged: (Long, Boolean) -> Unit,
    onAddOffer: () -> Unit,
    onRemoveOffer: (Long) -> Unit,
    onRetryCatalog: (String?) -> Unit,
    onRetryImplicitService: () -> Unit,
    onSubmit: () -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequesters = remember { mutableStateMapOf<ProviderField, FocusRequester>() }
    val keyboard = LocalSoftwareKeyboardController.current
    val submitting = content.submission == ProviderSubmissionState.Submitting
    val postalCodeLoading = content.postalCodeLookup is PostalCodeLookupState.Loading
    val categories = (content.categories as? LoadState.Content)?.value.orEmpty()
    val selectedServices = content.draft.offers.mapNotNull(OfferDraft::serviceId).toSet()

    LaunchedEffect(content.focusRequest) {
        val request = content.focusRequest ?: return@LaunchedEffect
        formRowIndex(request.field, content)?.let { itemIndex ->
            listState.animateScrollToItem(itemIndex)
            yield()
        }
        focusRequesters[request.field]?.requestFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(PitGoSpacing.Large),
    ) {
        item {
            FormWidth {
                Text(
                    stringResource(R.string.provider_registration_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.provider_registration_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionTitle(R.string.provider_company_section) }
        item {
            ProviderModeSection(
                selected = content.draft.locationMode,
                enabled = !submitting,
                error = content.errors[ProviderField.LocationMode],
                focusRequester = rememberFieldFocusRequester(
                    focusRequesters,
                    ProviderField.LocationMode,
                ),
                onSelected = onLocationModeSelected,
            )
        }
        item {
            FormTextField(content.draft.company.name, ProviderField.Name, R.string.provider_name_label,
                content, submitting, focusRequesters, onFieldChanged,
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
        }
        item {
            FormTextField(content.draft.company.description, ProviderField.Description,
                R.string.provider_description_label, content, submitting, focusRequesters,
                onFieldChanged, KeyboardOptions(imeAction = ImeAction.Default), singleLine = false)
        }
        item {
            FormTextField(content.draft.company.email, ProviderField.Email, R.string.provider_email_label,
                content, submitting, focusRequesters, onFieldChanged,
                KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        }
        item {
            FormTextField(content.draft.company.phone, ProviderField.Phone, R.string.provider_phone_label,
                content, submitting, focusRequesters, onFieldChanged,
                KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                visualTransformation = PhoneVisualTransformation)
        }
        item {
            FormTextField(content.draft.company.cnpj, ProviderField.Cnpj, R.string.provider_cnpj_label,
                content, submitting, focusRequesters, onFieldChanged,
                KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                visualTransformation = CnpjVisualTransformation)
        }
        item { SectionTitle(R.string.provider_address_section) }
        item {
            CepLookupField(
                content = content,
                submitting = submitting,
                focusRequesters = focusRequesters,
                onChanged = { onFieldChanged(ProviderField.PostalCode, it) },
                onLookup = onLookupPostalCode,
            )
        }
        item {
            FormTextField(content.draft.address.street, ProviderField.Street, R.string.provider_street_label,
                content, submitting || postalCodeLoading, focusRequesters, onFieldChanged,
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
        }
        item {
            FormTextField(content.draft.address.number, ProviderField.Number, R.string.provider_number_label,
                content, submitting, focusRequesters, onFieldChanged,
                KeyboardOptions(imeAction = ImeAction.Next))
        }
        item {
            FormTextField(content.draft.address.complement, ProviderField.Complement,
                R.string.provider_complement_label, content, submitting || postalCodeLoading, focusRequesters,
                onFieldChanged, KeyboardOptions(imeAction = ImeAction.Next))
        }
        item {
            FormTextField(content.draft.address.neighborhood, ProviderField.Neighborhood,
                R.string.provider_neighborhood_label, content, submitting || postalCodeLoading, focusRequesters,
                onFieldChanged, KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
        }
        item {
            FormTextField(content.draft.address.city, ProviderField.City, R.string.provider_city_label,
                content, submitting || postalCodeLoading, focusRequesters, onFieldChanged,
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
        }
        item {
            StateDropdown(
                selected = content.draft.address.state,
                enabled = !submitting && !postalCodeLoading,
                error = content.errors[ProviderField.State],
                focusRequester = rememberFieldFocusRequester(focusRequesters, ProviderField.State),
                onSelected = { onFieldChanged(ProviderField.State, it) },
            )
        }
        item {
            FormWidth {
                OutlinedTextField(
                    value = stringResource(R.string.provider_country_brazil),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.provider_country_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            AddressVerificationSection(
                content,
                submitting || postalCodeLoading,
                rememberFieldFocusRequester(
                    focusRequesters,
                    ProviderField.LocationVerification,
                ),
                onVerifyAddress,
                onConfirmDivergence,
            )
        }
        content.implicitServiceIssue?.let { issue ->
            item { ErrorBanner(failureMessage(issue), onRetry = onRetryImplicitService) }
        }
        if (content.draft.locationMode == ProviderLocationMode.FIXED) {
            item {
                val offersError = content.errors[ProviderField.Offers]?.let {
                    fieldErrorMessage(it, ProviderField.Offers)
                }
                Box(
                    modifier = Modifier.focusRequester(
                        rememberFieldFocusRequester(focusRequesters, ProviderField.Offers),
                    ).focusable().then(
                        if (offersError != null) Modifier.semantics { error(offersError) }
                        else Modifier,
                    ),
                ) {
                    SectionTitle(R.string.provider_offers_section)
                }
            }
            if (content.categories == LoadState.Loading) {
                item { CatalogMessage(stringResource(R.string.provider_catalog_loading), loading = true) }
            } else if (content.categories == LoadState.Empty) {
                item { CatalogMessage(stringResource(R.string.provider_catalog_empty_message), onRetry = { onRetryCatalog(null) }) }
            } else if (content.categories is LoadState.Failure) {
                item { CatalogMessage(stringResource(R.string.provider_catalog_failure), onRetry = { onRetryCatalog(null) }) }
            }
            if (content.draft.offers.isEmpty()) {
                item {
                    ErrorBanner(stringResource(R.string.provider_offers_empty))
                }
            }
            itemsIndexed(content.draft.offers, key = { _, offer -> offer.localId }) { index, offer ->
                OfferCard(
                    index = index,
                    offer = offer,
                    categories = categories,
                    servicesState = offer.categoryId?.let(content.servicesByCategory::get),
                    selectedServiceIds = selectedServices - setOfNotNull(offer.serviceId),
                    errors = content.errors,
                    enabled = !submitting,
                    focusRequesters = focusRequesters,
                    onCategorySelected = onCategorySelected,
                    onServiceSelected = onServiceSelected,
                    onFieldChanged = onOfferFieldChanged,
                    onFreeChanged = onFreeChanged,
                    onRemove = onRemoveOffer,
                    onRetryServices = { offer.categoryId?.let { onRetryCatalog(it) } },
                )
            }
            item {
                FormWidth {
                    OutlinedButton(
                        onClick = onAddOffer,
                        enabled = !submitting && content.categories is LoadState.Content,
                        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
                    ) { Text(stringResource(R.string.provider_add_offer)) }
                }
            }
        } else if (content.draft.locationMode == ProviderLocationMode.MOBILE) {
            content.draft.offers.firstOrNull()?.let { offer ->
                item {
                    MobileOfferSection(
                        offer = offer,
                        errors = content.errors,
                        enabled = !submitting,
                        focusRequesters = focusRequesters,
                        onFieldChanged = onOfferFieldChanged,
                        onFreeChanged = onFreeChanged,
                    )
                }
            }
        } else if (content.draft.locationMode == ProviderLocationMode.PITTEC) {
            // Spec 016 US3: nenhum campo e nenhum título de seção de serviço para PitTec — só
            // uma frase curta explicando que o serviço é atribuído automaticamente sem preço.
            item {
                FormWidth {
                    Text(
                        stringResource(R.string.provider_offer_pittec_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val failure = (content.submission as? ProviderSubmissionState.Failure)?.reason
        if (failure != null) item {
            ErrorBanner(failureMessage(failure), onRetry = onSubmit)
        }
        if (content.errors[ProviderField.Offers] != null && content.draft.offers.isNotEmpty()) {
            item { ErrorBanner(stringResource(R.string.provider_service_duplicate_error)) }
        }
        item {
            FormWidth {
                Button(
                    onClick = {
                        keyboard?.hide()
                        onSubmit()
                    },
                    enabled = !submitting && !postalCodeLoading &&
                        content.categories is LoadState.Content &&
                        content.draft.offers.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(PitGoSize.FeatureIcon),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(PitGoSpacing.Small))
                        Text(stringResource(R.string.provider_submitting))
                    } else Text(stringResource(R.string.provider_submit))
                }
            }
        }
    }
}

@Composable
private fun ProviderModeSection(
    selected: ProviderLocationMode?,
    enabled: Boolean,
    error: ProviderFieldError?,
    focusRequester: FocusRequester,
    onSelected: (ProviderLocationMode) -> Unit,
) {
    val errorMessage = error?.let { fieldErrorMessage(it, ProviderField.LocationMode) }
    FormWidth {
        Column(
            modifier = Modifier.focusRequester(focusRequester).focusable()
                .then(
                    if (errorMessage != null) Modifier.semantics { error(errorMessage) }
                    else Modifier,
                ),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            Text(
                stringResource(R.string.provider_mode_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                OutlinedButton(
                    onClick = { onSelected(ProviderLocationMode.FIXED) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.provider_mode_fixed)) }
                OutlinedButton(
                    onClick = { onSelected(ProviderLocationMode.MOBILE) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.provider_mode_mobile)) }
                OutlinedButton(
                    onClick = { onSelected(ProviderLocationMode.PITTEC) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.provider_mode_pittec)) }
            }
            if (selected != null) {
                Text(
                    when (selected) {
                        ProviderLocationMode.FIXED -> stringResource(R.string.provider_mode_fixed_description)
                        ProviderLocationMode.MOBILE -> stringResource(R.string.provider_mode_mobile_description)
                        ProviderLocationMode.PITTEC -> stringResource(R.string.provider_mode_pittec_description)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddressVerificationSection(
    content: ProviderEditingContent,
    submitting: Boolean,
    focusRequester: FocusRequester,
    onVerifyAddress: () -> Unit,
    onConfirmDivergence: () -> Unit,
) {
    val errorMessage = content.errors[ProviderField.LocationVerification]?.let {
        fieldErrorMessage(it, ProviderField.LocationVerification)
    }
    FormWidth {
        Column(
            modifier = Modifier.focusRequester(focusRequester).focusable()
                .then(
                    if (errorMessage != null) Modifier.semantics { error(errorMessage) }
                    else Modifier,
                ),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            Text(
                stringResource(R.string.provider_location_verification_explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val verification = content.addressVerification) {
                AddressVerificationState.Unverified,
                AddressVerificationState.PermissionRequired,
                -> OutlinedButton(
                    onClick = onVerifyAddress,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
                ) { Text(stringResource(R.string.provider_verify_address)) }
                is AddressVerificationState.Failure -> {
                    ErrorBanner(failureMessage(verification.reason))
                    OutlinedButton(
                        onClick = onVerifyAddress,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
                    ) { Text(stringResource(R.string.provider_verify_address)) }
                }
                AddressVerificationState.Locating,
                AddressVerificationState.Verifying,
                -> Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                    CircularProgressIndicator(Modifier.size(PitGoSize.FeatureIcon))
                    Text(stringResource(R.string.provider_location_verifying))
                }
                is AddressVerificationState.Diverged -> {
                    ErrorBanner(
                        stringResource(
                            R.string.provider_location_diverged,
                            verification.distanceMeters,
                        ),
                    )
                    Button(
                        onClick = onConfirmDivergence,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.provider_location_confirm_divergence)) }
                }
                is AddressVerificationState.Verified -> Text(
                    stringResource(R.string.provider_location_verified),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun CepLookupField(
    content: ProviderEditingContent,
    submitting: Boolean,
    focusRequesters: MutableMap<ProviderField, FocusRequester>,
    onChanged: (String) -> Unit,
    onLookup: () -> Unit,
) {
    val lookup = content.postalCodeLookup
    val loading = lookup is PostalCodeLookupState.Loading
    val error = content.errors[ProviderField.PostalCode]
    val errorMessage = error?.let { fieldErrorMessage(it, ProviderField.PostalCode) }
    val focusRequester = rememberFieldFocusRequester(
        focusRequesters,
        ProviderField.PostalCode,
    )
    val keyboard = LocalSoftwareKeyboardController.current
    val canLookup = content.draft.address.postalCode.length == 8 && !loading && !submitting
    val status = when (lookup) {
        PostalCodeLookupState.Idle -> null
        is PostalCodeLookupState.Loading -> stringResource(R.string.provider_zip_searching)
        is PostalCodeLookupState.Found -> {
            val address = content.draft.address
            if (address.street.isNotBlank() && address.neighborhood.isNotBlank() &&
                address.city.isNotBlank() && BrazilianInputValidation.isValidState(address.state)
            ) stringResource(R.string.provider_zip_success)
            else stringResource(R.string.provider_zip_success_incomplete)
        }
        is PostalCodeLookupState.NotFound -> null
        is PostalCodeLookupState.Failure -> when (lookup.reason) {
            PostalCodeLookupFailure.OFFLINE -> stringResource(R.string.provider_zip_lookup_offline)
            PostalCodeLookupFailure.TIMEOUT -> stringResource(R.string.provider_zip_lookup_timeout)
            PostalCodeLookupFailure.SERVICE_UNAVAILABLE ->
                stringResource(R.string.provider_zip_lookup_unavailable)
            PostalCodeLookupFailure.UNEXPECTED_RESPONSE ->
                stringResource(R.string.provider_zip_lookup_unexpected)
        }
    }
    val buttonLabel = if (lookup is PostalCodeLookupState.Failure ||
        lookup is PostalCodeLookupState.NotFound
    ) {
        stringResource(R.string.action_retry)
    } else {
        stringResource(R.string.provider_zip_search)
    }

    FormWidth {
        BoxWithConstraints {
            val horizontal = maxWidth >= 480.dp && LocalDensity.current.fontScale < 1.5f
            val field: @Composable () -> Unit = {
                OutlinedTextField(
                    value = content.draft.address.postalCode,
                    onValueChange = onChanged,
                    enabled = !submitting,
                    label = { Text(stringResource(R.string.provider_zip_code_label)) },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (canLookup) {
                            keyboard?.hide()
                            onLookup()
                        }
                    }),
                    visualTransformation = CepVisualTransformation,
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(focusRequester)
                        .then(
                            if (errorMessage != null) {
                                Modifier.semantics { error(errorMessage) }
                            } else Modifier,
                        ),
                )
            }
            val action: @Composable () -> Unit = {
                OutlinedButton(
                    onClick = onLookup,
                    enabled = canLookup,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = PitGoSize.MinimumTouchTarget),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(PitGoSize.FeatureIcon),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(PitGoSpacing.Small))
                    }
                    Text(if (loading) stringResource(R.string.provider_zip_searching) else buttonLabel)
                }
            }
            if (horizontal) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.weight(1f)) { field() }
                    Box(Modifier.widthIn(min = 144.dp)) { action() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                    field()
                    action()
                }
            }
        }
        status?.let { message ->
            Text(
                message,
                color = if (lookup is PostalCodeLookupState.Failure) {
                    MaterialTheme.colorScheme.error
                } else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = message
                },
            )
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    field: ProviderField,
    labelRes: Int,
    content: ProviderEditingContent,
    disabled: Boolean,
    focusRequesters: MutableMap<ProviderField, FocusRequester>,
    onChanged: (ProviderField, String) -> Unit,
    keyboardOptions: KeyboardOptions,
    singleLine: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val fieldError = content.errors[field]
    val message = if (fieldError != null) fieldErrorMessage(fieldError, field) else null
    val focusRequester = rememberFieldFocusRequester(focusRequesters, field)
    FormWidth {
        OutlinedTextField(
            value = value,
            onValueChange = { onChanged(field, it) },
            enabled = !disabled,
            label = { Text(stringResource(labelRes)) },
            isError = fieldError != null,
            supportingText = message?.let { { Text(it) } },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .then(if (message != null) Modifier.semantics { error(message) } else Modifier),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateDropdown(
    selected: String,
    enabled: Boolean,
    error: ProviderFieldError?,
    focusRequester: FocusRequester,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val errorMessage = if (error != null) fieldErrorMessage(error, ProviderField.State) else null
    FormWidth {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {
            if (enabled) expanded = !expanded
        }) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(stringResource(R.string.provider_state_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                isError = error != null,
                supportingText = errorMessage?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                    .focusRequester(focusRequester)
                    .then(
                        if (errorMessage != null) Modifier.semantics { error(errorMessage) }
                        else Modifier,
                    ),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BrazilianInputValidation.states.forEach { state ->
                    DropdownMenuItem(text = { Text(state) }, onClick = {
                        expanded = false
                        onSelected(state)
                    })
                }
            }
        }
    }
}

/**
 * Oferta simplificada de Móvel (spec 016, US2): o serviço "Guincho" já é o implícito dessa
 * modalidade (ver `ProviderRegistrationViewModel.resolveImplicitOffer`), então esta seção só
 * pede preço e duração — sem categoria, serviço, descrição ou os controles de
 * adicionar/remover que só fazem sentido quando o prestador escolhe entre vários serviços.
 */
@Composable
private fun MobileOfferSection(
    offer: OfferDraft,
    errors: Map<ProviderField, ProviderFieldError>,
    enabled: Boolean,
    focusRequesters: MutableMap<ProviderField, FocusRequester>,
    onFieldChanged: (Long, OfferField, String) -> Unit,
    onFreeChanged: (Long, Boolean) -> Unit,
) {
    FormWidth {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            elevation = CardDefaults.cardElevation(PitGoElevation.Card),
        ) {
            Column(
                Modifier.padding(PitGoSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            ) {
                Text(
                    stringResource(R.string.provider_mode_mobile),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.provider_offer_mobile_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = offer.isFree,
                        enabled = enabled,
                        onValueChange = { onFreeChanged(offer.localId, it) },
                    ).heightIn(min = PitGoSize.MinimumTouchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = offer.isFree, onCheckedChange = null, enabled = enabled)
                    Text(stringResource(R.string.provider_free_service_label))
                }
                OfferTextField(offer, OfferField.PRICE, R.string.provider_price_label, errors,
                    enabled && !offer.isFree, focusRequesters, onFieldChanged,
                    KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    prefix = stringResource(R.string.provider_price_prefix))
                OfferTextField(offer, OfferField.DURATION, R.string.provider_duration_label, errors,
                    enabled, focusRequesters, onFieldChanged,
                    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                Text(
                    stringResource(R.string.provider_duration_support),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OfferCard(
    index: Int,
    offer: OfferDraft,
    categories: List<ServiceCategory>,
    servicesState: LoadState<List<CatalogService>>?,
    selectedServiceIds: Set<String>,
    errors: Map<ProviderField, ProviderFieldError>,
    enabled: Boolean,
    focusRequesters: MutableMap<ProviderField, FocusRequester>,
    onCategorySelected: (Long, String) -> Unit,
    onServiceSelected: (Long, String) -> Unit,
    onFieldChanged: (Long, OfferField, String) -> Unit,
    onFreeChanged: (Long, Boolean) -> Unit,
    onRemove: (Long) -> Unit,
    onRetryServices: () -> Unit,
) {
    val categoryField = ProviderField.Offer(offer.localId, OfferField.CATEGORY)
    val serviceField = ProviderField.Offer(offer.localId, OfferField.SERVICE)
    val serviceItems = (servicesState as? LoadState.Content)?.value.orEmpty()
    FormWidth {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            elevation = CardDefaults.cardElevation(PitGoElevation.Card),
        ) {
            Column(
                Modifier.padding(PitGoSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            ) {
                Text(
                    stringResource(R.string.provider_offer_title, index + 1),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                CatalogDropdown(
                    label = stringResource(R.string.provider_category_label),
                    selectedId = offer.categoryId,
                    items = categories.map { it.id to it.name },
                    enabled = enabled && categories.isNotEmpty(),
                    error = errors[categoryField]?.let { fieldErrorMessage(it, categoryField) },
                    focusRequester = rememberFieldFocusRequester(focusRequesters, categoryField),
                    onSelected = { onCategorySelected(offer.localId, it) },
                )
                when (servicesState) {
                    LoadState.Loading -> CatalogMessage(stringResource(R.string.provider_catalog_loading), loading = true)
                    LoadState.Empty -> CatalogMessage(stringResource(R.string.provider_catalog_empty_message))
                    is LoadState.Failure -> CatalogMessage(
                        stringResource(R.string.provider_catalog_failure),
                        onRetry = onRetryServices,
                    )
                    else -> CatalogDropdown(
                        label = stringResource(R.string.provider_service_label),
                        selectedId = offer.serviceId,
                        items = serviceItems.map { it.id to it.name },
                        disabledIds = selectedServiceIds,
                        enabled = enabled && offer.categoryId != null && servicesState is LoadState.Content,
                        error = errors[serviceField]?.let { fieldErrorMessage(it, serviceField) },
                        focusRequester = rememberFieldFocusRequester(focusRequesters, serviceField),
                        onSelected = { onServiceSelected(offer.localId, it) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = offer.isFree,
                        enabled = enabled,
                        onValueChange = { onFreeChanged(offer.localId, it) },
                    ).heightIn(min = PitGoSize.MinimumTouchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = offer.isFree, onCheckedChange = null, enabled = enabled)
                    Text(stringResource(R.string.provider_free_service_label))
                }
                OfferTextField(offer, OfferField.PRICE, R.string.provider_price_label, errors,
                    enabled && !offer.isFree, focusRequesters, onFieldChanged,
                    KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    prefix = stringResource(R.string.provider_price_prefix))
                OfferTextField(offer, OfferField.DURATION, R.string.provider_duration_label, errors,
                    enabled, focusRequesters, onFieldChanged,
                    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                Text(
                    stringResource(R.string.provider_duration_support),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OfferTextField(offer, OfferField.DESCRIPTION, R.string.provider_offer_description_label,
                    errors, enabled, focusRequesters, onFieldChanged,
                    KeyboardOptions(imeAction = ImeAction.Default), singleLine = false)
                TextButton(
                    onClick = { onRemove(offer.localId) },
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.End).heightIn(min = PitGoSize.MinimumTouchTarget),
                ) { Text(stringResource(R.string.provider_remove_offer)) }
            }
        }
    }
}

@Composable
private fun OfferTextField(
    offer: OfferDraft,
    field: OfferField,
    labelRes: Int,
    errors: Map<ProviderField, ProviderFieldError>,
    enabled: Boolean,
    focusRequesters: MutableMap<ProviderField, FocusRequester>,
    onFieldChanged: (Long, OfferField, String) -> Unit,
    keyboardOptions: KeyboardOptions,
    singleLine: Boolean = true,
    prefix: String? = null,
) {
    val target = ProviderField.Offer(offer.localId, field)
    val value = when (field) {
        OfferField.PRICE -> offer.priceInput
        OfferField.DURATION -> offer.durationInput
        OfferField.DESCRIPTION -> offer.description
        else -> ""
    }
    val fieldError = errors[target]
    val message = if (fieldError != null) fieldErrorMessage(fieldError, target) else null
    val focusRequester = rememberFieldFocusRequester(focusRequesters, target)
    OutlinedTextField(
        value = value,
        onValueChange = { onFieldChanged(offer.localId, field, it) },
        enabled = enabled,
        label = { Text(stringResource(labelRes)) },
        prefix = prefix?.let { { Text(it) } },
        isError = message != null,
        supportingText = message?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth()
            .focusRequester(focusRequester)
            .then(if (message != null) Modifier.semantics { error(message) } else Modifier),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogDropdown(
    label: String,
    selectedId: String?,
    items: List<Pair<String, String>>,
    enabled: Boolean,
    error: String?,
    focusRequester: FocusRequester,
    disabledIds: Set<String> = emptySet(),
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = items.firstOrNull { it.first == selectedId }?.second.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {
        if (enabled) expanded = !expanded
    }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .focusRequester(focusRequester)
                .then(if (error != null) Modifier.semantics { error(error) } else Modifier),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (id, name) ->
                val available = id !in disabledIds
                DropdownMenuItem(
                    text = {
                        Text(
                            if (available) name
                            else "$name · ${stringResource(R.string.provider_service_already_added)}",
                        )
                    },
                    enabled = available,
                    onClick = {
                        expanded = false
                        onSelected(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun SummaryContent(
    summary: ProviderSummary,
    title: String,
    message: String,
    actionLabel: String,
    onFinished: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(PitGoSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            FormWidth {
                Text(title, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    })
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SummaryCard(
                stringResource(R.string.provider_summary_company),
                listOf(summary.name, summary.email, summary.phone, summary.maskedCnpj),
            )
        }
        item {
            SummaryCard(
                stringResource(R.string.provider_summary_address),
                listOf(
                    "${summary.address.street}, ${summary.address.number}",
                    "${summary.address.neighborhood} · ${summary.address.city}/${summary.address.state}",
                ),
            )
        }
        item { SectionTitle(R.string.provider_summary_services) }
        itemsIndexed(summary.offers) { _, offer ->
            val price = if (offer.price.signum() == 0) stringResource(R.string.provider_summary_free)
            else "R$ ${offer.price.setScale(2).toPlainString().replace('.', ',')}"
            SummaryCard(
                offer.serviceName,
                listOf(offer.categoryName, "$price · ${offer.durationMinutes} min") + listOfNotNull(offer.description),
            )
        }
        item {
            FormWidth {
                Button(
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, lines: List<String>) {
    FormWidth {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Column(Modifier.padding(PitGoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                lines.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun SectionTitle(textRes: Int) {
    FormWidth {
        Text(
            stringResource(textRes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        HorizontalDivider()
    }
}

@Composable
private fun CatalogMessage(message: String, loading: Boolean = false, onRetry: (() -> Unit)? = null) {
    FormWidth {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                Modifier.fillMaxWidth().padding(PitGoSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(PitGoSize.FeatureIcon))
                Text(message, modifier = Modifier.weight(1f))
                onRetry?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_retry)) } }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null) {
    FormWidth {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = message
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(PitGoSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                onRetry?.let {
                    TextButton(onClick = it) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}

@Composable
private fun StatusContent(message: String, loading: Boolean = false, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.padding(PitGoSpacing.Large).widthIn(max = 600.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
    ) {
        if (loading) CircularProgressIndicator()
        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        onRetry?.let { Button(onClick = it) { Text(stringResource(R.string.action_retry)) } }
    }
}

@Composable
private fun FormWidth(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
    ) { content() }
}

@Composable
private fun rememberFieldFocusRequester(
    registry: MutableMap<ProviderField, FocusRequester>,
    field: ProviderField,
): FocusRequester {
    val requester = remember(field) { FocusRequester() }
    DisposableEffect(registry, field, requester) {
        registry[field] = requester
        onDispose {
            if (registry[field] === requester) registry.remove(field)
        }
    }
    return requester
}

@Composable
private fun fieldErrorMessage(
    error: ProviderFieldError,
    field: ProviderField? = null,
): String = when (error) {
    ProviderFieldError.REQUIRED -> when (field) {
        ProviderField.LocationMode -> stringResource(R.string.provider_mode_required_error)
        ProviderField.Offers -> stringResource(R.string.provider_offers_empty)
        is ProviderField.Offer -> when (field.field) {
            OfferField.CATEGORY -> stringResource(R.string.provider_category_required_error)
            OfferField.SERVICE -> stringResource(R.string.provider_service_required_error)
            OfferField.PRICE -> stringResource(R.string.provider_price_required_error)
            OfferField.DURATION -> stringResource(R.string.provider_duration_invalid_error)
            OfferField.DESCRIPTION -> stringResource(R.string.provider_required_error)
        }
        else -> stringResource(R.string.provider_required_error)
    }
    ProviderFieldError.INVALID_EMAIL -> stringResource(R.string.provider_email_invalid_error)
    ProviderFieldError.INVALID_PHONE -> stringResource(R.string.provider_phone_invalid_error)
    ProviderFieldError.INVALID_CNPJ -> stringResource(R.string.provider_cnpj_invalid_error)
    ProviderFieldError.INVALID_POSTAL_CODE -> stringResource(R.string.provider_zip_code_invalid_error)
    ProviderFieldError.POSTAL_CODE_NOT_FOUND -> stringResource(R.string.provider_zip_not_found)
    ProviderFieldError.INVALID_STATE -> stringResource(R.string.provider_state_invalid_error)
    ProviderFieldError.COORDINATE_PAIR_REQUIRED -> stringResource(R.string.provider_coordinates_pair_error)
    ProviderFieldError.COORDINATE_OUT_OF_RANGE -> if (field == ProviderField.Longitude) {
        stringResource(R.string.provider_longitude_invalid_error)
    } else {
        stringResource(R.string.provider_latitude_invalid_error)
    }
    ProviderFieldError.SERVICE_UNAVAILABLE -> stringResource(R.string.provider_service_unavailable_error)
    ProviderFieldError.DUPLICATE_SERVICE -> stringResource(R.string.provider_service_duplicate_error)
    ProviderFieldError.INVALID_PRICE -> stringResource(R.string.provider_price_invalid_error)
    ProviderFieldError.PRICE_MUST_BE_POSITIVE -> stringResource(R.string.provider_price_positive_error)
    ProviderFieldError.INVALID_DURATION -> stringResource(R.string.provider_duration_range_error)
    ProviderFieldError.TEXT_TOO_LONG -> stringResource(
        R.string.provider_text_too_long_error,
        ProviderRegistrationValidation.maximumLength(field ?: ProviderField.Description) ?: 1,
    )
    ProviderFieldError.UNAVAILABLE -> if (field == ProviderField.Cnpj) {
        stringResource(R.string.provider_cnpj_unavailable_error)
    } else {
        stringResource(R.string.provider_email_unavailable_error)
    }
}

@Composable
private fun failureMessage(failure: ProviderFailure): String = when (failure) {
    ProviderFailure.Offline -> stringResource(R.string.provider_failure_offline)
    ProviderFailure.Timeout -> stringResource(R.string.provider_failure_timeout)
    ProviderFailure.SessionExpired -> stringResource(R.string.provider_failure_session)
    ProviderFailure.ServiceUnavailable -> stringResource(R.string.provider_failure_service)
    ProviderFailure.Contract -> stringResource(R.string.provider_failure_contract)
    ProviderFailure.LocationUnavailable -> stringResource(R.string.provider_location_unavailable)
    ProviderFailure.CatalogChanged -> stringResource(R.string.provider_failure_catalog_changed)
    ProviderFailure.Unknown -> stringResource(R.string.provider_failure_unknown)
}

private fun formRowIndex(field: ProviderField, content: ProviderEditingContent): Int? = when (field) {
    ProviderField.LocationMode -> 2
    ProviderField.Name -> 3
    ProviderField.Description -> 4
    ProviderField.Email -> 5
    ProviderField.Phone -> 6
    ProviderField.Cnpj -> 7
    ProviderField.PostalCode -> 9
    ProviderField.Street -> 10
    ProviderField.Number -> 11
    ProviderField.Complement -> 12
    ProviderField.Neighborhood -> 13
    ProviderField.City -> 14
    ProviderField.State -> 15
    ProviderField.LocationVerification -> 17
    ProviderField.Offers -> 18
    is ProviderField.Offer -> {
        val catalogRowCount = if (content.categories is LoadState.Content) 0 else 1
        val emptyOffersRowCount = if (content.draft.offers.isEmpty()) 1 else 0
        val offerIndex = content.draft.offers.indexOfFirst { it.localId == field.localId }
        if (offerIndex < 0) null else 19 + catalogRowCount + emptyOffersRowCount + offerIndex
    }
    ProviderField.Latitude, ProviderField.Longitude -> 17
}
