package com.trustarx.pitgo.core.vehicle.repository

import com.trustarx.pitgo.core.vehicle.data.SetPrimaryVehicleRpcParams
import com.trustarx.pitgo.core.vehicle.data.VehicleDto
import com.trustarx.pitgo.core.vehicle.data.toDomain
import com.trustarx.pitgo.core.vehicle.data.toRpcParams
import com.trustarx.pitgo.core.vehicle.model.VehicleDraft
import com.trustarx.pitgo.core.vehicle.model.VehicleFailure
import com.trustarx.pitgo.core.vehicle.model.VehicleField
import com.trustarx.pitgo.core.vehicle.model.VehicleListResult
import com.trustarx.pitgo.core.vehicle.model.VehicleMutationResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class VehicleRepository(private val client: SupabaseClient) {
    suspend fun listActiveVehicles(userId: String): VehicleListResult = try {
        val vehicles = client.from(VEHICLES).select {
            filter {
                eq("user_id", userId)
                eq("active", true)
            }
            order("is_primary", Order.DESCENDING)
            order("created_at", Order.ASCENDING)
            limit(MAXIMUM_VEHICLES)
        }.decodeList<VehicleDto>()
            .map(VehicleDto::toDomain)
            .filter { it.userId == userId && it.active }
        VehicleListResult.Success(vehicles)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        VehicleListResult.Failure(classifyFailure(error))
    }

    suspend fun createVehicle(draft: VehicleDraft): VehicleMutationResult = try {
        val params = Json.encodeToJsonElement(draft.toRpcParams()).jsonObject
        val vehicle = client.postgrest.rpc(CREATE_VEHICLE, params)
            .decodeSingle<VehicleDto>()
            .toDomain()
        VehicleMutationResult.Success(vehicle)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        classifyMutationFailure(error)
    }

    suspend fun setPrimaryVehicle(vehicleId: String): VehicleMutationResult = try {
        val params = Json.encodeToJsonElement(SetPrimaryVehicleRpcParams(vehicleId)).jsonObject
        val vehicle = client.postgrest.rpc(SET_PRIMARY_VEHICLE, params)
            .decodeSingle<VehicleDto>()
            .toDomain()
        VehicleMutationResult.Success(vehicle)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        classifyMutationFailure(error)
    }

    private fun classifyMutationFailure(error: Throwable): VehicleMutationResult {
        if (error is PostgrestRestException && error.code == UNIQUE_VIOLATION) {
            val field = when {
                error.error.contains(PLATE_UNIQUE) -> VehicleField.PLATE
                error.error.contains(RENAVAM_UNIQUE) -> VehicleField.RENAVAM
                error.error.contains(CHASSIS_UNIQUE) -> VehicleField.CHASSIS
                else -> null
            }
            if (field != null) return VehicleMutationResult.FieldUnavailable(field)
        }
        return VehicleMutationResult.Failure(classifyFailure(error))
    }

    private fun classifyFailure(error: Throwable): VehicleFailure = when (error) {
        is HttpRequestTimeoutException -> VehicleFailure.Timeout
        is HttpRequestException -> VehicleFailure.Offline
        is PostgrestRestException -> when {
            error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
                VehicleFailure.SessionExpired
            error.code == "P0002" -> VehicleFailure.VehicleUnavailable
            error.code == "23514" || error.code == "22023" -> VehicleFailure.Contract
            error.statusCode >= 500 -> VehicleFailure.ServiceUnavailable
            else -> VehicleFailure.Unknown
        }
        else -> VehicleFailure.Unknown
    }

    private companion object {
        const val VEHICLES = "vehicles"
        const val CREATE_VEHICLE = "create_vehicle"
        const val SET_PRIMARY_VEHICLE = "set_primary_vehicle"
        const val UNIQUE_VIOLATION = "23505"
        const val PLATE_UNIQUE = "vehicles_plate_unique"
        const val RENAVAM_UNIQUE = "vehicles_renavam_unique"
        const val CHASSIS_UNIQUE = "vehicles_chassis_unique"
        const val MAXIMUM_VEHICLES = 100L
    }
}
