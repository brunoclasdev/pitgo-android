package com.trustarx.pitgo.features.guincho.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class InspectionPoint { FRONT, REAR, LEFT, RIGHT, DETAIL }
@Serializable
enum class InspectionVersionState { DRAFT, SUBMITTED, CORRECTION_REQUESTED, APPROVED }
@Serializable
enum class InspectionPhotoState { RESERVED, READY }

@Serializable
data class GuinchoInspectionSummary(
    val required: Boolean,
    val state: InspectionVersionState? = null,
    @SerialName("current_version_id") val versionId: String? = null,
    @SerialName("current_version_number") val versionNumber: Int? = null,
    @SerialName("required_points_ready") val readyPoints: Int = 0,
    @SerialName("photo_count") val photoCount: Int = 0,
    @SerialName("approved_at") val approvedAt: String? = null,
    @SerialName("correction_reason") val correctionReason: String? = null,
    @SerialName("allowed_actions") val actions: List<String> = emptyList(),
) {
    fun validate(): GuinchoInspectionSummary = apply {
        require(readyPoints in 0..4 && photoCount >= readyPoints)
        require(!required || (versionId != null && state != null && (versionNumber ?: 0) > 0))
    }
}

@Serializable
data class GuinchoInspectionVersion(
    val id: String,
    @SerialName("version_number") val number: Int,
    val state: InspectionVersionState,
    @SerialName("created_at") val createdAt: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("decided_by") val decidedBy: String? = null,
    @SerialName("decided_by_name") val decidedByName: String? = null,
    @SerialName("correction_reason") val correctionReason: String? = null,
)

@Serializable
data class GuinchoInspectionPhoto(
    val id: String,
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("reserved_for_version_id") val reservedVersionId: String,
    val point: InspectionPoint,
    val caption: String? = null,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("captured_by") val capturedBy: String,
    @SerialName("captured_by_name") val capturedByName: String,
    @SerialName("saved_at") val savedAt: String? = null,
    @SerialName("photo_order") val order: Long,
    @SerialName("object_path") val objectPath: String,
    @SerialName("byte_size") val byteSize: Long,
    val state: InspectionPhotoState,
    val sent: Boolean = false,
) {
    fun validate(): GuinchoInspectionPhoto = apply {
        require(objectPath == "$appointmentId/$id.jpg" && byteSize in 1..5_242_880)
        require(state != InspectionPhotoState.READY || savedAt != null)
    }
}

data class GuinchoInspectionDocument(
    val appointmentId: String,
    val revision: Long,
    val summary: GuinchoInspectionSummary,
    val version: GuinchoInspectionVersion?,
    val vehicle: String,
    val isProvider: Boolean,
    val closed: Boolean,
    val pending: List<GuinchoInspectionPhoto>,
    val requiredPhotos: List<GuinchoInspectionPhoto> = emptyList(),
)

enum class InspectionFailure { OFFLINE, TIMEOUT, SESSION, FORBIDDEN, CONFLICT, CLOSED, INCOMPLETE, IMAGE, CAMERA, UNKNOWN }
class InspectionException(val failure: InspectionFailure) : Exception()
