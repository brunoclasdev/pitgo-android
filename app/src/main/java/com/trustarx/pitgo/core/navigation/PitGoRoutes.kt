package com.trustarx.pitgo.core.navigation

import android.net.Uri
import com.trustarx.pitgo.core.common.model.DemoServiceId

object PitGoRoutes {
    const val Welcome = "welcome"
    const val Home = "home"
    const val RequestService = "request-service"
    const val RequestServiceArg = "serviceId"
    const val RequestServicePattern = "$RequestService?$RequestServiceArg={$RequestServiceArg}"

    fun requestService(serviceId: DemoServiceId): String =
        "$RequestService?$RequestServiceArg=${Uri.encode(serviceId.name)}"
}
