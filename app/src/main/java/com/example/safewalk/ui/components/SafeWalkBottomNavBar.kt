package com.example.safewalk.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safewalk.core.navigation.Screen
import com.example.safewalk.ui.theme.SafeWalkRedPrimary

enum class NavigationTab(val route: String, val label: String, val icon: String) {
    HOME(Screen.Home.route, "Home", "🏠"),
    HISTORY(Screen.History.route, "History", "📜"),
    PROFILE(Screen.Profile.route, "Profile", "👤"),
    SETTINGS(Screen.Settings.route, "Settings", "⚙️")
}

@Composable
fun SafeWalkBottomNavBar(
    currentRoute: String,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        NavigationTab.values().forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        onTabSelected(tab)
                    }
                },
                icon = {
                    Text(text = tab.icon, fontSize = 20.sp)
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SafeWalkRedPrimary,
                    selectedTextColor = SafeWalkRedPrimary,
                    indicatorColor = SafeWalkRedPrimary.copy(alpha = 0.12f),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }
}
