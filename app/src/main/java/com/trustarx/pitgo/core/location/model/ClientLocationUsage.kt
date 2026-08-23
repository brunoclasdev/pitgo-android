package com.trustarx.pitgo.core.location.model

enum class ClientLocationPurpose { PROVIDER_DISCOVERY, APPOINTMENT_TRACKING }

sealed interface ClientLocationUsage {
    data object Idle : ClientLocationUsage
    data class Visible(val purpose: ClientLocationPurpose, val startedAtEpochMillis: Long) :
        ClientLocationUsage
}
