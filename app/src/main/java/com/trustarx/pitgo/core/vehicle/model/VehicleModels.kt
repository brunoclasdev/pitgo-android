package com.trustarx.pitgo.core.vehicle.model

data class Vehicle(
    val id: String,
    val userId: String,
    val brand: String,
    val model: String,
    val version: String?,
    val manufactureYear: Int?,
    val modelYear: Int?,
    val plate: String?,
    val renavam: String?,
    val chassis: String?,
    val color: String?,
    val fuelType: String?,
    val transmission: String?,
    val engine: String?,
    val mileage: Int?,
    val isPrimary: Boolean,
    val active: Boolean,
    val createdAt: String,
    val updatedAt: String,
) {
    val displayName: String
        get() = listOfNotNull(brand, model, version).joinToString(" ")
}

data class VehicleDraft(
    val brand: String,
    val model: String,
    val version: String?,
    val manufactureYear: Int?,
    val modelYear: Int?,
    val plate: String?,
    val renavam: String?,
    val chassis: String?,
    val color: String?,
    val fuelType: String?,
    val transmission: String?,
    val engine: String?,
    val mileage: Int?,
    val makePrimary: Boolean,
)

enum class VehicleFormOrigin {
    ONBOARDING,
    GARAGE,
}

enum class VehicleField {
    BRAND,
    MODEL,
    VERSION,
    MANUFACTURE_YEAR,
    MODEL_YEAR,
    PLATE,
    RENAVAM,
    CHASSIS,
    COLOR,
    FUEL_TYPE,
    TRANSMISSION,
    ENGINE,
    MILEAGE,
}

enum class VehicleFieldError {
    REQUIRED,
    INVALID_FORMAT,
    OUT_OF_RANGE,
    UNAVAILABLE,
}

sealed interface VehicleFailure {
    data object Offline : VehicleFailure
    data object Timeout : VehicleFailure
    data object SessionExpired : VehicleFailure
    data object ServiceUnavailable : VehicleFailure
    data object RpcUnavailable : VehicleFailure
    data object Contract : VehicleFailure
    data object VehicleUnavailable : VehicleFailure
    data object Unknown : VehicleFailure
}

sealed interface VehicleMutationResult {
    data class Success(val vehicle: Vehicle) : VehicleMutationResult
    data class FieldFailure(
        val field: VehicleField,
        val error: VehicleFieldError,
    ) : VehicleMutationResult
    data class Failure(val reason: VehicleFailure) : VehicleMutationResult
}

sealed interface VehicleListResult {
    data class Success(val vehicles: List<Vehicle>) : VehicleListResult
    data class Failure(val reason: VehicleFailure) : VehicleListResult
}
