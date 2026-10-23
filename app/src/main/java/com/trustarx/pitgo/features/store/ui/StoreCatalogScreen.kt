package com.trustarx.pitgo.features.store.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.trustarx.pitgo.features.store.model.*
import com.trustarx.pitgo.features.store.viewmodel.StoreViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreCatalogScreen(viewModel: StoreViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = state.selectedStoreId != null) { viewModel.backToStores() }
    Scaffold(topBar = { TopAppBar(title = { Text(state.selectedStoreName ?: "Lojas") }, navigationIcon = {
        TextButton(onClick = { if (state.selectedStoreId == null) onBack() else viewModel.backToStores() }) { Text("Voltar") }
    }) }) { padding ->
        StoreCatalogContent(state, Modifier.padding(padding), onRetry = viewModel::refresh,
            onStore = viewModel::openStore)
    }
}

@Composable
internal fun StoreCatalogContent(state: StoreUiState, modifier: Modifier = Modifier,
    onRetry: () -> Unit, onStore: (StoreSummary) -> Unit = {},
    ownerActions: (@Composable (StoreProduct) -> Unit)? = null) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(PitGoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
        if (state.operation != StoreOperation.IDLE) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item { TextButton(onClick = onRetry, enabled = state.operation == StoreOperation.IDLE) { Text("Atualizar") } }
        if (state.selectedStoreId == null) {
            items(state.stores, key = { it.id }) { store ->
                OutlinedCard(onClick = { onStore(store) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(PitGoSpacing.Medium)) {
                        Text(store.name, style = MaterialTheme.typography.titleLarge)
                        Text(store.city)
                        store.description?.let { Text(it) }
                    }
                }
            }
            if (state.stores.isEmpty() && state.operation == StoreOperation.IDLE) item { Text("Nenhuma loja disponível no momento.") }
        } else {
            items(state.products, key = { it.id }) { product ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(PitGoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                        product.imageUrl?.let { url ->
                            var imageLoading by remember(url) { mutableStateOf(true) }
                            var imageFailed by remember(url) { mutableStateOf(false) }
                            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                AsyncImage(model = url, contentDescription = product.name,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    onLoading = { imageLoading = true; imageFailed = false },
                                    onSuccess = { imageLoading = false }, onError = { imageLoading = false; imageFailed = true })
                                if (imageLoading) CircularProgressIndicator()
                                if (imageFailed) Text("Foto indisponível")
                            }
                        }
                        Text(product.name, style = MaterialTheme.typography.titleLarge)
                        Text(product.description)
                        Text(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(product.amount),
                            style = MaterialTheme.typography.titleMedium)
                        if (!product.active) Text("Oculto no catálogo")
                        ownerActions?.invoke(product)
                    }
                }
            }
            if (state.products.isEmpty() && state.operation == StoreOperation.IDLE) item { Text("Nenhum produto cadastrado.") }
        }
    }
}
