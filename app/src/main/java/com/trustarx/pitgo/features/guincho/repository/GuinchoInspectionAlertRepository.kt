package com.trustarx.pitgo.features.guincho.repository

import com.trustarx.pitgo.BuildConfig
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.features.guincho.data.InspectionAlertCursor
import com.trustarx.pitgo.features.guincho.data.InspectionAlertPage
import com.trustarx.pitgo.features.guincho.data.InspectionAlertResponse
import com.trustarx.pitgo.features.guincho.model.GuinchoInspectionAlert
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GuinchoInspectionAlertRepository(
    private val client: SupabaseClient,
    private val sessions: () -> SessionRepository,
) {
    private val environment = BuildConfig.ENVIRONMENT_ID

    private fun current(account: String) =
        (sessions().state.value as? AppSessionState.Authenticated.Client)?.userId == account &&
            client.auth.currentUserOrNull()?.id == account

    suspend fun open(account: String, id: String): Pair<String, Boolean>? {
        if (!current(account)) return null
        val result = withTimeout(8000) {
            client.postgrest.rpc("get_my_guincho_inspection_alert", buildJsonObject {
                put("p_alert_id", id)
                put("p_environment_id", environment)
            }).decodeAs<InspectionAlertResponse>()
        }
        require(result.outcome in setOf("PENDING", "RESOLVED"))
        if (!current(account)) return null
        return result.alert?.validate()?.takeIf { it.environment == environment }?.let {
            it.appointmentId to (result.outcome == "PENDING")
        }
    }

    suspend fun refresh(account: String): List<GuinchoInspectionAlert> {
        if (!current(account)) return emptyList()
        val alerts = mutableListOf<GuinchoInspectionAlert>()
        var cursor: InspectionAlertCursor? = null
        do {
            val oldCursor = cursor
            val page = withTimeout(8000) {
                client.postgrest.rpc("list_my_pending_guincho_inspection_approvals", buildJsonObject {
                    put("p_environment_id", environment)
                    put("p_limit", 20)
                    oldCursor?.let {
                        put("p_after_created_at", it.createdAt)
                        put("p_after_id", it.id)
                    }
                }).decodeAs<InspectionAlertPage>()
            }
            alerts += page.items.map { it.validate() }.filter { it.environment == environment }
            cursor = page.next
            require(cursor == null || cursor != oldCursor)
            if (!current(account)) return emptyList()
        } while (cursor != null)
        return alerts
    }
}
