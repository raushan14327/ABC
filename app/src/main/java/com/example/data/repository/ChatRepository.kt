package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.*
import com.example.data.encryption.MessageEncryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    
    val chatDao = database.chatDao()
    val messageDao = database.messageDao()
    val userDao = database.userDao()
    val groupDao = database.groupDao()
    val callDao = database.callDao()
    val notificationDao = database.notificationDao()
    val blockedUserDao = database.blockedUserDao()
    val settingsDao = database.settingsDao()

    val allChats: Flow<List<ChatEntity>> = chatDao.getAllChatsFlow()
    val allCalls: Flow<List<CallEntity>> = callDao.getAllCalls()
    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val prefs = context.getSharedPreferences("connect_chat_prefs", Context.MODE_PRIVATE)
    private var realtimeJob: Job? = null

    init {
        // Initialize database with some realistic seed data on first run
        repositoryScope.launch {
            try {
                seedDataIfEmpty()
            } catch (e: Exception) {
                Log.e("ChatRepository", "Error seeding database", e)
            }
        }
        val savedId = getSavedUserId()
        if (savedId != null && isSavedAuthenticated()) {
            startRealtimeConnection(savedId)
        }
    }

    // --- SharedPreferences Persistence for Authentication ---
    fun saveUserIdentity(userId: String, name: String, phone: String, bio: String) {
        prefs.edit().apply {
            putString("my_user_id", userId)
            putString("my_name", name)
            putString("my_phone", phone)
            putString("my_bio", bio)
            putBoolean("is_authenticated", true)
            apply()
        }
        // Start connection
        startRealtimeConnection(userId)
    }

    fun getSavedUserId(): String? = prefs.getString("my_user_id", null)
    fun getSavedName(): String = prefs.getString("my_name", "John") ?: "John"
    fun getSavedPhone(): String = prefs.getString("my_phone", "") ?: ""
    fun getSavedBio(): String = prefs.getString("my_bio", "Hey there! I am using ConnectChat.") ?: ""
    fun isSavedAuthenticated(): Boolean = prefs.getBoolean("is_authenticated", false)

    private suspend fun seedDataIfEmpty() {
        val existingChats = allChats.firstOrNull() ?: emptyList()
        if (existingChats.isNotEmpty()) return

        Log.d("ChatRepository", "Seeding database with ConnectChat demo data...")

        // 1. Insert Core Users
        val users = listOf(
            UserEntity("me", "You (Me)", "", "Feeling happy! ✨", "Available", true, System.currentTimeMillis()),
            UserEntity("gemini_assistant", "Gemini Assistant", "https://ai.google.dev/static/images/logo_gemini.png", "Official Gemini AI Partner", "Online", true, System.currentTimeMillis()),
            UserEntity("john_doe", "John Doe", "", "Code is life 💻", "Busy", true, System.currentTimeMillis()),
            UserEntity("alice_smith", "Alice Smith", "", "Living, laughing, loving", "Available", false, System.currentTimeMillis() - 3600000),
            UserEntity("mom", "Mom ❤️", "", "Family first", "At work", true, System.currentTimeMillis()),
            UserEntity("priya_sharma", "Priya Sharma", "", "Music & Coffee ☕🎵", "Online", true, System.currentTimeMillis()),
            UserEntity("amit_patel", "Amit Patel", "", "Exploring the world 🌍", "Available", true, System.currentTimeMillis())
        )
        userDao.insertUsers(users)

        // 2. Insert Default Chats (Only Gemini Assistant and Group are default, others require friend approval)
        val chats = listOf(
            ChatEntity("gemini_assistant", "Gemini Assistant", isGroup = false, lastMessage = "Welcome to ConnectChat! Let's talk.", lastMessageTime = System.currentTimeMillis() - 10000, isPinned = true),
            ChatEntity("android_dev_group", "Android Dev Team 🤖", isGroup = true, lastMessage = "Compose 1.8 is amazing!", lastMessageTime = System.currentTimeMillis() - 1200000)
        )
        for (chat in chats) {
            chatDao.insertChat(chat)
        }

        // 3. Insert Default Group
        val devGroup = GroupEntity(
            id = "android_dev_group",
            name = "Android Dev Team 🤖",
            description = "Official group for Android Jetpack Compose discussion.",
            createdBy = "john_doe",
            groupIcon = "",
            adminIds = "john_doe",
            memberIds = "me,john_doe,alice_smith"
        )
        groupDao.insertGroup(devGroup)

        // 4. Insert Messages for chats (encrypted text)
        val defaultMessages = listOf(
            MessageEntity(chatId = "gemini_assistant", senderId = "gemini_assistant", text = MessageEncryption.encrypt("Hello! I am Gemini Assistant, your smart companion. Let me know what you want to talk about!"), timestamp = System.currentTimeMillis() - 10000, status = "SEEN"),
            
            MessageEntity(chatId = "john_doe", senderId = "me", text = MessageEncryption.encrypt("Hey John!"), timestamp = System.currentTimeMillis() - 120000, status = "SEEN"),
            MessageEntity(chatId = "john_doe", senderId = "john_doe", text = MessageEncryption.encrypt("Are we on for soccer tonight? ⚽"), timestamp = System.currentTimeMillis() - 60000, status = "DELIVERED"),
            
            MessageEntity(chatId = "alice_smith", senderId = "alice_smith", text = MessageEncryption.encrypt("Hey, how's it going? Check out this beautiful photo!"), timestamp = System.currentTimeMillis() - 360000, status = "SEEN", mediaUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb", mediaType = "IMAGE"),
            
            MessageEntity(chatId = "mom", senderId = "mom", text = MessageEncryption.encrypt("Call me when you're free, honey."), timestamp = System.currentTimeMillis() - 720000, status = "SEEN"),
            
            MessageEntity(chatId = "android_dev_group", senderId = "john_doe", text = MessageEncryption.encrypt("Hey all, did you try Jetpack Compose edge-to-edge?"), timestamp = System.currentTimeMillis() - 1500000, status = "SEEN"),
            MessageEntity(chatId = "android_dev_group", senderId = "alice_smith", text = MessageEncryption.encrypt("Yes, it's super clean! Compose 1.8 is amazing!"), timestamp = System.currentTimeMillis() - 1200000, status = "SEEN")
        )
        for (msg in defaultMessages) {
            messageDao.insertMessage(msg)
        }

        // 5. Seeding Default Calls
        val defaultCalls = listOf(
            CallEntity("call_1", "john_doe", "John Doe", "me", "You (Me)", isVideo = false, status = "COMPLETED", startTime = System.currentTimeMillis() - 1800000, durationSec = 120),
            CallEntity("call_2", "me", "You (Me)", "alice_smith", "Alice Smith", isVideo = true, status = "MISSED", startTime = System.currentTimeMillis() - 3600000, durationSec = 0)
        )
        for (call in defaultCalls) {
            callDao.insertCall(call)
        }

        // 6. Seeding Default Settings
        val settings = UserSettingsEntity(
            userId = "me",
            isDarkMode = false,
            isTwoFactorEnabled = false,
            notificationsSoundEnabled = true,
            privacyStatus = "EVERYONE",
            lastSeenPrivacy = "EVERYONE"
        )
        settingsDao.insertSettings(settings)

        // 7. Seed initial notification
        notificationDao.insertNotification(
            NotificationEntity(
                id = "notif_1",
                userId = "me",
                title = "ConnectChat Installed!",
                body = "Welcome to your high-security end-to-end encrypted chat platform.",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- Message Access & Sending ---
    fun getMessagesFlow(chatId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForChat(chatId)
    }

    suspend fun sendMessage(
        chatId: String,
        text: String,
        mediaUrl: String? = null,
        mediaType: String? = null,
        replyToMessageId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        val encryptedText = MessageEncryption.encrypt(text)
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        val message = MessageEntity(
            chatId = chatId,
            senderId = cleanMyId,
            text = encryptedText,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            replyToMessageId = replyToMessageId,
            status = "SENT"
        )
        
        val messageId = messageDao.insertMessage(message)
        
        // Update last message in Chat List
        val lastMsgPreview = when {
            mediaType != null -> "📁 $mediaType"
            else -> text
        }
        chatDao.updateLastMessage(chatId, lastMsgPreview, System.currentTimeMillis())

        // Handle Realtime network publishing or Gemini assistant locally
        if (chatId == "gemini_assistant") {
            handleGeminiResponse(text)
        } else {
            val cleanChatId = chatId.replace("@", "")
            val isGroup = chatId.startsWith("group_") || chatId == "android_dev_group"
            
            val payload = JSONObject().apply {
                put("messageId", messageId)
                put("senderId", cleanMyId)
                put("senderName", getSavedName())
                put("text", encryptedText)
                put("timestamp", System.currentTimeMillis())
            }
            
            if (isGroup) {
                payload.put("groupId", chatId)
                val groupTopic = "connectchat_v1_msg_group_${chatId.replace("group_", "")}"
                publishToNtfy(groupTopic, payload)
            } else {
                val peerTopic = "connectchat_v1_msg_$cleanChatId"
                publishToNtfy(peerTopic, payload)
            }
        }

        messageId
    }

    private suspend fun handleGeminiResponse(userMessage: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val chat = chatDao.getChatById("gemini_assistant") ?: return
        
        // Simulate Typing Indicator by setting Gemini status to Typing
        userDao.updateUserStatus("gemini_assistant", isOnline = true, System.currentTimeMillis())
        
        val responseText = if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            delay(2000)
            "Hello! I am the Gemini AI Assistant.\n\n" +
            "To unlock real-time intelligence and dynamic replies from me, please enter your actual **Gemini API Key** in the **Secrets Panel** inside AI Studio with the name `GEMINI_API_KEY`.\n\n" +
            "Currently running in Offline Safe Mode. Try asking me other questions!"
        } else {
            // Call Gemini API REST
            val result = callGeminiApiRest(userMessage, apiKey)
            result
        }

        val incomingMsg = MessageEntity(
            chatId = "gemini_assistant",
            senderId = "gemini_assistant",
            text = MessageEncryption.encrypt(responseText),
            status = "SEEN"
        )
        messageDao.insertMessage(incomingMsg)
        chatDao.updateLastMessage("gemini_assistant", responseText, System.currentTimeMillis())
        
        // Notify
        notificationDao.insertNotification(
            NotificationEntity(
                id = "notif_${System.currentTimeMillis()}",
                userId = "me",
                title = "Gemini Assistant",
                body = if (responseText.length > 50) responseText.take(50) + "..." else responseText,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private suspend fun callGeminiApiRest(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "You are the smart Gemini Chat Assistant inside ConnectChat, a high-fidelity messaging app. Be friendly, helpful, short, and use emojis like a messenger contact. User says: $prompt")
                }))
            }))
        }

        val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Gemini service had an issue (Code: ${response.code}). Check your API Key."
                }
                val bodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(bodyStr)
                val text = responseJson
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                text
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Oops, I had trouble connecting to the network: ${e.localizedMessage}"
        }
    }

    // --- Message Interactions ---
    suspend fun starMessage(messageId: Long, isStarred: Boolean) {
        messageDao.updateMessageStarred(messageId, isStarred)
    }

    suspend fun addReaction(messageId: Long, currentReactions: String, newReaction: String) {
        val updated = if (currentReactions.isEmpty()) {
            newReaction
        } else if (currentReactions.contains(newReaction)) {
            // Remove reaction if already present
            currentReactions.replace(newReaction, "").replace(",,", ",").trim(',')
        } else {
            "$currentReactions,$newReaction"
        }
        messageDao.updateMessageReactions(messageId, updated)
    }

    suspend fun deleteMessageForMe(messageId: Long) {
        messageDao.deleteMessage(messageId)
    }

    suspend fun deleteMessageForEveryone(messageId: Long, chatId: String) {
        // Update database item to show "This message was deleted"
        val deletedText = MessageEncryption.encrypt("🚫 This message was deleted")
        val entity = messageDao.getStarredMessages() // temporary wait/get
        messageDao.updateMessageStatus(messageId, "DELETED")
        // Overwrite text to signify deleted for everyone
        val currentMsgs = messageDao.getLatestMessageForChat(chatId)
        if (currentMsgs?.id == messageId) {
            chatDao.updateLastMessage(chatId, "🚫 Message deleted", System.currentTimeMillis())
        }
        messageDao.deleteMessage(messageId)
    }

    suspend fun clearChat(chatId: String) {
        messageDao.clearChatHistory(chatId)
        chatDao.updateLastMessage(chatId, "No messages yet", System.currentTimeMillis())
    }

    // --- Wallpaper and Settings ---
    suspend fun updateChatWallpaper(chatId: String, wallpaperPath: String?) {
        chatDao.updateWallpaper(chatId, wallpaperPath)
    }

    suspend fun getSettings(): UserSettingsEntity {
        return settingsDao.getSettingsDirect("me") ?: UserSettingsEntity("me")
    }

    suspend fun saveSettings(settings: UserSettingsEntity) {
        settingsDao.insertSettings(settings)
    }

    // --- Calls ---
    suspend fun logCall(callerId: String, callerName: String, receiverId: String, receiverName: String, isVideo: Boolean, status: String, durationSec: Int) {
        val call = CallEntity(
            id = "call_${System.currentTimeMillis()}",
            callerId = callerId,
            callerName = callerName,
            receiverId = receiverId,
            receiverName = receiverName,
            isVideo = isVideo,
            status = status,
            durationSec = durationSec
        )
        callDao.insertCall(call)
    }

    // --- Groups ---
    suspend fun createGroup(name: String, description: String, memberIds: List<String>) {
        val id = "group_${System.currentTimeMillis()}"
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        val membersStr = (cleanMyId + "," + memberIds.joinToString(",")).trim(',')
        val group = GroupEntity(
            id = id,
            name = name,
            description = description,
            createdBy = cleanMyId,
            groupIcon = "",
            adminIds = cleanMyId,
            memberIds = membersStr
        )
        groupDao.insertGroup(group)

        // Create Chat Entity
        val chat = ChatEntity(
            id = id,
            name = name,
            isGroup = true,
            lastMessage = "Group created successfully!",
            lastMessageTime = System.currentTimeMillis()
        )
        chatDao.insertChat(chat)
    }

    // --- Blocks ---
    suspend fun blockUser(blockedUserId: String) {
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        blockedUserDao.blockUser(BlockedUserEntity("${cleanMyId}_$blockedUserId", cleanMyId, blockedUserId))
    }

    suspend fun unblockUser(blockedUserId: String) {
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        blockedUserDao.unblockUser(cleanMyId, blockedUserId)
    }

    suspend fun isUserBlocked(blockedUserId: String): Boolean {
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        return blockedUserDao.isUserBlocked(cleanMyId, blockedUserId)
    }

    // --- Real-Time Pub-Sub Server Subscriptions (ntfy.sh) ---
    fun startRealtimeConnection(myId: String) {
        realtimeJob?.cancel()
        realtimeJob = repositoryScope.launch {
            val cleanMyId = myId.trim().replace("@", "")

            // Periodically broadcast ourselves so other users can discover us
            launch {
                while (true) {
                    try {
                        broadcastDiscovery(myId)
                    } catch (e: Exception) {
                        Log.e("ChatRepository", "Error broadcasting discovery", e)
                    }
                    delay(30000) // every 30 seconds
                }
            }

            // Build subscription topics list
            val baseTopics = listOf(
                "connectchat_v1_discovery",
                "connectchat_v1_friendreq_$cleanMyId",
                "connectchat_v1_msg_$cleanMyId"
            )

            var delayMs = 1000L
            while (true) {
                try {
                    val groups = groupDao.getAllGroupsDirect()
                    val groupTopics = groups.map { "connectchat_v1_msg_group_${it.id.replace("group_", "")}" }
                    val allTopics = baseTopics + groupTopics
                    val topicsStr = allTopics.joinToString(",")

                    val url = "https://ntfy.sh/$topicsStr/json"
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    val client = OkHttpClient.Builder()
                        .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite timeout for long lived stream
                        .build()

                    Log.d("ChatRepository", "Opening SSE connection to ntfy.sh with topics: $topicsStr")

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Server returned code ${response.code}")
                        }

                        val reader = response.body?.charStream()?.buffered() ?: throw Exception("No response body")
                        delayMs = 1000L // Reset retry delay on success

                        var line = reader.readLine()
                        while (line != null) {
                            try {
                                handleIncomingServerMessage(line, myId)
                            } catch (e: Exception) {
                                Log.e("ChatRepository", "Error parsing SSE line", e)
                            }
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "SSE connection lost, retrying in ${delayMs / 1000}s", e)
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(60000L) // Exponential backoff
                }
            }
        }
    }

    private suspend fun broadcastDiscovery(myId: String) = withContext(Dispatchers.IO) {
        val cleanMyId = myId.trim().replace("@", "")
        val payload = JSONObject().apply {
            put("id", cleanMyId)
            put("name", getSavedName())
            put("bio", getSavedBio())
            put("statusText", "Online")
            put("isOnline", true)
            put("timestamp", System.currentTimeMillis())
        }
        publishToNtfy("connectchat_v1_discovery", payload)
    }

    private suspend fun publishToNtfy(topic: String, payload: JSONObject) = withContext(Dispatchers.IO) {
        try {
            val requestBody = payload.toString().toRequestBody("text/plain".toMediaType())
            val request = Request.Builder()
                .url("https://ntfy.sh/$topic")
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ChatRepository", "Failed to publish to $topic: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error publishing to $topic", e)
        }
    }

    private suspend fun handleIncomingServerMessage(line: String, myId: String) {
        val cleanMyId = myId.trim().replace("@", "")
        val sseObj = JSONObject(line)
        if (sseObj.optString("event") != "message") return

        val topic = sseObj.optString("topic")
        val messageStr = sseObj.optString("message")
        if (messageStr.isEmpty()) return

        val payload = try {
            JSONObject(messageStr)
        } catch (e: Exception) {
            return
        }

        when {
            topic == "connectchat_v1_discovery" -> {
                val userId = payload.optString("id")
                if (userId.isEmpty() || userId == cleanMyId) return

                val user = UserEntity(
                    id = userId,
                    name = payload.optString("name", "User"),
                    photoUrl = "",
                    bio = payload.optString("bio", ""),
                    statusText = payload.optString("statusText", "Online"),
                    isOnline = payload.optBoolean("isOnline", true),
                    lastSeen = payload.optLong("timestamp", System.currentTimeMillis())
                )
                userDao.insertUser(user)
            }

            topic == "connectchat_v1_friendreq_$cleanMyId" -> {
                val type = payload.optString("type")
                val requestId = payload.optLong("requestId", 0L)
                val senderId = payload.optString("senderId")
                val senderName = payload.optString("senderName")
                val receiverId = payload.optString("receiverId")
                val timestamp = payload.optLong("timestamp", System.currentTimeMillis())

                when (type) {
                    "REQUEST" -> {
                        val existing = friendRequestDao.getFriendRequestBetween(senderId, cleanMyId)
                        if (existing == null || existing.status != "ACCEPTED") {
                            val request = FriendRequestEntity(
                                id = requestId,
                                senderId = senderId,
                                senderName = senderName,
                                receiverId = cleanMyId,
                                status = "PENDING",
                                timestamp = timestamp
                            )
                            friendRequestDao.insertFriendRequest(request)

                            val existingUser = userDao.getUserById(senderId)
                            if (existingUser == null) {
                                userDao.insertUser(
                                    UserEntity(senderId, senderName, "", "Hey there! Let's chat.", "Online", true, System.currentTimeMillis())
                                )
                            }

                            notificationDao.insertNotification(
                                NotificationEntity(
                                    id = "notif_${System.currentTimeMillis()}",
                                    userId = "me",
                                    title = "Friend Request Received",
                                    body = "$senderName wants to connect with you!",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    "ACCEPTED" -> {
                        val requests = friendRequestDao.getAllFriendRequestsFlow(cleanMyId).firstOrNull() ?: emptyList()
                        val req = requests.find { it.senderId == cleanMyId && it.receiverId == senderId }
                        if (req != null) {
                            friendRequestDao.updateFriendRequestStatus(req.id, "ACCEPTED")
                        } else {
                            val newReq = FriendRequestEntity(
                                id = requestId,
                                senderId = cleanMyId,
                                senderName = getSavedName(),
                                receiverId = senderId,
                                status = "ACCEPTED",
                                timestamp = timestamp
                            )
                            friendRequestDao.insertFriendRequest(newReq)
                        }

                        // Create Chat
                        val chat = ChatEntity(
                            id = senderId,
                            name = senderName,
                            isGroup = false,
                            lastMessage = "You are now connected! Say hello. 👋",
                            lastMessageTime = timestamp
                        )
                        chatDao.insertChat(chat)

                        // Seed initial message
                        val welcomeMsg = MessageEntity(
                            chatId = senderId,
                            senderId = senderId,
                            text = MessageEncryption.encrypt("Thanks for accepting my request! Let's chat. 😊"),
                            timestamp = timestamp,
                            status = "DELIVERED"
                        )
                        messageDao.insertMessage(welcomeMsg)

                        notificationDao.insertNotification(
                            NotificationEntity(
                                id = "notif_${System.currentTimeMillis()}",
                                userId = "me",
                                title = "Friend Request Accepted",
                                body = "$senderName accepted your friend request!",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    "DECLINED" -> {
                        val requests = friendRequestDao.getAllFriendRequestsFlow(cleanMyId).firstOrNull() ?: emptyList()
                        val req = requests.find { it.senderId == cleanMyId && it.receiverId == senderId }
                        if (req != null) {
                            friendRequestDao.updateFriendRequestStatus(req.id, "DECLINED")
                        }
                    }
                }
            }

            topic == "connectchat_v1_msg_$cleanMyId" -> {
                val senderId = payload.optString("senderId")
                val senderName = payload.optString("senderName")
                val text = payload.optString("text")
                val timestamp = payload.optLong("timestamp", System.currentTimeMillis())

                if (senderId.isEmpty() || senderId == cleanMyId) return
                if (isUserBlocked(senderId)) return

                val msg = MessageEntity(
                    chatId = senderId,
                    senderId = senderId,
                    text = text,
                    timestamp = timestamp,
                    status = "DELIVERED"
                )
                messageDao.insertMessage(msg)

                val decrypted = MessageEncryption.decrypt(text)

                val chat = chatDao.getChatById(senderId)
                if (chat != null) {
                    chatDao.updateLastMessage(senderId, decrypted, timestamp)
                } else {
                    val newChat = ChatEntity(
                        id = senderId,
                        name = senderName,
                        isGroup = false,
                        lastMessage = decrypted,
                        lastMessageTime = timestamp
                    )
                    chatDao.insertChat(newChat)
                }

                notificationDao.insertNotification(
                    NotificationEntity(
                        id = "notif_${System.currentTimeMillis()}",
                        userId = "me",
                        title = senderName,
                        body = decrypted,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            topic.startsWith("connectchat_v1_msg_group_") -> {
                val groupId = payload.optString("groupId")
                val senderId = payload.optString("senderId")
                val senderName = payload.optString("senderName")
                val text = payload.optString("text")
                val timestamp = payload.optLong("timestamp", System.currentTimeMillis())

                if (senderId == cleanMyId) return

                val msg = MessageEntity(
                    chatId = groupId,
                    senderId = senderId,
                    text = text,
                    timestamp = timestamp,
                    status = "DELIVERED"
                )
                messageDao.insertMessage(msg)

                val decrypted = MessageEncryption.decrypt(text)
                chatDao.updateLastMessage(groupId, "$senderName: $decrypted", timestamp)

                notificationDao.insertNotification(
                    NotificationEntity(
                        id = "notif_${System.currentTimeMillis()}",
                        userId = "me",
                        title = "Group: " + (chatDao.getChatById(groupId)?.name ?: "Android Dev Team"),
                        body = "$senderName: $decrypted",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // --- Friend Request System ---
    val friendRequestDao = database.friendRequestDao()

    fun getFriendRequestsFlow(myId: String): Flow<List<FriendRequestEntity>> {
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        return friendRequestDao.getAllFriendRequestsFlow(cleanMyId)
    }

    fun getPendingRequestsFlow(myId: String): Flow<List<FriendRequestEntity>> {
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        return friendRequestDao.getPendingRequestsForMe(cleanMyId)
    }

    fun getAcceptedRequestsFlow(myId: String): Flow<List<FriendRequestEntity>> {
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        return friendRequestDao.getAcceptedFriendRequestsFlow(cleanMyId)
    }

    suspend fun sendFriendRequest(senderId: String, senderName: String, receiverId: String) = withContext(Dispatchers.IO) {
        val cleanReceiverId = receiverId.trim().replace("@", "")
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        
        // Find if user exists
        val receiver = userDao.getUserById(cleanReceiverId)
        val receiverName = receiver?.name ?: cleanReceiverId
        
        if (cleanReceiverId == cleanMyId) {
            throw IllegalArgumentException("You cannot send a friend request to yourself.")
        }
        
        // Check if already friends or pending
        val existing = friendRequestDao.getFriendRequestBetween(cleanMyId, cleanReceiverId)
        if (existing != null) {
            if (existing.status == "ACCEPTED") {
                throw IllegalArgumentException("You are already friends.")
            } else if (existing.status == "PENDING") {
                throw IllegalArgumentException("A friend request is already pending.")
            }
        }

        val requestTime = System.currentTimeMillis()
        val requestId = requestTime

        // Insert new friend request locally
        val request = FriendRequestEntity(
            id = requestId,
            senderId = cleanMyId,
            senderName = getSavedName(),
            receiverId = cleanReceiverId,
            status = "PENDING",
            timestamp = requestTime
        )
        friendRequestDao.insertFriendRequest(request)

        // Publish friend request over real-time server
        val payload = JSONObject().apply {
            put("type", "REQUEST")
            put("requestId", requestId)
            put("senderId", cleanMyId)
            put("senderName", getSavedName())
            put("receiverId", cleanReceiverId)
            put("timestamp", requestTime)
        }
        
        publishToNtfy("connectchat_v1_friendreq_$cleanReceiverId", payload)
    }

    suspend fun acceptFriendRequest(requestId: Long, myId: String) = withContext(Dispatchers.IO) {
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        val requests = friendRequestDao.getAllFriendRequestsFlow(cleanMyId).firstOrNull() ?: emptyList()
        val request = requests.find { it.id == requestId } ?: return@withContext
        
        friendRequestDao.updateFriendRequestStatus(requestId, "ACCEPTED")
        
        // Create Chat Entity
        val chat = ChatEntity(
            id = request.senderId,
            name = request.senderName,
            isGroup = false,
            lastMessage = "You are now connected! Say hello. 👋",
            lastMessageTime = System.currentTimeMillis()
        )
        chatDao.insertChat(chat)

        // Seed initial friendly message
        val welcomeMsg = MessageEntity(
            chatId = request.senderId,
            senderId = request.senderId,
            text = MessageEncryption.encrypt("Thanks for accepting my request! Let's chat. 😊"),
            timestamp = System.currentTimeMillis(),
            status = "DELIVERED"
        )
        messageDao.insertMessage(welcomeMsg)

        // Publish acceptance payload
        val payload = JSONObject().apply {
            put("type", "ACCEPTED")
            put("requestId", requestId)
            put("senderId", cleanMyId)
            put("senderName", getSavedName())
            put("receiverId", request.senderId)
            put("timestamp", System.currentTimeMillis())
        }
        
        publishToNtfy("connectchat_v1_friendreq_${request.senderId}", payload)
    }

    suspend fun declineFriendRequest(requestId: Long) = withContext(Dispatchers.IO) {
        val cleanMyId = getSavedUserId()?.trim()?.replace("@", "") ?: "me"
        val requests = friendRequestDao.getAllFriendRequestsFlow(cleanMyId).firstOrNull() ?: emptyList()
        val request = requests.find { it.id == requestId } ?: return@withContext
        
        friendRequestDao.updateFriendRequestStatus(requestId, "DECLINED")

        // Publish decline payload
        val payload = JSONObject().apply {
            put("type", "DECLINED")
            put("requestId", requestId)
            put("senderId", cleanMyId)
            put("senderName", getSavedName())
            put("receiverId", request.senderId)
            put("timestamp", System.currentTimeMillis())
        }
        
        publishToNtfy("connectchat_v1_friendreq_${request.senderId}", payload)
    }
}

