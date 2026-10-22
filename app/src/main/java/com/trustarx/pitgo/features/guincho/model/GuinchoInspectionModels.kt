package com.trustarx.pitgo.features.guincho.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class InspectionPoint { FRONT, REAR, LEFT, RIGHT, DETAIL }
@Serializable
enum class InspectionVersionState { DRAFT, SUBMITTED, CORRECTION_REQUESTED, APPROVED }
@Serializable
enum class InspectionPhotoState { RESERVED, READY }

enum class InspectionDetailPoint {
    TRUNK_INTERIOR, INSTRUMENT_PANEL, FRONT_SEATS, REAR_SEATS, FULL_DASHBOARD,
    TOOL_KIT, SPARE_TIRE, FRONT_LEFT_TIRE, FRONT_RIGHT_TIRE, REAR_RIGHT_TIRE,
    REAR_LEFT_TIRE, ENGINE, BATTERY,
}
data class InspectionSlot(val point: InspectionPoint, val detail: String? = null) {
    val key: String get() = detail ?: point.name
    val required: Boolean get() = point in REQUIRED_INSPECTION_POINTS
}
val REQUIRED_INSPECTION_POINTS: Set<InspectionPoint> = setOf(InspectionPoint.FRONT, InspectionPoint.REAR, InspectionPoint.LEFT, InspectionPoint.RIGHT)
val INSPECTION_SLOTS: List<InspectionSlot> = REQUIRED_INSPECTION_POINTS.map { InspectionSlot(it) } +
    InspectionDetailPoint.entries.map { InspectionSlot(InspectionPoint.DETAIL, it.name) }

@Serializable
enum class InspectionCaptureStage {
    AWAITING_CAMERA, RAW_CAPTURED, PREPARING, PREPARED, RESERVING, UPLOADING_ORIGINAL,
    UPLOADING_THUMBNAIL, CONFIRMING, RETRY_WAIT, NEEDS_RECAPTURE, BLOCKED_CONTEXT, SAVED, DISCARDED,
}
@Serializable
data class InspectionPendingCapture(
    val schemaVersion: Int = 1,
    val captureId: String,
    val accountId: String,
    val appointmentId: String,
    val versionId: String,
    val point: InspectionPoint,
    val detailPoint: String? = null,
    val caption: String = "",
    val replacesPhotoId: String? = null,
    val reserveOperationId: String,
    val confirmOperationId: String,
    val discardOperationId: String,
    val capturedAt: String? = null,
    val capturedElapsedNanos: Long? = null,
    val capturedBootCount: Int? = null,
    val stage: InspectionCaptureStage = InspectionCaptureStage.AWAITING_CAMERA,
    val resumeStage: InspectionCaptureStage? = null,
    val photo: GuinchoInspectionPhoto? = null,
    val originalSize: Long = 0,
    val thumbnailSize: Long = 0,
    val originalHash: String? = null,
    val thumbnailHash: String? = null,
    val failure: InspectionFailure? = null,
    val attempts: Int = 0,
    val retryAt: Long = 0,
    val discardRequested: Boolean = false,
    val repairReservation: Boolean = false,
) {
    val slot: InspectionSlot get() = InspectionSlot(point, detailPoint)
    val pending: Boolean get() = stage !in setOf(InspectionCaptureStage.SAVED, InspectionCaptureStage.DISCARDED)
    val retryable: Boolean get() = pending && !discardRequested && stage in setOf(
        InspectionCaptureStage.RETRY_WAIT, InspectionCaptureStage.NEEDS_RECAPTURE)
}

enum class InspectionCommandOutcome { APPLIED, EXISTING, REVISION_CONFLICT }
data class InspectionSubmitResult(val outcome: InspectionCommandOutcome, val document: GuinchoInspectionDocument)

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
    @SerialName("detail_point") val detailPoint: String? = null,
    @SerialName("thumbnail_object_path") val thumbnailPath: String? = null,
    @SerialName("thumbnail_byte_size") val thumbnailByteSize: Long? = null,
    @SerialName("abandoned_at") val abandonedAt: String? = null,
    @SerialName("discard_operation_id") val discardOperationId: String? = null,
    @SerialName("reserve_operation_id") val reserveOperationId: String? = null,
) {
    val slot: InspectionSlot get() = InspectionSlot(point, detailPoint)
    fun validate(): GuinchoInspectionPhoto = apply {
        require(objectPath == "$appointmentId/$id.jpg" && byteSize in 1..5_242_880)
        require(state != InspectionPhotoState.READY || savedAt != null)
        require(state != InspectionPhotoState.READY || abandonedAt == null)
        require(detailPoint == null || point == InspectionPoint.DETAIL)
        require((thumbnailPath == null && thumbnailByteSize == null) ||
            (thumbnailPath == "$appointmentId/$id.thumb.jpg" && thumbnailByteSize != null && thumbnailByteSize in 1L..262_144L))
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
    val namedPhotos: List<GuinchoInspectionPhoto> = emptyList(),
)

@Serializable
enum class InspectionFailure { OFFLINE, TIMEOUT, SESSION, FORBIDDEN, CONFLICT, CONTEXT, CLOSED, INCOMPLETE, IMAGE, CAMERA, SPACE, INTERRUPTED, SERVICE, UNKNOWN }
class InspectionException(val failure: InspectionFailure) : Exception()
