package com.trustarx.pitgo.features.rating.model

import kotlinx.serialization.Serializable

@Serializable
data class ProviderRatingSummary(val count: Long, val average: Double? = null) {
    fun validate() = apply {
        require(count >= 0 && if (count == 0L) average == null else average != null && average.isFinite() && average in 1.0..5.0)
    }
}

@Serializable
data class ServiceRatingState(val submitted: Boolean, @kotlinx.serialization.SerialName("can_rate") val canRate: Boolean)

data class ServiceRating(val id: String, val stars: Int, val comment: String?, val createdAt: String)
data class ServiceRatingContext(
    val appointmentId: String,
    val providerName: String,
    val canRate: Boolean,
    val rating: ServiceRating?,
)

enum class RatingFailure { OFFLINE, TIMEOUT, SESSION_EXPIRED, FORBIDDEN, INELIGIBLE, INVALID_INPUT, SERVER, UNKNOWN }

sealed interface RatingResult<out T> {
    data class Success<T>(val value: T) : RatingResult<T>
    data class Failure(val reason: RatingFailure) : RatingResult<Nothing>
}

enum class RatingPhase { LOADING, READY, SUBMITTING, SAVED, INELIGIBLE, LOAD_ERROR }

data class ServiceRatingUiState(
    val phase: RatingPhase = RatingPhase.LOADING,
    val providerName: String = "",
    val stars: Int? = null,
    val comment: String = "",
    val failure: RatingFailure? = null,
)

fun String.ratingCharacterCount() = codePointCount(0, length)
fun String.limitRatingComment() = if (ratingCharacterCount() <= 500) this else substring(0, offsetByCodePoints(0, 500))

enum class ProviderRatingLoadPhase { NOT_LOADED, LOADING, READY, ERROR }

data class ProviderRatingsUiState(
    val summary: ProviderRatingSummary? = null,
    val summaryPhase: ProviderRatingLoadPhase = ProviderRatingLoadPhase.NOT_LOADED,
    val summaryFailure: RatingFailure? = null,
    val ratings: List<ServiceRating> = emptyList(),
    val commentsPhase: ProviderRatingLoadPhase = ProviderRatingLoadPhase.NOT_LOADED,
    val commentsFailure: RatingFailure? = null,
    val hasMore: Boolean = false,
)
