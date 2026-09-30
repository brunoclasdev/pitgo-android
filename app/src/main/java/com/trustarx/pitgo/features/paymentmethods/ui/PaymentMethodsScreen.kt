package com.trustarx.pitgo.features.paymentmethods.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercadopago.sdk.android.coremethods.domain.interactor.coreMethods
import com.mercadopago.sdk.android.coremethods.domain.model.BuyerIdentification
import com.mercadopago.sdk.android.coremethods.domain.model.CardToken
import com.mercadopago.sdk.android.coremethods.domain.model.ResultError
import com.mercadopago.sdk.android.coremethods.domain.utils.Result as CoreMethodsResult
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.cardnumber.CardNumberTextField
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.expirationdate.ExpirationDateTextField
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.pcitextfield.PCIFieldState
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.securitycode.SecurityCodeTextField
import com.mercadopago.sdk.android.initializer.MercadoPagoSDK
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.model.Profile
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.paymentmethods.model.PaymentMethodsUiState
import com.trustarx.pitgo.features.paymentmethods.model.SavedCard
import com.trustarx.pitgo.features.paymentmethods.model.SavedCardFailure
import com.trustarx.pitgo.features.paymentmethods.viewmodel.PaymentMethodsViewModel
import kotlinx.coroutines.launch

@Composable
fun PaymentMethodsRoute(
    viewModel: PaymentMethodsViewModel,
    profile: Profile,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PaymentMethodsScreen(
        uiState = uiState,
        profile = profile,
        simulated = viewModel.simulated,
        onBack = onBack,
        onCardTokenGenerated = viewModel::registerCard,
        onDismissRegisterFailure = viewModel::dismissRegisterFailure,
        onRemove = viewModel::removeCard,
        onRetryLoad = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodsScreen(
    uiState: PaymentMethodsUiState,
    profile: Profile,
    simulated: Boolean,
    onBack: () -> Unit,
    onCardTokenGenerated: (String) -> Unit,
    onDismissRegisterFailure: () -> Unit,
    onRemove: (String) -> Unit,
    onRetryLoad: () -> Unit,
) {
    var showRegistrationForm by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.payment_methods_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(PitGoSize.MinimumTouchTarget)) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
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
            when (uiState) {
                PaymentMethodsUiState.Loading -> CircularProgressIndicator()
                PaymentMethodsUiState.Failure -> {
                    Text(stringResource(R.string.payment_failure_message))
                    Button(onClick = onRetryLoad) { Text(stringResource(R.string.payment_retry)) }
                }
                is PaymentMethodsUiState.Listing -> {
                    CardsList(uiState.cards, onRemove, null) { if (simulated) onCardTokenGenerated("simulated") else showRegistrationForm = true }
                    if (uiState.removalFailed) Text(stringResource(R.string.payment_card_remove_error))
                }
                is PaymentMethodsUiState.Removing -> CardsList(uiState.cards, onRemove, uiState.removingId) {
                    if (simulated) onCardTokenGenerated("simulated") else showRegistrationForm = true
                }
                PaymentMethodsUiState.Registering -> CircularProgressIndicator()
                is PaymentMethodsUiState.RegisterFailed -> {
                    CardsList(uiState.cards, onRemove, null) { if (simulated) onCardTokenGenerated("simulated") else showRegistrationForm = true }
                    Text(stringResource(R.string.payment_methods_register_failed_title))
                    Text(
                        registerFailureMessage(uiState.reason),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onDismissRegisterFailure) { Text(stringResource(R.string.action_cancel)) }
                }
            }
            if (showRegistrationForm && !simulated) {
                CardRegistrationForm(
                    profile = profile,
                    onCancel = { showRegistrationForm = false },
                    onTokenGenerated = { token ->
                        showRegistrationForm = false
                        onCardTokenGenerated(token)
                    },
                )
            }
        }
    }
}

@Composable
private fun registerFailureMessage(reason: SavedCardFailure): String = when (reason) {
    SavedCardFailure.CARD_REJECTED -> stringResource(R.string.payment_card_save_rejected)
    SavedCardFailure.SESSION_EXPIRED -> stringResource(R.string.payment_card_save_session_expired)
    SavedCardFailure.OFFLINE, SavedCardFailure.TIMEOUT -> stringResource(R.string.payment_card_save_network_error)
    SavedCardFailure.SERVICE_UNAVAILABLE -> stringResource(R.string.payment_card_save_service_error)
    else -> stringResource(R.string.payment_methods_register_failed_message)
}

