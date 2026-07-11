package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: ChatViewModel,
    onNavigateToSignup: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var isPhoneAuth by remember { mutableStateOf(false) } // Toggle between Email & Phone OTP
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Forum,
                contentDescription = "App Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome Back",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("login_title")
            )

            Text(
                text = "Log in to access your secure chat backups.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Auth toggle tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                Button(
                    onClick = { isPhoneAuth = false; errorMessage = "" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isPhoneAuth) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (!isPhoneAuth) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).testTag("email_tab")
                ) {
                    Text("Email")
                }

                Button(
                    onClick = { isPhoneAuth = true; errorMessage = "" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPhoneAuth) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isPhoneAuth) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).testTag("phone_tab")
                ) {
                    Text("Phone OTP")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isPhoneAuth) {
                // Email Auth Inputs
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("you@example.com") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("login_email"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("login_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (email.trim().isEmpty() || password.trim().isEmpty()) {
                            errorMessage = "Please enter email and password"
                        } else if (password.length < 6) {
                            errorMessage = "Password must be at least 6 characters"
                        } else {
                            isLoading = true
                            errorMessage = ""
                            // Simulate authentication & navigate
                            val defaultName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                            viewModel.completeOnboarding(
                                userId = email.substringBefore("@").lowercase(),
                                name = defaultName,
                                phone = email,
                                bio = "Hey there! I am using ConnectChat."
                            )
                            onLoginSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_submit_btn")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Log In")
                    }
                }
            } else {
                // Phone OTP Auth Inputs
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+91 98765 43210") },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("login_phone"),
                    singleLine = true
                )

                if (isOtpSent) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it },
                        label = { Text("6-Digit OTP Code") },
                        placeholder = { Text("123456") },
                        leadingIcon = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("login_otp"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (phoneNumber.trim().isEmpty()) {
                            errorMessage = "Please enter phone number"
                        } else if (!isOtpSent) {
                            isOtpSent = true
                            errorMessage = "OTP code sent successfully!"
                        } else {
                            if (otpCode.length < 4) {
                                errorMessage = "Please enter the OTP code"
                            } else {
                                isLoading = true
                                val userSeed = phoneNumber.takeLast(4)
                                viewModel.completeOnboarding(
                                    userId = "usr_$userSeed",
                                    name = "User $userSeed",
                                    phone = phoneNumber,
                                    bio = "Hey there! I am using ConnectChat."
                                )
                                onLoginSuccess()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_otp_submit_btn")
                ) {
                    Text(if (!isOtpSent) "Send OTP" else "Verify OTP")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Sign In Mock Button
            OutlinedButton(
                onClick = {
                    viewModel.completeOnboarding(
                        userId = "google_user",
                        name = "Google Connected User",
                        phone = "google@connectchat.com",
                        bio = "Happy Google account."
                    )
                    onLoginSuccess()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_google")
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Google")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Direct to Sign Up Link
            Row(
                modifier = Modifier.clickable { onNavigateToSignup() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have an account? ",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Sign Up",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = if (errorMessage.contains("success") || errorMessage.contains("sent")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
