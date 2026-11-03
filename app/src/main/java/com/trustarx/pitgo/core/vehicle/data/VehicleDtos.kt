package com.trustarx.pitgo.core.vehicle.data

import com.trustarx.pitgo.core.vehicle.model.Vehicle
import com.trustarx.pitgo.core.vehicle.model.VehicleDraft
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class VehicleDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val brand: String,
    val model: String,
    val version: String? = null,
    @SerialName("manufacture_year") val manufactureYear: Int? = null,
    @SerialName("model_year") val modelYear: Int? = null,
    val plate: String? = null,
    val renavam: String? = null,
    val chassis: String? = null,
    val color: String? = null,
    @SerialName("fuel_type") val fuelType: String? = null,
    val transmission: String? = null,
    val engine: String? = null,
    val mileage: Int? = null,
    @SerialName("is_primary") val isPrimary: Boolean,
    val active: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("transport_category_id") val transportCategoryId: String? = null,
)

@Serializable
internal data class CreateVehicleRpcParams(
    @SerialName("p_brand") val brand: String,
    @SerialName("p_model") val model: String,
    @SerialName("p_version") val version: String?,
    @SerialName("p_manufacture_year") val manufactureYear: Int?,
    @SerialName("p_model_year") val modelYear: Int?,
    @SerialName("p_plate") val plate: String?,
    @SerialName("p_renavam") val renavam: String?,
    @SerialName("p_chassis") val chassis: String?,
    @SerialName("p_color") val color: String?,
    @SerialName("p_fuel_type") val fuelType: String?,
    @SerialName("p_transmission") val transmission: String?,
    @SerialName("p_engine") val engine: String?,
    @SerialName("p_mileage") val mileage: Int?,
    @SerialName("p_make_primary") val makePrimary: Boolean,
    @SerialName("p_transport_category_id") val transportCategoryId: String? = null,
)

@Serializable
internal data class SetPrimaryVehicleRpcParams(
    @SerialName("p_vehicle_id") val vehicleId: String,
)

internal fun VehicleDto.toDomain() = Vehicle(
    id = id,
    userId = userId,
    brand = brand,
    model = model,
    version = version,
    manufactureYear = manufactureYear,
    modelYear = modelYear,
    plate = plate,
    renavam = renavam,
    chassis = chassis,
    color = color,
    fuelType = fuelType,
    transmission = transmission,
    engine = engine,
    mileage = mileage,
    isPrimary = isPrimary,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
    transportCategoryId = transportCategoryId,
)

internal fun VehicleDraft.toRpcParams() = CreateVehicleRpcParams(
    brand = brand,
    model = model,
    version = version,
    manufactureYear = manufactureYear,
    modelYear = modelYear,
    plate = plate,
    renavam = renavam,
    chassis = chassis,
    color = color,
    fuelType = fuelType,
    transmission = transmission,
    engine = engine,
    mileage = mileage,
    makePrimary = makePrimary,
    transportCategoryId = transportCategoryId,
)
