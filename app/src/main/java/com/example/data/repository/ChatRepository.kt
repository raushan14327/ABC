package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.*
import com.example.data.encryption.MessageEncryption
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FirestoreUserDao(private val firestore: FirebaseFirestore) {
    fun getAllUsers(): Flow<List<UserEntity>> {
        return callbackFlow {
            val listener = firestore.collection("users")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                UserEntity(
                                    id = doc.getString("id") ?: doc.id,
                                    name = doc.getString("name") ?: "User",
                                    photoUrl = doc.getString("photoUrl") ?: "",
                                    bio = doc.getString("bio") ?: "",
                                    statusText = doc.getString("statusText") ?: "Online",
                                    isOnline = doc.getBoolean("isOnline") ?: false,
                                    lastSeen = doc.getLong("lastSeen") ?: 0L
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        trySend(list)
                    }
                }
            awaitClose { listener.remove() }
        }
    }

    suspend fun insertUser(user: UserEntity) {
        val userMap = hashMapOf(
            "id" to user.id,
            "name" to user.name,
            "photoUrl" to user.photoUrl,
            "bio" to user.bio,
            "statusText" to user.statusText,
            "isOnline" to user.isOnline,
            "lastSeen" to user.lastSeen
        )
        Tasks.await(firestore.collection("users").document(user.id).set(userMap))
    }
}

class FirestoreChatDao(private val firestore: FirebaseFirestore) {
    suspend fun getChatById(chatId: String): ChatEntity? {
        val doc = Tasks.await(firestore.collection("chats").document(chatId).get())
        if (!doc.exists()) return null
        return ChatEntity(
            id = doc.id,
            name = doc.getString("name") ?: "Chat",
            isGroup = doc.getBoolean("isGroup") ?: false,
            lastMessage = doc.getString("lastMessage") ?: "",
            lastMessageTime = doc.getLong("lastMessageTime") ?: System.currentTimeMillis(),
            isPinned = doc.getBoolean("isPinned") ?: false,
            isArchived = doc.getBoolean("isArchived") ?: false,
            isMuted = doc.getBoolean("isMuted") ?: false,
            wallpaperPath = doc.getString("wallpaperPath")
        )
    }
}

class FirestoreNotificationDao(private val firestore: FirebaseFirestore) {
    suspend fun insertNotification(notification: NotificationEntity) {
        val notifMap = hashMapOf(
            "id" to notification.id,
            "userId" to notification.userId,
            "title" to notification.title,
            "body" to notification.body,
            "timestamp" to notification.timestamp,
            "isRead" to notification.isRead
        )
        Tasks.await(firestore.collection("notifications").document(notification.id).set(notifMap))
    }
}

class ChatRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("connect_chat_prefs", Context.MODE_PRIVATE)
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Public DAO helper properties for ViewModel access
    val userDao = FirestoreUserDao(firestore)
    val chatDao = FirestoreChatDao(firestore)
    val notificationDao = FirestoreNotificationDao(firestore)

    // 1. Reactive Auth State Flow to drive subsequent Firestore subscriptions
    val authStateFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            trySend(uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // 2. Real-time Subscription to Chats collection
    val allChats: Flow<List<ChatEntity>> = authStateFlow.flatMapLatest { uid ->
        val currentUid = uid ?: getSavedUserId() ?: "me"
        if (currentUid == "me") {
            flowOf(emptyList())
        } else {
            // Trigger background seeding of default Gemini chat if not present
            repositoryScope.launch {
                try {
                    seedDefaultData(currentUid)
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Error seeding default data", e)
                }
            }

            callbackFlow {
                val listener = firestore.collection("chats")
                    .whereArrayContains("participants", currentUid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("ChatRepository", "allChats snapshot listener error", error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val list = snapshot.documents.mapNotNull { doc ->
                                try {
                                    ChatEntity(
                                        id = doc.id,
                                        name = doc.getString("name") ?: "Chat",
                                        isGroup = doc.getBoolean("isGroup") ?: false,
                                        lastMessage = doc.getString("lastMessage") ?: "",
                                        lastMessageTime = doc.getLong("lastMessageTime") ?: System.currentTimeMillis(),
                                        isPinned = doc.getBoolean("isPinned") ?: false,
                                        isArchived = doc.getBoolean("isArchived") ?: false,
                                        isMuted = doc.getBoolean("isMuted") ?: false,
                                        wallpaperPath = doc.getString("wallpaperPath")
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            trySend(list)
                        }
                    }
                awaitClose { listener.remove() }
            }
        }
    }

    // 3. Real-time Subscription to Calls log collection
    val allCalls: Flow<List<CallEntity>> = authStateFlow.flatMapLatest { uid ->
        val currentUid = uid ?: getSavedUserId() ?: "me"
        if (currentUid == "me") {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val listener1 = firestore.collection("calls")
                    .whereEqualTo("callerId", currentUid)
                    .addSnapshotListener { snapshot1, error1 ->
                        if (error1 != null) {
                            Log.e("ChatRepository", "allCalls list 1 error", error1)
                            return@addSnapshotListener
                        }
                        firestore.collection("calls")
                            .whereEqualTo("receiverId", currentUid)
                            .addSnapshotListener { snapshot2, error2 ->
                                if (error2 != null) {
                                    Log.e("ChatRepository", "allCalls list 2 error", error2)
                                    return@addSnapshotListener
                                }
                                val callsList = mutableListOf<CallEntity>()
                                val docs = (snapshot1?.documents ?: emptyList()) + (snapshot2?.documents ?: emptyList())
                                val distinctDocs = docs.distinctBy { it.id }
                                for (doc in distinctDocs) {
                                    try {
                                        callsList.add(
                                            CallEntity(
                                                id = doc.id,
                                                callerId = doc.getString("callerId") ?: "",
                                                callerName = doc.getString("callerName") ?: "User",
                                                receiverId = doc.getString("receiverId") ?: "",
                                                receiverName = doc.getString("receiverName") ?: "User",
                                                isVideo = doc.getBoolean("isVideo") ?: false,
                                                status = doc.getString("status") ?: "COMPLETED",
                                                startTime = doc.getLong("startTime") ?: System.currentTimeMillis(),
                                                durationSec = doc.getLong("durationSec")?.toInt() ?: 0
                                            )
                                        )
                                    } catch (e: Exception) {
                                        // Ignore malformed
                                    }
                                }
                                callsList.sortByDescending { it.startTime }
                                trySend(callsList)
                            }
                    }
                awaitClose { }
            }
        }
    }

    // 4. Real-time Subscription to Notifications
    val allNotifications: Flow<List<NotificationEntity>> = authStateFlow.flatMapLatest { uid ->
        val currentUid = uid ?: getSavedUserId() ?: "me"
        if (currentUid == "me") {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val listener = firestore.collection("notifications")
                    .whereEqualTo("userId", currentUid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val list = snapshot.documents.mapNotNull { doc ->
                                try {
                                    NotificationEntity(
                                        id = doc.id,
                                        userId = doc.getString("userId") ?: "",
                                        title = doc.getString("title") ?: "",
                                        body = doc.getString("body") ?: "",
                                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                        isRead = doc.getBoolean("isRead") ?: false
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            trySend(list)
                        }
                    }
                awaitClose { listener.remove() }
            }
        }
    }

    // --- SharedPreferences Persistence backing local variables ---
    fun saveUserIdentity(userId: String, name: String, phone: String, bio: String) {
        prefs.edit().apply {
            putString("my_user_id", userId)
            putString("my_name", name)
            putString("my_phone", phone)
            putString("my_bio", bio)
            putBoolean("is_authenticated", true)
            apply()
        }
    }

    fun getSavedUserId(): String? = auth.currentUser?.uid ?: prefs.getString("my_user_id", null)
    fun getSavedName(): String = prefs.getString("my_name", "John") ?: "John"
    fun getSavedPhone(): String = prefs.getString("my_phone", "") ?: ""
    fun getSavedBio(): String = prefs.getString("my_bio", "Hey there! I am using ConnectChat.") ?: ""
    fun isSavedAuthenticated(): Boolean = auth.currentUser != null || prefs.getBoolean("is_authenticated", false)

    // Seeding default chat & Gemini response on first login
    private suspend fun seedDefaultData(myUid: String) {
        val geminiRef = firestore.collection("users").document("gemini_assistant")
        val docGemini = Tasks.await(geminiRef.get())
        if (!docGemini.exists()) {
            val geminiUser = hashMapOf(
                "id" to "gemini_assistant",
                "name" to "Gemini Assistant",
                "photoUrl" to "https://ai.google.dev/static/images/logo_gemini.png",
                "bio" to "Official Gemini AI Partner",
                "statusText" to "Online",
                "isOnline" to true,
                "lastSeen" to System.currentTimeMillis()
            )
            Tasks.await(geminiRef.set(geminiUser))
        }

        val chatGeminiId = "chat_gemini_${myUid}"
        val chatRef = firestore.collection("chats").document(chatGeminiId)
        val docChat = Tasks.await(chatRef.get())
        if (!docChat.exists()) {
            val geminiChat = hashMapOf(
                "id" to chatGeminiId,
                "name" to "Gemini Assistant",
                "participants" to listOf(myUid, "gemini_assistant"),
                "isGroup" to false,
                "lastMessage" to "Welcome to ConnectChat! Let's talk.",
                "lastMessageTime" to System.currentTimeMillis() - 10000,
                "isPinned" to true,
                "isArchived" to false,
                "isMuted" to false,
                "wallpaperPath" to null
            )
            Tasks.await(chatRef.set(geminiChat))

            // Seed initial welcoming greeting message
            val welcomeMsgId = System.currentTimeMillis()
            val welcomeMsg = hashMapOf(
                "id" to welcomeMsgId,
                "chatId" to chatGeminiId,
                "senderId" to "gemini_assistant",
                "text" to MessageEncryption.encrypt("Hello! I am Gemini Assistant, your smart companion. Let me know what you want to talk about!"),
                "timestamp" to System.currentTimeMillis() - 10000,
                "status" to "SEEN",
                "isStarred" to false,
                "replyToMessageId" to null,
                "reactions" to ""
            )
            Tasks.await(chatRef.collection("messages").document(welcomeMsgId.toString()).set(welcomeMsg))
        }
    }

    // --- Message Access & Real-Time Snapshots ---
    fun getMessagesFlow(chatId: String): Flow<List<MessageEntity>> {
        return callbackFlow {
            val listener = firestore.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("ChatRepository", "getMessagesFlow error", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val messages = snapshot.documents.mapNotNull { doc ->
                            try {
                                MessageEntity(
                                    id = doc.getLong("id") ?: 0L,
                                    chatId = doc.getString("chatId") ?: "",
                                    senderId = doc.getString("senderId") ?: "",
                                    text = MessageEncryption.decrypt(doc.getString("text") ?: ""),
                                    mediaUrl = doc.getString("mediaUrl"),
                                    mediaType = doc.getString("mediaType"),
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                    status = doc.getString("status") ?: "SENT",
                                    isStarred = doc.getBoolean("isStarred") ?: false,
                                    replyToMessageId = doc.getLong("replyToMessageId"),
                                    reactions = doc.getString("reactions") ?: ""
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        trySend(messages)
                    }
                }
            awaitClose { listener.remove() }
        }
    }

    suspend fun sendMessage(
        chatId: String,
        text: String,
        mediaUrl: String? = null,
        mediaType: String? = null,
        replyToMessageId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        val messageId = System.currentTimeMillis()
        val encryptedText = MessageEncryption.encrypt(text)
        val cleanMyId = getSavedUserId() ?: "me"

        val message = hashMapOf(
            "id" to messageId,
            "chatId" to chatId,
            "senderId" to cleanMyId,
            "text" to encryptedText,
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "timestamp" to System.currentTimeMillis(),
            "status" to "SENT",
            "isStarred" to false,
            "replyToMessageId" to replyToMessageId,
            "reactions" to ""
        )

        // Write message document
        Tasks.await(
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId.toString())
                .set(message)
        )

        // Update Chat summary details
        val lastMsgPreview = when {
            mediaType != null -> "📁 $mediaType"
            else -> text
        }
        val updateChatMap = hashMapOf<String, Any>(
            "lastMessage" to lastMsgPreview,
            "lastMessageTime" to System.currentTimeMillis()
        )
        Tasks.await(firestore.collection("chats").document(chatId).update(updateChatMap))

        // Trigger AI helper response if talking to Gemini
        if (chatId.contains("gemini_assistant")) {
            repositoryScope.launch {
                try {
                    handleGeminiResponse(chatId, text)
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Gemini generation error", e)
                }
            }
        }

        messageId
    }

    private suspend fun handleGeminiResponse(chatId: String, userMessage: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        // Indicate typing
        firestore.collection("users").document("gemini_assistant")
            .update("statusText", "typing...")

        val responseText = if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            delay(2000)
            "Hello! I am the Gemini AI Assistant.\n\n" +
            "To unlock real-time intelligence and dynamic replies from me, please enter your actual **Gemini API Key** in the **Secrets Panel** inside AI Studio with the name `GEMINI_API_KEY`.\n\n" +
            "Currently running in Offline Safe Mode. Try asking me other questions!"
        } else {
            callGeminiApiRest(userMessage, apiKey)
        }

        val incomingMsgId = System.currentTimeMillis()
        val incomingMsg = hashMapOf(
            "id" to incomingMsgId,
            "chatId" to chatId,
            "senderId" to "gemini_assistant",
            "text" to MessageEncryption.encrypt(responseText),
            "timestamp" to System.currentTimeMillis(),
            "status" to "SEEN",
            "isStarred" to false,
            "replyToMessageId" to null,
            "reactions" to ""
        )

        // Write Gemini's answer
        Tasks.await(
            firestore.collection("chats").document(chatId)
                .collection("messages").document(incomingMsgId.toString())
                .set(incomingMsg)
        )
        Tasks.await(
            firestore.collection("chats").document(chatId)
                .update("lastMessage", responseText, "lastMessageTime", System.currentTimeMillis())
        )

        // Reset status
        firestore.collection("users").document("gemini_assistant")
            .update("statusText", "Online")

        // Send local notification
        val notif = NotificationEntity(
            id = "notif_${System.currentTimeMillis()}",
            userId = getSavedUserId() ?: "me",
            title = "Gemini Assistant",
            body = if (responseText.length > 50) responseText.take(50) + "..." else responseText,
            timestamp = System.currentTimeMillis()
        )
        notificationDao.insertNotification(notif)
    }

    private suspend fun callGeminiApiRest(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "You are the smart Gemini Chat Assistant inside ConnectChat. Be friendly, helpful, short, and use emojis like a messenger contact. User says: $prompt")
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
                    return@withContext "Gemini service had an issue (Code: ${response.code})."
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
            "Oops, I had trouble connecting to the network: ${e.localizedMessage}"
        }
    }

    // --- Message Interactions (using collectionGroup for universal matching) ---
    suspend fun starMessage(messageId: Long, isStarred: Boolean) = withContext(Dispatchers.IO) {
        try {
            val query = firestore.collectionGroup("messages").whereEqualTo("id", messageId)
            val snapshot = Tasks.await(query.get())
            for (doc in snapshot.documents) {
                Tasks.await(doc.reference.update("isStarred", isStarred))
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "starMessage failed", e)
        }
    }

    suspend fun addReaction(messageId: Long, currentReactions: String, newReaction: String) = withContext(Dispatchers.IO) {
        try {
            val updated = if (currentReactions.isEmpty()) {
                newReaction
            } else if (currentReactions.contains(newReaction)) {
                currentReactions.replace(newReaction, "").replace(",,", ",").trim(',')
            } else {
                "$currentReactions,$newReaction"
            }
            val query = firestore.collectionGroup("messages").whereEqualTo("id", messageId)
            val snapshot = Tasks.await(query.get())
            for (doc in snapshot.documents) {
                Tasks.await(doc.reference.update("reactions", updated))
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "addReaction failed", e)
        }
    }

    suspend fun deleteMessageForMe(messageId: Long) = withContext(Dispatchers.IO) {
        try {
            val query = firestore.collectionGroup("messages").whereEqualTo("id", messageId)
            val snapshot = Tasks.await(query.get())
            for (doc in snapshot.documents) {
                Tasks.await(doc.reference.delete())
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "deleteMessageForMe failed", e)
        }
    }

    suspend fun deleteMessageForEveryone(messageId: Long, chatId: String) = withContext(Dispatchers.IO) {
        try {
            val query = firestore.collectionGroup("messages").whereEqualTo("id", messageId)
            val snapshot = Tasks.await(query.get())
            for (doc in snapshot.documents) {
                Tasks.await(
                    doc.reference.update(
                        "status", "DELETED",
                        "text", MessageEncryption.encrypt("🚫 This message was deleted")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "deleteMessageForEveryone failed", e)
        }
    }

    suspend fun clearChat(chatId: String) = withContext(Dispatchers.IO) {
        try {
            val msgs = Tasks.await(firestore.collection("chats").document(chatId).collection("messages").get())
            for (doc in msgs.documents) {
                Tasks.await(doc.reference.delete())
            }
            Tasks.await(firestore.collection("chats").document(chatId).update("lastMessage", "No messages yet"))
        } catch (e: Exception) {
            Log.e("ChatRepository", "clearChat failed", e)
        }
    }

    suspend fun updateChatWallpaper(chatId: String, wallpaperPath: String?) {
        try {
            Tasks.await(firestore.collection("chats").document(chatId).update("wallpaperPath", wallpaperPath))
        } catch (e: Exception) {
            Log.e("ChatRepository", "updateChatWallpaper failed", e)
        }
    }

    suspend fun getSettings(): UserSettingsEntity = withContext(Dispatchers.IO) {
        val currentUid = getSavedUserId() ?: "me"
        try {
            val doc = Tasks.await(firestore.collection("user_settings").document(currentUid).get())
            if (doc.exists()) {
                UserSettingsEntity(
                    userId = currentUid,
                    isDarkMode = doc.getBoolean("isDarkMode") ?: false,
                    isTwoFactorEnabled = doc.getBoolean("isTwoFactorEnabled") ?: false,
                    notificationsSoundEnabled = doc.getBoolean("notificationsSoundEnabled") ?: true,
                    privacyStatus = doc.getString("privacyStatus") ?: "EVERYONE",
                    lastSeenPrivacy = doc.getString("lastSeenPrivacy") ?: "EVERYONE"
                )
            } else {
                UserSettingsEntity(currentUid)
            }
        } catch (e: Exception) {
            UserSettingsEntity(currentUid)
        }
    }

    suspend fun saveSettings(settings: UserSettingsEntity) = withContext(Dispatchers.IO) {
        val currentUid = getSavedUserId() ?: "me"
        val settingsMap = hashMapOf(
            "userId" to currentUid,
            "isDarkMode" to settings.isDarkMode,
            "isTwoFactorEnabled" to settings.isTwoFactorEnabled,
            "notificationsSoundEnabled" to settings.notificationsSoundEnabled,
            "privacyStatus" to settings.privacyStatus,
            "lastSeenPrivacy" to settings.lastSeenPrivacy
        )
        try {
            Tasks.await(firestore.collection("user_settings").document(currentUid).set(settingsMap))
        } catch (e: Exception) {
            Log.e("ChatRepository", "saveSettings failed", e)
        }
    }

    suspend fun logCall(
        callerId: String,
        callerName: String,
        receiverId: String,
        receiverName: String,
        isVideo: Boolean,
        status: String,
        durationSec: Int
    ) = withContext(Dispatchers.IO) {
        val id = "call_${System.currentTimeMillis()}"
        val callMap = hashMapOf(
            "id" to id,
            "callerId" to callerId,
            "callerName" to callerName,
            "receiverId" to receiverId,
            "receiverName" to receiverName,
            "isVideo" to isVideo,
            "status" to status,
            "startTime" to System.currentTimeMillis(),
            "durationSec" to durationSec
        )
        try {
            Tasks.await(firestore.collection("calls").document(id).set(callMap))
        } catch (e: Exception) {
            Log.e("ChatRepository", "logCall failed", e)
        }
    }

    suspend fun createGroup(name: String, description: String, memberIds: List<String>) = withContext(Dispatchers.IO) {
        val id = "group_${System.currentTimeMillis()}"
        val cleanMyId = getSavedUserId() ?: "me"
        val allMembers = (memberIds + cleanMyId).distinct()

        val groupChat = hashMapOf(
            "id" to id,
            "name" to name,
            "isGroup" to true,
            "participants" to allMembers,
            "lastMessage" to "Group created: $description",
            "lastMessageTime" to System.currentTimeMillis(),
            "adminUid" to cleanMyId
        )
        try {
            Tasks.await(firestore.collection("chats").document(id).set(groupChat))
        } catch (e: Exception) {
            Log.e("ChatRepository", "createGroup failed", e)
        }
    }

    suspend fun blockUser(userId: String) = withContext(Dispatchers.IO) {
        val currentUid = getSavedUserId() ?: "me"
        val id = "${currentUid}_$userId"
        val blockedMap = hashMapOf(
            "id" to id,
            "userId" to currentUid,
            "blockedUserId" to userId
        )
        try {
            Tasks.await(firestore.collection("blocked_users").document(id).set(blockedMap))
        } catch (e: Exception) {
            Log.e("ChatRepository", "blockUser failed", e)
        }
    }

    suspend fun unblockUser(userId: String) = withContext(Dispatchers.IO) {
        val currentUid = getSavedUserId() ?: "me"
        val id = "${currentUid}_$userId"
        try {
            Tasks.await(firestore.collection("blocked_users").document(id).delete())
        } catch (e: Exception) {
            Log.e("ChatRepository", "unblockUser failed", e)
        }
    }

    // --- Friend Requests & Search ---
    fun getFriendRequestsFlow(myId: String): Flow<List<FriendRequestEntity>> {
        return authStateFlow.flatMapLatest { uid ->
            val currentUid = uid ?: getSavedUserId() ?: "me"
            if (currentUid == "me") {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val listener = firestore.collection("friend_requests")
                        .whereEqualTo("receiverId", currentUid)
                        .whereEqualTo("status", "PENDING")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) return@addSnapshotListener
                            if (snapshot != null) {
                                val list = snapshot.documents.mapNotNull { doc ->
                                    try {
                                        FriendRequestEntity(
                                            id = doc.getLong("id") ?: 0L,
                                            senderId = doc.getString("senderId") ?: "",
                                            senderName = doc.getString("senderName") ?: "User",
                                            receiverId = doc.getString("receiverId") ?: "",
                                            status = doc.getString("status") ?: "PENDING",
                                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                        )
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                trySend(list)
                            }
                        }
                    awaitClose { listener.remove() }
                }
            }
        }
    }

    fun getPendingRequestsFlow(myId: String): Flow<List<FriendRequestEntity>> {
        return authStateFlow.flatMapLatest { uid ->
            val currentUid = uid ?: getSavedUserId() ?: "me"
            if (currentUid == "me") {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val listener = firestore.collection("friend_requests")
                        .whereEqualTo("senderId", currentUid)
                        .whereEqualTo("status", "PENDING")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) return@addSnapshotListener
                            if (snapshot != null) {
                                val list = snapshot.documents.mapNotNull { doc ->
                                    try {
                                        FriendRequestEntity(
                                            id = doc.getLong("id") ?: 0L,
                                            senderId = doc.getString("senderId") ?: "",
                                            senderName = doc.getString("senderName") ?: "User",
                                            receiverId = doc.getString("receiverId") ?: "",
                                            status = doc.getString("status") ?: "PENDING",
                                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                        )
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                trySend(list)
                            }
                        }
                    awaitClose { listener.remove() }
                }
            }
        }
    }

    suspend fun sendFriendRequest(senderId: String, senderName: String, receiverId: String) = withContext(Dispatchers.IO) {
        val currentUid = getSavedUserId() ?: "me"
        val cleanReceiverId = receiverId.replace("@", "").trim()
        val requestId = System.currentTimeMillis()

        val request = hashMapOf(
            "id" to requestId,
            "senderId" to currentUid,
            "senderName" to senderName,
            "receiverId" to cleanReceiverId,
            "status" to "PENDING",
            "timestamp" to System.currentTimeMillis()
        )
        try {
            Tasks.await(firestore.collection("friend_requests").document(requestId.toString()).set(request))
        } catch (e: Exception) {
            Log.e("ChatRepository", "sendFriendRequest failed", e)
        }
    }

    suspend fun acceptFriendRequest(requestId: Long, myId: String) = withContext(Dispatchers.IO) {
        val currentUid = getSavedUserId() ?: "me"
        try {
            // Update status to ACCEPTED
            Tasks.await(firestore.collection("friend_requests").document(requestId.toString()).update("status", "ACCEPTED"))

            // Retrieve details to build 1-to-1 chat document
            val doc = Tasks.await(firestore.collection("friend_requests").document(requestId.toString()).get())
            val senderId = doc.getString("senderId") ?: ""
            val senderName = doc.getString("senderName") ?: "User"

            val sortedUids = listOf(currentUid, senderId).sorted()
            val chatId = "chat_${sortedUids[0]}_${sortedUids[1]}"

            val chat = hashMapOf(
                "id" to chatId,
                "name" to senderName,
                "participants" to listOf(currentUid, senderId),
                "isGroup" to false,
                "lastMessage" to "You are now connected! Say hello.",
                "lastMessageTime" to System.currentTimeMillis()
            )
            Tasks.await(firestore.collection("chats").document(chatId).set(chat))
        } catch (e: Exception) {
            Log.e("ChatRepository", "acceptFriendRequest failed", e)
        }
    }

    suspend fun declineFriendRequest(requestId: Long) = withContext(Dispatchers.IO) {
        try {
            Tasks.await(firestore.collection("friend_requests").document(requestId.toString()).update("status", "DECLINED"))
        } catch (e: Exception) {
            Log.e("ChatRepository", "declineFriendRequest failed", e)
        }
    }
}
