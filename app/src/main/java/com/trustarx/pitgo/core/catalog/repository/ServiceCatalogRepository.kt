package com.trustarx.pitgo.core.catalog.repository

import com.trustarx.pitgo.core.catalog.model.CatalogLoadResult
import com.trustarx.pitgo.core.catalog.model.CatalogItemResult
import com.trustarx.pitgo.core.catalog.model.CatalogService
import com.trustarx.pitgo.core.catalog.model.HomeService
import com.trustarx.pitgo.core.catalog.model.ServiceCategory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class ServiceCatalogRepository(private val client: SupabaseClient) {
    suspend fun categories(): CatalogLoadResult<ServiceCategory> = loadPaged(
        table = "service_categories",
        decode = { client.from("service_categories").select {
            order("name", Order.ASCENDING); range(it, it + PAGE_SIZE - 1)
        }.decodeList<CategoryDto>() },
        map = { ServiceCategory(it.id, it.name, it.description) },
    )

    suspend fun services(categoryId: String): CatalogLoadResult<CatalogService> = loadPaged(
        table = "services",
        decode = { start -> client.from("services").select {
            filter { eq("category_id", categoryId) }
            order("name", Order.ASCENDING); range(start, start + PAGE_SIZE - 1)
        }.decodeList<ServiceDto>() },
        map = { CatalogService(it.id, it.categoryId, it.name, it.description) },
    )

    suspend fun homeServices(): CatalogLoadResult<HomeService> = try {
        val rows = client.postgrest.rpc("list_home_services").decodeList<HomeServiceDto>()
        CatalogLoadResult.Success(rows.map {
            HomeService(
                it.serviceId, it.name, it.description, it.categoryName,
                it.minimumPrice?.jsonPrimitive?.content,
            )
        })
    } catch (cancel: CancellationException) { throw cancel }
      catch (error: Throwable) { classify(error) }

    suspend fun service(serviceId: String): CatalogItemResult<HomeService> {
        if (!isUuid(serviceId)) return CatalogItemResult.NotFound
        return when (val result = homeServices()) {
            is CatalogLoadResult.Success -> result.items.firstOrNull { it.id == serviceId }
                ?.let { CatalogItemResult.Success(it) }
                ?: CatalogItemResult.NotFound
            CatalogLoadResult.Offline -> CatalogItemResult.Offline
            CatalogLoadResult.Timeout -> CatalogItemResult.Timeout
            CatalogLoadResult.SessionExpired -> CatalogItemResult.SessionExpired
            CatalogLoadResult.ServiceUnavailable -> CatalogItemResult.ServiceUnavailable
            CatalogLoadResult.Unknown -> CatalogItemResult.Unknown
        }
    }

    private suspend fun <D, T> loadPaged(
        @Suppress("UNUSED_PARAMETER") table: String,
        decode: suspend (Long) -> List<D>,
        map: (D) -> T,
    ): CatalogLoadResult<T> = try {
        val all = mutableListOf<T>()
        var start = 0L
        do {
            val page = decode(start)
            all += page.map(map)
            start += page.size
        } while (page.size == PAGE_SIZE.toInt())
        CatalogLoadResult.Success(all)
    } catch (cancel: CancellationException) { throw cancel }
      catch (error: Throwable) { classify(error) }

    private fun classify(error: Throwable): CatalogLoadResult<Nothing> = when (error) {
        is HttpRequestTimeoutException -> CatalogLoadResult.Timeout
        is HttpRequestException -> CatalogLoadResult.Offline
        is PostgrestRestException -> when {
            error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
                CatalogLoadResult.SessionExpired
            error.statusCode >= 500 -> CatalogLoadResult.ServiceUnavailable
            else -> CatalogLoadResult.Unknown
        }
        else -> CatalogLoadResult.Unknown
    }

    private companion object { const val PAGE_SIZE = 100L }

    private fun isUuid(value: String): Boolean = runCatching {
        java.util.UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)
}

@Serializable private data class CategoryDto(
    val id: String, val name: String, val description: String? = null,
)
@Serializable private data class ServiceDto(
    val id: String, @SerialName("category_id") val categoryId: String,
    val name: String, val description: String? = null,
)
@Serializable private data class HomeServiceDto(
    @SerialName("service_id") val serviceId: String,
    val name: String,
    val description: String? = null,
    @SerialName("category_name") val categoryName: String,
    @SerialName("minimum_price") val minimumPrice: JsonElement? = null,
)
