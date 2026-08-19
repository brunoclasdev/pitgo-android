package com.trustarx.pitgo.features.requestservice.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.BrandPurple
import com.trustarx.pitgo.core.design.theme.DarkPurple
import com.trustarx.pitgo.core.design.theme.PitGoElevation
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.ui.PitGoBottomNavigationItems
import com.trustarx.pitgo.core.ui.PitGoBottomNavigation
import com.trustarx.pitgo.core.vehicle.model.Vehicle
import com.trustarx.pitgo.features.requestservice.model.PaymentMethod
import com.trustarx.pitgo.features.requestservice.model.RequestServiceUiState
import com.trustarx.pitgo.features.requestservice.model.SubmissionStatus
import com.trustarx.pitgo.features.requestservice.viewmodel.RequestServiceViewModel

@Composable
fun RequestServiceRoute(
    vehicle: Vehicle?,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onBottomItemClick: (BottomItemId) -> Unit,
    viewModel: RequestServiceViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(vehicle) { viewModel.bindVehicle(vehicle) }

    RequestServiceScreen(
        uiState = uiState,
        onBack = onBack,
        onProblemDescriptionChanged = viewModel::onProblemDescriptionChanged,
        onPaymentMethodSelected = viewModel::onPaymentMethodSelected,
        onSubmit = {
            if (viewModel.onSubmitClicked()) {
                onSubmit()
            }
        },
        onBottomItemClick = onBottomItemClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestServiceScreen(
    uiState: RequestServiceUiState,
    onBack: () -> Unit,
    onProblemDescriptionChanged: (String) -> Unit,
    onPaymentMethodSelected: (PaymentMethod) -> Unit,
    onSubmit: () -> Unit,
    onBottomItemClick: (BottomItemId) -> Unit,
) {
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
                        text = stringResource(R.string.request_service_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                },
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
                            contentDescription = stringResource(R.string.request_service_back_description),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            PitGoBottomNavigation(
                items = PitGoBottomNavigationItems,
                selectedItem = BottomItemId.HOME,
                onItemClick = onBottomItemClick,
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is RequestServiceUiState.Content -> RequestServiceContent(
                uiState = uiState,
                innerPadding = innerPadding,
                onProblemDescriptionChanged = onProblemDescriptionChanged,
                onPaymentMethodSelected = onPaymentMethodSelected,
                onSubmit = onSubmit,
            )

            RequestServiceUiState.InvalidService -> InvalidServiceContent(
                innerPadding = innerPadding,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun RequestServiceContent(
    uiState: RequestServiceUiState.Content,
    innerPadding: PaddingValues,
    onProblemDescriptionChanged: (String) -> Unit,
    onPaymentMethodSelected: (PaymentMethod) -> Unit,
    onSubmit: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val startInset = if (layoutDirection == LayoutDirection.Ltr) {
        innerPadding.calculateLeftPadding(layoutDirection)
    } else {
        innerPadding.calculateRightPadding(layoutDirection)
    }
    val endInset = if (layoutDirection == LayoutDirection.Ltr) {
        innerPadding.calculateRightPadding(layoutDirection)
    } else {
        innerPadding.calculateLeftPadding(layoutDirection)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(
            start = startInset + PitGoSpacing.Large,
            top = innerPadding.calculateTopPadding() + PitGoSpacing.Small,
            end = endInset + PitGoSpacing.Large,
            bottom = innerPadding.calculateBottomPadding() + PitGoSpacing.Large,
        ),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium),
    ) {
        item {
            InfoCard(
                label = stringResource(R.string.request_service_label),
                value = stringResource(uiState.selectedService.nameRes),
                iconRes = uiState.selectedService.iconRes,
            )
        }

        item {
            val vehicle = uiState.vehicle
            val vehicleValue = if (vehicle == null) {
                stringResource(R.string.request_vehicle_none)
            } else {
                val year = vehicle.modelYear ?: vehicle.manufactureYear
                if (year == null) vehicle.displayName else stringResource(
                    R.string.request_vehicle_details,
                    vehicle.displayName,
                    year,
                )
            }
            InfoCard(
                label = stringResource(R.string.request_vehicle_label),
                value = vehicleValue,
                iconRes = R.drawable.ic_home_vehicle,
            )
        }

        item {
            InfoCard(
                label = stringResource(R.string.request_location_label),
                value = stringResource(R.string.home_address_empty),
                iconRes = R.drawable.ic_home_location,
            )
        }

        item {
            OutlinedTextField(
                value = uiState.problemDescription,
                onValueChange = onProblemDescriptionChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                label = { Text(stringResource(R.string.request_problem_label)) },
                placeholder = { Text(stringResource(R.string.request_problem_placeholder)) },
                minLines = 4,
                singleLine = false,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
            )
        }

        item {
            EstimateCards(
                arrival = stringResource(uiState.estimatedArrivalRes),
                price = stringResource(R.string.request_estimate_value, uiState.estimatedPrice),
            )
        }

        item {
            Text(
                text = stringResource(R.string.request_payment_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = PitGoSpacing.Small)
                    .semantics { heading() },
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            ) {
                PaymentMethod.entries.forEach { paymentMethod ->
                    PaymentOption(
                        paymentMethod = paymentMethod,
                        selected = paymentMethod == uiState.paymentMethod,
                        onClick = { onPaymentMethodSelected(paymentMethod) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            PrimaryRequestButton(
                enabled = uiState.submissionStatus == SubmissionStatus.READY,
                onClick = onSubmit,
            )
        }

        item {
            Text(
                text = stringResource(R.string.request_value_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PitGoSpacing.Small),
            )
        }
    }
}

@Composable
private fun InvalidServiceContent(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(PitGoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.request_invalid_service_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.heightIn(min = PitGoSpacing.Small))
        Text(
            text = stringResource(R.string.request_invalid_service_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onBack,
            modifier = Modifier
                .padding(top = PitGoSpacing.Large)
                .heightIn(min = PitGoSize.MinimumTouchTarget),
        ) {
            Text(stringResource(R.string.request_invalid_service_action))
        }
    }
}

@Composable
private fun InfoCard(
    label: String,
    value: String,
    @DrawableRes iconRes: Int,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = PitGoElevation.Card),
    ) {
        Row(
            modifier = Modifier.padding(PitGoSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(PitGoSize.ServiceIconContainer)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(PitGoSize.ServiceIcon),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun EstimateCards(
    arrival: String,
    price: String,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useSingleColumn = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.3f

        if (useSingleColumn) {
            Column(verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
                EstimateCard(
                    label = stringResource(R.string.request_arrival_label),
                    value = arrival,
                    iconRes = R.drawable.ic_request_clock,
                    modifier = Modifier.fillMaxWidth(),
                )
                EstimateCard(
                    label = stringResource(R.string.request_estimate_label),
                    value = price,
                    iconRes = R.drawable.ic_service_mechanic,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium),
            ) {
                EstimateCard(
                    label = stringResource(R.string.request_arrival_label),
                    value = arrival,
                    iconRes = R.drawable.ic_request_clock,
                    modifier = Modifier.weight(1f),
                )
                EstimateCard(
                    label = stringResource(R.string.request_estimate_label),
                    value = price,
                    iconRes = R.drawable.ic_service_mechanic,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EstimateCard(
    label: String,
    value: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier,
) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = PitGoElevation.Card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PitGoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(PitGoSize.FeatureIcon),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PaymentOption(
    paymentMethod: PaymentMethod,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val selectedDescription = stringResource(R.string.request_payment_selected)

    Surface(
        modifier = modifier
            .heightIn(min = PitGoSize.MinimumTouchTarget)
            .semantics {
                if (selected) {
                    stateDescription = selectedDescription
                }
            }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = CircleShape,
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
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = PitGoSpacing.Small,
                vertical = PitGoSpacing.SmallMedium,
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_request_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(PitGoSpacing.ExtraSmall))
            }
            Text(
                text = stringResource(paymentMethod.labelRes),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PrimaryRequestButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val gradientColors = if (enabled) {
        listOf(BrandPurple, DarkPurple)
    } else {
        listOf(BrandPurple.copy(alpha = 0.5f), DarkPurple.copy(alpha = 0.5f))
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PitGoSize.PrimaryActionMinHeight)
            .clip(CircleShape)
            .background(
                brush = Brush.horizontalGradient(gradientColors),
                shape = CircleShape,
            ),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.White.copy(alpha = 0.8f),
        ),
    ) {
        Text(
            text = stringResource(R.string.request_submit_action),
            fontWeight = FontWeight.Bold,
        )
    }
}
