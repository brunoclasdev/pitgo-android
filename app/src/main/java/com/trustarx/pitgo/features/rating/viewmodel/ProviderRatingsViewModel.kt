package com.trustarx.pitgo.features.rating.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.rating.model.*
import com.trustarx.pitgo.features.rating.repository.ServiceRatingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProviderRatingsViewModel(private val providerId: String, private val repository: ServiceRatingRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ProviderRatingsUiState())
    val state = mutable.asStateFlow()
    private var lastCommentsMore = false

    fun loadSummary() {
        if (mutable.value.summaryPhase == ProviderRatingLoadPhase.LOADING) return
        mutable.update { it.copy(summaryPhase = ProviderRatingLoadPhase.LOADING, summaryFailure = null) }
        viewModelScope.launch {
            when (val result = repository.summary(providerId)) {
                is RatingResult.Success -> mutable.update { it.copy(summary = result.value, summaryPhase = ProviderRatingLoadPhase.READY) }
                is RatingResult.Failure -> mutable.update { it.copy(summaryPhase = ProviderRatingLoadPhase.ERROR, summaryFailure = result.reason) }
            }
        }
    }

    fun loadComments(more: Boolean = false) {
        val current = mutable.value
        if (current.commentsPhase == ProviderRatingLoadPhase.LOADING || (more && !current.hasMore)) return
        val cursor = if (more) current.ratings.lastOrNull() else null
        lastCommentsMore = more
        mutable.update { it.copy(commentsPhase = ProviderRatingLoadPhase.LOADING, commentsFailure = null) }
        viewModelScope.launch {
            when (val result = repository.received(cursor)) {
                is RatingResult.Success -> mutable.update { it.copy(
                    ratings = (if (more) current.ratings + result.value else result.value).distinctBy(ServiceRating::id),
                    hasMore = result.value.size == 20,
                    commentsPhase = ProviderRatingLoadPhase.READY,
                ) }
                is RatingResult.Failure -> mutable.update { it.copy(commentsPhase = ProviderRatingLoadPhase.ERROR, commentsFailure = result.reason) }
            }
        }
    }

    fun retryComments() = loadComments(more = lastCommentsMore)

    companion object {
        fun factory(providerId: String, repository: ServiceRatingRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProviderRatingsViewModel(providerId, repository) as T
        }
    }
}
