package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.database.MessageEntity
import com.example.ui.theme.DarkBubbleMe
import com.example.ui.theme.DarkBubbleOther
import com.example.ui.theme.LightBubbleMe
import com.example.ui.theme.LightBubbleOther
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    isDark: Boolean,
    onLongClick: () -> Unit
) {
    val bubbleColor = if (isMe) {
        if (isDark) DarkBubbleMe else LightBubbleMe
    } else {
        if (isDark) DarkBubbleOther else LightBubbleOther
    }

    val contentColor = if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isMe) 12.dp else 0.dp,
                            bottomEnd = if (isMe) 0.dp else 12.dp
                        )
                    )
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
                    .padding(10.dp)
            ) {
                Column {
                    // Media elements (e.g. image, video, audio)
                    if (message.mediaUrl != null) {
                        if (message.mediaType == "IMAGE") {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = "Shared Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(bottom = 6.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (message.mediaType) {
                                        "VIDEO" -> Icons.Default.PlayCircle
                                        "AUDIO" -> Icons.Default.Headphones
                                        else -> Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message.text,
                                    fontSize = 13.sp,
                                    color = contentColor
                                )
                            }
                        }
                    }

                    // Main message text
                    if (message.mediaType != "AUDIO" && message.mediaType != "DOCUMENT" && message.mediaType != "VIDEO") {
                        Text(
                            text = message.text,
                            fontSize = 15.sp,
                            color = contentColor
                        )
                    }

                    // Status details
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp)
                    ) {
                        if (message.isStarred) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Yellow,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = timeFormat,
                            fontSize = 10.sp,
                            color = contentColor.copy(alpha = 0.6f)
                        )
                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = when (message.status) {
                                    "SENT" -> Icons.Default.Check
                                    "DELIVERED" -> Icons.Default.DoneAll
                                    "SEEN" -> Icons.Default.DoneAll
                                    else -> Icons.Default.Check
                                },
                                contentDescription = message.status,
                                tint = if (message.status == "SEEN") Color(0xFF53BDEB) else contentColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Emoji reactions
            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .offset(y = (-6).dp, x = if (isMe) (-4).dp else 4.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    message.reactions.split(",").forEach { react ->
                        Text(text = react, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
