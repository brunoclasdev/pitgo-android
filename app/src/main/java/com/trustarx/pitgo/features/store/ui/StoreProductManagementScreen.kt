package com.trustarx.pitgo.features.store.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.features.store.model.StoreOperation
import com.trustarx.pitgo.features.store.viewmodel.StoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreProductManagementScreen(viewModel: StoreViewModel, onLogout: () -> Unit, onEditRegistration: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val mime = context.contentResolver.getType(uri) ?: ""
                    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 5 * 1024 * 1024) {
                            val count = stream.read(buffer, 0, minOf(buffer.size, 5 * 1024 * 1024 + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray() }
                    requireNotNull(bytes) to mime
                }
                viewModel.photo(data.first, data.second)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { viewModel.photoFailure() }
        }
    }
    val enabled = state.operation == StoreOperation.IDLE
    androidx.activity.compose.BackHandler(enabled = state.draft != null) { viewModel.cancelEdit() }
    Scaffold(topBar = { TopAppBar(title = { Text(if (state.draft == null) "Minha loja" else "Produto") },
        navigationIcon = { if (state.draft != null) TextButton(onClick = viewModel::cancelEdit, enabled = enabled) { Text("Voltar") } },
        actions = { if(state.draft==null) TextButton(onClick=onEditRegistration) { Text(androidx.compose.ui.res.stringResource(com.trustarx.pitgo.R.string.registration_edit_title)) }; if (state.draft == null) TextButton(onClick = onLogout) { Text("Sair") } }) }) { padding ->
        val draft = state.draft
        if (draft == null) Column(Modifier.fillMaxSize().padding(padding)) {
            Button(onClick = { viewModel.edit() }, enabled = enabled, modifier = Modifier.padding(horizontal = PitGoSpacing.Medium).fillMaxWidth()) { Text("Cadastrar produto") }
            StoreCatalogContent(state, Modifier.weight(1f), onRetry = viewModel::refresh, ownerActions = { product ->
                Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                    TextButton(onClick = { viewModel.edit(product) }, enabled = enabled) { Text("Editar") }
                    TextButton(onClick = { viewModel.delete(product) }, enabled = enabled) { Text("Excluir") }
                }
            })
        } else Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(PitGoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
            OutlinedTextField(draft.name, { viewModel.update("name", it) }, label = { Text("Nome") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.description, { viewModel.update("description", it) }, label = { Text("Descrição") }, enabled = enabled, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(draft.price, { viewModel.update("price", it) }, label = { Text("Preço (R$)") }, enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, enabled = enabled) {
                Text(if (draft.photo != null || draft.imagePath != null) "Trocar foto" else "Adicionar foto (opcional)")
            }
            if (draft.photo != null) Text("Foto selecionada")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!enabled) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = viewModel::save, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Salvar produto") }
        }
    }
}
