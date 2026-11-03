package com.trustarx.pitgo.features.profile.repository

import com.trustarx.pitgo.features.profile.data.RegistrationSnapshotDto
import com.trustarx.pitgo.features.profile.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

class RegistrationEditRepository(private val client: SupabaseClient) {
    suspend fun load() = call { client.postgrest.rpc("get_my_registration").decodeAs<RegistrationSnapshotDto>().domain() }
    suspend fun save(snapshot: RegistrationSnapshot,draft: RegistrationDraft,verificationToken: String?) = call {
        client.postgrest.rpc("update_my_registration",buildJsonObject {
            put("p_expected_updated_at",snapshot.updatedAt); put("p_name",draft.name.trim())
            put("p_phone",draft.phone.filter(Char::isDigit)); put("p_description",draft.description.trim())
            if(draft.address != (snapshot.address ?: com.trustarx.pitgo.core.address.model.AddressDraft())) {
                put("p_address_expected_updated_at",snapshot.addressUpdatedAt)
                put("p_verification_token",verificationToken)
                put("p_address",buildJsonObject {
                    put("street",draft.address.street.trim()); put("number",draft.address.number.trim())
                    put("neighborhood",draft.address.neighborhood.trim()); put("zip_code",draft.address.postalCode)
                    put("city",draft.address.city.trim()); put("state",draft.address.state.trim().uppercase())
                    put("complement",draft.address.complement.trim())
                })
            }
        }).decodeAs<RegistrationSnapshotDto>().domain()
    }
    private suspend fun call(block: suspend () -> RegistrationSnapshot): RegistrationResult = try {
        RegistrationResult.Success(withTimeout(15_000) { block() })
    } catch(_: TimeoutCancellationException) { RegistrationResult.Failure(RegistrationFailure.TIMEOUT) }
      catch(cancel: CancellationException) { throw cancel }
      catch(_: HttpRequestTimeoutException) { RegistrationResult.Failure(RegistrationFailure.TIMEOUT) }
      catch(_: HttpRequestException) { RegistrationResult.Failure(RegistrationFailure.OFFLINE) }
      catch(error: RestException) {
          val reason=when {
              error.statusCode==401 || error.statusCode==403 -> RegistrationFailure.SESSION_EXPIRED
              error.message.orEmpty().contains("REGISTRATION_CONFLICT") -> RegistrationFailure.CONFLICT
              error.message.orEmpty().let { it.contains("ADDRESS_CHANGED") || it.contains("ADDRESS_VERIFICATION_REQUIRED") } -> RegistrationFailure.ADDRESS_UNAVAILABLE
              error.message.orEmpty().let { it.contains("INVALID_REGISTRATION") || it.contains("INVALID_ADDRESS") } -> RegistrationFailure.INVALID_INPUT
              else -> RegistrationFailure.UNAVAILABLE
          }
          RegistrationResult.Failure(reason)
      } catch(_: SerializationException) { RegistrationResult.Failure(RegistrationFailure.UNAVAILABLE) }
      catch(_: IllegalArgumentException) { RegistrationResult.Failure(RegistrationFailure.UNAVAILABLE) }
      catch(_: Exception) { RegistrationResult.Failure(RegistrationFailure.UNKNOWN) }
}
