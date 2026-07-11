package com.example.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel) {
    val settingsState by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    var editingName by remember { mutableStateOf(viewModel.myName.value) }
    var editingBio by remember { mutableStateOf(viewModel.myBio.value) }

    var selectedWallpaper by remember { mutableStateOf("standard") }
    var showBlockedDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PROFILE EDIT HEADER
            Text("Profile Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = { Text("Your Name") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_name_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editingBio,
                        onValueChange = { editingBio = it },
                        label = { Text("Bio Status") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_bio_input"),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            viewModel.myName.value = editingName
                            viewModel.myBio.value = editingBio
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Profile")
                    }
                }
            }

            // APP THEMING & CHAT WALLPAPER
            Text("Chat Personalization", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Chat Wallpaper Theme:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WallpaperItem("Standard", "standard", Color(0xFFE5DDD5), selectedWallpaper) {
                            selectedWallpaper = "standard"
                            viewModel.selectWallpaper(null)
                        }
                        WallpaperItem("Forest Green", "forest", Color(0xFF385E38), selectedWallpaper) {
                            selectedWallpaper = "forest"
                            viewModel.selectWallpaper("forest")
                        }
                        WallpaperItem("Ocean Blue", "ocean", Color(0xFF1D5A75), selectedWallpaper) {
                            selectedWallpaper = "ocean"
                            viewModel.selectWallpaper("ocean")
                        }
                    }
                }
            }

            // HIGH SECURITY CONTROLS
            Text("Security & Privacy", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Two-Factor Authentication", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Add an extra layer of account security", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settingsState.isTwoFactorEnabled,
                            onCheckedChange = { viewModel.toggleTwoFactor(it) },
                            modifier = Modifier.testTag("2fa_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("End-to-End Cryptography", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("AES-256 chat transcript encryption is active", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.VerifiedUser, contentDescription = "Active", tint = Color.Green)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Blocked Users List", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Moderate blocked accounts & spam blocklist", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showBlockedDialog = true }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "View blocklist")
                        }
                    }
                }
            }
        }
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Accounts") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("The following users are blocked from sending you messages:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Spam Bot #214", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { viewModel.unblockUser("Spam Bot #214") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Unblock")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun RowScope.WallpaperItem(
    name: String,
    id: String,
    color: Color,
    selectedId: String,
    onClick: () -> Unit
) {
    val isSelected = selectedId == id
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .height(70.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White)
            }
        }
        Text(
            text = name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )
    }
}
