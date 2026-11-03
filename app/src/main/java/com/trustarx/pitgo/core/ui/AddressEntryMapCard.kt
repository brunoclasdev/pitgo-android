package com.trustarx.pitgo.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.core.design.theme.PitGoSpacing

@Composable
fun AddressEntryMapCard(modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = if(containerColor == androidx.compose.ui.graphics.Color.White) androidx.compose.ui.graphics.Color(0xFF202124) else MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp, shadowElevation = 4.dp,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(PitGoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small), content = content)
    }
}
