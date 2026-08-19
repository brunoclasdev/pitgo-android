package com.trustarx.pitgo.features.login.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.login.model.LoginFailureReason
import com.trustarx.pitgo.features.login.model.LoginField
import com.trustarx.pitgo.features.login.model.LoginFieldError
import com.trustarx.pitgo.features.login.model.LoginOperation
import com.trustarx.pitgo.features.login.model.LoginUiState
import com.trustarx.pitgo.features.login.viewmodel.LoginViewModel

@Composable
fun LoginRoute(
    onBack: () -> Unit,
    viewModel: LoginViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onBack = {
            viewModel.clearPassword()
            onBack()
        },
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onSubmit = viewModel::onSubmit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    onBack: () -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.focusRequestId) {
        when (state.focusField) {
            LoginField.EMAIL -> emailFocus.requestFocus()
            LoginField.PASSWORD -> passwordFocus.requestFocus()
            null -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(
                            minWidth = PitGoSize.MinimumTouchTarget,
                            minHeight = PitGoSize.MinimumTouchTarget,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = PitGoSpacing.Large,
                top = padding.calculateTopPadding() + PitGoSpacing.Large,
                end = PitGoSpacing.Large,
                bottom = padding.calculateBottomPadding() + PitGoSpacing.ExtraLarge,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_login),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(PitGoSize.ServiceIconContainer),
                    )
                    Text(
                        text = stringResource(R.string.login_heading),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.login_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LoginFailureBanner(state.operation)
                    val emailError = when (state.emailError) {
                        LoginFieldError.REQUIRED -> stringResource(R.string.field_required)
                        LoginFieldError.INVALID_EMAIL -> stringResource(R.string.login_invalid_email)
                        null -> null
                    }
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChanged,
                        label = { Text(stringResource(R.string.login_email)) },
                        singleLine = true,
                        enabled = !state.isSubmitting,
                        isError = emailError != null,
                        supportingText = emailError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocus.requestFocus() },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(emailFocus)
                            .then(if (emailError != null) Modifier.semantics { error(emailError) } else Modifier),
                        shape = MaterialTheme.shapes.large,
                    )
                    val passwordError = state.passwordError?.let {
                        stringResource(R.string.field_required)
                    }
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChanged,
                        label = { Text(stringResource(R.string.login_password)) },
                        singleLine = true,
                        enabled = !state.isSubmitting,
                        isError = passwordError != null,
                        supportingText = passwordError?.let { { Text(it) } },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onSubmit()
                        }),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    painter = painterResource(
                                        if (passwordVisible) R.drawable.ic_visibility_off
                                        else R.drawable.ic_visibility,
                                    ),
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.signup_hide_password
                                        else R.string.signup_show_password,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocus)
                            .then(if (passwordError != null) Modifier.semantics { error(passwordError) } else Modifier),
                        shape = MaterialTheme.shapes.large,
                    )
                    Button(
                        onClick = onSubmit,
                        enabled = !state.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PitGoSize.PrimaryActionMinHeight),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(
                            text = stringResource(
                                if (state.isSubmitting) R.string.login_submitting
                                else R.string.action_enter,
                            ),
                            modifier = Modifier.padding(start = if (state.isSubmitting) PitGoSpacing.Small else 0.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginFailureBanner(operation: LoginOperation) {
    val failure = operation as? LoginOperation.Failure ?: return
    val message = when (failure.reason) {
        LoginFailureReason.INVALID_CREDENTIALS -> R.string.login_failure_credentials
        LoginFailureReason.RATE_LIMITED -> R.string.login_failure_rate_limited
        LoginFailureReason.OFFLINE -> R.string.login_failure_offline
        LoginFailureReason.TIMEOUT -> R.string.login_failure_timeout
        LoginFailureReason.PROFILE_BLOCKED -> R.string.login_failure_profile
        LoginFailureReason.SERVICE_UNAVAILABLE -> R.string.login_failure_service
        LoginFailureReason.UNKNOWN -> R.string.login_failure_unknown
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = stringResource(message),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(PitGoSpacing.Medium),
        )
    }
}
