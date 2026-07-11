package com.example.data.database

data class UserEntity(
    val id: String,
    val name: String,
    val photoUrl: String,
    val bio: String,
    val statusText: String,
    val isOnline: Boolean,
    val lastSeen: Long
) {
    // Empty constructor for Firestore serialization
    constructor() : this("", "", "", "", "", true, 0)
}

data class MessageEntity(
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
    val reactions: String = "" // Comma-separated list of reactions (e.g. "👍,❤️")
) {
    // Empty constructor for Firestore serialization
    constructor() : this(0, "", "", "", null, null, 0, "SENT", false, null, "")
}

data class ChatEntity(
    val id: String,
    val name: String,
    val isGroup: Boolean,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val wallpaperPath: String? = null
) {
    // Empty constructor for Firestore serialization
    constructor() : this("", "", false, "", 0, false, false, false, null)
}

data class GroupEntity(
    val id: String,
    val name: String,
    val description: String,
    val createdBy: String,
    val createdTime: Long = System.currentTimeMillis(),
    val groupIcon: String,
    val adminIds: String, // Comma-separated user IDs
    val memberIds: String // Comma-separated user IDs
) {
    // Empty constructor for Firestore serialization
    constructor() : this("", "", "", "", 0, "", "", "")
}

data class CallEntity(
    val id: String,
    val callerId: String,
    val callerName: String,
    val receiverId: String,
    val receiverName: String,
    val isVideo: Boolean,
    val status: String, // "MISSED", "REJECTED", "ACCEPTED", "COMPLETED"
    val startTime: Long = System.currentTimeMillis(),
    val durationSec: Int = 0
) {
    // Empty constructor for Firestore serialization
    constructor() : this("", "", "", "", "", false, "", 0, 0)
}

data class NotificationEntity(
    val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) {
    // Empty constructor for Firestore serialization
    constructor() : this("", "", "", "", 0, false)
}

data class MediaFileEntity(
    val id: String,
    val url: String,
    val name: String,
    val sizeBytes: Long,
    val type: String,
    val uploadTime: Long = System.currentTimeMillis()
) {
    // Empty constructor for Firestore serialization
    constructor() : this("", "", "", 0, "", 0)
}

data class UserSettingsEntity(
    val userId: String,
    val isDarkMode: Boolean = false,
    val isTwoFactorEnabled: Boolean = false,
    val notificationsSoundEnabled: Boolean = true,
    val privacyStatus: String = "EVERYONE", // "EVERYONE", "CONTACTS", "NOBODY"
    val lastSeenPrivacy: String = "EVERYONE"
) {
    // Empty constructor for Firestore serialization
    constructor() : this("", false, false, true, "EVERYONE", "EVERYONE")
}

data class BlockedUserEntity(
    val id: String, // format: "userId_blockedUserId"
    val userId: String,
    val blockedUserId: String
) {
    // Empty constructor for Firestore serialization
    constructor() : this("", "", "")
}

data class FriendRequestEntity(
    val id: Long = 0,
    val senderId: String,
    val senderName: String,
    val receiverId: String,
    val status: String, // "PENDING", "ACCEPTED", "DECLINED"
    val timestamp: Long = System.currentTimeMillis()
) {
    // Empty constructor for Firestore serialization
    constructor() : this(0, "", "", "", "PENDING", 0)
}
