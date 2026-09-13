package com.trustarx.pitgo.features.pittec.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
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

/**
 * Stateless bottom-card family for every pre-accept PitTec dispatch phase (US2). `ConfirmingValues`
 * (`019`) is the one exception past ACCEPTED — it only shows a brief transitional loading state
 * while `PitTecRoute` opens the real payment screen (`018`) and waits for approval; once approved,
 * the screen navigates away to the shared AppointmentTracking flow, so nothing further is modeled
 * here.
 */
@Composable
internal fun DispatchOverlay(
    phase: PitTecDispatchPhase,
    onRequestPitTec: () -> Unit,
    onCancelSearch: () -> Unit,
    onRetryDispatch: () -> Unit,
    onConfirmCandidate: () -> Unit,
    onDeclineCandidate: () -> Unit,
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

        is PitTecDispatchPhase.Active -> ActiveDispatchCard(phase, onCancelSearch, modifier)

        is PitTecDispatchPhase.CandidateFound -> FoundCandidateCard(
            phase, onConfirmCandidate, onDeclineCandidate, modifier,
        )

        is PitTecDispatchPhase.ConfirmingValues -> LoadingCard(modifier, stringResource(R.string.pittec_confirming_values))

        is PitTecDispatchPhase.NoProvider -> MessageCard(
            modifier, stringResource(R.string.pittec_none_nearby), onRetryDispatch,
        )

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

/**
 * Candidato encontrado, ainda não aceito pelo prestador (`019-pittec-request-flow`, US2) — nome e
 * distância aproximada, sem nenhuma cobrança neste ponto. "Recusar" cancela este appointment e a
 * busca automática segue para o próximo candidato sozinha.
 */
@Composable
private fun FoundCandidateCard(
    phase: PitTecDispatchPhase.CandidateFound,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier,
) {
    val candidate = phase.snapshot.candidate ?: return
    PitTecOutlinedCard(modifier) {
        Text(
            stringResource(R.string.pittec_candidate_found_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text(candidate.providerName)
        Text(
            stringResource(
                R.string.pittec_candidate_distance,
                formatDistanceMeters(candidate.approximateDistanceMeters),
            ),
        )
        val isPending = phase.pending != null
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            OutlinedButton(onClick = onDecline, enabled = !isPending, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pittec_candidate_decline))
            }
            Button(onClick = onConfirm, enabled = !isPending, modifier = Modifier.weight(1f)) {
                if (isPending) {
                    CircularProgressIndicator(Modifier.heightIn(max = PitGoSize.NavigationIcon))
                } else Text(stringResource(R.string.pittec_candidate_confirm))
            }
        }
    }
}

/** Every PitTec overlay card shares the app's standard outlined margin/padding. */
@Composable
private fun PitTecOutlinedCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    PitGoOutlinedCard(
        modifier = modifier.padding(PitGoSpacing.Medium),
        contentPadding = PitGoSpacing.Large,
        content = content,
    )
}

@Composable
private fun ActiveDispatchCard(
    active: PitTecDispatchPhase.Active,
    onCancelSearch: () -> Unit,
    modifier: Modifier,
) {
    val snapshot = active.snapshot
    PitTecOutlinedCard(modifier) {
        val eventText = when (snapshot.lastEvent) {
            PitTecLastEvent.PROVIDER_REJECTED -> R.string.pittec_rejected
            PitTecLastEvent.OFFER_EXPIRED -> R.string.pittec_expired
            else -> null
        }
        Text(
            stringResource(
                eventText ?: when (snapshot.status) {
                    PitTecDispatchStatus.AWAITING_PROVIDER -> R.string.pittec_awaiting
                    else -> R.string.pittec_searching
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        active.remainingSeconds?.let { Text(stringResource(R.string.mobile_dispatch_seconds, it)) }
        active.failure?.let { Text(failureTitle(it), color = MaterialTheme.colorScheme.error) }
        OutlinedButton(
            onClick = onCancelSearch,
            enabled = active.pending == null && PitTecDispatchAction.CANCEL in snapshot.allowedActions,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (active.pending?.kind == PitTecPendingAction.CANCEL) {
                CircularProgressIndicator(Modifier.heightIn(max = PitGoSize.NavigationIcon))
            } else Text(stringResource(R.string.pittec_cancel_search))
        }
    }
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
private fun failureTitle(reason: PitTecFailure): String = stringResource(
    when (reason) {
        PitTecFailure.OFFLINE -> R.string.mobile_dispatch_offline
        PitTecFailure.TIMEOUT -> R.string.mobile_dispatch_timeout
        PitTecFailure.LOCATION_REQUIRED -> R.string.mobile_dispatch_location_message
        PitTecFailure.LOCATION_UNAVAILABLE -> R.string.mobile_dispatch_location_unavailable_message
        else -> R.string.mobile_dispatch_unavailable
    },
)
