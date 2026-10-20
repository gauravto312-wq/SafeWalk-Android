package com.example.safewalk.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.core.navigation.Screen
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import com.example.safewalk.ui.components.EmergencyStatusBadge
import com.example.safewalk.ui.components.NavigationTab
import com.example.safewalk.ui.components.SafeWalkBottomNavBar
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.components.SosEmergencyButton
import com.example.safewalk.ui.components.UserAvatar
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.safewalk.viewmodel.HomeViewModel
import com.example.safewalk.viewmodel.LocationViewModel
import com.example.safewalk.viewmodel.NotificationViewModel

@Composable
fun HomeScreen(
    onTriggerSos: (String) -> Unit,
    onEmergencyClick: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
    notificationViewModel: NotificationViewModel = viewModel(),
    locationViewModel: LocationViewModel = viewModel()
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val activeEmergencies by viewModel.activeEmergencies.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val unreadCount by notificationViewModel.unreadCount.collectAsState()
    val locationState by locationViewModel.uiState.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        locationViewModel.onPermissionResult(fineGranted || coarseGranted)
        if (fineGranted || coarseGranted) {
            locationViewModel.refreshAndSyncLocation()
        }
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        locationViewModel.onLocationProviderAvailabilityChanged()
    }

    // Auto-detect when location provider is turned ON (via Settings return or Quick Settings tile)
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationViewModel.onLocationProviderAvailabilityChanged()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    locationViewModel.onLocationProviderAvailabilityChanged()
                }
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    // Protected screen redirection and data refresh
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            onNavigateToLogin()
        } else {
            viewModel.refreshActiveEmergencies()
            locationViewModel.checkStatus()
            val state = locationViewModel.uiState.value
            if (!state.hasPermission) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else if (!state.isGpsEnabled) {
                try {
                    locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (_: Exception) {}
            } else if (state.currentLocation == null) {
                locationViewModel.refreshAndSyncLocation()
            }
        }
    }

    val user = currentUser
    val isUnverifiedAuthority = user != null && user.role.isAuthority && 
        (!user.isVerified || user.verificationStatus != VerificationStatus.VERIFIED)
    val displayedEmergencies = if (isUnverifiedAuthority) {
        emptyList()
    } else {
        activeEmergencies.filter { emg ->
            emg.isUserRequester(user?.id ?: "") ||
            emg.isUserAcceptedResponder(user?.id ?: "") ||
            viewModel.isEligibleToRespond(emg)
        }
    }

    val userActiveEmergency = activeEmergencies.firstOrNull { user != null && it.isUserRequester(user.id) && !it.isTerminal }

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = "SafeWalk",
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Notification Bell with Badge
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clickable { onNavigateToNotifications() }
                        ) {
                            Text(text = "🔔", fontSize = 20.sp)
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(SafeWalkRedPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (unreadCount > 9) "9+" else "$unreadCount",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "📜",
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clickable { onNavigateToHistory() },
                            fontSize = 20.sp
                        )
                        Text(
                            text = "⚙️",
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clickable { onNavigateToSettings() },
                            fontSize = 20.sp
                        )
                        Text(
                            text = "👤",
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clickable { onNavigateToProfile() },
                            fontSize = 20.sp
                        )
                    }
                }
            )
        },
        bottomBar = {
            SafeWalkBottomNavBar(
                currentRoute = Screen.Home.route,
                onTabSelected = { tab ->
                    when (tab) {
                        NavigationTab.HOME -> { /* Current */ }
                        NavigationTab.HISTORY -> onNavigateToHistory()
                        NavigationTab.PROFILE -> onNavigateToProfile()
                        NavigationTab.SETTINGS -> onNavigateToSettings()
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Current User Status Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                if (currentUser != null) {
                    val user = currentUser!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = user.name, role = user.role, size = 48.dp, avatarUrl = user.avatarUrl)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = user.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (user.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "✓", color = SafeWalkBlueSecondary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        text = "${user.role.displayName} • ${user.safeWalkId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (user.role.isAuthority) Color(0xFFE3F2FD) else Color(0xFFE8F5E9),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = user.status,
                                        color = if (user.role.isAuthority) SafeWalkBlueSecondary else Color(0xFF2E7D32),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            if (user.role.isAuthority) {
                                Spacer(modifier = Modifier.height(10.dp))
                                when {
                                    user.verificationStatus == VerificationStatus.PENDING -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFFF3E0), shape = RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "⏳ Verification Pending — SafeWalk administrative review required before emergency dispatch activation.",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = Color(0xFFE65100)
                                            )
                                        }
                                    }
                                    user.verificationStatus == VerificationStatus.REJECTED -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "✕ Verification Rejected — Credentials rejected. Please update documents in Profile.",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = SafeWalkRedPrimary
                                            )
                                        }
                                    }
                                    user.role == UserRole.POLICE -> {
                                        Text(
                                            text = "🚨 Verified Law Enforcement Patrol • 50m Exclusion Bypassed",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = SafeWalkBlueSecondary
                                        )
                                    }
                                    user.role == UserRole.HOSPITAL -> {
                                        Text(
                                            text = "🏥 Emergency Medical Response Unit • Dispatch Ready",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = SafeWalkBlueSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // GPS Location Services & Cloud Telemetry Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📍", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Device GPS Location",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            val badgeText = when {
                                locationState.isSyncing || locationState.isAcquiring -> "Updating..."
                                locationState.isSyncedWithCloud -> "Cloud Synced ✓"
                                !locationState.hasPermission -> "Permission Missing"
                                !locationState.isGpsEnabled -> "GPS Disabled"
                                locationState.currentLocation != null -> "Acquired"
                                else -> "Standby"
                            }
                            val badgeColor = when {
                                locationState.isSyncedWithCloud -> Color(0xFF2E7D32)
                                locationState.isSyncing || locationState.isAcquiring -> SafeWalkBlueSecondary
                                !locationState.hasPermission -> Color(0xFFE65100)
                                !locationState.isGpsEnabled -> SafeWalkRedPrimary
                                else -> Color.Gray
                            }

                            Box(
                                modifier = Modifier
                                    .background(badgeColor.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeColor,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (locationState.currentLocation != null) {
                            val loc = locationState.currentLocation!!
                            Text(
                                text = "Coordinates: ${loc.formattedCoordinates}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = loc.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "Last Updated: ${loc.formattedTimestamp}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        } else if (!locationState.isGpsEnabled) {
                            Text(
                                text = "Device location is off. Please turn on location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SafeWalkRedPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "No GPS coordinates acquired yet. Tap update to read live device location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        if (locationState.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = locationState.errorMessage!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafeWalkRedPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!locationState.hasPermission) {
                                Button(
                                    onClick = {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafeWalkBlueSecondary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Grant Location Permission", fontSize = 13.sp)
                                }
                            } else if (!locationState.isGpsEnabled) {
                                Button(
                                    onClick = {
                                        try {
                                            locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafeWalkRedPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Enable Device Location", fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = { locationViewModel.refreshAndSyncLocation() },
                                    modifier = Modifier.weight(1f),
                                    enabled = !locationState.isAcquiring && !locationState.isSyncing,
                                    colors = ButtonDefaults.buttonColors(containerColor = SafeWalkBlueSecondary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (locationState.isSyncing || locationState.isAcquiring) "Syncing..." else "Update & Sync GPS",
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Central SOS Emergency Trigger
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Need Immediate Assistance?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SafeWalkRedPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SosEmergencyButton(
                            onClick = {
                                viewModel.triggerSosEmergency { emergencyId ->
                                    onTriggerSos(emergencyId)
                                }
                            },
                            enabled = !uiState.isCreatingSos
                        )

                        if (userActiveEmergency != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTriggerSos(userActiveEmergency.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "🚨 Active SOS in Progress",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = SafeWalkRedPrimary
                                            )
                                        )
                                        Text(
                                            text = "Emergency ID: ${userActiveEmergency.numericId} • Tap to manage or cancel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.DarkGray
                                        )
                                    }
                                    Text(
                                        text = "Manage →",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = SafeWalkRedPrimary
                                        )
                                    )
                                }
                            }
                        }

                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                            ) {
                                Text(
                                    text = uiState.errorMessage!!,
                                    modifier = Modifier.padding(10.dp),
                                    color = SafeWalkRedPrimary,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }

            // Nearby Emergencies (Section 4)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nearby Emergencies",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${displayedEmergencies.size} Active",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = SafeWalkBlueSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    text = "Tap to review details, accept, and dispatch assistance",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            if (displayedEmergencies.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active emergencies in your patrol radius.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                items(displayedEmergencies) { emg ->
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatar(name = emg.requester.name, role = emg.requester.role, size = 32.dp, avatarUrl = emg.requester.avatarUrl)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = emg.requester.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                EmergencyStatusBadge(status = emg.status)
                            }
                            val dist = viewModel.getDistanceToEmergencyMeters(emg)
                            val distText = if (dist >= 1000) String.format("%.1f km", dist / 1000.0) else "${dist.toInt()}m"
                            val isRequester = currentUser != null && emg.isUserRequester(currentUser!!.id)
                            val isAccepted = currentUser != null && emg.isUserAcceptedResponder(currentUser!!.id)
                            val actionText = if (isRequester) "Manage SOS →" else if (isAccepted) "Navigate →" else "View Emergency →"
                            val locationText = if (isRequester || isAccepted) {
                                "📍 ${emg.requesterLocation.address} • ~$distText away"
                            } else {
                                "🔒 Location: Protected until accepted • ~$distText away"
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = locationText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRequester || isAccepted) Color.DarkGray else Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Responders: ${emg.acceptedResponders.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Text(
                                    text = actionText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRequester) SafeWalkRedPrimary else SafeWalkBlueSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
