package com.trustarx.pitgo.features.providermigration.repository

import com.trustarx.pitgo.features.providermigration.model.ProviderMigrationResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProviderMigrationRepository(private val client: SupabaseClient) {
    suspend fun begin(token: String) = invoke("begin_provider_ownership_migration", token)
    suspend fun claim(token: String) = invoke("claim_provider_ownership_migration", token)

    private suspend fun invoke(function: String, token: String): ProviderMigrationResult = try {
        val result = client.postgrest.rpc(function, buildJsonObject {
            put("p_transfer_token", token)
        }).decodeAs<MigrationResultDto>()
        when (result.outcome) {
            "PENDING" -> ProviderMigrationResult.Pending
            "MIGRATED", "ALREADY_MIGRATED" -> ProviderMigrationResult.Migrated
            else -> ProviderMigrationResult.Failed
        }
    } catch (cancel: CancellationException) { throw cancel }
      catch (error: PostgrestRestException) {
          if (error.statusCode == 401 || error.statusCode == 403 || error.code == "42501") {
              ProviderMigrationResult.SessionExpired
          } else if (error.code == "P0002" || error.code == "55000") {
              ProviderMigrationResult.Unavailable
          } else ProviderMigrationResult.Failed
      } catch (_: HttpRequestTimeoutException) { ProviderMigrationResult.Failed }
      catch (_: HttpRequestException) { ProviderMigrationResult.Failed }
      catch (_: Throwable) { ProviderMigrationResult.Failed }
}

@Serializable private data class MigrationResultDto(
    val outcome: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int? = null,
)
