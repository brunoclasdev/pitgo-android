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

class ProviderDashboardRepository(private val client: SupabaseClient,
    val transportCatalog: com.trustarx.pitgo.core.vehicle.repository.TransportCatalogRepository = com.trustarx.pitgo.core.vehicle.repository.TransportCatalogRepository(client)) {
    suspend fun guinchoVehicles() = client.postgrest.rpc("get_my_guincho_vehicles").decodeList<com.trustarx.pitgo.features.providerdashboard.model.GuinchoFleetVehicle>()
    suspend fun updateGuinchoVehicle(vehicle: com.trustarx.pitgo.features.providerdashboard.model.GuinchoFleetVehicle,
        draft: com.trustarx.pitgo.features.providerregistration.model.ProviderVehicle) {
        client.postgrest.rpc("update_my_guincho_vehicle",buildJsonObject {
            put("p_vehicle_id",vehicle.id); put("p_expected_revision",vehicle.revision)
            put("p_vehicle",kotlinx.serialization.json.Json.encodeToJsonElement(
                com.trustarx.pitgo.features.providerregistration.model.ProviderVehicle.serializer(),draft))
        })
    }
    suspend fun registerGuinchoVehicle(vehicle: com.trustarx.pitgo.features.providerregistration.model.ProviderVehicle) {
        client.postgrest.rpc("register_my_guincho_vehicle",buildJsonObject {
            put("p_request_id",java.util.UUID.randomUUID().toString()); put("p_plate",vehicle.plate)
            put("p_make",vehicle.make); put("p_model",vehicle.model); put("p_color",vehicle.color); put("p_size_id",vehicle.guinchoSizeId)
        })
    }
    suspend fun setGuinchoVehicle(vehicle: com.trustarx.pitgo.features.providerdashboard.model.GuinchoFleetVehicle, sizeId: String) {
        client.postgrest.rpc("set_my_guincho_vehicle",buildJsonObject {
            put("p_vehicle_id",vehicle.id); put("p_size_id",sizeId); put("p_expected_revision",vehicle.revision)
            put("p_request_id",java.util.UUID.randomUUID().toString())
        })
        client.postgrest.rpc("select_my_guincho_dispatch_vehicle",buildJsonObject {
            put("p_vehicle_id",vehicle.id); put("p_request_id",java.util.UUID.randomUUID().toString())
        })
    }
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
