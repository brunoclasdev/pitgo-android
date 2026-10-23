package com.trustarx.pitgo.features.providersignup.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.account.ProviderKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderKindSelectionScreen(onBack: () -> Unit, onSelected: (ProviderKind) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Criar cadastro") },
        navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(PitGoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium)) {
            Text("Como você quer atuar?", style = MaterialTheme.typography.headlineSmall)
            ProviderKind.entries.forEach { kind ->
                OutlinedCard(onClick = { onSelected(kind) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(PitGoSpacing.MediumLarge)) {
                        Text(kind.label, style = MaterialTheme.typography.titleLarge)
                        Text(when (kind) {
                            ProviderKind.PITTEC -> "Atendimento e serviços automotivos"
                            ProviderKind.GUINCHO -> "Transporte e socorro de veículos"
                            ProviderKind.STORE -> "Sua loja e seus produtos"
                        })
                    }
                }
            }
        }
    }
}
