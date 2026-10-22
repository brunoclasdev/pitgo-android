package com.trustarx.pitgo.features.rating.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.rating.model.*
import com.trustarx.pitgo.features.rating.viewmodel.ServiceRatingViewModel
import java.util.Locale

@Composable
fun ServiceRatingRoute(viewModel: ServiceRatingViewModel, onClose: () -> Unit, onSessionExpired: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.failure) { if (state.failure == RatingFailure.SESSION_EXPIRED) onSessionExpired() }
    ServiceRatingScreen(state, viewModel::stars, viewModel::comment, viewModel::submit, viewModel::load, onClose)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceRatingScreen(
    state: ServiceRatingUiState,
    onStars: (Int) -> Unit,
    onComment: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetryLoad: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.rating_title)) }, navigationIcon = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.guincho_back)) }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(PitGoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            if (state.providerName.isNotBlank()) Text(state.providerName, style = MaterialTheme.typography.titleLarge)
            when (state.phase) {
                RatingPhase.LOADING -> CircularProgressIndicator()
                RatingPhase.LOAD_ERROR -> {
                    Text(stringResource(R.string.rating_load_failure))
                    Button(onClick = onRetryLoad) { Text(stringResource(R.string.rating_retry)) }
                }
                RatingPhase.INELIGIBLE -> Text(stringResource(R.string.rating_not_available))
                RatingPhase.SAVED -> {
                    Text(stringResource(R.string.rating_saved), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.rating_selected, state.stars ?: 0))
                    if (state.comment.isNotBlank()) Text(state.comment)
                    Text(stringResource(R.string.rating_final), style = MaterialTheme.typography.bodyMedium)
                }
                RatingPhase.READY, RatingPhase.SUBMITTING -> {
                    Text(stringResource(R.string.rating_optional))
                    Text(stringResource(R.string.rating_choose), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.selectableGroup()) {
                        for (value in 1..5) {
                            val label = stringResource(R.string.rating_star_label, value)
                            IconToggleButton(
                                checked = state.stars == value,
                                onCheckedChange = { onStars(value) },
                                enabled = state.phase == RatingPhase.READY,
                                modifier = Modifier.size(48.dp).semantics { contentDescription = label },
                            ) {
                                Icon(
                                    painterResource(if (value <= (state.stars ?: 0)) R.drawable.ic_rating_star else R.drawable.ic_rating_star_outline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    state.stars?.let { Text(stringResource(R.string.rating_selected, it)) }
                    OutlinedTextField(
                        value = state.comment,
                        onValueChange = onComment,
                        enabled = state.phase == RatingPhase.READY,
                        label = { Text(stringResource(R.string.rating_comment)) },
                        supportingText = { Text(stringResource(R.string.rating_comment_count, state.comment.ratingCharacterCount())) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.failure?.let { Text(ratingFailureText(it), color = MaterialTheme.colorScheme.error) }
                    if (state.phase == RatingPhase.SUBMITTING) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.rating_sending))
                    }
                    Button(onClick = onSubmit, enabled = state.stars != null && state.phase == RatingPhase.READY, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (state.failure == null) R.string.rating_send else R.string.rating_retry))
                    }
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.phase == RatingPhase.SAVED) R.string.rating_done else R.string.rating_skip))
            }
        }
    }
}

@Composable
private fun ratingFailureText(failure: RatingFailure?): String = stringResource(when (failure) {
    RatingFailure.OFFLINE -> R.string.rating_offline
    RatingFailure.TIMEOUT -> R.string.rating_timeout
    RatingFailure.FORBIDDEN, RatingFailure.INELIGIBLE -> R.string.rating_not_available
    RatingFailure.INVALID_INPUT -> R.string.rating_invalid
    RatingFailure.SESSION_EXPIRED -> R.string.rating_session_expired
    else -> R.string.rating_failure
})

@Composable
fun ProviderRatingSummaryText(summary: ProviderRatingSummary?) {
    if (summary == null) return
    Text(if (summary.count == 0L) stringResource(R.string.rating_no_reviews)
        else pluralStringResource(R.plurals.rating_provider_summary, summary.count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            String.format(Locale.getDefault(), "%.1f", summary.average), summary.count))
}
