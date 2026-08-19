package com.trustarx.pitgo.features.signup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.signup.model.PersonType
import com.trustarx.pitgo.features.signup.model.SignUpFailureReason
import com.trustarx.pitgo.features.signup.model.SignUpField
import com.trustarx.pitgo.features.signup.model.SignUpFieldError
import com.trustarx.pitgo.features.signup.model.SignUpStage
import com.trustarx.pitgo.features.signup.model.SignUpSubmissionState
import com.trustarx.pitgo.features.signup.model.SignUpUiState
import com.trustarx.pitgo.features.signup.viewmodel.SignUpViewModel
import kotlinx.coroutines.yield

private data class FocusHandles(
    val focusRequester: FocusRequester,
    val bringIntoViewRequester: BringIntoViewRequester,
)

@Composable
fun SignUpRoute(
    onBack: () -> Unit,
    onAccountCreated: () -> Unit,
    viewModel: SignUpViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.submissionState) {
        if (uiState.submissionState is SignUpSubmissionState.Success) {
            onAccountCreated()
        }
    }

    SignUpScreen(
        uiState = uiState,
        onBack = onBack,
        onPersonTypeChanged = viewModel::onPersonTypeChanged,
        onNameChanged = viewModel::onNameChanged,
        onEmailChanged = viewModel::onEmailChanged,
        onPhoneChanged = viewModel::onPhoneChanged,
        onDocumentChanged = viewModel::onDocumentChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onPasswordConfirmationChanged = viewModel::onPasswordConfirmationChanged,
        onSubmit = viewModel::onSubmit,
        onRetryProfile = viewModel::onRetryProfile,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    uiState: SignUpUiState,
    onBack: () -> Unit,
    onPersonTypeChanged: (PersonType) -> Unit,
    onNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onDocumentChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordConfirmationChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetryProfile: () -> Unit,
) {
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showProcessingNotice by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showPasswordConfirmation by rememberSaveable { mutableStateOf(false) }

    val requestBack = {
        when {
            uiState.isProcessing -> showProcessingNotice = true
            uiState.hasDraft -> showDiscardDialog = true
            else -> onBack()
        }
    }
    BackHandler(onBack = requestBack)

    val focusHandles = remember {
        SignUpField.entries.associateWith {
            FocusHandles(FocusRequester(), BringIntoViewRequester())
        }
    }
    uiState.focusRequest?.let { request ->
        LaunchedEffect(request.requestId) {
            focusHandles[request.field]?.let { handles ->
                handles.focusRequester.requestFocus()
                yield()
                handles.bringIntoViewRequester.bringIntoView()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.signup_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = requestBack,
                        modifier = Modifier.sizeIn(
                            minWidth = PitGoSize.MinimumTouchTarget,
                            minHeight = PitGoSize.MinimumTouchTarget,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.signup_back_description),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        SignUpForm(
            uiState = uiState,
            innerPadding = innerPadding,
            focusHandles = focusHandles,
            showPassword = showPassword,
            showPasswordConfirmation = showPasswordConfirmation,
            onTogglePassword = { showPassword = !showPassword },
            onTogglePasswordConfirmation = {
                showPasswordConfirmation = !showPasswordConfirmation
            },
            onPersonTypeChanged = onPersonTypeChanged,
            onNameChanged = onNameChanged,
            onEmailChanged = onEmailChanged,
            onPhoneChanged = onPhoneChanged,
            onDocumentChanged = onDocumentChanged,
            onPasswordChanged = onPasswordChanged,
            onPasswordConfirmationChanged = onPasswordConfirmationChanged,
            onSubmit = onSubmit,
            onRetryProfile = onRetryProfile,
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.signup_discard_title)) },
            text = { Text(stringResource(R.string.signup_discard_message)) },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.signup_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.signup_keep_editing))
                }
            },
        )
    }

    if (showProcessingNotice) {
        AlertDialog(
            onDismissRequest = { showProcessingNotice = false },
            text = { Text(stringResource(R.string.signup_processing_back_message)) },
            confirmButton = {
                TextButton(onClick = { showProcessingNotice = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun SignUpForm(
    uiState: SignUpUiState,
    innerPadding: PaddingValues,
    focusHandles: Map<SignUpField, FocusHandles>,
    showPassword: Boolean,
    showPasswordConfirmation: Boolean,
    onTogglePassword: () -> Unit,
    onTogglePasswordConfirmation: () -> Unit,
    onPersonTypeChanged: (PersonType) -> Unit,
    onNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onDocumentChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordConfirmationChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetryProfile: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputEnabled = uiState.isInputEnabled

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(
            start = PitGoSpacing.Large,
            top = innerPadding.calculateTopPadding() + PitGoSpacing.Small,
            end = PitGoSpacing.Large,
            bottom = innerPadding.calculateBottomPadding() + PitGoSpacing.ExtraLarge,
        ),
    ) {
        item {
            Box(
                modifier = Modifier.fillParentMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
                ) {
                    Text(
                        text = stringResource(R.string.signup_heading),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.signup_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FailureBanner(uiState.submissionState)

                    PersonTypeSelector(
                        selected = uiState.personType,
                        enabled = inputEnabled,
                        error = uiState.errors.personType,
                        handles = focusHandles.getValue(SignUpField.PERSON_TYPE),
                        onSelected = onPersonTypeChanged,
                    )

                    SignUpTextField(
                        value = uiState.name,
                        onValueChange = onNameChanged,
                        label = when (uiState.personType) {
                            PersonType.INDIVIDUAL -> stringResource(R.string.signup_name_individual)
                            PersonType.COMPANY -> stringResource(R.string.signup_name_company)
                            null -> stringResource(R.string.signup_name_neutral)
                        },
                        error = uiState.errors.name,
                        enabled = inputEnabled,
                        handles = focusHandles.getValue(SignUpField.NAME),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = nextKeyboardAction(focusManager),
                    )

                    SignUpTextField(
                        value = uiState.email,
                        onValueChange = onEmailChanged,
                        label = stringResource(R.string.signup_email),
                        error = uiState.errors.email,
                        enabled = inputEnabled,
                        handles = focusHandles.getValue(SignUpField.EMAIL),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = nextKeyboardAction(focusManager),
                        unavailableErrorText = stringResource(
                            R.string.signup_email_unavailable_error,
                        ),
                        modifier = Modifier.contentType(
                            ContentType.NewUsername + ContentType.EmailAddress,
                        ),
                    )

                    SignUpTextField(
                        value = uiState.phone,
                        onValueChange = onPhoneChanged,
                        label = stringResource(R.string.signup_phone),
                        error = uiState.errors.phone,
                        enabled = inputEnabled,
                        handles = focusHandles.getValue(SignUpField.PHONE),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = nextKeyboardAction(focusManager),
                        visualTransformation = PhoneVisualTransformation,
                        modifier = Modifier.contentType(ContentType.PhoneNumber),
                    )

                    if (uiState.personType != null) {
                        SignUpTextField(
                            value = uiState.document,
                            onValueChange = onDocumentChanged,
                            label = if (uiState.personType == PersonType.INDIVIDUAL) {
                                stringResource(R.string.signup_cpf)
                            } else {
                                stringResource(R.string.signup_cnpj)
                            },
                            error = uiState.errors.document,
                            enabled = inputEnabled,
                            handles = focusHandles.getValue(SignUpField.DOCUMENT),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (uiState.personType == PersonType.INDIVIDUAL) {
                                    KeyboardType.Number
                                } else {
                                    KeyboardType.Ascii
                                },
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = nextKeyboardAction(focusManager),
                            visualTransformation = if (uiState.personType == PersonType.INDIVIDUAL) {
                                CpfVisualTransformation
                            } else {
                                CnpjVisualTransformation
                            },
                        )
                    }

                    SignUpTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChanged,
                        label = stringResource(R.string.signup_password),
                        error = uiState.errors.password,
                        enabled = inputEnabled,
                        handles = focusHandles.getValue(SignUpField.PASSWORD),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = nextKeyboardAction(focusManager),
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        supportingText = if (uiState.errors.password == null) {
                            stringResource(R.string.signup_password_support)
                        } else {
                            null
                        },
                        trailingIcon = {
                            PasswordVisibilityButton(showPassword, onTogglePassword)
                        },
                        modifier = Modifier.contentType(ContentType.NewPassword),
                    )

                    SignUpTextField(
                        value = uiState.passwordConfirmation,
                        onValueChange = onPasswordConfirmationChanged,
                        label = stringResource(R.string.signup_password_confirmation),
                        error = uiState.errors.passwordConfirmation,
                        enabled = inputEnabled,
                        handles = focusHandles.getValue(SignUpField.PASSWORD_CONFIRMATION),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                            onSubmit()
                        }),
                        visualTransformation = if (showPasswordConfirmation) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            PasswordVisibilityButton(
                                showPasswordConfirmation,
                                onTogglePasswordConfirmation,
                            )
                        },
                        modifier = Modifier.contentType(ContentType.NewPassword),
                    )

                    Spacer(Modifier.height(PitGoSpacing.ExtraSmall))

                    Button(
                        onClick = if (uiState.isProfilePending) onRetryProfile else onSubmit,
                        enabled = !uiState.isProcessing &&
                            uiState.submissionState !is SignUpSubmissionState.Success,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PitGoSize.PrimaryActionMinHeight),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        if (uiState.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.size(PitGoSpacing.Small))
                        }
                        Text(
                            text = submitLabel(uiState),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonTypeSelector(
    selected: PersonType?,
    enabled: Boolean,
    error: SignUpFieldError?,
    handles: FocusHandles,
    onSelected: (PersonType) -> Unit,
) {
    val errorText = error?.let { fieldErrorText(it, PersonType.INDIVIDUAL) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(handles.focusRequester)
            .bringIntoViewRequester(handles.bringIntoViewRequester)
            .focusable()
            .selectableGroup()
            .then(
                if (errorText != null) Modifier.semantics { error(errorText) } else Modifier,
            ),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.signup_person_type_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        BoxWithConstraints {
            val stackOptions = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.5f
            if (stackOptions) {
                Column(verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                    PersonType.entries.forEach { type ->
                        PersonTypeOption(type, selected == type, enabled, onSelected)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
                    PersonType.entries.forEach { type ->
                        PersonTypeOption(
                            type = type,
                            selected = selected == type,
                            enabled = enabled,
                            onSelected = onSelected,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PersonTypeOption(
    type: PersonType,
    selected: Boolean,
    enabled: Boolean,
    onSelected: (PersonType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PitGoSize.MinimumTouchTarget)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { onSelected(type) },
            ),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PitGoSpacing.Small, vertical = PitGoSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Text(
                text = stringResource(
                    if (type == PersonType.INDIVIDUAL) {
                        R.string.signup_person_individual
                    } else {
                        R.string.signup_person_company
                    },
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SignUpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: SignUpFieldError?,
    enabled: Boolean,
    handles: FocusHandles,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
    unavailableErrorText: String = stringResource(R.string.signup_document_unavailable_error),
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val errorText = error?.let {
        if (it == SignUpFieldError.UNAVAILABLE) unavailableErrorText else fieldErrorText(it)
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(handles.focusRequester)
            .bringIntoViewRequester(handles.bringIntoViewRequester)
            .then(
                if (errorText != null) Modifier.semantics { error(errorText) } else Modifier,
            ),
        enabled = enabled,
        singleLine = true,
        isError = errorText != null,
        supportingText = when {
            errorText != null -> ({ Text(errorText) })
            supportingText != null -> ({ Text(supportingText) })
            else -> null
        },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun PasswordVisibilityButton(visible: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.sizeIn(
            minWidth = PitGoSize.MinimumTouchTarget,
            minHeight = PitGoSize.MinimumTouchTarget,
        ),
    ) {
        Icon(
            painter = painterResource(
                if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
            ),
            contentDescription = stringResource(
                if (visible) R.string.signup_hide_password else R.string.signup_show_password,
            ),
        )
    }
}

@Composable
private fun FailureBanner(submissionState: SignUpSubmissionState) {
    val failure = submissionState as? SignUpSubmissionState.Failure ?: return
    if (failure.reason in setOf(
            SignUpFailureReason.INVALID_FORM,
            SignUpFailureReason.EMAIL_UNAVAILABLE,
            SignUpFailureReason.DOCUMENT_UNAVAILABLE,
        )
    ) {
        return
    }

    val message = when (failure.reason) {
        SignUpFailureReason.INVALID_SERVER_INPUT -> R.string.signup_failure_invalid_input
        SignUpFailureReason.RATE_LIMITED -> R.string.signup_failure_rate_limited
        SignUpFailureReason.OFFLINE -> R.string.signup_failure_offline
        SignUpFailureReason.SERVICE_UNAVAILABLE -> R.string.signup_failure_service
        SignUpFailureReason.PROFILE_RECOVERY_REQUIRED -> R.string.signup_failure_profile
        SignUpFailureReason.ENVIRONMENT_MISCONFIGURED -> R.string.signup_failure_environment
        SignUpFailureReason.UNKNOWN -> R.string.signup_failure_unknown
        else -> return
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringResource(message),
            modifier = Modifier.padding(PitGoSpacing.Medium),
        )
    }
}

@Composable
private fun fieldErrorText(
    error: SignUpFieldError,
    personType: PersonType? = null,
): String = stringResource(
    when (error) {
        SignUpFieldError.REQUIRED -> if (personType != null) {
            R.string.signup_person_type_required_error
        } else {
            R.string.signup_required_error
        }
        SignUpFieldError.INVALID_EMAIL -> R.string.signup_email_invalid_error
        SignUpFieldError.INVALID_PHONE -> R.string.signup_phone_invalid_error
        SignUpFieldError.INVALID_CPF -> R.string.signup_cpf_invalid_error
        SignUpFieldError.INVALID_CNPJ -> R.string.signup_cnpj_invalid_error
        SignUpFieldError.PASSWORD_TOO_SHORT -> R.string.signup_password_short_error
        SignUpFieldError.PASSWORDS_DO_NOT_MATCH -> R.string.signup_password_mismatch_error
        SignUpFieldError.UNAVAILABLE -> R.string.signup_document_unavailable_error
    },
)

@Composable
private fun submitLabel(uiState: SignUpUiState): String = when (val state = uiState.submissionState) {
    is SignUpSubmissionState.Submitting -> stringResource(
        when (state.stage) {
            SignUpStage.CREATING_ACCOUNT -> R.string.signup_creating_account
            SignUpStage.ENSURING_PROFILE,
            SignUpStage.RECOVERING_PROFILE,
            -> R.string.signup_ensuring_profile
        },
    )
    is SignUpSubmissionState.Failure -> if (state.profilePending) {
        stringResource(R.string.signup_retry_profile)
    } else {
        stringResource(R.string.signup_submit)
    }
    else -> stringResource(R.string.signup_submit)
}

private fun nextKeyboardAction(focusManager: FocusManager): KeyboardActions = KeyboardActions(
    onNext = { focusManager.moveFocus(FocusDirection.Down) },
)
