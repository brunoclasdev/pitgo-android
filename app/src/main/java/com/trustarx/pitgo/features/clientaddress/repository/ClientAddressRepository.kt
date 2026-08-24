package com.trustarx.pitgo.features.clientaddress.repository

import com.trustarx.pitgo.features.clientaddress.model.ClientAddress
import com.trustarx.pitgo.features.clientaddress.model.ClientAddressFailure
import com.trustarx.pitgo.features.clientaddress.model.ClientAddressResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client's own contact address (get_my_client_address / upsert_my_client_address —
 * specs/014-client-ux-improvements/contracts/client-address-rpcs.md). Deliberately separate from
 * ProviderRegistrationRepository/ProviderAddressVerificationRepository: this address has no public
 * discovery or geocoding-verification requirement, only ViaCEP autofill (already shared via
 * core/address).
 */
class ClientAddressRepository(private val client: SupabaseClient) {
    suspend fun current(): ClientAddressResult = call {
        client.postgrest.rpc("get_my_client_address").decodeAs<AddressEnvelopeDto>()
    }

    suspend fun save(address: ClientAddress): ClientAddressResult = call {
        client.postgrest.rpc(
            "upsert_my_client_address",
            buildJsonObject {
                put("p_street", address.street)
                put("p_number", address.number)
                put("p_neighborhood", address.neighborhood)
                put("p_zip_code", address.zipCode)
                put("p_city", address.city)
                put("p_state", address.state)
                put("p_complement", address.complement)
                put("p_latitude", address.latitude)
                put("p_longitude", address.longitude)
            },
        ).decodeAs<AddressEnvelopeDto>()
    }

    private suspend fun call(
        block: suspend () -> AddressEnvelopeDto,
    ): ClientAddressResult = try {
        val dto = withTimeout(RPC_TIMEOUT_MILLIS) { block() }
        ClientAddressResult.Success(dto.address?.toDomain())
    } catch (_: TimeoutCancellationException) {
        ClientAddressResult.Failure(ClientAddressFailure.TIMEOUT)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: HttpRequestTimeoutException) {
        ClientAddressResult.Failure(ClientAddressFailure.TIMEOUT)
    } catch (_: HttpRequestException) {
        ClientAddressResult.Failure(ClientAddressFailure.OFFLINE)
    } catch (error: PostgrestRestException) {
        ClientAddressResult.Failure(classify(error))
    } catch (_: Throwable) {
        ClientAddressResult.Failure(ClientAddressFailure.UNKNOWN)
    }

    private fun classify(error: PostgrestRestException): ClientAddressFailure = when {
        error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
            ClientAddressFailure.SESSION_EXPIRED
        error.code == "22023" -> ClientAddressFailure.CONTRACT
        error.code in setOf("PGRST202", "PGRST203", "42883", "42P01") ->
            ClientAddressFailure.SERVICE_UNAVAILABLE
        error.statusCode >= 500 -> ClientAddressFailure.SERVICE_UNAVAILABLE
        else -> ClientAddressFailure.CONTRACT
    }

    private companion object {
        const val RPC_TIMEOUT_MILLIS = 15_000L
    }
}

@Serializable
private data class AddressEnvelopeDto(
    val outcome: String,
    val address: AddressDto? = null,
)

@Serializable
private data class AddressDto(
    val street: String,
    val number: String,
    val neighborhood: String,
    @SerialName("zip_code") val zipCode: String,
    val city: String,
    val state: String,
    val complement: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    fun toDomain() = ClientAddress(
        street = street,
        number = number,
        neighborhood = neighborhood,
        zipCode = zipCode,
        city = city,
        state = state,
        complement = complement,
        latitude = latitude,
        longitude = longitude,
    )
}
