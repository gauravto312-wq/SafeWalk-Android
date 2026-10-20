package com.example.safewalk.ui.auth

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.ui.components.SafeWalkButton
import com.example.safewalk.ui.components.SafeWalkTextField
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import com.example.safewalk.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    var selectedRole by remember { mutableStateOf(UserRole.USER) }

    // Common fields
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var emergencyPassword by remember { mutableStateOf("") }

    // Police-specific fields
    var policeDepartment by remember { mutableStateOf("") }
    var badgeId by remember { mutableStateOf("") }
    var policeDesignation by remember { mutableStateOf("") }

    // Hospital-specific fields
    var hospitalName by remember { mutableStateOf("") }
    var hospitalLicenseId by remember { mutableStateOf("") }
    var hospitalDesignation by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = "Create SafeWalk Account",
                onBackClick = onNavigateBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Select account type",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Choose your role within the SafeWalk emergency network",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Role Selector: Citizen, Police, Hospital (ADMIN is explicitly prohibited from public mobile registration)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedRole == UserRole.USER,
                        onClick = { selectedRole = UserRole.USER },
                        label = { Text("Citizen") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SafeWalkRedPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = SafeWalkRedPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedRole == UserRole.POLICE,
                        onClick = { selectedRole = UserRole.POLICE },
                        label = { Text("Police") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SafeWalkBlueSecondary.copy(alpha = 0.15f),
                            selectedLabelColor = SafeWalkBlueSecondary
                        )
                    )
                    FilterChip(
                        selected = selectedRole == UserRole.HOSPITAL,
                        onClick = { selectedRole = UserRole.HOSPITAL },
                        label = { Text("Hospital") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2E7D32).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF2E7D32)
                        )
                    )
                }
            }

            // Role Description Notice
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        when (selectedRole) {
                            UserRole.USER -> {
                                Text(
                                    text = "Citizen Account (ROLE_USER)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Full access to trigger SOS emergencies and respond to nearby community alerts (>50m to 2km). Account is active immediately.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            UserRole.POLICE -> {
                                Text(
                                    text = "Law Enforcement (ROLE_POLICE)",
                                    fontWeight = FontWeight.Bold,
                                    color = SafeWalkBlueSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Initial status: PENDING VERIFICATION. Requires official ID upload and verification review before 50m exclusion bypass is activated.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            UserRole.HOSPITAL -> {
                                Text(
                                    text = "Medical Facility (ROLE_HOSPITAL)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Initial status: PENDING VERIFICATION. Requires hospital licensing upload and administrative review for priority medical dispatch.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            UserRole.ADMIN -> {}
                        }
                    }
                }
            }

            // Registration Fields
            item {
                SafeWalkTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        viewModel.clearError()
                    },
                    label = if (selectedRole == UserRole.HOSPITAL) "Facility / Admin Name" else "Full Name",
                    placeholder = if (selectedRole == UserRole.HOSPITAL) "e.g. City Care Center" else "e.g. Rahul Sharma"
                )
            }

            item {
                SafeWalkTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        viewModel.clearError()
                    },
                    label = "Email Address",
                    placeholder = "e.g. user@safewalk.local",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            }

            item {
                SafeWalkTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        viewModel.clearError()
                    },
                    label = "Contact Mobile Number",
                    placeholder = "e.g. +91 98765 43210 or 9876543210",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }

            if (selectedRole == UserRole.POLICE) {
                item {
                    SafeWalkTextField(
                        value = policeDepartment,
                        onValueChange = { policeDepartment = it },
                        label = "Police Department / Station (Optional)",
                        placeholder = "e.g. Central Police Station"
                    )
                }
                item {
                    SafeWalkTextField(
                        value = badgeId,
                        onValueChange = { badgeId = it },
                        label = "Badge ID / Service Number (Optional)",
                        placeholder = "e.g. DP-8842"
                    )
                }
            } else if (selectedRole == UserRole.HOSPITAL) {
                item {
                    SafeWalkTextField(
                        value = hospitalName,
                        onValueChange = { hospitalName = it },
                        label = "Hospital / Facility Name (Optional)",
                        placeholder = "e.g. Metro Care Hospital"
                    )
                }
                item {
                    SafeWalkTextField(
                        value = hospitalLicenseId,
                        onValueChange = { hospitalLicenseId = it },
                        label = "Medical License ID (Optional)",
                        placeholder = "e.g. MED-LIC-2041"
                    )
                }
            }

            item {
                SafeWalkTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        viewModel.clearError()
                    },
                    label = "Password",
                    placeholder = "Minimum 6 characters",
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Text(
                            text = if (passwordVisible) "👁️" else "🙈",
                            modifier = Modifier
                                .clickable { passwordVisible = !passwordVisible }
                                .padding(8.dp),
                            fontSize = 18.sp
                        )
                    }
                )
            }

            item {
                SafeWalkTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        viewModel.clearError()
                    },
                    label = "Confirm Password",
                    placeholder = "Re-enter password",
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Text(
                            text = if (confirmPasswordVisible) "👁️" else "🙈",
                            modifier = Modifier
                                .clickable { confirmPasswordVisible = !confirmPasswordVisible }
                                .padding(8.dp),
                            fontSize = 18.sp
                        )
                    }
                )
            }

            item {
                SafeWalkTextField(
                    value = emergencyPassword,
                    onValueChange = {
                        emergencyPassword = it
                        viewModel.clearError()
                    },
                    label = "Emergency / SOS PIN (Optional)",
                    placeholder = "Default: 1234 (Used for 5s SOS cancel verification)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }

            if (uiState.errorMessage != null) {
                item {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                SafeWalkButton(
                    text = "Complete ${selectedRole.displayName} Registration",
                    onClick = {
                        viewModel.register(
                            name = name,
                            email = email,
                            phone = phone,
                            password = password,
                            confirmPassword = confirmPassword,
                            role = selectedRole,
                            emergencyPassword = emergencyPassword.ifBlank { "1234" },
                            department = if (selectedRole == UserRole.POLICE) policeDepartment.ifBlank { null } else null,
                            badgeId = if (selectedRole == UserRole.POLICE) badgeId.ifBlank { null } else null,
                            hospitalName = if (selectedRole == UserRole.HOSPITAL) hospitalName.ifBlank { null } else null,
                            licenseId = if (selectedRole == UserRole.HOSPITAL) hospitalLicenseId.ifBlank { null } else null,
                            designation = if (selectedRole == UserRole.POLICE) policeDesignation.ifBlank { null } else if (selectedRole == UserRole.HOSPITAL) hospitalDesignation.ifBlank { null } else null,
                            onSuccess = onRegisterSuccess
                        )
                    },
                    isLoading = uiState.isLoading
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Already have an account?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            text = "Login",
                            fontWeight = FontWeight.Bold,
                            color = SafeWalkRedPrimary
                        )
                    }
                }
            }
        }
    }
}
