package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: ChatViewModel) {
    var step by remember { mutableStateOf(1) } // Step 1: Login, Step 2: Profile Setup
    var isPhoneAuth by remember { mutableStateOf(true) } // Toggle Phone OTP vs Email

    // Inputs
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }

    var fullName by remember { mutableStateOf("") }
    var userIdInput by remember { mutableStateOf("@user123") }
    var bioStatus by remember { mutableStateOf("Hey there! I am using ConnectChat.") }
    var errorMessage by remember { mutableStateOf("") }

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
            // Header Logo
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
                text = "ConnectChat",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("app_title")
            )

            Text(
                text = "End-to-End Encrypted Real-Time Messaging",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (step == 1) {
                // AUTHENTICATION TAB SELECTION
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    Button(
                        onClick = { isPhoneAuth = true; errorMessage = "" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPhoneAuth) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (isPhoneAuth) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("phone_auth_tab")
                    ) {
                        Text("Phone OTP")
                    }

                    Button(
                        onClick = { isPhoneAuth = false; errorMessage = "" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isPhoneAuth) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (!isPhoneAuth) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("email_auth_tab")
                    ) {
                        Text("Email Login")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isPhoneAuth) {
                    // Phone Authentication Form
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+1 (555) 019-2834") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phone_input"),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_input"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (phoneNumber.trim().isEmpty()) {
                                errorMessage = "Please enter a valid phone number"
                            } else if (!isOtpSent) {
                                isOtpSent = true
                                errorMessage = "OTP code sent successfully!"
                            } else {
                                if (otpCode.length < 4) {
                                    errorMessage = "Please enter the OTP code"
                                } else {
                                    step = 2
                                    errorMessage = ""
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("phone_auth_button")
                    ) {
                        Text(if (!isOtpSent) "Send OTP" else "Verify OTP")
                    }

                } else {
                    // Email Authentication Form
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        placeholder = { Text("you@example.com") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (email.trim().isEmpty() || password.trim().isEmpty()) {
                                errorMessage = "Please enter email and password"
                            } else {
                                step = 2
                                errorMessage = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("email_auth_button")
                    ) {
                        Text("Log In / Sign Up")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Alternative: Google Sign-In Button
                OutlinedButton(
                    onClick = {
                        step = 2
                        errorMessage = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_auth_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "Google Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with Google")
                }

            } else {
                // STEP 2: PROFILE DETAILS & BIO SETUP
                Text(
                    text = "Setup Profile",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Choose your profile name and details. You can change these anytime in settings.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Profile Image Placeholder with Add overlay
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.size(100.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter("https://images.unsplash.com/photo-1534528741775-53994a69daeb"),
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                    IconButton(
                        onClick = { /* camera/gallery */ },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "Add Photo",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = userIdInput,
                    onValueChange = { userIdInput = it },
                    label = { Text("Unique User ID / Username") },
                    placeholder = { Text("e.g. @rahul123") },
                    leadingIcon = { Icon(Icons.Filled.AlternateEmail, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("userid_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. John Doe") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = bioStatus,
                    onValueChange = { bioStatus = it },
                    label = { Text("Bio Status") },
                    placeholder = { Text("Feeling happy! ✨") },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bio_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val cleanUserId = userIdInput.trim().replace("@", "")
                        if (cleanUserId.length < 3) {
                            errorMessage = "User ID must be at least 3 characters long."
                        } else if (fullName.trim().isEmpty()) {
                            errorMessage = "Name cannot be empty!"
                        } else {
                            viewModel.completeOnboarding(
                                userId = userIdInput,
                                name = fullName,
                                phone = phoneNumber.ifEmpty { "user@example.com" },
                                bio = bioStatus
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("enter_chat_button")
                ) {
                    Text("Enter ConnectChat")
                }
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
