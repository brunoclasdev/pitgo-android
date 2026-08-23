package com.trustarx.pitgo.features.providerdashboard.repository

import com.trustarx.pitgo.core.account.model.ProviderSnapshot
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUpdateResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProviderDashboardRepository(private val client: SupabaseClient) {
    fun current(snapshot: ProviderSnapshot): ProviderSnapshot = snapshot

    suspend fun update(
        snapshot: ProviderSnapshot,
        available: Boolean,
        mode: ProviderLocationMode,
    ): ProviderDashboardUpdateResult = try {
        val dto = client.postgrest.rpc(
            "set_provider_availability",
            buildJsonObject {
                put("p_available", available)
                put("p_location_mode", mode.name)
            },
        ).decodeAs<ProviderAvailabilityDto>()
        ProviderDashboardUpdateResult.Success(
            snapshot.copy(
                available = dto.available,
                locationMode = ProviderLocationMode.valueOf(dto.locationMode),
            ),
        )
    } catch (_: Throwable) { ProviderDashboardUpdateResult.Failure }
}

@Serializable private data class ProviderAvailabilityDto(
    val available: Boolean,
    @SerialName("location_mode") val locationMode: String,
)
