package com.trustarx.pitgo.features.store.repository

import com.trustarx.pitgo.features.store.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class StoreRepository(private val client: SupabaseClient) {
    suspend fun stores(): List<StoreSummary> = client.postgrest.rpc("list_stores").decodeList()

    suspend fun products(storeId: String): List<StoreProduct> = client.postgrest.rpc("list_store_products",
        buildJsonObject { put("p_store_id", storeId) }).decodeList<StoreProduct>().map { product ->
        val url = product.imagePath?.let { path ->
            try { client.storage.from("store-product-images").createSignedUrl(path, 5.minutes) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null }
        }
        product.copy(imageUrl = url)
    }

    suspend fun upload(storeId: String, draft: ProductDraft): String {
        val bytes = requireNotNull(draft.photo)
        require(bytes.size <= 5 * 1024 * 1024)
        require(draft.photoMime in setOf("image/jpeg", "image/png", "image/webp"))
        val extension = when (draft.photoMime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val path = "$storeId/${draft.id}/${UUID.randomUUID()}.$extension"
        client.storage.from("store-product-images").upload(path, bytes) {
            upsert = false; contentType = ContentType.parse(draft.photoMime)
        }
        return path
    }

    suspend fun save(draft: ProductDraft) {
        client.postgrest.rpc("save_store_product", buildJsonObject {
            put("p_product", buildJsonObject {
                put("id", draft.id); put("name", draft.name.trim()); put("description", draft.description.trim())
                put("price", draft.price.replace(',', '.')); put("active", draft.active)
                put("image_path", draft.imagePath?.let(::JsonPrimitive) ?: JsonNull)
            })
        })
    }

    suspend fun delete(product: StoreProduct) {
        client.postgrest.rpc("delete_store_product", buildJsonObject { put("p_product_id", product.id) })
        // Product visibility changes atomically; an old object can be cleaned up separately.
        product.imagePath?.let {
            try { client.storage.from("store-product-images").delete(it) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Deletion is committed; object cleanup can be retried separately. */ }
        }
    }
}
