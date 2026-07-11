package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CallEntity
import com.example.data.database.ChatEntity
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(viewModel: ChatViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Chats, 1: Groups, 2: Calls, 3: Updates (Notifs)
    var showMenu by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    // Group creation dialog state
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupDesc by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }

    val chatsList by viewModel.chats.collectAsState()
    val callsList by viewModel.calls.collectAsState()
    val notificationsList by viewModel.notifications.collectAsState()
    val isDarkMode by viewModel.settings.collectAsState()

    val filteredChats = chatsList.filter { !it.isGroup && it.name.contains(searchQuery, ignoreCase = true) }
    val filteredGroups = chatsList.filter { it.isGroup && it.name.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("Search ConnectChat...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_field")
                        )
                    } else {
                        Text(
                            text = "ConnectChat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    if (isSearchExpanded) {
                        IconButton(onClick = { 
                            isSearchExpanded = false
                            viewModel.searchQuery.value = "" 
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close search", tint = Color.White)
                        }
                    } else null
                },
                actions = {
                    if (!isSearchExpanded) {
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        // Toggle Dark/Light Mode
                        viewModel.toggleDarkMode(!isDarkMode.isDarkMode)
                    }) {
                        Icon(
                            imageVector = if (isDarkMode.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                    }
                    
                    // Top Bar Overflow Menu
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Admin Panel") },
                            onClick = {
                                showMenu = false
                                viewModel.navigateTo(AppScreen.AdminPanel)
                            },
                            leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                            modifier = Modifier.testTag("menu_admin_panel")
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                viewModel.navigateTo(AppScreen.Settings)
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            modifier = Modifier.testTag("menu_settings")
                        )
                        DropdownMenuItem(
                            text = { Text("Clear All Chats") },
                            onClick = {
                                showMenu = false
                                // Clear all messages flow
                                viewModel.chats.value.forEach {
                                    viewModel.sendMessage("🚫 Chat history cleared by user")
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == 1) {
                            showCreateGroupDialog = true
                        } else if (selectedTab == 2) {
                            // Start mock incoming call
                            viewModel.receiveIncomingCall("Mom ❤️", isVideo = true, chatId = "mom")
                        } else {
                            // Start mock incoming text from Alice
                            viewModel.receiveIncomingCall("Alice Smith", isVideo = false, chatId = "alice_smith")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("dashboard_fab")
                ) {
                    Icon(
                        imageVector = when (selectedTab) {
                            1 -> Icons.Default.GroupAdd
                            2 -> Icons.Default.Call
                            else -> Icons.Default.Chat
                        },
                        contentDescription = "Action"
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                val tabs = listOf(
                    Triple("Chats", Icons.Default.Chat, Icons.Outlined.Chat),
                    Triple("Groups", Icons.Default.Group, Icons.Outlined.Group),
                    Triple("Calls", Icons.Default.Call, Icons.Outlined.Call),
                    Triple("Friends", Icons.Default.People, Icons.Outlined.People),
                    Triple("Alerts", Icons.Default.Notifications, Icons.Outlined.Notifications)
                )

                tabs.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) filledIcon else outlinedIcon,
                                contentDescription = label
                            )
                        },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main views based on tab index
            when (selectedTab) {
                0 -> ChatsTab(filteredChats, viewModel)
                1 -> GroupsTab(filteredGroups, viewModel)
                2 -> CallsTab(callsList, viewModel)
                3 -> FriendsTab(viewModel)
                4 -> AlertsTab(notificationsList)
            }
        }
    }

    // CREATE GROUP DIALOG
    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create New Group") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth().testTag("group_name_input")
                    )

                    OutlinedTextField(
                        value = newGroupDesc,
                        onValueChange = { newGroupDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth().testTag("group_desc_input")
                    )

                    Text("Select Members:", fontWeight = FontWeight.Bold)

                    LazyColumn(
                        modifier = Modifier.height(150.dp)
                    ) {
                        items(viewModel.usersList.value) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedMembers.contains(user.id)) {
                                            selectedMembers.remove(user.id)
                                        } else {
                                            selectedMembers.add(user.id)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedMembers.contains(user.id),
                                    onCheckedChange = {
                                        if (it == true) selectedMembers.add(user.id)
                                        else selectedMembers.remove(user.id)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(user.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotEmpty()) {
                            viewModel.createGroup(newGroupName, newGroupDesc, selectedMembers.toList())
                            showCreateGroupDialog = false
                            newGroupName = ""
                            newGroupDesc = ""
                            selectedMembers.clear()
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChatsTab(chats: List<ChatEntity>, viewModel: ChatViewModel) {
    ChatListScreen(
        viewModel = viewModel,
        onChatClick = { chat ->
            viewModel.navigateTo(AppScreen.ChatDetail(chat.id, chat.name, isGroup = false))
        }
    )
}

@Composable
fun GroupsTab(groups: List<ChatEntity>, viewModel: ChatViewModel) {
    if (groups.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.GroupWork,
            title = "No Groups Created",
            subtitle = "Tap the Group Add FAB to create a customized chat group."
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(groups) { group ->
                ChatListItem(chat = group) {
                    viewModel.navigateTo(AppScreen.ChatDetail(group.id, group.name, isGroup = true))
                }
            }
        }
    }
}

@Composable
fun CallsTab(calls: List<CallEntity>, viewModel: ChatViewModel) {
    if (calls.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CallEnd,
            title = "Call Log Empty",
            subtitle = "Initiate voice or video calling to build communication stats!"
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(calls) { call ->
                CallListItem(call = call, myUserId = viewModel.myUserId.value) {
                    val targetName = if (call.callerId == viewModel.myUserId.value) call.receiverName else call.callerName
                    val targetId = if (call.callerId == viewModel.myUserId.value) call.receiverId else call.callerId
                    viewModel.startCall(targetName, call.isVideo, targetId)
                }
            }
        }
    }
}

@Composable
fun AlertsTab(notifications: List<com.example.data.database.NotificationEntity>) {
    if (notifications.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.NotificationsNone,
            title = "No Notifications",
            subtitle = "Updates and system broadcast announcements will appear here."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(notifications) { notif ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = notif.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = notif.body,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notif.timestamp)),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(chat: ChatEntity, onClick: () -> Unit) {
    val dateStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(chat.lastMessageTime))
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with initials
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (chat.isGroup) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
        ) {
            Text(
                text = chat.name.take(2).uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (chat.isGroup) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateStr,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessage,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    if (chat.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    if (chat.isMuted) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun CallListItem(call: CallEntity, myUserId: String, onClick: () -> Unit) {
    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(call.startTime))
    val callerLabel = if (call.callerId == myUserId) call.receiverName else call.callerName
    val isMissed = call.status == "MISSED"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Icon(
                imageVector = if (call.isVideo) Icons.Default.VideoCall else Icons.Default.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = callerLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (call.callerId == myUserId) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (isMissed) MaterialTheme.colorScheme.error else Color.Green,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$dateStr • ${if (isMissed) "Missed" else "${call.durationSec}s"}",
                    fontSize = 13.sp,
                    color = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "Call Back",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FriendsTab(viewModel: ChatViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val myUserId by viewModel.myUserId.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    val chatsList by viewModel.chats.collectAsState()
    val usersList = viewModel.usersList.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<com.example.data.database.UserEntity?>(null) }
    var searchPerformed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. My ID Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "My Unique User ID",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = myUserId,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("my_user_id_text")
                    )
                }
                IconButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("ConnectChat ID", myUserId)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "User ID copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // 2. Search ID Section
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Friend by User ID",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("e.g. @john_doe, @alice_smith") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("friend_search_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Button(
                        onClick = {
                            val cleanQuery = searchQuery.trim().replace("@", "")
                            val matchedUser = usersList.value.find { it.id.equals(cleanQuery, ignoreCase = true) }
                            if (matchedUser != null) {
                                searchResult = matchedUser
                                errorMessage = ""
                            } else {
                                searchResult = null
                                errorMessage = "User ID @$cleanQuery not found in directory."
                            }
                            searchPerformed = true
                            successMessage = ""
                        },
                        modifier = Modifier.testTag("friend_search_button")
                    ) {
                        Text("Search")
                    }
                }

                // Search Results
                if (searchPerformed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (searchResult != null) {
                        val resultUser = searchResult!!
                        val isAlreadyChatting = chatsList.any { it.id == resultUser.id && !it.isGroup }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(
                                    text = resultUser.name.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = resultUser.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "@${resultUser.id}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = resultUser.bio,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }

                            if (isAlreadyChatting) {
                                Text(
                                    text = "Friends",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.sendFriendRequest(
                                            receiverId = resultUser.id,
                                            onSuccess = {
                                                successMessage = "Friend request sent to @${resultUser.id}! They will auto-accept in 3 seconds."
                                                errorMessage = ""
                                                searchPerformed = false
                                                searchQuery = ""
                                            },
                                            onError = { err ->
                                                errorMessage = err
                                                successMessage = ""
                                            }
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("send_request_button")
                                ) {
                                    Text("Add", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    if (errorMessage.isNotEmpty()) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    if (successMessage.isNotEmpty()) {
                        Text(successMessage, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        }

        // 3. Pending Friend Requests (Received)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Pending Friend Requests (${pendingRequests.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (pendingRequests.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No pending requests. Try searching or invite friends!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                pendingRequests.forEach { req ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
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
                                    text = req.senderName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "@${req.senderId}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.acceptFriendRequest(req.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Accept", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.declineFriendRequest(req.id) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Decline", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Friends Directory
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val directFriends = chatsList.filter { !it.isGroup && it.id != "gemini_assistant" }

            Text(
                text = "My Friends (${directFriends.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (directFriends.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Your friends list is empty. Add users by their ID above to connect and start secure messaging!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                directFriends.forEach { friend ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.navigateTo(AppScreen.ChatDetail(friend.id, friend.name, isGroup = false))
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Text(
                                    text = friend.name.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = friend.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "@${friend.id} • Active Chat",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "Chat Now",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
