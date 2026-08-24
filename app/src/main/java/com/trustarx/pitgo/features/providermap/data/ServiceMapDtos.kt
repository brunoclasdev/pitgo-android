package com.trustarx.pitgo.features.providermap.data

import com.trustarx.pitgo.features.providermap.model.PublicProvider
import com.trustarx.pitgo.features.providermap.model.ProviderLocationMode
import com.trustarx.pitgo.core.map.model.MapCoordinate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class PublicProviderDto(
    @SerialName("provider_id") val providerId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("location_mode") val locationMode: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_meters") val distanceMeters: Double? = null,
    @SerialName("minimum_price") val minimumPrice: JsonElement? = null,
) {
    fun toDomainOrNull(): PublicProvider? {
        val coordinate = MapCoordinate(latitude, longitude)
        val mode = ProviderLocationMode.entries.firstOrNull { it.name == locationMode }
            ?: return null
        if (!coordinate.isValid || displayName.isBlank() ||
            (distanceMeters != null && (!distanceMeters.isFinite() || distanceMeters < 0)) ||
            !isUuid(providerId)
        ) return null
        return PublicProvider(
            id = providerId,
            displayName = displayName.trim(),
            locationMode = mode,
            addressCoordinate = coordinate,
            distanceMeters = distanceMeters,
            minimumPrice = minimumPrice?.jsonPrimitive?.content,
        )
    }

    private fun isUuid(value: String): Boolean = runCatching {
        java.util.UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)
}
