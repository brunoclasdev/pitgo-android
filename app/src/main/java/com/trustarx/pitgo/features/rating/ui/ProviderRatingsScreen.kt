package com.trustarx.pitgo.features.rating.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.rating.model.*
import com.trustarx.pitgo.features.rating.viewmodel.ProviderRatingsViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProviderRatingProfileSection(viewModel: ProviderRatingsViewModel, onOpenComments: () -> Unit, onSessionExpired: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadSummary() }
    LaunchedEffect(state.summaryFailure) { if (state.summaryFailure == RatingFailure.SESSION_EXPIRED) onSessionExpired() }
    Column(verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
        ProviderRatingSummaryContent(state, viewModel::loadSummary)
        OutlinedButton(onClick = onOpenComments, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.rating_open_comments))
        }
    }
}

@Composable
fun ProviderRatingsRoute(viewModel: ProviderRatingsViewModel, onBack: () -> Unit, onSessionExpired: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadSummary()
        viewModel.loadComments()
    }
    LaunchedEffect(state.summaryFailure, state.commentsFailure) {
        if (state.summaryFailure == RatingFailure.SESSION_EXPIRED || state.commentsFailure == RatingFailure.SESSION_EXPIRED) onSessionExpired()
    }
    ProviderRatingsScreen(state, onBack, viewModel::loadSummary, viewModel::retryComments, { viewModel.loadComments(more = true) })
}

@Composable
private fun ProviderRatingSummaryContent(state: ProviderRatingsUiState, onRetry: () -> Unit) {
    Text(stringResource(R.string.rating_your_reviews), style = MaterialTheme.typography.titleMedium)
    when (state.summaryPhase) {
        ProviderRatingLoadPhase.NOT_LOADED, ProviderRatingLoadPhase.LOADING -> LinearProgressIndicator(Modifier.fillMaxWidth())
        ProviderRatingLoadPhase.ERROR -> {
            Text(stringResource(R.string.rating_summary_failure), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.rating_retry)) }
        }
        ProviderRatingLoadPhase.READY -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            Icon(painterResource(R.drawable.ic_rating_star), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            ProviderRatingSummaryText(state.summary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderRatingsScreen(
    state: ProviderRatingsUiState,
    onBack: () -> Unit,
    onRetrySummary: () -> Unit,
    onRetryComments: () -> Unit,
    onMore: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.rating_comments_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.guincho_back)) } },
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(PitGoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                    ProviderRatingSummaryContent(state, onRetrySummary)
                }
            }
            if (state.commentsPhase == ProviderRatingLoadPhase.READY && state.ratings.isEmpty()) {
                item { Text(stringResource(R.string.rating_received_empty)) }
            }
            items(state.ratings, key = ServiceRating::id) { rating ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(PitGoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                            Icon(painterResource(R.drawable.ic_rating_star), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.rating_selected, rating.stars), style = MaterialTheme.typography.titleMedium)
                        }
                        val date = remember(rating.createdAt) {
                            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
                                .format(parseSupabaseInstant(rating.createdAt))
                        }
                        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(rating.comment?.takeIf(String::isNotBlank) ?: stringResource(R.string.rating_without_comment))
                    }
                }
            }
            if (state.commentsPhase == ProviderRatingLoadPhase.LOADING) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.commentsPhase == ProviderRatingLoadPhase.ERROR) item {
                Text(stringResource(R.string.rating_comments_failure), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetryComments) { Text(stringResource(R.string.rating_retry)) }
            }
            if (state.hasMore && state.commentsPhase == ProviderRatingLoadPhase.READY) item {
                OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rating_more_comments)) }
            }
        }
    }
}
