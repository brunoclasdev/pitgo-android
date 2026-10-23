package com.trustarx.pitgo.features.pittec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.ui.PitGoOutlinedCard
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchAction
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchPhase
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchStatus
import com.trustarx.pitgo.features.pittec.model.PitTecFailure
import com.trustarx.pitgo.features.pittec.model.PitTecLastEvent
import com.trustarx.pitgo.features.pittec.model.PitTecPendingAction
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
internal fun DispatchOverlay(
    phase: PitTecDispatchPhase,
    onRequestPitTec: () -> Unit,
    onCancelSearch: () -> Unit,
    onRetryDispatch: () -> Unit,
    onConfirmQuote: () -> Unit,
    onDeclineQuote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (phase) {
        PitTecDispatchPhase.None -> Button(
            onClick = onRequestPitTec,
            modifier = modifier.fillMaxWidth().padding(PitGoSpacing.Medium)
                .heightIn(min = PitGoSize.PrimaryActionMinHeight),
        ) { Text(stringResource(R.string.pittec_call)) }

        PitTecDispatchPhase.Restoring -> Unit
        PitTecDispatchPhase.Locating,
        PitTecDispatchPhase.StartingSearch,
        -> LoadingCard(modifier, stringResource(R.string.pittec_searching))
        is PitTecDispatchPhase.Searching -> when (phase.snapshot.status) {
            PitTecDispatchStatus.AWAITING_PROVIDER -> AwaitingProviderCard(phase, onCancelSearch, modifier)
            else -> SearchingCard(phase, onCancelSearch, modifier)
        }
        is PitTecDispatchPhase.AwaitingClientConfirmation -> QuoteCard(
            phase, onConfirmQuote, onDeclineQuote, modifier,
        )
        is PitTecDispatchPhase.AwaitingPayment -> LoadingCard(
            modifier, stringResource(R.string.pittec_awaiting_payment),
        )
        is PitTecDispatchPhase.TrackingReady -> Unit
        is PitTecDispatchPhase.Terminal -> MessageCard(
            modifier,
            stringResource(
                if (phase.status == PitTecDispatchStatus.CANCELLED) R.string.pittec_cancelled
                else R.string.pittec_call,
            ),
            onRetryDispatch,
        )
        is PitTecDispatchPhase.Failure -> MessageCard(modifier, failureTitle(phase.reason), onRetryDispatch)
    }
}

