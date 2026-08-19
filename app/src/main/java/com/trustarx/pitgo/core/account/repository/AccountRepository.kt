package com.trustarx.pitgo.core.account.repository

import com.trustarx.pitgo.core.account.data.AccountProfileDto
import com.trustarx.pitgo.core.account.data.toDomainOrNull
import com.trustarx.pitgo.core.account.model.AccountSnapshot
import com.trustarx.pitgo.core.auth.model.ProfileBlockedReason
import com.trustarx.pitgo.core.vehicle.data.VehicleDto
import com.trustarx.pitgo.core.vehicle.data.toDomain
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

sealed interface AccountLoadResult {
    data class Success(val account: AccountSnapshot) : AccountLoadResult
    data class Blocked(val reason: ProfileBlockedReason) : AccountLoadResult
    data object Offline : AccountLoadResult
    data object Timeout : AccountLoadResult
    data object Unauthorized : AccountLoadResult
    data object ServiceUnavailable : AccountLoadResult
    data object Unknown : AccountLoadResult
}

class AccountRepository(private val client: SupabaseClient) {
    suspend fun loadAccount(userId: String, authEmail: String): AccountLoadResult {
        return try {
            val profileDto = client.from(PROFILES).select {
                filter { eq("id", userId) }
                limit(1)
            }.decodeSingleOrNull<AccountProfileDto>()
                ?: return AccountLoadResult.Blocked(ProfileBlockedReason.MISSING)

            if (!profileDto.active) return AccountLoadResult.Blocked(ProfileBlockedReason.INACTIVE)
            val profile = profileDto.toDomainOrNull()
                ?: return AccountLoadResult.Blocked(ProfileBlockedReason.INCONSISTENT)

            val primaryVehicle = client.from(VEHICLES).select {
                filter {
                    eq("user_id", userId)
                    eq("active", true)
                    eq("is_primary", true)
                }
                limit(1)
            }.decodeSingleOrNull<VehicleDto>()?.toDomain()

            val snapshot = AccountSnapshot(userId, authEmail, profile, primaryVehicle)
            if (snapshot.isConsistent()) AccountLoadResult.Success(snapshot)
            else AccountLoadResult.Blocked(ProfileBlockedReason.INCONSISTENT)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: HttpRequestTimeoutException) {
            AccountLoadResult.Timeout
        } catch (_: HttpRequestException) {
            AccountLoadResult.Offline
        } catch (error: PostgrestRestException) {
            when {
                error.statusCode == 401 || error.statusCode == 403 -> AccountLoadResult.Unauthorized
                error.statusCode >= 500 -> AccountLoadResult.ServiceUnavailable
                else -> AccountLoadResult.Unknown
            }
        } catch (_: Throwable) {
            AccountLoadResult.Unknown
        }
    }

    private companion object {
        const val PROFILES = "profiles"
        const val VEHICLES = "vehicles"
    }
}
