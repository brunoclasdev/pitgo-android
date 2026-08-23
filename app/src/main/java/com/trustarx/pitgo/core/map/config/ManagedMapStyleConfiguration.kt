package com.trustarx.pitgo.core.map.config

import com.trustarx.pitgo.BuildConfig

data class ManagedMapStyleConfiguration(
    val styleUrl: String,
    val providerName: String,
    val attribution: String,
    val attributionUrl: String,
)

object PitGoManagedMapStyle {
    val configuration = ManagedMapStyleConfiguration(
        styleUrl = BuildConfig.MAP_STYLE_URL,
        providerName = BuildConfig.MAP_STYLE_PROVIDER_NAME,
        attribution = BuildConfig.MAP_STYLE_ATTRIBUTION,
        attributionUrl = BuildConfig.MAP_STYLE_ATTRIBUTION_URL,
    )
}
