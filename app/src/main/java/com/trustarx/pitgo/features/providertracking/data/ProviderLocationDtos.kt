package com.trustarx.pitgo.features.providertracking.data

import kotlinx.serialization.Serializable

@Serializable
internal data class PublishLocationResponseDto(
    val outcome: String,
    val version: Long? = null,
    val target: String? = null,
    @kotlinx.serialization.SerialName("published_at") val publishedAt: String? = null,
)
