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

    fun maximumYear(): Int = Year.now().value + 1

    fun validateField(
        field: VehicleField,
        value: String,
        maximumYear: Int = maximumYear(),
    ): VehicleFieldError? = when (field) {
        VehicleField.BRAND,
        VehicleField.MODEL,
        -> VehicleFieldError.REQUIRED.takeIf { value.trim().isEmpty() }
        VehicleField.PLATE -> normalizePlate(value).takeIf(String::isNotEmpty)?.let { plate ->
            VehicleFieldError.INVALID_FORMAT.takeUnless {
                legacyPlate.matches(plate) || mercosurPlate.matches(plate)
            }
        }
        VehicleField.RENAVAM -> normalizeRenavam(value).takeIf(String::isNotEmpty)?.let { renavam ->
            VehicleFieldError.INVALID_FORMAT.takeUnless { renavamPattern.matches(renavam) }
        }
        VehicleField.CHASSIS -> normalizeChassis(value).takeIf(String::isNotEmpty)?.let { chassis ->
            VehicleFieldError.INVALID_FORMAT.takeUnless { chassisPattern.matches(chassis) }
        }
        VehicleField.MANUFACTURE_YEAR,
        VehicleField.MODEL_YEAR,
        -> validateOptionalInteger(value, 1900..maximumYear)
        VehicleField.MILEAGE -> validateOptionalNonNegativeInteger(value)
        VehicleField.VERSION,
        VehicleField.COLOR,
        VehicleField.FUEL_TYPE,
        VehicleField.TRANSMISSION,
        VehicleField.ENGINE,
        -> null
    }

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
        val maximumYear = maximumYear()
        val errors = linkedMapOf<VehicleField, VehicleFieldError>()

        val rawValues = mapOf(
            VehicleField.BRAND to brand,
            VehicleField.MODEL to model,
            VehicleField.VERSION to version,
            VehicleField.MANUFACTURE_YEAR to manufactureYear,
            VehicleField.MODEL_YEAR to modelYear,
            VehicleField.PLATE to plate,
            VehicleField.RENAVAM to renavam,
            VehicleField.CHASSIS to chassis,
            VehicleField.COLOR to color,
            VehicleField.FUEL_TYPE to fuelType,
            VehicleField.TRANSMISSION to transmission,
            VehicleField.ENGINE to engine,
            VehicleField.MILEAGE to mileage,
        )
        VehicleField.entries.forEach { field ->
            validateField(field, rawValues.getValue(field), maximumYear)?.let { error ->
                errors[field] = error
            }
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

    private fun validateOptionalInteger(
        value: String,
        validRange: IntRange,
    ): VehicleFieldError? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null
        val number = normalized.toIntOrNull() ?: return VehicleFieldError.INVALID_FORMAT
        return VehicleFieldError.OUT_OF_RANGE.takeUnless { number in validRange }
    }

    private fun validateOptionalNonNegativeInteger(value: String): VehicleFieldError? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null
        val number = normalized.toIntOrNull() ?: return VehicleFieldError.INVALID_FORMAT
        return VehicleFieldError.OUT_OF_RANGE.takeIf { number < 0 }
    }
}
