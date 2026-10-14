package com.trustarx.pitgo.features.guincho.data

import com.trustarx.pitgo.features.guincho.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class GuinchoInspectionDocumentDto(
    @SerialName("appointment_id") val appointmentId: String,
    val revision: Long,
    val summary: GuinchoInspectionSummary,
    @SerialName("current_version") val version: GuinchoInspectionVersion? = null,
    val vehicle: JsonObject? = null,
    @SerialName("is_provider") val isProvider: Boolean,
    val closed: Boolean,
    @SerialName("required_photos") val requiredPhotos: List<GuinchoInspectionPhoto> = emptyList(),
    @SerialName("pending_photos") val pending: List<GuinchoInspectionPhoto> = emptyList(),
) {
    fun domain(): GuinchoInspectionDocument {
        require(revision > 0 && appointmentId.isNotBlank())
        return GuinchoInspectionDocument(appointmentId, revision, summary.validate(), version,
            listOf("brand", "model", "plate", "color").mapNotNull { vehicle?.get(it)?.jsonPrimitive?.contentOrNull }.joinToString(" · "),
            isProvider, closed, pending.map { it.validate() }, requiredPhotos.map { it.validate() })
    }
}

@Serializable
internal data class GuinchoInspectionEnvelopeDto(
    val outcome: String,
    val inspection: GuinchoInspectionDocumentDto,
    val photo: GuinchoInspectionPhoto? = null,
)

@Serializable
internal data class GuinchoInspectionPhotoPage(
    val items: List<GuinchoInspectionPhoto>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_order") val nextOrder: Long? = null,
    @SerialName("next_id") val nextId: String? = null,
)
@Serializable
internal data class GuinchoInspectionVersionPage(
    val items: List<GuinchoInspectionVersion>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_version") val nextVersion: Int? = null,
)
