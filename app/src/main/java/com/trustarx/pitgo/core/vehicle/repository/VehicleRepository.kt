package com.trustarx.pitgo.core.vehicle.repository

import com.trustarx.pitgo.core.vehicle.data.SetPrimaryVehicleRpcParams
import com.trustarx.pitgo.core.vehicle.data.VehicleDto
import com.trustarx.pitgo.core.vehicle.data.toDomain
import com.trustarx.pitgo.core.vehicle.data.toRpcParams
import com.trustarx.pitgo.core.vehicle.model.Vehicle
import com.trustarx.pitgo.core.vehicle.model.VehicleDraft
import com.trustarx.pitgo.core.vehicle.model.VehicleFailure
import com.trustarx.pitgo.core.vehicle.model.VehicleField
import com.trustarx.pitgo.core.vehicle.model.VehicleFieldError
import com.trustarx.pitgo.core.vehicle.model.VehicleListResult
import com.trustarx.pitgo.core.vehicle.model.VehicleMutationResult
import com.trustarx.pitgo.core.vehicle.validation.VehicleValidation
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
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

    suspend fun createVehicle(
        expectedUserId: String,
        draft: VehicleDraft,
    ): VehicleMutationResult = try {
        val params = Json.encodeToJsonElement(draft.toRpcParams()).jsonObject
        val vehicle = client.postgrest.rpc(CREATE_VEHICLE, params)
            .decodeSingle<VehicleDto>()
            .toDomain()
        if (vehicle.userId == expectedUserId && vehicle.active && vehicle.hasValidDomainValues()) {
            VehicleMutationResult.Success(vehicle)
        } else {
            VehicleMutationResult.Failure(VehicleFailure.RpcUnavailable)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        classifyMutationFailure(error)
    }

    suspend fun setPrimaryVehicle(
        expectedUserId: String,
        vehicleId: String,
    ): VehicleMutationResult = try {
        val params = Json.encodeToJsonElement(SetPrimaryVehicleRpcParams(vehicleId)).jsonObject
        val vehicle = client.postgrest.rpc(SET_PRIMARY_VEHICLE, params)
            .decodeSingle<VehicleDto>()
            .toDomain()
        if (vehicle.id == vehicleId && vehicle.userId == expectedUserId &&
            vehicle.active && vehicle.isPrimary && vehicle.hasValidDomainValues()
        ) {
            VehicleMutationResult.Success(vehicle)
        } else {
            VehicleMutationResult.Failure(VehicleFailure.RpcUnavailable)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        classifyMutationFailure(error)
    }

    private fun classifyMutationFailure(error: Throwable): VehicleMutationResult {
        if (error is PostgrestRestException) {
            val fieldFailure = when (error.code) {
                BRAND_REQUIRED -> VehicleField.BRAND to VehicleFieldError.REQUIRED
                MODEL_REQUIRED -> VehicleField.MODEL to VehicleFieldError.REQUIRED
                INVALID_PLATE -> VehicleField.PLATE to VehicleFieldError.INVALID_FORMAT
                INVALID_RENAVAM -> VehicleField.RENAVAM to VehicleFieldError.INVALID_FORMAT
                INVALID_CHASSIS -> VehicleField.CHASSIS to VehicleFieldError.INVALID_FORMAT
                INVALID_MANUFACTURE_YEAR ->
                    VehicleField.MANUFACTURE_YEAR to VehicleFieldError.OUT_OF_RANGE
                INVALID_MODEL_YEAR -> VehicleField.MODEL_YEAR to VehicleFieldError.OUT_OF_RANGE
                INVALID_MILEAGE -> VehicleField.MILEAGE to VehicleFieldError.OUT_OF_RANGE
                PLATE_UNAVAILABLE -> VehicleField.PLATE to VehicleFieldError.UNAVAILABLE
                RENAVAM_UNAVAILABLE -> VehicleField.RENAVAM to VehicleFieldError.UNAVAILABLE
                CHASSIS_UNAVAILABLE -> VehicleField.CHASSIS to VehicleFieldError.UNAVAILABLE
                else -> null
            }
            if (fieldFailure != null) {
                return VehicleMutationResult.FieldFailure(fieldFailure.first, fieldFailure.second)
            }
        }
        return VehicleMutationResult.Failure(classifyFailure(error))
    }

    private fun classifyFailure(error: Throwable): VehicleFailure = when (error) {
        is HttpRequestTimeoutException -> VehicleFailure.Timeout
        is HttpRequestException -> VehicleFailure.Offline
        is SerializationException -> VehicleFailure.RpcUnavailable
        is PostgrestRestException -> when {
            error.statusCode == 401 || error.statusCode == 403 || error.code in SESSION_ERRORS ->
                VehicleFailure.SessionExpired
            error.code == POOL_TIMEOUT -> VehicleFailure.Timeout
            error.code == "P0002" -> VehicleFailure.VehicleUnavailable
            error.code in RPC_CONTRACT_ERRORS -> VehicleFailure.RpcUnavailable
            error.code == SANITIZED_UNKNOWN_UNIQUE -> VehicleFailure.RpcUnavailable
            error.code == "23514" || error.code == "22023" -> VehicleFailure.RpcUnavailable
            error.code in SERVICE_ERRORS || error.code?.startsWith("08") == true ->
                VehicleFailure.ServiceUnavailable
            error.statusCode >= 500 -> VehicleFailure.ServiceUnavailable
            else -> VehicleFailure.Unknown
        }
        else -> VehicleFailure.Unknown
    }

    private fun Vehicle.hasValidDomainValues(): Boolean {
        val normalized = VehicleValidation.validate(
            brand = brand,
            model = model,
            version = version.orEmpty(),
            manufactureYear = manufactureYear?.toString().orEmpty(),
            modelYear = modelYear?.toString().orEmpty(),
            plate = plate.orEmpty(),
            renavam = renavam.orEmpty(),
            chassis = chassis.orEmpty(),
            color = color.orEmpty(),
            fuelType = fuelType.orEmpty(),
            transmission = transmission.orEmpty(),
            engine = engine.orEmpty(),
            mileage = mileage?.toString().orEmpty(),
            makePrimary = isPrimary,
        ).draft ?: return false
        return id.isNotBlank() && createdAt.isNotBlank() && updatedAt.isNotBlank() &&
            normalized.brand == brand && normalized.model == model &&
            normalized.version == version && normalized.manufactureYear == manufactureYear &&
            normalized.modelYear == modelYear && normalized.plate == plate &&
            normalized.renavam == renavam && normalized.chassis == chassis &&
            normalized.color == color && normalized.fuelType == fuelType &&
            normalized.transmission == transmission && normalized.engine == engine &&
            normalized.mileage == mileage
    }

    private companion object {
        const val VEHICLES = "vehicles"
        const val CREATE_VEHICLE = "create_vehicle"
        const val SET_PRIMARY_VEHICLE = "set_primary_vehicle"
        const val BRAND_REQUIRED = "P2001"
        const val MODEL_REQUIRED = "P2002"
        const val INVALID_PLATE = "P2003"
        const val INVALID_RENAVAM = "P2004"
        const val INVALID_CHASSIS = "P2005"
        const val INVALID_MANUFACTURE_YEAR = "P2006"
        const val INVALID_MODEL_YEAR = "P2007"
        const val INVALID_MILEAGE = "P2008"
        const val PLATE_UNAVAILABLE = "P2011"
        const val RENAVAM_UNAVAILABLE = "P2012"
        const val CHASSIS_UNAVAILABLE = "P2013"
        const val SANITIZED_UNKNOWN_UNIQUE = "P2099"
        const val POOL_TIMEOUT = "PGRST003"
        val SESSION_ERRORS = setOf("42501", "PGRST301", "PGRST302", "PGRST303")
        val RPC_CONTRACT_ERRORS = setOf(
            "PGRST116", "PGRST202", "PGRST203", "42883", "42P01", "42703",
        )
        val SERVICE_ERRORS = setOf("PGRST000", "PGRST001", "PGRST002", "PGRSTX00")
        const val MAXIMUM_VEHICLES = 100L
    }
}
