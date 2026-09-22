package com.trustarx.pitgo.features.payment.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.payment.model.PaymentCheckoutUiState
import com.trustarx.pitgo.features.payment.viewmodel.PaymentCheckoutViewModel
import com.trustarx.pitgo.features.paymentmethods.model.SavedCard
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val brlAmountFormat = DecimalFormat(
    "#,##0.00",
    DecimalFormatSymbols(Locale.Builder().setLanguage("pt").setRegion("BR").build()),
)

private fun formatBrl(amount: BigDecimal): String = brlAmountFormat.format(amount)

@Composable
fun PaymentCheckoutRoute(
    viewModel: PaymentCheckoutViewModel,
    onBack: () -> Unit,
    onCancel: () -> Unit = onBack,
    onApproved: (requestId: String) -> Unit,
    onManagePaymentMethods: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onActive()
        onStopOrDispose { viewModel.onInactive() }
    }
    (uiState as? PaymentCheckoutUiState.ConfirmingCard)?.let {
        SavedCardSecurityCodeSheet(it.context, viewModel::chooseAnotherMethod, viewModel::confirmCard)
    }
    if (uiState is PaymentCheckoutUiState.Approved) {
        LaunchedEffect(viewModel.requestId) { onApproved(viewModel.requestId) }
        return
    }
    PaymentCheckoutScreen(
        uiState = uiState,
        onBack = { viewModel.cancelPayment(onCancel) },
        onCancel = { viewModel.cancelPayment(onCancel) },
        onPayWithPix = viewModel::payWithPix,
        onPayWithSavedCard = viewModel::payWithSavedCard,
        onRetry = viewModel::retry,
        onChooseAnotherMethod = viewModel::chooseAnotherMethod,
        onManagePaymentMethods = onManagePaymentMethods,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentCheckoutScreen(
    uiState: PaymentCheckoutUiState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onPayWithPix: () -> Unit,
    onPayWithSavedCard: (String) -> Unit,
    onRetry: () -> Unit,
    onChooseAnotherMethod: () -> Unit,
    onManagePaymentMethods: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.payment_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(PitGoSize.MinimumTouchTarget),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
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
                is PaymentCheckoutUiState.FinancialStatus -> {
                    Text(paymentStatusLabel(uiState.payment))
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.payment_check_status)) }
                }
                is PaymentCheckoutUiState.ConfirmingCard -> Unit
                PaymentCheckoutUiState.Loading, PaymentCheckoutUiState.CreatingPayment ->
                    CircularProgressIndicator(modifier = Modifier.padding(top = PitGoSpacing.Large))

                PaymentCheckoutUiState.Cancelling -> {
                    Text(stringResource(R.string.payment_cancelling))
                    CircularProgressIndicator(modifier = Modifier.padding(top = PitGoSpacing.Large))
                }

                PaymentCheckoutUiState.Verifying -> {
                    Text(stringResource(R.string.payment_verifying))
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.payment_check_status)) }
                }

                PaymentCheckoutUiState.InvalidService, PaymentCheckoutUiState.Failure -> FailureContent(onRetry)

                is PaymentCheckoutUiState.SelectingMethod ->
                    SelectingMethodContent(uiState.amount, uiState.savedCards, onPayWithPix, onPayWithSavedCard, onManagePaymentMethods)

                is PaymentCheckoutUiState.AwaitingPix -> AwaitingPixContent(uiState, onRetry, onChooseAnotherMethod)
                PaymentCheckoutUiState.AwaitingCardConfirmation ->
                    CircularProgressIndicator(modifier = Modifier.padding(top = PitGoSpacing.Large))

                PaymentCheckoutUiState.Declined -> DeclinedContent(onChooseAnotherMethod)
                PaymentCheckoutUiState.Expired -> ExpiredContent(onRetry, onChooseAnotherMethod)
                PaymentCheckoutUiState.Approved -> Unit // handled by the Route before reaching here
            }

            // The top app-bar back icon is not sufficiently discoverable during checkout. Keep an
            // explicit cancellation action in the content so the client can leave before paying.
            if (uiState !is PaymentCheckoutUiState.Approved && uiState !is PaymentCheckoutUiState.FinancialStatus
                && uiState !is PaymentCheckoutUiState.CreatingPayment && uiState !is PaymentCheckoutUiState.Cancelling
                && uiState !is PaymentCheckoutUiState.Loading) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
                ) { Text(stringResource(R.string.payment_cancel)) }
            }
        }
    }
}

