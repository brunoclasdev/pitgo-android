package com.trustarx.pitgo.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize

enum class BottomItemId {
    HOME,
    SERVICES,
    GARAGE,
    APPOINTMENTS,
    PROFILE,
    MAP,
    RIDE_DATA,
    VALUES,
}

data class BottomNavigationItem(
    val id: BottomItemId,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val displayOrder: Int,
)

val PitGoBottomNavigationItems = listOf(
    BottomNavigationItem(BottomItemId.HOME, R.string.nav_home, R.drawable.ic_nav_home, 0),
    BottomNavigationItem(BottomItemId.SERVICES, R.string.nav_services, R.drawable.ic_nav_services, 1),
    BottomNavigationItem(BottomItemId.GARAGE, R.string.nav_garage, R.drawable.ic_nav_garage, 2),
    BottomNavigationItem(BottomItemId.APPOINTMENTS, R.string.nav_appointments, R.drawable.ic_nav_appointments, 3),
    BottomNavigationItem(BottomItemId.PROFILE, R.string.nav_profile, R.drawable.ic_nav_profile, 4),
)

/** Mocked PitTec provider shell (US5) — same component/behavior, provider-specific options. */
val PitTecBottomNavigationItems = listOf(
    BottomNavigationItem(BottomItemId.MAP, R.string.nav_pittec_map, R.drawable.ic_nav_map, 0),
    BottomNavigationItem(BottomItemId.RIDE_DATA, R.string.nav_pittec_ride_data, R.drawable.ic_nav_ride_data, 1),
    BottomNavigationItem(BottomItemId.VALUES, R.string.nav_pittec_values, R.drawable.ic_nav_values, 2),
    BottomNavigationItem(BottomItemId.PROFILE, R.string.nav_profile, R.drawable.ic_nav_profile, 3),
)

@Composable
fun PitGoBottomNavigation(
    items: List<BottomNavigationItem>,
    selectedItem: BottomItemId,
    onItemClick: (BottomItemId) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.id == selectedItem,
                onClick = { onItemClick(item.id) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(PitGoSize.NavigationIcon),
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
