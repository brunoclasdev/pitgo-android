package com.trustarx.pitgo.features.rating.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.rating.model.*
import com.trustarx.pitgo.features.rating.repository.ServiceRatingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServiceRatingViewModel(private val appointmentId: String, private val repository: ServiceRatingRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ServiceRatingUiState())
    val state = mutable.asStateFlow()

    init { load() }

    fun load() {
        if (mutable.value.phase == RatingPhase.SUBMITTING) return
        mutable.value = mutable.value.copy(phase = RatingPhase.LOADING, failure = null)
        viewModelScope.launch {
            when (val result = repository.get(appointmentId)) {
                is RatingResult.Success -> apply(result.value)
                is RatingResult.Failure -> mutable.value = mutable.value.copy(phase = RatingPhase.LOAD_ERROR, failure = result.reason)
            }
        }
    }

    fun stars(value: Int) {
        if (mutable.value.phase == RatingPhase.READY && value in 1..5) {
            mutable.value = mutable.value.copy(stars = value, failure = null)
        }
    }

    fun comment(value: String) {
        if (mutable.value.phase == RatingPhase.READY) mutable.value = mutable.value.copy(comment = value.limitRatingComment(), failure = null)
    }

    fun submit() {
        val current = mutable.value
        val stars = current.stars ?: return
        if (current.phase != RatingPhase.READY) return
        mutable.value = current.copy(phase = RatingPhase.SUBMITTING, failure = null)
        viewModelScope.launch {
            when (val result = repository.submit(appointmentId, stars, current.comment)) {
                is RatingResult.Success -> apply(result.value)
                is RatingResult.Failure -> mutable.value = current.copy(
                    phase = if (result.reason in setOf(RatingFailure.INELIGIBLE, RatingFailure.FORBIDDEN)) RatingPhase.INELIGIBLE else RatingPhase.READY,
                    failure = result.reason,
                )
            }
        }
    }

    private fun apply(context: ServiceRatingContext) {
        mutable.value = ServiceRatingUiState(
            phase = when { context.rating != null -> RatingPhase.SAVED; context.canRate -> RatingPhase.READY; else -> RatingPhase.INELIGIBLE },
            providerName = context.providerName,
            stars = context.rating?.stars,
            comment = context.rating?.comment.orEmpty(),
        )
    }

    companion object {
        fun factory(id: String, repository: ServiceRatingRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ServiceRatingViewModel(id, repository) as T
        }
    }
}
