package com.trustarx.pitgo.features.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoElevation
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.home.model.ServiceCategory
import com.trustarx.pitgo.features.home.model.ServiceCategoryId
import com.trustarx.pitgo.features.home.viewmodel.HomeUiState
import com.trustarx.pitgo.features.home.viewmodel.HomeViewModel

@Composable
fun HomeRoute(
    onCategorySelected: (ServiceCategoryId) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onCategorySelected = onCategorySelected,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onCategorySelected: (ServiceCategoryId) -> Unit,
) {
    val gridState = rememberLazyGridState()

    Scaffold { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PitGoSize.ServiceCardMinWidth),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(
                start = PitGoSpacing.Medium,
                top = innerPadding.calculateTopPadding() + PitGoSpacing.Large,
                end = PitGoSpacing.Medium,
                bottom = innerPadding.calculateBottomPadding() + PitGoSpacing.Large,
            ),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(PitGoSpacing.Large))
                    Text(
                        text = stringResource(R.string.home_services_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }

            items(
                items = uiState.categories,
                key = { category -> category.id.routeValue },
            ) { category ->
                ServiceCategoryCard(
                    category = category,
                    onClick = { onCategorySelected(category.id) },
                )
            }
        }
    }
}

@Composable
private fun ServiceCategoryCard(
    category: ServiceCategory,
    onClick: () -> Unit,
) {
    val actionDescription = stringResource(category.accessibilityActionRes)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = PitGoSize.MinimumTouchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = actionDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = PitGoElevation.ServiceCard),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PitGoSpacing.Medium),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(category.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(PitGoSize.ServiceIcon),
                )
                Spacer(Modifier.height(PitGoSpacing.Small))
                Text(
                    text = stringResource(category.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
