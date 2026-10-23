package com.trustarx.pitgo.features.store.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Serializable
data class StoreSummary(val id: String, val name: String, val description: String? = null, val city: String = "")

@Serializable
data class StoreProduct(
    val id: String,
    @SerialName("store_provider_id") val storeProviderId: String,
    val name: String,
    val description: String,
    val price: JsonElement,
    @SerialName("image_path") val imagePath: String? = null,
    val active: Boolean = true,
    @Transient val imageUrl: String? = null,
) {
    val amount get() = price.jsonPrimitive.content.toBigDecimal()
}

data class ProductDraft(
    val id: String = UUID.randomUUID().toString(), val name: String = "", val description: String = "",
    val price: String = "", val imagePath: String? = null, val photo: ByteArray? = null,
    val photoMime: String = "image/jpeg", val active: Boolean = true,
)

enum class StoreOperation { IDLE, LOADING, SAVING }
data class StoreUiState(
    val stores: List<StoreSummary> = emptyList(), val products: List<StoreProduct> = emptyList(),
    val selectedStoreId: String? = null, val selectedStoreName: String? = null,
    val draft: ProductDraft? = null, val operation: StoreOperation = StoreOperation.IDLE,
    val error: String? = null,
)