@Composable
private fun SelectingMethodContent(
    amount: BigDecimal,
    savedCards: List<SavedCard>,
    onPayWithPix: () -> Unit,
    onPayWithSavedCard: (String) -> Unit,
    onManagePaymentMethods: () -> Unit,
) {
    Text(
        stringResource(R.string.request_estimate_value, formatBrl(amount)),
        style = MaterialTheme.typography.headlineSmall,
    )
    Button(
        onClick = onPayWithPix,
        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
    ) { Text(stringResource(R.string.payment_method_pix)) }
    if (savedCards.isEmpty()) {
        Text(
            stringResource(R.string.payment_no_saved_cards_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onManagePaymentMethods,
            modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
        ) { Text(stringResource(R.string.payment_methods_add_action)) }
    } else {
        savedCards.forEach { card ->
            OutlinedButton(
                onClick = { onPayWithSavedCard(card.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
            ) { Text(stringResource(R.string.payment_method_saved_card_ending, card.lastFourDigits)) }
        }
    }
}

@Composable
private fun AwaitingPixContent(
    state: PaymentCheckoutUiState.AwaitingPix,
    onRetry: () -> Unit,
    onChooseAnotherMethod: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val bitmap = remember(state.pix.qrCodeBase64) {
        runCatching {
            val bytes = Base64.decode(state.pix.qrCodeBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_payment_pix),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(PitGoSpacing.Small))
                Text(stringResource(R.string.payment_pix_heading), style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                stringResource(R.string.payment_pix_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.request_estimate_value, formatBrl(state.amount)),
                style = MaterialTheme.typography.titleLarge,
            )
            Card(
                modifier = Modifier.padding(vertical = PitGoSpacing.Small),
                shape = MaterialTheme.shapes.large,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.payment_pix_qr_description),
                    modifier = Modifier
                        .size(236.dp)
                        .padding(PitGoSpacing.Small)
                        .clip(RoundedCornerShape(PitGoSpacing.Small)),
                )
            }
            Text(
                stringResource(R.string.payment_pix_valid_until,
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                        .format(java.util.Date(state.pix.expiresAtEpochMillis))),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else {
        Text(
            stringResource(R.string.payment_pix_unavailable_message),
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.payment_retry))
        }
    }
    if (bitmap != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(PitGoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                Text(stringResource(R.string.payment_pix_copy_label), style = MaterialTheme.typography.labelLarge)
                Text(
                    state.pix.qrCode,
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(state.pix.qrCode))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(PitGoSpacing.Small))
                    Text(stringResource(R.string.payment_pix_copy_code))
                }
            }
        }
    }
    if (copied) Text(stringResource(R.string.payment_pix_code_copied), color = MaterialTheme.colorScheme.primary)
    OutlinedButton(
        onClick = onChooseAnotherMethod,
        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
    ) { Text(stringResource(R.string.payment_choose_another_method)) }
}

@Composable
private fun DeclinedContent(onChooseAnotherMethod: () -> Unit) {
    Text(stringResource(R.string.payment_declined_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.payment_declined_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(
        onClick = onChooseAnotherMethod,
        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
    ) { Text(stringResource(R.string.payment_try_another_method)) }
}

@Composable
private fun ExpiredContent(onRetry: () -> Unit, onChooseAnotherMethod: () -> Unit) {
    Text(stringResource(R.string.payment_pix_expired_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.payment_pix_expired_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
    ) { Text(stringResource(R.string.payment_generate_new_code)) }
    OutlinedButton(
        onClick = onChooseAnotherMethod,
        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
    ) { Text(stringResource(R.string.payment_choose_another_method)) }
}

@Composable
private fun FailureContent(onRetry: () -> Unit) {
    Text(stringResource(R.string.payment_failure_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.payment_failure_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
    ) { Text(stringResource(R.string.payment_retry)) }
}
