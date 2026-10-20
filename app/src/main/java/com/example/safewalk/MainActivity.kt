package com.example.safewalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.safewalk.core.navigation.SafeWalkNavHost
import com.example.safewalk.ui.theme.SafewalkTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.i("MainActivity", "POST_NOTIFICATIONS permission GRANTED by user")
            com.example.safewalk.core.notification.EmergencyNotificationHelper.createNotificationChannels(this)
            com.example.safewalk.core.notification.FcmTokenManager.fetchAndSyncToken()
        } else {
            android.util.Log.w("MainActivity", "POST_NOTIFICATIONS permission DENIED by user")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        checkNotificationPermission()
        enableEdgeToEdge()
        setContent {
            SafewalkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SafeWalkNavHost()
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            } else {
                com.example.safewalk.core.notification.EmergencyNotificationHelper.createNotificationChannels(this)
            }
        } else {

            com.example.safewalk.core.notification.EmergencyNotificationHelper.createNotificationChannels(this)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.action == "com.example.safewalk.action.SHOW_TEST_NOTIFICATION") {
            val emgId = intent.getStringExtra(com.example.safewalk.service.EmergencyNotificationActionReceiver.EXTRA_EMERGENCY_ID) ?: "999"
            val reqId = intent.getStringExtra(com.example.safewalk.service.EmergencyNotificationActionReceiver.EXTRA_REQUESTER_ID) ?: "638"
            val reqName = intent.getStringExtra("extra_requester_name") ?: "Priya Sharma"
            val dist = intent.getStringExtra("extra_distance") ?: "120 m"
            com.example.safewalk.core.notification.EmergencyNotificationHelper.showEmergencySosNotification(
                context = this,
                emergencyId = emgId,
                requesterId = reqId,
                requesterName = reqName,
                approxDistance = dist
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    SafewalkTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SafeWalkNavHost()
        }
    }
}









