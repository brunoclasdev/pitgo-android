package com.trustarx.pitgo.core.vehicle.validation

import com.trustarx.pitgo.core.vehicle.model.VehicleDraft
import com.trustarx.pitgo.core.vehicle.model.VehicleField
import com.trustarx.pitgo.core.vehicle.model.VehicleFieldError
import java.time.Year
import java.util.Locale

data class VehicleValidationResult(
    val draft: VehicleDraft?,
    val errors: Map<VehicleField, VehicleFieldError>,
) {
    val firstInvalidField: VehicleField?
        get() = VehicleField.entries.firstOrNull(errors::containsKey)
}

object VehicleValidation {
    private val legacyPlate = Regex("^[A-Z]{3}[0-9]{4}$")
    private val mercosurPlate = Regex("^[A-Z]{3}[0-9][A-Z][0-9]{2}$")
    private val renavamPattern = Regex("^[0-9]{11}$")
    private val chassisPattern = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    fun normalizePlate(value: String): String = value
        .trim()
        .uppercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    fun normalizeRenavam(value: String): String = value.trim()

    fun normalizeChassis(value: String): String = value
        .trim()
        .uppercase(Locale.ROOT)

    @Suppress("LongParameterList")
    fun validate(
        brand: String,
        model: String,
        version: String,
        manufactureYear: String,
        modelYear: String,
        plate: String,
        renavam: String,
        chassis: String,
        color: String,
        fuelType: String,
        transmission: String,
        engine: String,
        mileage: String,
        makePrimary: Boolean,
    ): VehicleValidationResult {
        val normalizedBrand = brand.trim()
        val normalizedModel = model.trim()
        val normalizedPlate = normalizePlate(plate).takeIf(String::isNotEmpty)
        val normalizedRenavam = normalizeRenavam(renavam).takeIf(String::isNotEmpty)
        val normalizedChassis = normalizeChassis(chassis).takeIf(String::isNotEmpty)
        val manufactureYearValue = manufactureYear.trim().toIntOrNull()
        val modelYearValue = modelYear.trim().toIntOrNull()
        val mileageValue = mileage.trim().toIntOrNull()
        val maximumYear = Year.now().value + 1
        val errors = linkedMapOf<VehicleField, VehicleFieldError>()

        if (normalizedBrand.isBlank()) errors[VehicleField.BRAND] = VehicleFieldError.REQUIRED
        if (normalizedModel.isBlank()) errors[VehicleField.MODEL] = VehicleFieldError.REQUIRED
        if (manufactureYear.isNotBlank() && manufactureYearValue == null) {
            errors[VehicleField.MANUFACTURE_YEAR] = VehicleFieldError.INVALID_FORMAT
        } else if (manufactureYearValue != null && manufactureYearValue !in 1900..maximumYear) {
            errors[VehicleField.MANUFACTURE_YEAR] = VehicleFieldError.OUT_OF_RANGE
        }
        if (modelYear.isNotBlank() && modelYearValue == null) {
            errors[VehicleField.MODEL_YEAR] = VehicleFieldError.INVALID_FORMAT
        } else if (modelYearValue != null && modelYearValue !in 1900..maximumYear) {
            errors[VehicleField.MODEL_YEAR] = VehicleFieldError.OUT_OF_RANGE
        }
        if (normalizedPlate != null &&
            !legacyPlate.matches(normalizedPlate) && !mercosurPlate.matches(normalizedPlate)
        ) {
            errors[VehicleField.PLATE] = VehicleFieldError.INVALID_FORMAT
        }
        if (normalizedRenavam != null && !renavamPattern.matches(normalizedRenavam)) {
            errors[VehicleField.RENAVAM] = VehicleFieldError.INVALID_FORMAT
        }
        if (normalizedChassis != null && !chassisPattern.matches(normalizedChassis)) {
            errors[VehicleField.CHASSIS] = VehicleFieldError.INVALID_FORMAT
        }
        if (mileage.isNotBlank() && mileageValue == null) {
            errors[VehicleField.MILEAGE] = VehicleFieldError.INVALID_FORMAT
        } else if (mileageValue != null && mileageValue < 0) {
            errors[VehicleField.MILEAGE] = VehicleFieldError.OUT_OF_RANGE
        }

        if (errors.isNotEmpty()) return VehicleValidationResult(null, errors)

        return VehicleValidationResult(
            draft = VehicleDraft(
                brand = normalizedBrand,
                model = normalizedModel,
                version = version.trim().takeIf(String::isNotEmpty),
                manufactureYear = manufactureYearValue,
                modelYear = modelYearValue,
                plate = normalizedPlate,
                renavam = normalizedRenavam,
                chassis = normalizedChassis,
                color = color.trim().takeIf(String::isNotEmpty),
                fuelType = fuelType.trim().takeIf(String::isNotEmpty),
                transmission = transmission.trim().takeIf(String::isNotEmpty),
                engine = engine.trim().takeIf(String::isNotEmpty),
                mileage = mileageValue,
                makePrimary = makePrimary,
            ),
            errors = emptyMap(),
        )
    }
}
