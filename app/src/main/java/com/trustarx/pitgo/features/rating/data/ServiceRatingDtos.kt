package com.trustarx.pitgo.features.rating.data

import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.features.mobiledispatch.model.isUuid
import com.trustarx.pitgo.features.rating.model.ServiceRating
import com.trustarx.pitgo.features.rating.model.ServiceRatingContext
import com.trustarx.pitgo.features.rating.model.ratingCharacterCount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ServiceRatingDto(
    val id: String,
    val rating: Int,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    fun domain() = ServiceRating(id, rating, comment, createdAt).also {
        require(id.isUuid() && rating in 1..5 && (comment?.ratingCharacterCount() ?: 0) <= 500)
        parseSupabaseInstant(createdAt)
    }
}

@Serializable
internal data class ServiceRatingContextDto(
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("provider_name") val providerName: String,
    @SerialName("can_rate") val canRate: Boolean,
    val rating: ServiceRatingDto? = null,
) {
    fun domain() = ServiceRatingContext(appointmentId, providerName, canRate, rating?.domain()).also {
        require(appointmentId.isUuid() && providerName.isNotBlank() && !(canRate && rating != null))
    }
}

@Serializable
internal data class ServiceRatingSubmissionDto(val outcome: String, val context: ServiceRatingContextDto) {
    fun domain() = context.domain().also {
        require(outcome in setOf("submitted", "already_submitted") && it.rating != null && !it.canRate)
    }
}
