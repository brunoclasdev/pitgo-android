package com.trustarx.pitgo.core.environment

import com.trustarx.pitgo.BuildConfig

enum class EnvironmentId { DEV, HOMOL, PROD }
enum class PaymentMode { SIMULATED, INTEGRATED }

object EnvironmentConfig {
    val id = EnvironmentId.valueOf(BuildConfig.ENVIRONMENT_ID.uppercase())
    val paymentMode = if (id == EnvironmentId.DEV) PaymentMode.SIMULATED else PaymentMode.INTEGRATED
    val simulated = paymentMode == PaymentMode.SIMULATED
    val label = "${BuildConfig.ENVIRONMENT_ID} • ${BuildConfig.VERSION_NAME}"
    val fingerprint = "${id.name}:${BuildConfig.SUPABASE_PROJECT_REF}:1"
    init {
        require(simulated == BuildConfig.SIMULATED_PAYMENTS)
        require(BuildConfig.SUPABASE_URL == "https://${BuildConfig.SUPABASE_PROJECT_REF}.supabase.co")
    }
}
