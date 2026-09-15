package com.trustarx.pitgo.features.payment.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mercadopago.sdk.android.coremethods.domain.interactor.coreMethods
import com.mercadopago.sdk.android.coremethods.domain.model.BuyerIdentification
import com.mercadopago.sdk.android.coremethods.domain.model.CardToken
import com.mercadopago.sdk.android.coremethods.domain.utils.Result as CoreMethodsResult
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.pcitextfield.PCIFieldState
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.securitycode.SecurityCodeTextField
import com.mercadopago.sdk.android.initializer.MercadoPagoSDK
import com.trustarx.pitgo.R
import com.trustarx.pitgo.features.paymentmethods.model.SavedCardTokenizationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedCardSecurityCodeSheet(context: SavedCardTokenizationContext, onDismiss: () -> Unit, onToken: (String) -> Unit) {
    val state = remember(context.savedCardId) { PCIFieldState.create() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Neither the field nor the token is saveable. Leaving the sheet cancels its coroutine.
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(stringResource(R.string.payment_methods_security_code_label))
            SecurityCodeTextField(state = state, onEvent = {}, modifier = Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.payment_card_token_error), color = MaterialTheme.colorScheme.error)
            Button(enabled = !busy, onClick = {
                busy = true; failed = false
                scope.launch {
                    try {
                        val result = MercadoPagoSDK.getInstance().coreMethods.generateCardToken(
                            cardId = context.cardId, securityCodeState = state,
                            buyerIdentification = BuyerIdentification(context.holderName, context.documentNumber, context.documentType),
                        )
                        val token = ((result as? CoreMethodsResult.Success<*>)?.data as? CardToken)?.token
                        if (token.isNullOrBlank()) failed = true else onToken(token)
                    } catch (cancel: CancellationException) { throw cancel
                    } catch (_: Exception) { failed = true
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.payment_card_confirm)) }
            if (busy) CircularProgressIndicator()
        }
    }
}
