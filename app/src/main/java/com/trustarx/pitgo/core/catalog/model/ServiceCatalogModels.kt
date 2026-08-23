package com.trustarx.pitgo.core.catalog.model

data class ServiceCategory(
    val id: String,
    val name: String,
    val description: String?,
)

data class CatalogService(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String?,
)

data class HomeService(
    val id: String,
    val name: String,
    val description: String?,
    val categoryName: String,
    val minimumPrice: String?,
)

sealed interface CatalogLoadResult<out T> {
    data class Success<T>(val items: List<T>) : CatalogLoadResult<T>
    data object Offline : CatalogLoadResult<Nothing>
    data object Timeout : CatalogLoadResult<Nothing>
    data object SessionExpired : CatalogLoadResult<Nothing>
    data object ServiceUnavailable : CatalogLoadResult<Nothing>
    data object Unknown : CatalogLoadResult<Nothing>
}

sealed interface CatalogItemResult<out T> {
    data class Success<T>(val item: T) : CatalogItemResult<T>
    data object NotFound : CatalogItemResult<Nothing>
    data object Offline : CatalogItemResult<Nothing>
    data object Timeout : CatalogItemResult<Nothing>
    data object SessionExpired : CatalogItemResult<Nothing>
    data object ServiceUnavailable : CatalogItemResult<Nothing>
    data object Unknown : CatalogItemResult<Nothing>
}
