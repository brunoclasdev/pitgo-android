package com.trustarx.pitgo.features.providerregistration.repository

import com.trustarx.pitgo.features.providerregistration.model.AddressDraft
import com.trustarx.pitgo.features.providerregistration.model.AddressVerificationState
import com.trustarx.pitgo.features.providerregistration.model.ProviderFailure
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

sealed interface AddressVerificationResult {
    data class Verified(val state: AddressVerificationState.Verified) : AddressVerificationResult
    data class Diverged(val distanceMeters: Int) : AddressVerificationResult
    data class Failure(val reason: ProviderFailure) : AddressVerificationResult
}

class ProviderAddressVerificationRepository(private val client: SupabaseClient) {
    suspend fun verify(
        address: AddressDraft,
    ): AddressVerificationResult = try {
        val response = client.functions.invoke(
            function = "geocode-provider-address",
            body = VerificationRequestDto(
                address = AddressDto(
                    address.street.trim(), address.number.trim(), address.neighborhood.trim(),
                    address.postalCode.filter(Char::isDigit), address.city.trim(),
                    address.state.trim().uppercase(),
                ),
            ),
        )
        val dto = response.body<VerificationResponseDto>()
        AddressVerificationResult.Verified(
            AddressVerificationState.Verified(
                token = dto.verificationToken,
                latitude = dto.latitude,
                longitude = dto.longitude,
                distanceMeters = dto.distanceMeters,
                mismatchConfirmed = dto.mismatchConfirmed,
                expiresAtEpochMillis = Instant.parse(dto.expiresAt).toEpochMilli(),
            ),
        )
    } catch (cancel: CancellationException) { throw cancel }
      catch (error: RestException) {
          if (error.statusCode == 409) {
              AddressVerificationResult.Diverged(DIVERGENCE_FALLBACK_METERS)
          } else AddressVerificationResult.Failure(classify(error))
      } catch (error: Throwable) {
          AddressVerificationResult.Failure(classify(error))
      }

    private fun classify(error: Throwable): ProviderFailure = when (error) {
        is HttpRequestTimeoutException -> ProviderFailure.Timeout
        is HttpRequestException -> ProviderFailure.Offline
        is RestException -> when {
            error.statusCode == 401 || error.statusCode == 403 -> ProviderFailure.SessionExpired
            error.statusCode >= 500 -> ProviderFailure.ServiceUnavailable
            else -> ProviderFailure.Contract
        }
        else -> ProviderFailure.Unknown
    }

    private companion object { const val DIVERGENCE_FALLBACK_METERS = 501 }
}

@Serializable private data class VerificationRequestDto(
    val address: AddressDto,
)
@Serializable private data class AddressDto(
    val street: String,
    val number: String,
    val neighborhood: String,
    @SerialName("zip_code") val postalCode: String,
    val city: String,
    val state: String,
    val country: String = "BR",
)
@Serializable private data class VerificationResponseDto(
    @SerialName("verification_token") val verificationToken: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_m") val distanceMeters: Int,
    @SerialName("mismatch_confirmed") val mismatchConfirmed: Boolean,
    @SerialName("expires_at") val expiresAt: String,
)
