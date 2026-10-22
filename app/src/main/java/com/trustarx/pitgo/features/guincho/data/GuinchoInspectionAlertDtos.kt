package com.trustarx.pitgo.features.guincho.data

import com.trustarx.pitgo.features.guincho.model.GuinchoInspectionAlert
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class InspectionAlertCursor(@SerialName("created_at") val createdAt: String, val id: String)
@Serializable
internal data class InspectionAlertPage(val items: List<GuinchoInspectionAlert>, @SerialName("next_cursor") val next: InspectionAlertCursor? = null)
@Serializable
internal data class InspectionAlertResponse(val outcome: String, val alert: GuinchoInspectionAlert? = null)
