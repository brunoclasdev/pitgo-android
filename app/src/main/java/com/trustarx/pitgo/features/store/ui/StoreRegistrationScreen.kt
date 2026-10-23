package com.trustarx.pitgo.features.store.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** The business/address form is shared with provider registration. */
@Composable
fun StoreRegistrationIntro() {
    Text("Informe os dados da sua loja. Depois de concluir, você poderá cadastrar seus produtos.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
