package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("UPDATE users SET isOnline = :isOnline, lastSeen = :lastSeen WHERE id = :userId")
    suspend fun updateUserStatus(userId: String, isOnline: Boolean, lastSeen: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessageForChat(chatId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE text LIKE '%' || :query || '%'")
    suspend fun searchMessages(query: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isStarred = 1")
    fun getStarredMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET reactions = :reactions WHERE id = :messageId")
    suspend fun updateMessageReactions(messageId: Long, reactions: String)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: String)

    @Query("UPDATE messages SET isStarred = :isStarred WHERE id = :messageId")
    suspend fun updateMessageStarred(messageId: Long, isStarred: Boolean)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChatHistory(chatId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY isPinned DESC, lastMessageTime DESC")
    fun getAllChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("UPDATE chats SET lastMessage = :lastMessage, lastMessageTime = :lastMessageTime WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: String, lastMessage: String, lastMessageTime: Long)

    @Query("UPDATE chats SET isPinned = :isPinned WHERE id = :chatId")
    suspend fun updatePinned(chatId: String, isPinned: Boolean)

    @Query("UPDATE chats SET isArchived = :isArchived WHERE id = :chatId")
    suspend fun updateArchived(chatId: String, isArchived: Boolean)

    @Query("UPDATE chats SET isMuted = :isMuted WHERE id = :chatId")
    suspend fun updateMuted(chatId: String, isMuted: Boolean)

    @Query("UPDATE chats SET wallpaperPath = :wallpaperPath WHERE id = :chatId")
    suspend fun updateWallpaper(chatId: String, wallpaperPath: String?)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups")
    suspend fun getAllGroupsDirect(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Query("UPDATE groups SET memberIds = :memberIds WHERE id = :groupId")
    suspend fun updateMembers(groupId: String, memberIds: String)
}

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY startTime DESC")
    fun getAllCalls(): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)
}

@Dao
interface BlockedUserDao {
    @Query("SELECT * FROM blocked_users WHERE userId = :userId")
    fun getBlockedUsersFlow(userId: String): Flow<List<BlockedUserEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_users WHERE userId = :userId AND blockedUserId = :blockedUserId LIMIT 1)")
    suspend fun isUserBlocked(userId: String, blockedUserId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockUser(blockedUser: BlockedUserEntity)

    @Query("DELETE FROM blocked_users WHERE userId = :userId AND blockedUserId = :blockedUserId")
    suspend fun unblockUser(userId: String, blockedUserId: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    fun getSettingsFlow(userId: String): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE userId = :userId LIMIT 1")
    suspend fun getSettingsDirect(userId: String): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: UserSettingsEntity)
}

@Dao
interface FriendRequestDao {
    @Query("SELECT * FROM friend_requests WHERE senderId = :myId OR receiverId = :myId ORDER BY timestamp DESC")
    fun getAllFriendRequestsFlow(myId: String): Flow<List<FriendRequestEntity>>

    @Query("SELECT * FROM friend_requests WHERE receiverId = :myId AND status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingRequestsForMe(myId: String): Flow<List<FriendRequestEntity>>

    @Query("SELECT * FROM friend_requests WHERE (senderId = :myId OR receiverId = :myId) AND status = 'ACCEPTED'")
    fun getAcceptedFriendRequestsFlow(myId: String): Flow<List<FriendRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendRequest(friendRequest: FriendRequestEntity): Long

    @Query("UPDATE friend_requests SET status = :status WHERE id = :id")
    suspend fun updateFriendRequestStatus(id: Long, status: String)

    @Query("DELETE FROM friend_requests WHERE id = :id")
    suspend fun deleteFriendRequest(id: Long)
    
    @Query("SELECT * FROM friend_requests WHERE (senderId = :myId AND receiverId = :otherId) OR (senderId = :otherId AND receiverId = :myId) LIMIT 1")
    suspend fun getFriendRequestBetween(myId: String, otherId: String): FriendRequestEntity?
}

