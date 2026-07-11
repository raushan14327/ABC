package com.example.data.model

data class Message(
    val id: Long = 0,
    val chatId: String,
    val senderId: String,
    val text: String,
    val mediaUrl: String? = null,
    val mediaType: String? = null, // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // "SENT", "DELIVERED", "SEEN"
    val isStarred: Boolean = false,
    val replyToMessageId: Long? = null,
    val reactions: String = ""
)
