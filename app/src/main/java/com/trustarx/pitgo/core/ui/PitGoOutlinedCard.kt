package com.trustarx.pitgo.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.core.design.theme.PitGoSpacing

/**
 * The app's standard outlined card: white/surface fill with a 1dp purple border, used instead of
 * the plain Material3 Card default — which renders as a flat gray tonal surface that reads as
 * disabled/inert next to the rest of the purple-accented design system.
 */
@Composable
fun PitGoOutlinedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = PitGoSpacing.Medium,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    if (onClick != null) {
        Card(onClick = onClick, colors = colors, border = border, modifier = modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                content = content,
            )
        }
    } else {
        Card(colors = colors, border = border, modifier = modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                content = content,
            )
        }
    }
}
