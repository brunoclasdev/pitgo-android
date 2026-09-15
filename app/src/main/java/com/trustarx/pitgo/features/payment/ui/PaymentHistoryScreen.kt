package com.trustarx.pitgo.features.payment.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.features.payment.model.*
import com.trustarx.pitgo.features.payment.viewmodel.PaymentHistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryScreen(viewModel: PaymentHistoryViewModel, onBack: () -> Unit, onOpen: (Payment) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load(refresh = true) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.payment_history)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.payments, key = { it.id }) { payment ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR")).format(payment.amount))
                        Text(paymentStatusLabel(payment))
                        Text(if (payment.method == PaymentMethod.PIX) stringResource(R.string.payment_method_pix)
                            else stringResource(R.string.payment_method_saved_card_ending, payment.cardLastFourDigits ?: "••••"))
                        if (payment.blocksNewAttempt) OutlinedButton(onClick = { onOpen(payment) }) {
                            Text(stringResource(R.string.payment_resume))
                        }
                    }
                }
            }
            item {
                if (state.loading) CircularProgressIndicator()
                else if (state.failed) {
                    Text(stringResource(R.string.payment_failure_message))
                    Button(onClick = { viewModel.load() }) { Text(stringResource(R.string.payment_retry)) }
                } else if (state.payments.isEmpty()) Text(stringResource(R.string.payment_history_empty))
                else if (state.hasMore) OutlinedButton(onClick = { viewModel.load() }) { Text(stringResource(R.string.payment_history_more)) }
            }
        }
    }
}

@Composable
fun paymentStatusLabel(payment: Payment): String = stringResource(
    if (payment.status == PaymentStatus.REFUNDED) R.string.payment_refunded
    else if (payment.status == PaymentStatus.REFUND_FAILED || payment.supportRequired) R.string.payment_support
    else if (payment.syncState != PaymentSyncState.CURRENT) R.string.payment_verifying
    else when (payment.status) {
        PaymentStatus.APPROVED -> R.string.payment_confirmed
        PaymentStatus.PENDING -> if (payment.cancelRequested) R.string.payment_cancelling else R.string.payment_pending
        PaymentStatus.REJECTED -> R.string.payment_declined_title
        PaymentStatus.EXPIRED -> R.string.payment_pix_expired_title
        PaymentStatus.CANCELLED -> R.string.payment_cancelled
        PaymentStatus.REFUND_PENDING -> R.string.payment_refund_pending
        PaymentStatus.REFUNDED -> R.string.payment_refunded
        PaymentStatus.REFUND_FAILED -> R.string.payment_support
    },
)
