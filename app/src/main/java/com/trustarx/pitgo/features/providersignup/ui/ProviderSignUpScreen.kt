package com.trustarx.pitgo.features.providersignup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpFailure
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpSubmission
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpUiState
import com.trustarx.pitgo.features.providersignup.viewmodel.ProviderSignUpViewModel

@Composable
fun ProviderSignUpRoute(
    viewModel: ProviderSignUpViewModel,
    onBack: () -> Unit,
    onAccountCreated: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.submission) {
        if (state.submission == ProviderSignUpSubmission.Success) onAccountCreated()
    }
    ProviderSignUpScreen(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmationChanged = viewModel::onConfirmationChanged,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSignUpScreen(
    state: ProviderSignUpUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.hasDraft && !state.isProcessing) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_signup_title)) },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(PitGoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            Text(
                stringResource(R.string.provider_signup_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChanged,
                label = { Text(stringResource(R.string.signup_email)) },
                singleLine = true,
                enabled = !state.isProcessing,
                isError = state.emailError != null,
                supportingText = state.emailError?.let { { Text(stringResource(R.string.signup_email_invalid_error)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = { Text(stringResource(R.string.signup_password)) },
                singleLine = true,
                enabled = !state.isProcessing,
                isError = state.passwordError != null,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = { Text(stringResource(R.string.signup_password_support)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.passwordConfirmation,
                onValueChange = onConfirmationChanged,
                label = { Text(stringResource(R.string.signup_password_confirmation)) },
                singleLine = true,
                enabled = !state.isProcessing,
                isError = state.confirmationError != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            (state.submission as? ProviderSignUpSubmission.Failure)?.let {
                Text(
                    text = stringResource(it.reason.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onSubmit,
                enabled = !state.isProcessing,
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
            ) {
                if (state.isProcessing) CircularProgressIndicator()
                else Text(stringResource(R.string.provider_signup_action))
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.signup_discard_title)) },
            text = { Text(stringResource(R.string.signup_discard_message)) },
            confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.signup_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.signup_keep_editing)) } },
        )
    }
}

private fun ProviderSignUpFailure.messageRes(): Int = when (this) {
    ProviderSignUpFailure.EMAIL_UNAVAILABLE -> R.string.signup_email_unavailable_error
    ProviderSignUpFailure.RATE_LIMITED -> R.string.signup_failure_rate_limited
    ProviderSignUpFailure.OFFLINE -> R.string.signup_failure_offline
    ProviderSignUpFailure.CONFIRMATION_REQUIRED -> R.string.provider_signup_confirmation_required
    ProviderSignUpFailure.INVALID_FORM -> R.string.signup_failure_invalid_input
    ProviderSignUpFailure.SERVICE_UNAVAILABLE, ProviderSignUpFailure.UNKNOWN ->
        R.string.signup_failure_service
}
