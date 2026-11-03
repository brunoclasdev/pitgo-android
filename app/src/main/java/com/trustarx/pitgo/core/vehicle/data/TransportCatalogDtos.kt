package com.trustarx.pitgo.core.vehicle.data

import com.trustarx.pitgo.core.vehicle.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
internal data class TransportCategoryDto(val id: String, val code: String, val name: String) {
    fun domain() = TransportCategory(id, code, name).also { java.util.UUID.fromString(id); require(code.isNotBlank() && name.isNotBlank()) }
}
@Serializable
internal data class GuinchoSizeDto(val id: String, val code: String, val name: String, val revision: Long,
    @SerialName("vehicle_category_ids") val categoryIds: List<String>) {
    fun domain() = GuinchoSize(id, code, name, revision, categoryIds).also {
        java.util.UUID.fromString(id); categoryIds.forEach { java.util.UUID.fromString(it) }
        require(revision > 0 && name.isNotBlank() && code.isNotBlank())
    }
}
@Serializable
internal data class TransportCatalogDto(@SerialName("vehicle_categories") val categories: List<TransportCategoryDto>, val sizes: List<GuinchoSizeDto>) {
    fun domain() = TransportCatalog(categories.map { it.domain() }, sizes.map { it.domain() }).also { c ->
        require(c.categories.map { it.id }.distinct().size == c.categories.size)
        require(c.sizes.map { it.id }.distinct().size == c.sizes.size)
        require(c.sizes.all { c.categories.map { it.id }.containsAll(it.vehicleCategoryIds) })
    }
}
