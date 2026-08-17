package com.trustarx.pitgo.features.welcome.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.BrandPurple
import com.trustarx.pitgo.core.design.theme.DarkPurple
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.design.theme.White
import com.trustarx.pitgo.features.welcome.model.WelcomeBenefit
import com.trustarx.pitgo.features.welcome.viewmodel.WelcomeUiState
import com.trustarx.pitgo.features.welcome.viewmodel.WelcomeViewModel

@Composable
fun WelcomeRoute(
    onCreateAccount: () -> Unit,
    onEnter: () -> Unit,
    onRegisterCompany: () -> Unit,
    viewModel: WelcomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WelcomeScreen(
        uiState = uiState,
        onCreateAccount = onCreateAccount,
        onEnter = onEnter,
        onRegisterCompany = onRegisterCompany,
    )
}

@Composable
fun WelcomeScreen(
    uiState: WelcomeUiState,
    onCreateAccount: () -> Unit,
    onEnter: () -> Unit,
    onRegisterCompany: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BrandPurple, DarkPurple),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = PitGoSpacing.Large, vertical = PitGoSpacing.Medium),
        ) {
            Text(
                text = stringResource(R.string.welcome_wordmark),
                style = MaterialTheme.typography.titleLarge,
                color = White,
            )

            Spacer(Modifier.height(PitGoSpacing.Huge))

            Text(
                text = stringResource(R.string.welcome_title_line_one),
                style = MaterialTheme.typography.displaySmall,
                color = White,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.welcome_title_line_two),
                style = MaterialTheme.typography.displaySmall,
                color = White,
            )

            Spacer(Modifier.height(PitGoSpacing.Medium))

            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = White.copy(alpha = 0.86f),
            )

            Spacer(Modifier.height(PitGoSpacing.ExtraLarge))

            Column(verticalArrangement = Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
                uiState.benefits.forEach { benefit ->
                    FeatureCard(benefit = benefit)
                }
            }

            Spacer(Modifier.height(PitGoSpacing.ExtraLarge))

            Button(
                onClick = onCreateAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = PitGoSize.MinimumTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = White,
                    contentColor = BrandPurple,
                ),
            ) {
                Text(stringResource(R.string.action_create_account))
            }

            Spacer(Modifier.height(PitGoSpacing.SmallMedium))

            OutlinedButton(
                onClick = onEnter,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = PitGoSize.MinimumTouchTarget),
                border = BorderStroke(1.dp, White.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = White),
            ) {
                Text(stringResource(R.string.action_enter))
            }

            TextButton(
                onClick = onRegisterCompany,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = PitGoSize.MinimumTouchTarget),
                colors = ButtonDefaults.textButtonColors(contentColor = White),
            ) {
                Text(stringResource(R.string.action_register_company))
            }
        }
    }
}

@Composable
private fun FeatureCard(benefit: WelcomeBenefit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = White.copy(alpha = 0.14f),
            contentColor = White,
        ),
    ) {
        Row(
            modifier = Modifier.padding(PitGoSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(PitGoSize.FeatureIconContainer)
                    .background(White.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(benefit.iconRes),
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(PitGoSize.FeatureIcon),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(benefit.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = White,
                )
                Text(
                    text = stringResource(benefit.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = White.copy(alpha = 0.82f),
                )
            }
        }
    }
}
