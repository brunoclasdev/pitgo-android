package com.trustarx.pitgo.features.providertracking.data

import kotlinx.serialization.Serializable

@Serializable
internal data class PublishLocationResponseDto(
    val outcome: String,
    val version: Long? = null,
)