@Composable
private fun SearchingCard(
    phase: PitTecDispatchPhase.Searching,
    onCancelSearch: () -> Unit,
    modifier: Modifier,
) {
    val snapshot = phase.snapshot
    PitTecOutlinedCard(modifier) {
        val eventText = when (snapshot.lastEvent) {
            PitTecLastEvent.PROVIDER_REJECTED,
            PitTecLastEvent.PROVIDER_CANCELLED,
            -> R.string.pittec_rejected_search_continues
            PitTecLastEvent.OFFER_EXPIRED -> R.string.pittec_expired_search_continues
            else -> null
        }
        Text(
            stringResource(
                eventText ?: when (snapshot.status) {
                    PitTecDispatchStatus.AWAITING_PROVIDER -> R.string.pittec_awaiting
                    PitTecDispatchStatus.SEARCHING -> R.string.pittec_searching_persistent
                    else -> R.string.pittec_searching_persistent
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text(
            stringResource(
                if (snapshot.status == PitTecDispatchStatus.AWAITING_PROVIDER) {
                    R.string.pittec_status_awaiting_provider
                } else R.string.pittec_status_searching,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        phase.remainingSeconds?.let { Text(stringResource(R.string.mobile_dispatch_seconds, it)) }
        phase.failure?.let { Text(failureTitle(it), color = MaterialTheme.colorScheme.error) }
        OutlinedButton(
            onClick = onCancelSearch,
            enabled = phase.pending == null && PitTecDispatchAction.CANCEL in snapshot.allowedActions,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (phase.pending?.kind == PitTecPendingAction.CANCEL) {
                CircularProgressIndicator(Modifier.heightIn(max = PitGoSize.NavigationIcon))
            } else Text(stringResource(R.string.pittec_cancel_search))
        }
    }
}

@Composable
private fun AwaitingProviderCard(
    phase: PitTecDispatchPhase.Searching,
    onCancelSearch: () -> Unit,
    modifier: Modifier,
) {
    PitTecOutlinedCard(modifier) {
        Text(
            stringResource(R.string.pittec_awaiting),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text(stringResource(R.string.pittec_status_awaiting_provider))
        phase.remainingSeconds?.let { Text(stringResource(R.string.mobile_dispatch_seconds, it)) }
        phase.failure?.let { Text(failureTitle(it), color = MaterialTheme.colorScheme.error) }
        OutlinedButton(
            onClick = onCancelSearch,
            enabled = phase.pending == null && PitTecDispatchAction.CANCEL in phase.snapshot.allowedActions,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (phase.pending?.kind == PitTecPendingAction.CANCEL) {
                CircularProgressIndicator(Modifier.heightIn(max = PitGoSize.NavigationIcon))
            } else Text(stringResource(R.string.pittec_cancel_search))
        }
    }
}

@Composable
private fun QuoteCard(
    phase: PitTecDispatchPhase.AwaitingClientConfirmation,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier,
) {
    val snapshot = phase.snapshot
    val appointment = snapshot.appointment ?: return
    val quote = appointment.quote ?: snapshot.offer?.quote ?: return
    val providerName = snapshot.provider?.displayName ?: stringResource(R.string.pittec_provider_fallback)
    val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).apply {
        currency = Currency.getInstance(quote.currency)
    }
    val pending = phase.pending != null
    PitTecOutlinedCard(modifier) {
        Text(
            stringResource(R.string.pittec_quote_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text(stringResource(R.string.pittec_quote_provider, providerName))
        com.trustarx.pitgo.features.rating.ui.ProviderRatingSummaryText(snapshot.provider?.ratingSummary)
        Text(formatter.format(quote.amount), style = MaterialTheme.typography.headlineSmall)
        quote.durationMinutes?.let { Text(stringResource(R.string.service_duration_minutes, it)) }
        Text(stringResource(R.string.pittec_quote_deadline, phase.remainingSeconds))
        phase.failure?.let { Text(quoteFailureTitle(it), color = MaterialTheme.colorScheme.error) }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            OutlinedButton(
                onClick = onDecline,
                enabled = !pending && PitTecDispatchAction.DECLINE_QUOTE in snapshot.allowedActions,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.pittec_quote_decline)) }
            Button(
                onClick = onConfirm,
                enabled = !pending && phase.remainingSeconds > 0 &&
                    PitTecDispatchAction.CONFIRM_QUOTE in snapshot.allowedActions,
                modifier = Modifier.weight(1f),
            ) {
                if (pending) CircularProgressIndicator(Modifier.heightIn(max = PitGoSize.NavigationIcon))
                else Text(stringResource(R.string.pittec_quote_confirm))
            }
        }
    }
}

@Composable
private fun PitTecOutlinedCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    PitGoOutlinedCard(
        modifier = modifier.padding(PitGoSpacing.Medium),
        contentPadding = PitGoSpacing.Large,
        content = content,
    )
}

@Composable
private fun LoadingCard(modifier: Modifier, text: String) {
    PitTecOutlinedCard(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.heightIn(max = PitGoSize.NavigationIcon))
            Text(text)
        }
    }
}

@Composable
private fun MessageCard(modifier: Modifier, text: String, onRetry: () -> Unit) {
    PitTecOutlinedCard(modifier) {
        Text(text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun quoteFailureTitle(reason: PitTecFailure): String = stringResource(
    when (reason) {
        PitTecFailure.CONFIRMATION_EXPIRED -> R.string.pittec_quote_expired
        PitTecFailure.REVISION_CONFLICT -> R.string.pittec_quote_updated
        PitTecFailure.PRICING_UNAVAILABLE -> R.string.service_pricing_unavailable
        PitTecFailure.OFFLINE -> R.string.mobile_dispatch_offline
        PitTecFailure.TIMEOUT -> R.string.mobile_dispatch_timeout
        else -> R.string.pittec_quote_action_failed
    },
)

@Composable
private fun failureTitle(reason: PitTecFailure): String = stringResource(
    when (reason) {
        PitTecFailure.PRICING_UNAVAILABLE -> R.string.service_pricing_unavailable
        PitTecFailure.OFFLINE -> R.string.mobile_dispatch_offline
        PitTecFailure.TIMEOUT -> R.string.mobile_dispatch_timeout
        PitTecFailure.LOCATION_REQUIRED -> R.string.mobile_dispatch_location_message
        PitTecFailure.LOCATION_UNAVAILABLE -> R.string.mobile_dispatch_location_unavailable_message
        else -> R.string.mobile_dispatch_unavailable
    },
)
