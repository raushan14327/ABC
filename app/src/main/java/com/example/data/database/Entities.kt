package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val photoUrl: String,
    val bio: String,
    val statusText: String,
    val isOnline: Boolean,
    val lastSeen: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val senderId: String,
    val text: String,
    val mediaUrl: String? = null,
    val mediaType: String? = null, // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // "SENT", "DELIVERED", "SEEN"
    val isStarred: Boolean = false,
    val replyToMessageId: Long? = null,
    val reactions: String = "" // Comma-separated list of reactions (e.g. "👍,❤️")
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isGroup: Boolean,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val wallpaperPath: String? = null
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val createdBy: String,
    val createdTime: Long = System.currentTimeMillis(),
    val groupIcon: String,
    val adminIds: String, // Comma-separated user IDs
    val memberIds: String // Comma-separated user IDs
)

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey val id: String,
    val callerId: String,
    val callerName: String,
    val receiverId: String,
    val receiverName: String,
    val isVideo: Boolean,
    val status: String, // "MISSED", "REJECTED", "ACCEPTED", "COMPLETED"
    val startTime: Long = System.currentTimeMillis(),
    val durationSec: Int = 0
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "media_files")
data class MediaFileEntity(
    @PrimaryKey val id: String,
    val url: String,
    val name: String,
    val sizeBytes: Long,
    val type: String,
    val uploadTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val userId: String,
    val isDarkMode: Boolean = false,
    val isTwoFactorEnabled: Boolean = false,
    val notificationsSoundEnabled: Boolean = true,
    val privacyStatus: String = "EVERYONE", // "EVERYONE", "CONTACTS", "NOBODY"
    val lastSeenPrivacy: String = "EVERYONE"
)

@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey val id: String, // format: "userId_blockedUserId"
    val userId: String,
    val blockedUserId: String
)

@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: String,
    val senderName: String,
    val receiverId: String,
    val status: String, // "PENDING", "ACCEPTED", "DECLINED"
    val timestamp: Long = System.currentTimeMillis()
)

