package com.example.safewalk.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.core.navigation.Screen
import com.example.safewalk.ui.components.EmergencyStatusBadge
import com.example.safewalk.ui.components.NavigationTab
import com.example.safewalk.ui.components.SafeWalkBottomNavBar
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkGreenSuccess
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import com.example.safewalk.viewmodel.HistoryViewModel

@Composable
fun HistoryScreen(
    onEmergencyClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (NavigationTab) -> Unit = {},
    viewModel: HistoryViewModel = viewModel()
) {
    val history by viewModel.history.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            viewModel.loadHistory(currentUser!!.id)
        }
    }

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = "Emergency History",
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            SafeWalkBottomNavBar(
                currentRoute = Screen.History.route,
                onTabSelected = onNavigateToTab
            )
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📜", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No past emergencies found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Resolved and cancelled emergency records will be archived here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(history) { emg ->
                    val isRequester = currentUser != null && emg.isUserRequester(currentUser!!.id)
                    val isTrustedContact = emg.isTrustedContactViewer ||
                            (currentUser != null && emg.requester.emergencyContacts.any { it.equals(currentUser!!.safeWalkId, ignoreCase = true) })
                    val contextualRole = when {
                        isRequester -> "REQUESTER"
                        isTrustedContact -> "TRUSTED CONTACT"
                        else -> "RESPONDER"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEmergencyClick(emg.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Emergency ID: ${emg.numericId}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Role: $contextualRole",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = when {
                                                isRequester -> SafeWalkRedPrimary
                                                isTrustedContact -> Color(0xFFE65100)
                                                else -> SafeWalkBlueSecondary
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                EmergencyStatusBadge(status = emg.status)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (isRequester) {
                                val responderNames = emg.acceptedResponders.map { it.name }
                                val responderText = if (responderNames.isNotEmpty()) {
                                    responderNames.joinToString(", ")
                                } else {
                                    "No responders attached"
                                }
                                Text(
                                    text = "Responders: $responderText",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = "Requester: ${emg.requester.name}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Text(
                                text = "Date: ${emg.formattedTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            if (emg.resolvedAt != null) {
                                Text(
                                    text = "Resolved: ${emg.formattedResolvedTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SafeWalkGreenSuccess
                                )
                            }
                            if (emg.cancellationReason != null) {
                                Text(
                                    text = "Reason: ${emg.cancellationReason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
