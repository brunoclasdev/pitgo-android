package com.trustarx.pitgo.features.guincho.repository

import com.trustarx.pitgo.features.guincho.data.*
import com.trustarx.pitgo.features.guincho.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

class GuinchoRepository(private val client: SupabaseClient) {
    suspend fun reserve(quote: GuinchoQuote,operationId: String) = call {
        requireNotNull(client.postgrest.rpc("create_guincho_request",buildJsonObject {
            put("p_quote_id",quote.id); put("p_operation_id",operationId)
        }).decodeAs<com.trustarx.pitgo.features.mobiledispatch.data.MobileDispatchEnvelopeDto>().domainOrNull())
    }
    suspend fun history(before: String? = null,beforeId: String? = null): GuinchoResult<List<com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot>> = call {
        client.postgrest.rpc("list_my_guincho_appointments",buildJsonObject {
            put("p_limit",20); before?.let { put("p_before",it) }; beforeId?.let { put("p_before_id",it) }
        }).decodeList<com.trustarx.pitgo.features.mobiledispatch.data.MobileDispatchSnapshotDto>().map { requireNotNull(it.toDomainOrNull()) }
    }
    suspend fun saveTrip(input: GuinchoTripInput, previous: GuinchoDraft? = null): GuinchoResult<GuinchoDraft> {
        if (!input.isValid()) return GuinchoResult.Failure(GuinchoFailure.INVALID_INPUT)
        return call {
            client.postgrest.rpc("save_guincho_trip_draft", buildJsonObject {
                put("p_vehicle_id", input.vehicleId)
                put("p_pickup_address", input.pickupAddress.trim())
                put("p_pickup_latitude", input.pickup.latitude)
                put("p_pickup_longitude", input.pickup.longitude)
                put("p_destination_address", input.destinationAddress.trim())
                put("p_destination_latitude", input.destination.latitude)
                put("p_destination_longitude", input.destination.longitude)
                previous?.let { put("p_draft_id", it.id); put("p_expected_revision", it.revision) }
            }).decodeAs<GuinchoDraftDto>().domain()
        }
    }
    suspend fun discover(draft: GuinchoDraft): GuinchoResult<List<GuinchoProvider>> = call {
        client.postgrest.rpc("discover_guincho_providers", buildJsonObject {
            put("p_draft_id", draft.id); put("p_expected_revision", draft.revision); put("p_limit", 200)
        }).decodeList<GuinchoProviderDto>().map { it.domain() }
    }
    suspend fun quote(draft: GuinchoDraft, providerId: String): GuinchoResult<GuinchoQuote> = call {
        client.functions.invoke("guincho-quote", body = buildJsonObject {
            put("draft_id", draft.id); put("input_revision", draft.revision); put("provider_id", providerId)
        }).body<GuinchoQuoteDto>().domain()
    }

    private suspend fun <T> call(block: suspend () -> T): GuinchoResult<T> = try {
        GuinchoResult.Success(withTimeout(15_000) { block() })
    } catch (_: TimeoutCancellationException) {
        GuinchoResult.Failure(GuinchoFailure.TIMEOUT)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: HttpRequestTimeoutException) {
        GuinchoResult.Failure(GuinchoFailure.TIMEOUT)
    } catch (_: HttpRequestException) {
        GuinchoResult.Failure(GuinchoFailure.OFFLINE)
    } catch (error: RestException) {
        val code = runCatching { error.response.body<JsonObject>()["code"]?.jsonPrimitive?.content }.getOrNull()
        val reason = when {
            error.statusCode == 401 -> GuinchoFailure.SESSION_EXPIRED
            code == "ROUTE_UNAVAILABLE" || error.message?.contains("ROUTE_UNAVAILABLE") == true -> GuinchoFailure.ROUTE_UNAVAILABLE
            code == "PROVIDER_UNAVAILABLE" || error.message?.contains("PROVIDER_UNAVAILABLE") == true -> GuinchoFailure.PROVIDER_UNAVAILABLE
            code == "QUOTE_EXPIRED" || error.message?.contains("QUOTE_EXPIRED") == true -> GuinchoFailure.QUOTE_EXPIRED
            code == "QUOTE_CONFLICT" || error.message?.contains("QUOTE_CONFLICT") == true || error.message?.contains("REVISION_CONFLICT") == true -> GuinchoFailure.QUOTE_CONFLICT
            code == "PRICING_UNAVAILABLE" || error.message?.contains("PRICING_UNAVAILABLE") == true -> GuinchoFailure.PRICING_UNAVAILABLE
            error.statusCode >= 500 -> GuinchoFailure.SERVICE_UNAVAILABLE
            else -> GuinchoFailure.INVALID_INPUT
        }
        GuinchoResult.Failure(reason)
    } catch (_: IllegalArgumentException) {
        GuinchoResult.Failure(GuinchoFailure.CONTRACT)
    } catch (_: Exception) {
        GuinchoResult.Failure(GuinchoFailure.UNKNOWN)
    }
}
