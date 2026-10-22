package com.trustarx.pitgo.features.rating.repository

import com.trustarx.pitgo.features.mobiledispatch.model.isUuid
import com.trustarx.pitgo.features.rating.data.ServiceRatingContextDto
import com.trustarx.pitgo.features.rating.data.ServiceRatingSubmissionDto
import com.trustarx.pitgo.features.rating.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

class ServiceRatingRepository(private val client: SupabaseClient) {
    suspend fun summary(providerId: String): RatingResult<ProviderRatingSummary> {
        if (!providerId.isUuid()) return RatingResult.Failure(RatingFailure.INVALID_INPUT)
        return call {
            client.postgrest.rpc("get_provider_rating_summary", buildJsonObject { put("p_provider_id", providerId) })
                .decodeAs<ProviderRatingSummary>().validate()
        }
    }

    suspend fun received(before: ServiceRating? = null): RatingResult<List<ServiceRating>> = call {
        client.postgrest.rpc("list_my_provider_ratings", buildJsonObject {
            put("p_limit", 20)
            before?.let { put("p_before", it.createdAt); put("p_before_id", it.id) }
        }).decodeList<com.trustarx.pitgo.features.rating.data.ServiceRatingDto>().map { it.domain() }
    }

    suspend fun get(appointmentId: String): RatingResult<ServiceRatingContext> {
        if (!appointmentId.isUuid()) return RatingResult.Failure(RatingFailure.INVALID_INPUT)
        return call {
            client.postgrest.rpc("get_service_rating", buildJsonObject { put("p_appointment_id", appointmentId) })
                .decodeAs<ServiceRatingContextDto>().domain().also { require(it.appointmentId == appointmentId) }
        }
    }

    suspend fun submit(appointmentId: String, stars: Int, comment: String): RatingResult<ServiceRatingContext> {
        if (!appointmentId.isUuid() || stars !in 1..5 || comment.ratingCharacterCount() > 500) {
            return RatingResult.Failure(RatingFailure.INVALID_INPUT)
        }
        return call {
            client.postgrest.rpc("submit_service_rating", buildJsonObject {
                put("p_appointment_id", appointmentId)
                put("p_rating", stars)
                put("p_comment", comment)
            }).decodeAs<ServiceRatingSubmissionDto>().domain().also { require(it.appointmentId == appointmentId) }
        }
    }

    private suspend fun <T> call(block: suspend () -> T): RatingResult<T> = try {
        RatingResult.Success(withTimeout(15_000) { block() })
    } catch (_: TimeoutCancellationException) {
        RatingResult.Failure(RatingFailure.TIMEOUT)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: HttpRequestTimeoutException) {
        RatingResult.Failure(RatingFailure.TIMEOUT)
    } catch (_: HttpRequestException) {
        RatingResult.Failure(RatingFailure.OFFLINE)
    } catch (_: IOException) {
        RatingResult.Failure(RatingFailure.OFFLINE)
    } catch (error: PostgrestRestException) {
        RatingResult.Failure(when {
            error.statusCode == 401 -> RatingFailure.SESSION_EXPIRED
            error.code == "42501" -> RatingFailure.FORBIDDEN
            error.message?.contains("RATING_NOT_ELIGIBLE") == true -> RatingFailure.INELIGIBLE
            error.code == "22023" -> RatingFailure.INVALID_INPUT
            error.statusCode >= 500 -> RatingFailure.SERVER
            else -> RatingFailure.UNKNOWN
        })
    } catch (_: Exception) {
        RatingResult.Failure(RatingFailure.UNKNOWN)
    }
}
