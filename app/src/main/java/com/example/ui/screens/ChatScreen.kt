package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.database.MessageEntity
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String,
    chatName: String,
    isGroup: Boolean,
    onBackClick: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val messages by viewModel.activeMessages.collectAsState()
    val isDarkMode by viewModel.settings.collectAsState()
    val listState = rememberLazyListState()

    var selectedMessageForActions by remember { mutableStateOf<MessageEntity?>(null) }
    var replyingToMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isLocalSearchActive by remember { mutableStateOf(false) }

    val filteredMessages = if (isLocalSearchActive && searchQuery.isNotEmpty()) {
        messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    } else {
        messages
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isLocalSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search messages...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("local_chat_search")
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(
                                    text = chatName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(chatName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = if (chatId == "gemini_assistant") "Gemini AI Assistant" else "Online",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isLocalSearchActive) {
                            isLocalSearchActive = false
                            searchQuery = ""
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (!isLocalSearchActive) {
                        IconButton(onClick = { viewModel.startCall(chatName, isVideo = false, chatId) }) {
                            Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = Color.White)
                        }
                        IconButton(onClick = { viewModel.startCall(chatName, isVideo = true, chatId) }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White)
                        }
                        IconButton(onClick = { isLocalSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    if (isDarkMode.isDarkMode) Color(0xFF0F171C) else Color(0xFFE5DDD5)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                ) {
                    items(filteredMessages) { message ->
                        MessageBubble(
                            message = message,
                            isMe = message.senderId == "me",
                            isDark = isDarkMode.isDarkMode,
                            onLongClick = {
                                selectedMessageForActions = message
                            }
                        )
                    }
                }

                // Reply Preview
                if (replyingToMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${if (replyingToMessage!!.senderId == "me") "You" else chatName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = replyingToMessage!!.text,
                                fontSize = 13.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { replyingToMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Message text input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.SentimentSatisfied, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        TextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = false,
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f).testTag("message_input_field")
                        )

                        IconButton(onClick = { showAttachmentSheet = true }) {
                            Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = {
                            if (textInput.isNotEmpty()) {
                                viewModel.sendMessage(textInput, replyingToMessage?.id)
                                textInput = ""
                                replyingToMessage = null
                            } else {
                                viewModel.sendMediaMessage("AUDIO", "Voice note (0:10) 🎤")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp).testTag("message_send_btn")
                    ) {
                        Icon(
                            imageVector = if (textInput.isNotEmpty()) Icons.Default.Send else Icons.Default.Mic,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        AlertDialog(
            onDismissRequest = { showAttachmentSheet = false },
            title = { Text("Send Media File") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentIcon(Icons.Default.Description, "Document", Color(0xFF5F59F7)) {
                        viewModel.sendMediaMessage("DOCUMENT", "Document_File.pdf 📄")
                        showAttachmentSheet = false
                    }
                    AttachmentIcon(Icons.Default.Image, "Gallery", Color(0xFFE91E63)) {
                        viewModel.sendMediaMessage("IMAGE", "Shared_Picture.png 🖼️")
                        showAttachmentSheet = false
                    }
                    AttachmentIcon(Icons.Default.Videocam, "Video", Color(0xFF9C27B0)) {
                        viewModel.sendMediaMessage("VIDEO", "Video_Recording.mp4 📹")
                        showAttachmentSheet = false
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (selectedMessageForActions != null) {
        val msg = selectedMessageForActions!!
        AlertDialog(
            onDismissRequest = { selectedMessageForActions = null },
            title = { Text("Message Actions") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("👍", "❤️", "😂", "😮", "🙏").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 28.sp,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.addMessageReaction(msg.id, msg.reactions, emoji)
                                        selectedMessageForActions = null
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }

                    HorizontalDivider()

                    TextButton(
                        onClick = {
                            replyingToMessage = msg
                            selectedMessageForActions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row {
                            Icon(Icons.Default.Reply, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reply to message")
                        }
                    }

                    TextButton(
                        onClick = {
                            viewModel.starMessage(msg.id, !msg.isStarred)
                            selectedMessageForActions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row {
                            Icon(Icons.Default.Star, null, tint = if (msg.isStarred) Color.Yellow else Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (msg.isStarred) "Unstar" else "Star")
                        }
                    }

                    TextButton(
                        onClick = {
                            viewModel.deleteMessage(msg.id)
                            selectedMessageForActions = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row {
                            Icon(Icons.Default.Delete, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForActions = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun AttachmentIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
