package com.example.safewalk.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.core.navigation.Screen
import com.example.safewalk.ui.components.ButtonVariant
import com.example.safewalk.ui.components.NavigationTab
import com.example.safewalk.ui.components.SafeWalkBottomNavBar
import com.example.safewalk.ui.components.SafeWalkButton
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.viewmodel.AuthViewModel

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit = {},
    onNavigateToTab: (NavigationTab) -> Unit = {},
    viewModel: AuthViewModel = viewModel()
) {
    var pushNotificationsEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = "Settings & Rules",
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            SafeWalkBottomNavBar(
                currentRoute = Screen.Settings.route,
                onTabSelected = onNavigateToTab
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SafeWalk Backend Rules Engine",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SafeWalkBlueSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "• Discovery Radius: 2 km maximum boundary", style = MaterialTheme.typography.bodySmall)
                        Text(text = "• Proximity Exclusion: Normal users <50m excluded from dispatch", style = MaterialTheme.typography.bodySmall)
                        Text(text = "• Police Privilege: 50m exclusion bypassed for verified police", style = MaterialTheme.typography.bodySmall)
                        Text(text = "• Arrival Threshold: Server distance <= 25 meters", style = MaterialTheme.typography.bodySmall)
                        Text(text = "• Dual Confirmation: Requester and responder must both confirm resolution", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Application Preferences",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Emergency Notifications", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Receive alerts for incoming emergencies within 2km",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = pushNotificationsEnabled,
                                onCheckedChange = { pushNotificationsEnabled = it }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Account Session",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SafeWalkButton(
                            text = "Logout from SafeWalk",
                            onClick = {
                                viewModel.logout(onLogout)
                            },
                            variant = ButtonVariant.DANGER
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "About SafeWalk",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Version: 1.0.0-phase3-foundation", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(text = "Architecture: Clean MVVM + Repository Pattern", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(text = "Backend Integration: Spring Boot (Planned for Future Phase)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}
