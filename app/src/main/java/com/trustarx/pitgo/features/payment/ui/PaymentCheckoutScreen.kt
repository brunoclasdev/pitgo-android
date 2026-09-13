package com.trustarx.pitgo.features.payment.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    onApproved: (requestId: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState is PaymentCheckoutUiState.Approved) {
        onApproved(viewModel.requestId)
        return
    }
    PaymentCheckoutScreen(
        uiState = uiState,
        onBack = onBack,
        onPayWithPix = viewModel::payWithPix,
        onPayWithSavedCard = viewModel::payWithSavedCard,
        onRetry = viewModel::retry,
        onChooseAnotherMethod = viewModel::chooseAnotherMethod,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentCheckoutScreen(
    uiState: PaymentCheckoutUiState,
    onBack: () -> Unit,
    onPayWithPix: () -> Unit,
    onPayWithSavedCard: (String) -> Unit,
    onRetry: () -> Unit,
    onChooseAnotherMethod: () -> Unit,
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
                PaymentCheckoutUiState.Loading, PaymentCheckoutUiState.CreatingPayment ->
                    CircularProgressIndicator(modifier = Modifier.padding(top = PitGoSpacing.Large))

                PaymentCheckoutUiState.InvalidService, PaymentCheckoutUiState.Failure -> FailureContent(onRetry)

                is PaymentCheckoutUiState.SelectingMethod ->
                    SelectingMethodContent(uiState.amount, uiState.savedCards, onPayWithPix, onPayWithSavedCard)

                is PaymentCheckoutUiState.AwaitingPix -> AwaitingPixContent(uiState, onRetry, onChooseAnotherMethod)
                PaymentCheckoutUiState.AwaitingCardConfirmation ->
                    CircularProgressIndicator(modifier = Modifier.padding(top = PitGoSpacing.Large))

                PaymentCheckoutUiState.Declined -> DeclinedContent(onChooseAnotherMethod)
                PaymentCheckoutUiState.Expired -> ExpiredContent(onRetry, onChooseAnotherMethod)
                PaymentCheckoutUiState.Approved -> Unit // handled by the Route before reaching here
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
    val bitmap = remember(state.pix.qrCodeBase64) {
        runCatching {
            val bytes = Base64.decode(state.pix.qrCodeBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = stringResource(R.string.payment_pix_qr_description),
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(PitGoSpacing.Small)),
        )
    }
    Button(
        onClick = { clipboard.setText(AnnotatedString(state.pix.qrCode)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
    ) { Text(stringResource(R.string.payment_pix_copy_code)) }
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
