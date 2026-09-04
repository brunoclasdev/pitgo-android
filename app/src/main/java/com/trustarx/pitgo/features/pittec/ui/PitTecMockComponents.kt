package com.trustarx.pitgo.features.pittec.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.ui.PitGoOutlinedCard
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.pittec.model.MockSearch
import com.trustarx.pitgo.features.pittec.model.MockSearchFeedback
import com.trustarx.pitgo.features.pittec.model.MockServiceRequest
import com.trustarx.pitgo.features.pittec.model.ServiceEstimate
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** Cent-based totals are only ever formatted at the UI edge (data-model.md invariant). */
internal fun formatCents(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(cents / 100.0)

/** Every PitTec overlay card shares the app's standard outlined margin/padding. */
@Composable
private fun PitTecOutlinedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    PitGoOutlinedCard(
        modifier = modifier.padding(PitGoSpacing.Medium),
        contentPadding = PitGoSpacing.Large,
        content = content,
    )
}

/**
 * Persistent purple incoming-request card (US1). Plays exactly three ~0.7s pulses once per
 * request revision, skipping the animation entirely when [MotionDurationScale.scaleFactor] is
 * zero — the card, its text and both actions are identical either way (FR-004/FR-006).
 */
@Composable
fun IncomingRequestCard(
    request: MockServiceRequest,
    pulseConsumed: Boolean,
    responded: Boolean,
    onPulseStarted: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulseAlpha = remember(request.revision) { Animatable(0f) }
    LaunchedEffect(request.revision, pulseConsumed) {
        if (pulseConsumed) return@LaunchedEffect
        // Marked consumed before the effect body runs so recomposition/navigation never replays it.
        onPulseStarted()
        val scaleFactor = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scaleFactor == 0f) return@LaunchedEffect
        repeat(3) {
            pulseAlpha.animateTo(1f, tween(350))
            pulseAlpha.animateTo(0f, tween(350))
        }
    }
    val purple = MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(2.dp, purple),
        modifier = modifier.fillMaxWidth().padding(PitGoSpacing.Medium)
            .background(purple.copy(alpha = pulseAlpha.value * 0.25f))
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(
            Modifier.padding(PitGoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            Text(
                stringResource(R.string.pittec_mock_incoming_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.pittec_mock_incoming_client, request.client.displayName))
            Text(
                stringResource(R.string.pittec_mock_incoming_perimeter, request.estimate.perimeter.kilometers),
            )
            Text(
                stringResource(
                    R.string.pittec_mock_incoming_distance,
                    formatDistanceMeters(request.approximateDistanceMeters),
                ),
            )
            Text(
                stringResource(R.string.pittec_mock_incoming_total, formatCents(request.estimate.totalCents)),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !responded,
                    modifier = Modifier.weight(1f).heightIn(min = PitGoSize.MinimumTouchTarget),
                ) { Text(stringResource(R.string.pittec_mock_decline)) }
                Button(
                    onClick = onAccept,
                    enabled = !responded,
                    modifier = Modifier.weight(1f).heightIn(min = PitGoSize.MinimumTouchTarget),
                ) { Text(stringResource(R.string.pittec_mock_accept)) }
            }
        }
    }
}

@Composable
fun EstimatingCard(modifier: Modifier = Modifier) {
    PitTecOutlinedCard(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.heightIn(max = PitGoSize.NavigationIcon))
            Text(stringResource(R.string.pittec_mock_estimating))
        }
    }
}

@Composable
fun EstimateCard(
    estimate: ServiceEstimate,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PitTecOutlinedCard(modifier) {
        Text(
            stringResource(R.string.pittec_mock_estimate_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.pittec_mock_estimate_perimeter, estimate.perimeter.kilometers))
        Text(stringResource(R.string.pittec_mock_estimate_fixed, formatCents(estimate.fixedCents)))
        Text(
            stringResource(
                R.string.pittec_mock_estimate_additional,
                estimate.bandCount,
                formatCents(estimate.distanceAdditionalCents),
            ),
        )
        Text(
            stringResource(R.string.pittec_mock_estimate_total, formatCents(estimate.totalCents)),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).heightIn(min = PitGoSize.MinimumTouchTarget),
            ) { Text(stringResource(R.string.action_back)) }
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f).heightIn(min = PitGoSize.MinimumTouchTarget),
            ) { Text(stringResource(R.string.pittec_mock_confirm)) }
        }
    }
}

@Composable
fun SearchFeedbackCard(search: MockSearch, modifier: Modifier = Modifier) {
    PitTecOutlinedCard(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (search.feedback) {
            MockSearchFeedback.SEARCHING -> LoadingRow(stringResource(R.string.pittec_mock_searching))
            MockSearchFeedback.AWAITING_RESPONSE -> LoadingRow(stringResource(R.string.pittec_mock_awaiting_response))
            MockSearchFeedback.DECLINED_CONTINUING -> Text(stringResource(R.string.pittec_mock_declined_continuing))
            MockSearchFeedback.FOUND -> Text(stringResource(R.string.pittec_mock_found))
            MockSearchFeedback.NOT_FOUND -> Text(stringResource(R.string.pittec_mock_not_found))
        }
        Text(stringResource(R.string.pittec_mock_estimate_perimeter, search.currentPerimeter.kilometers))
        Text(stringResource(R.string.pittec_mock_estimate_total, formatCents(search.estimate.totalCents)))
    }
}

@Composable
fun NoProviderCard(onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PitTecOutlinedCard(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            stringResource(R.string.pittec_mock_no_provider_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.pittec_mock_no_provider_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).heightIn(min = PitGoSize.MinimumTouchTarget),
            ) { Text(stringResource(R.string.action_back)) }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).heightIn(min = PitGoSize.MinimumTouchTarget),
            ) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.heightIn(max = PitGoSize.NavigationIcon))
        Text(text)
    }
}

/**
 * Bare "540 m" / "7,4 km" distance value — every call site supplies its own label (e.g.
 * "Distância restante: %s"), so this must not carry a prefix of its own (unlike
 * R.string.mobile_dispatch_distance/service_map_distance, which already do).
 */
internal fun formatDistanceMeters(meters: Int): String = if (meters < 1000) {
    "$meters m"
} else {
    String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", meters / 1000.0)
}
