package com.example.safewalk.ui.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.core.navigation.Screen
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import com.example.safewalk.ui.components.ButtonVariant
import com.example.safewalk.ui.components.NavigationTab
import com.example.safewalk.ui.components.SafeWalkBottomNavBar
import com.example.safewalk.ui.components.SafeWalkButton
import com.example.safewalk.ui.components.SafeWalkTextField
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.components.UserAvatar
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkGreenSuccess
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import com.example.safewalk.viewmodel.AuthViewModel

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (NavigationTab) -> Unit = {},
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var newContactSafeWalkId by remember { mutableStateOf("") }
    var contactToReplace by remember { mutableStateOf<String?>(null) }
    var replacementContactSafeWalkId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshProfile()
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "avatar.jpg"
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    viewModel.uploadAvatar(bytes, fileName, mimeType)
                }
            } catch (_: Exception) {}
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var displayName = "credential_document.pdf"
                val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val userRole = currentUser?.role ?: UserRole.USER
                    val title = when (userRole) {
                        UserRole.POLICE -> "Official Police Credential"
                        UserRole.HOSPITAL -> "Official Hospital License"
                        else -> "Verification Document"
                    }
                    viewModel.uploadVerificationDocumentFile(
                        title = title,
                        fileName = displayName,
                        fileBytes = bytes,
                        mimeType = mimeType
                    )
                }
            } catch (e: Exception) {
                // handled by viewModel error state
            }
        }
    }

    // Protected screen redirection
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = "User Profile",
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            SafeWalkBottomNavBar(
                currentRoute = Screen.Profile.route,
                onTabSelected = onNavigateToTab
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentUser != null) {
                val user = currentUser!!

                // User Avatar and Header
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    UserAvatar(name = user.name, role = user.role, size = 80.dp, avatarUrl = user.avatarUrl)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { avatarPickerLauncher.launch("image/*") }) {
                        Text("📷 Change Profile Photo", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = user.safeWalkId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Verification Status Badge
                    val (statusBg, statusFg, statusLabel) = when {
                        user.role == UserRole.USER -> {
                            Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Community Citizen • Ready")
                        }
                        user.role == UserRole.POLICE && user.verificationStatus == VerificationStatus.VERIFIED -> {
                            Triple(Color(0xFFE3F2FD), SafeWalkBlueSecondary, "Verified Police Officer ✓")
                        }
                        user.role == UserRole.POLICE && user.verificationStatus == VerificationStatus.REJECTED -> {
                            Triple(Color(0xFFFFEBEE), SafeWalkRedPrimary, "Police Verification Rejected ✕")
                        }
                        user.role == UserRole.POLICE -> {
                            Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Police Verification Pending ⏳")
                        }
                        user.role == UserRole.HOSPITAL && user.verificationStatus == VerificationStatus.VERIFIED -> {
                            Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Verified Hospital Dispatch ✓")
                        }
                        user.role == UserRole.HOSPITAL && user.verificationStatus == VerificationStatus.REJECTED -> {
                            Triple(Color(0xFFFFEBEE), SafeWalkRedPrimary, "Hospital Verification Rejected ✕")
                        }
                        user.role == UserRole.HOSPITAL -> {
                            Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Hospital Verification Pending ⏳")
                        }
                        else -> {
                            Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), "${user.role.displayName} • Verified")
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(statusBg, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusFg,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Feedback Banner
                if (uiState.errorMessage != null) {
                    item {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                if (uiState.successMessage != null) {
                    item {
                        Text(
                            text = uiState.successMessage!!,
                            color = SafeWalkGreenSuccess,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                // Verification Documents Card (Police & Hospital only)
                if (user.role == UserRole.POLICE || user.role == UserRole.HOSPITAL) {
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Verification Documents",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                when (user.verificationStatus) {
                                                    VerificationStatus.VERIFIED -> Color(0xFFE8F5E9)
                                                    VerificationStatus.REJECTED -> Color(0xFFFFEBEE)
                                                    else -> Color(0xFFFFF3E0)
                                                },
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = when (user.verificationStatus) {
                                                VerificationStatus.VERIFIED -> "VERIFIED"
                                                VerificationStatus.REJECTED -> "REJECTED"
                                                else -> "PENDING REVIEW"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when (user.verificationStatus) {
                                                VerificationStatus.VERIFIED -> Color(0xFF2E7D32)
                                                VerificationStatus.REJECTED -> SafeWalkRedPrimary
                                                else -> Color(0xFFE65100)
                                            }
                                        )
                                    }
                                }

                                if (user.verificationStatus == VerificationStatus.REJECTED) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Rejection Reason: ${user.rejectionReason ?: "Your credentials did not satisfy verification criteria. Please upload updated documents to reapply."}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = SafeWalkRedPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (user.documents.isEmpty()) {
                                    Text(
                                        text = "No verification credentials submitted yet. Upload your official credentials for administrator review.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                } else {
                                    user.documents.forEach { doc ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "📄", fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = doc.title, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = "${doc.fileName} • ${doc.formattedTime}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                            }
                                            Text(
                                                text = "✓ Uploaded",
                                                color = SafeWalkGreenSuccess,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                SafeWalkButton(
                                    text = "📁 Upload File from Device (PDF / Image)",
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    variant = ButtonVariant.SECONDARY,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                SafeWalkButton(
                                    text = "🔄 Refresh Verification Status",
                                    onClick = { viewModel.refreshProfile() },
                                    variant = ButtonVariant.OUTLINED,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Emergency / Trusted Contacts (Max 3)
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Emergency Contacts (${user.emergencyContacts.size}/3)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Global Radius",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SafeWalkBlueSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Trusted SafeWalk contacts receive immediate SOS notifications regardless of distance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (user.emergencyContacts.isEmpty()) {
                                Text(
                                    text = "No emergency contacts configured yet. Add up to 3 SafeWalk User IDs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            } else {
                                user.emergencyContacts.forEach { contactId ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "🛡️", fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = contactId,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = {
                                                    contactToReplace = contactId
                                                    replacementContactSafeWalkId = ""
                                                }
                                            ) {
                                                Text("Replace", color = SafeWalkBlueSecondary, style = MaterialTheme.typography.labelSmall)
                                            }
                                            TextButton(
                                                onClick = { viewModel.removeEmergencyContact(contactId) }
                                            ) {
                                                Text("Remove", color = SafeWalkRedPrimary, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }

                            // Contact Replacement Inline Card
                            if (contactToReplace != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F8))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Replace $contactToReplace with new SafeWalk ID:",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            SafeWalkTextField(
                                                value = replacementContactSafeWalkId,
                                                onValueChange = { replacementContactSafeWalkId = it },
                                                label = "New SafeWalk ID",
                                                placeholder = "e.g. SW-USER-1003",
                                                modifier = Modifier.weight(1f)
                                            )
                                            SafeWalkButton(
                                                text = "Save",
                                                onClick = {
                                                    val old = contactToReplace
                                                    if (old != null && replacementContactSafeWalkId.isNotBlank()) {
                                                        viewModel.replaceEmergencyContact(old, replacementContactSafeWalkId) {
                                                            contactToReplace = null
                                                            replacementContactSafeWalkId = ""
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.width(72.dp)
                                            )
                                            SafeWalkButton(
                                                text = "Cancel",
                                                onClick = {
                                                    contactToReplace = null
                                                    replacementContactSafeWalkId = ""
                                                },
                                                variant = ButtonVariant.OUTLINED,
                                                modifier = Modifier.width(72.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (user.emergencyContacts.size < 3) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SafeWalkTextField(
                                        value = newContactSafeWalkId,
                                        onValueChange = {
                                            newContactSafeWalkId = it
                                            viewModel.clearError()
                                        },
                                        label = "SafeWalk User ID",
                                        placeholder = "e.g. SW-USER-1002",
                                        modifier = Modifier.weight(1f)
                                    )
                                    SafeWalkButton(
                                        text = "Add",
                                        onClick = {
                                            if (newContactSafeWalkId.isNotBlank()) {
                                                viewModel.addEmergencyContact(newContactSafeWalkId) {
                                                    newContactSafeWalkId = ""
                                                }
                                            }
                                        },
                                        modifier = Modifier.width(80.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Maximum limit of 3 emergency contacts reached. Tap 'Replace' to change a contact.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                // Account Details Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Account Details",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            ProfileItem(label = "Full Name", value = user.name)
                            ProfileItem(label = "SafeWalk User ID", value = user.safeWalkId)
                            ProfileItem(label = "Permanent Role", value = user.role.displayName)
                            ProfileItem(label = "Current Status", value = user.status)
                            ProfileItem(label = "Verification State", value = user.verificationStatus.displayName)

                            if (user.department != null) {
                                ProfileItem(label = "Police Department", value = user.department!!)
                            }
                            if (user.badgeId != null) {
                                ProfileItem(label = "Badge / Officer ID", value = user.badgeId!!)
                            }
                            if (user.hospitalName != null) {
                                ProfileItem(label = "Hospital Facility", value = user.hospitalName!!)
                            }
                            if (user.licenseId != null) {
                                ProfileItem(label = "License / Accreditation ID", value = user.licenseId!!)
                            }
                            if (user.designation != null) {
                                ProfileItem(label = "Professional Designation", value = user.designation!!)
                            }

                            ProfileItem(label = "Phone", value = user.phone)
                            ProfileItem(label = "Email", value = user.email)
                        }
                    }
                }

                // Logout
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    SafeWalkButton(
                        text = "Logout",
                        onClick = {
                            viewModel.logout(onLogout)
                        },
                        variant = ButtonVariant.DANGER
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
