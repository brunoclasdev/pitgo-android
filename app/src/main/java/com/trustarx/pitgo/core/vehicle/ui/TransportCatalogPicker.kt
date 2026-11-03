package com.trustarx.pitgo.core.vehicle.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.vehicle.model.TransportCatalogState

@Composable
fun TransportCatalogPicker(state: TransportCatalogState, selectedId: String?, size: Boolean, enabled: Boolean,
    onSelected: (String) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(if(size) R.string.guincho_size_label else R.string.vehicle_transport_category_label), style=MaterialTheme.typography.titleSmall)
        when(state) {
            TransportCatalogState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is TransportCatalogState.Failed -> {
                Text(stringResource(when(state.reason) {
                    com.trustarx.pitgo.core.vehicle.model.TransportCatalogFailure.SESSION_EXPIRED -> R.string.transport_session_expired
                    com.trustarx.pitgo.core.vehicle.model.TransportCatalogFailure.OFFLINE -> R.string.guincho_offline
                    com.trustarx.pitgo.core.vehicle.model.TransportCatalogFailure.TIMEOUT -> R.string.transport_catalog_timeout
                    else -> R.string.transport_catalog_unavailable
                }))
                TextButton(onClick=onRetry, enabled=enabled) { Text(stringResource(R.string.transport_catalog_retry)) }
            }
            is TransportCatalogState.Ready -> {
                val sizeOrder = listOf("LEVE", "MEDIO", "PESADO", "EXTRAPESADO", "ESPECIALIZADO")
                val options = if(size) state.catalog.sizes
                    .sortedBy { sizeOrder.indexOf(it.code).let { index -> if(index < 0) Int.MAX_VALUE else index } }
                    .map { it.id to it.name }
                else state.catalog.categories
                    .sortedBy { sizeOrder.indexOf(it.code).let { index -> if(index < 0) Int.MAX_VALUE else index } }
                    .map { it.id to it.name }
                if(options.isEmpty()) {
                    Text(stringResource(if(size) R.string.transport_catalog_empty_size else R.string.transport_catalog_empty_category))
                    TextButton(onClick=onRetry,enabled=enabled) { Text(stringResource(R.string.transport_catalog_retry)) }
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick={ expanded=true },enabled=enabled,modifier=Modifier.fillMaxWidth()) {
                            Text(options.firstOrNull { it.first==selectedId }?.second ?: stringResource(R.string.transport_select))
                        }
                        DropdownMenu(expanded=expanded && enabled,onDismissRequest={ expanded=false }) {
                            options.forEach { (id,name) -> DropdownMenuItem(text={ Text(name) },onClick={ expanded=false; onSelected(id) }) }
                        }
                    }
                }
            }
        }
    }
}
