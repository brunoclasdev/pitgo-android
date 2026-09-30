package com.trustarx.pitgo.core.environment

object EnvironmentDiagnostics {
    val label get() = "${EnvironmentConfig.label} • ${EnvironmentConfig.paymentMode.name}"
}
