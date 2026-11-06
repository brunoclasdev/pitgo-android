package com.trustarx.pitgo.features.registrationverification.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import com.trustarx.pitgo.features.registrationverification.model.RegistrationStatus
import com.trustarx.pitgo.features.registrationverification.model.DocumentKind

@Serializable
data class RegistrationMembership(
 @SerialName("account_type") val accountType: String,
 @SerialName("verification_status") val status: RegistrationStatus? = null,
 @SerialName("can_use_marketplace") val allowed: Boolean = false,
 @SerialName("legacy_exempt") val legacy: Boolean = false,
)
@Serializable
data class RegistrationEvidence(
 val id: String, val kind: DocumentKind,
 @SerialName("vehicle_reference_id") val vehicleId: String? = null,
 @SerialName("storage_path") val path: String,
 @SerialName("upload_state") val uploadState: com.trustarx.pitgo.features.registrationverification.model.RegistrationUploadState,
)
@Serializable
data class RegistrationReview(val result: String, val reason: String? = null,
 @SerialName("correction_items") val items: List<String> = emptyList())
@Serializable
data class RegistrationCase(
 @SerialName("case_id") val caseId: String,
 @SerialName("account_type") val accountType: String,
 val status: RegistrationStatus? = null, val revision: Long,
 @SerialName("submission_id") val submissionId: String? = null,
 @SerialName("submission_state") val submissionState: com.trustarx.pitgo.features.registrationverification.model.RegistrationSubmissionState? = null,
 val data: JsonObject = buildJsonObject {}, val identity: JsonObject? = null,
 @SerialName("pending_items") val pendingItems: List<String> = emptyList(),
 val evidence: List<RegistrationEvidence> = emptyList(), val reviews: List<RegistrationReview> = emptyList(),
)
@Serializable
data class EvidenceReservation(
 @SerialName("evidence_id") val evidenceId: String,
 @SerialName("object_path") val path: String, val revision: Long,
)