@Composable
private fun CardsList(
    cards: List<SavedCard>,
    onRemove: (String) -> Unit,
    removingId: String?,
    onAdd: () -> Unit,
) {
    if (cards.isEmpty()) {
        Text(stringResource(R.string.payment_methods_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.payment_methods_empty_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        cards.forEach { card ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(PitGoSpacing.Medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.payment_method_saved_card_ending, card.lastFourDigits))
                    if (removingId == card.id) {
                        CircularProgressIndicator(modifier = Modifier.size(PitGoSize.FeatureIcon))
                    } else {
                        TextButton(onClick = { onRemove(card.id) }) {
                            Text(stringResource(R.string.payment_methods_remove_action))
                        }
                    }
                }
            }
        }
    }
    Button(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
    ) { Text(stringResource(R.string.payment_methods_add_action)) }
}

/**
 * Tokenization happens entirely inside the Mercado Pago SDK's Secure Fields — this composable
 * never sees the raw card number, expiration or CVV (FR-006); it only receives the resulting
 * short-lived token to hand off to `mp-save-card`.
 */
@Composable
private fun CardRegistrationForm(
    profile: Profile,
    onCancel: () -> Unit,
    onTokenGenerated: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isTokenizing by remember { mutableStateOf(false) }
    var tokenizationFailed by remember { mutableStateOf(false) }
    var tokenizationMessage by remember { mutableStateOf<String?>(null) }
    var cardholderName by rememberSaveable { mutableStateOf(profile.name) }
    var cardholderCpf by rememberSaveable { mutableStateOf(profile.cpf.orEmpty()) }
    val cardNumberState = remember { PCIFieldState.Companion.create() }
    val expirationDateState = remember { PCIFieldState.Companion.create() }
    val securityCodeState = remember { PCIFieldState.Companion.create() }
    val cpfRequiredMessage = stringResource(R.string.payment_card_cpf_required)
    val holderNameRequiredMessage = stringResource(R.string.payment_cardholder_name_required)
    val networkErrorMessage = stringResource(R.string.payment_card_token_network_error)
    val tokenErrorMessage = stringResource(R.string.payment_card_token_error)

    Text(stringResource(R.string.payment_cardholder_name_label), style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = cardholderName,
        onValueChange = { cardholderName = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    Text(stringResource(R.string.payment_cardholder_cpf_label), style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = cardholderCpf,
        onValueChange = { value -> cardholderCpf = value.filter(Char::isDigit).take(11) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        ),
    )

    Text(stringResource(R.string.payment_methods_register_title), style = MaterialTheme.typography.titleMedium)

    // The Secure Fields below are plain input boxes with no label of their own (PCI compliance
    // keeps their internal content out of our composition), so the field names are ours to add.
    Text(stringResource(R.string.payment_methods_card_number_label), style = MaterialTheme.typography.labelLarge)
    CardNumberTextField(state = cardNumberState, onEvent = {}, modifier = Modifier.fillMaxWidth())

    Text(stringResource(R.string.payment_methods_expiration_label), style = MaterialTheme.typography.labelLarge)
    ExpirationDateTextField(modifier = Modifier.fillMaxWidth(), state = expirationDateState, onEvent = {})

    Text(stringResource(R.string.payment_methods_security_code_label), style = MaterialTheme.typography.labelLarge)
    SecurityCodeTextField(modifier = Modifier.fillMaxWidth(), state = securityCodeState, onEvent = {})

    if (tokenizationFailed) {
        Text(tokenizationMessage ?: tokenErrorMessage)
    }
    if (isTokenizing) {
        CircularProgressIndicator()
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(
                onClick = {
                    val cpf = cardholderCpf.filter(Char::isDigit)
                    if (cardholderName.isBlank()) {
                        tokenizationFailed = true
                        tokenizationMessage = holderNameRequiredMessage
                        return@Button
                    }
                    if (cpf.length != 11) {
                        tokenizationFailed = true
                        tokenizationMessage = cpfRequiredMessage
                        return@Button
                    }
                    isTokenizing = true
                    tokenizationFailed = false
                    tokenizationMessage = null
                    scope.launch {
                        val buyer = BuyerIdentification(cardholderName.trim(), cpf, "CPF")
                        val result = MercadoPagoSDK.getInstance().coreMethods.generateCardToken(
                            cardNumberState, expirationDateState, securityCodeState, buyer,
                        )
                        isTokenizing = false
                        val token = (result as? CoreMethodsResult.Success<*>)?.data as? CardToken
                        tokenizationFailed = token?.token.isNullOrBlank()
                        tokenizationMessage = if (tokenizationFailed) {
                            when (val error = (result as? CoreMethodsResult.Error<*>)?.error) {
                                is ResultError.Validation -> error.message
                                is ResultError.Request -> networkErrorMessage
                                else -> tokenErrorMessage
                            }
                        } else null
                        token?.token?.let(onTokenGenerated)
                    }
                },
            ) { Text(stringResource(R.string.payment_methods_register_action)) }
        }
    }
}
