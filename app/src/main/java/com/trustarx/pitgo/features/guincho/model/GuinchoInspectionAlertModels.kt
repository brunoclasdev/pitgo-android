package com.trustarx.pitgo.features.guincho.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class GuinchoInspectionAlert(
    @SerialName("alert_id") val id: String,
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("version_id") val versionId: String,
    @SerialName("version_number") val versionNumber: Int,
    @SerialName("submitted_at") val submittedAt: String,
    @SerialName("created_at") val createdAt: String,
    val revision: Long,
    @SerialName("environment_id") val environment: String,
) {
    fun validate() = apply {
        listOf(id, appointmentId, versionId).forEach { UUID.fromString(it) }
        require(revision > 0 && versionNumber > 0 && environment in setOf("dev", "homol", "prod"))
    }
}
