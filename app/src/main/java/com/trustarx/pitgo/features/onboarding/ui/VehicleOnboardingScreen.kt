package com.trustarx.pitgo.features.onboarding.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.onboarding.viewmodel.OnboardingDestination
import com.trustarx.pitgo.features.onboarding.viewmodel.VehicleOnboardingUiState
import com.trustarx.pitgo.features.onboarding.viewmodel.VehicleOnboardingViewModel

@Composable
fun VehicleOnboardingRoute(
    viewModel: VehicleOnboardingViewModel,
    onCompleted: (OnboardingDestination) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        (state as? VehicleOnboardingUiState.Completed)?.let { onCompleted(it.destination) }
    }
    VehicleOnboardingScreen(
        state = state,
        onRegisterVehicle = viewModel::registerVehicle,
        onSkip = viewModel::skipForNow,
    )
}

@Composable
fun VehicleOnboardingScreen(
    state: VehicleOnboardingUiState,
    onRegisterVehicle: () -> Unit,
    onSkip: () -> Unit,
) {
    BackHandler(enabled = true) {}
    val processing = state == VehicleOnboardingUiState.Resolving
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(PitGoSpacing.Large),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = PitGoSpacing.ExtraSmall,
            ) {
                Column(
                    modifier = Modifier
                        .padding(PitGoSpacing.Large)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_vehicle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(PitGoSize.ServiceIconContainer),
                    )
                    Text(
                        text = stringResource(R.string.onboarding_vehicle_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.onboarding_vehicle_message),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state == VehicleOnboardingUiState.Failure) {
                        Text(
                            text = stringResource(R.string.onboarding_failure),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Button(
                        onClick = onRegisterVehicle,
                        enabled = !processing,
                        modifier = Modifier.fillMaxWidth().heightIn(
                            min = PitGoSize.PrimaryActionMinHeight,
                        ),
                    ) {
                        if (processing) CircularProgressIndicator(
                            modifier = Modifier.size(PitGoSize.FeatureIcon),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = PitGoSpacing.ExtraSmall,
                        )
                        Text(stringResource(R.string.onboarding_register_vehicle))
                    }
                    OutlinedButton(
                        onClick = onSkip,
                        enabled = !processing,
                        modifier = Modifier.fillMaxWidth().heightIn(
                            min = PitGoSize.MinimumTouchTarget,
                        ),
                    ) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            }
        }
    }
}
