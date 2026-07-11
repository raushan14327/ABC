package com.example.ui.viewmodel

import android.app.Application
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.encryption.MessageEncryption
import com.example.data.repository.ChatRepository
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

sealed class AppScreen {
    object Onboarding : AppScreen()
    object Login : AppScreen()
    object Signup : AppScreen()
    object Profile : AppScreen()
    object Dashboard : AppScreen()
    data class ChatDetail(val chatId: String, val chatName: String, val isGroup: Boolean) : AppScreen()
    data class GroupSettings(val groupId: String) : AppScreen()
    data class Calling(val callerName: String, val isVideo: Boolean, val isIncoming: Boolean, val chatId: String) : AppScreen()
    object AdminPanel : AppScreen()
    object Settings : AppScreen()
}

data class CallingState(
    val isActive: Boolean = false,
    val callerName: String = "",
    val isVideo: Boolean = false,
    val isIncoming: Boolean = false,
    val status: String = "RINGING", // "RINGING", "CONNECTING", "CONNECTED", "ENDED"
    val durationSec: Int = 0,
    val chatId: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val authRepository = AuthRepository(application)

    // Navigation & Screen management
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Onboarding)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Auth & Profile State
    val isAuthenticated = MutableStateFlow(false)
    val myUserId = MutableStateFlow("@user_connectchat")
    val myName = MutableStateFlow("John")
    val myPhone = MutableStateFlow("")
    val myBio = MutableStateFlow("Hey there! I am using ConnectChat.")
    val myProfilePic = MutableStateFlow("")

    // Real Firebase Authentication state (used by new Login/Signup screens)
    val authError = MutableStateFlow<String?>(null)
    val isAuthLoading = MutableStateFlow(false)

    // Database Flows
    val chats: StateFlow<List<ChatEntity>> = repository.allChats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calls: StateFlow<List<CallEntity>> = repository.allCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Friend Requests Flows
    val friendRequests: StateFlow<List<FriendRequestEntity>> = repository.getFriendRequestsFlow("me")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingRequests: StateFlow<List<FriendRequestEntity>> = repository.getPendingRequestsFlow("me")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Chat Message Flow
    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeMessages: StateFlow<List<MessageEntity>> = _activeChatId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMessagesFlow(id).map { msgs ->
                    msgs.map { it.copy(text = MessageEncryption.decrypt(it.text)) }
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Calling State
    private val _callingState = MutableStateFlow(CallingState())
    val callingState: StateFlow<CallingState> = _callingState.asStateFlow()

    // Search and wallpaper preferences
    val searchQuery = MutableStateFlow("")
    val activeChatWallpaper = MutableStateFlow<String?>(null)

    // User settings
    private val _settings = MutableStateFlow(UserSettingsEntity(""))
    val settings: StateFlow<UserSettingsEntity> = _settings.asStateFlow()

    // Group Member Addition Temp lists
    val usersList = MutableStateFlow<List<UserEntity>>(emptyList())

    // Admin Dashboard values
    val adminReportedUsers = MutableStateFlow(listOf(
        "Spam Bot #214" to "Reported for continuous spam messages of financial links",
        "TrollMaster9" to "Reported for aggressive and offensive language in group chats",
        "CryptoScammer" to "Reported for unsolicited private messaging to community members"
    ))

    private var callTimerHandler: Handler? = null
    private var callTimerRunnable: Runnable? = null

    init {
        viewModelScope.launch {
            // Load user settings
            val saved = repository.getSettings()
            _settings.value = saved

            // Load saved user identity if already authenticated
            if (repository.isSavedAuthenticated()) {
                val savedId = repository.getSavedUserId() ?: "@user_connectchat"
                myUserId.value = savedId
                myName.value = repository.getSavedName()
                myPhone.value = repository.getSavedPhone()
                myBio.value = repository.getSavedBio()
                isAuthenticated.value = true
                _currentScreen.value = AppScreen.Dashboard
            }

            // Fetch standard users list for group invite
            repository.userDao.getAllUsers().collect {
                usersList.value = it.filter { user -> user.id != myUserId.value && user.id != myUserId.value.replace("@", "") }
            }
        }
    }

    // Navigation Methods
    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen is AppScreen.ChatDetail) {
            _activeChatId.value = screen.chatId
            viewModelScope.launch {
                val chat = repository.chatDao.getChatById(screen.chatId)
                activeChatWallpaper.value = chat?.wallpaperPath
            }
        } else {
            _activeChatId.value = null
        }
    }

    fun navigateBack() {
        when (_currentScreen.value) {
            is AppScreen.ChatDetail, AppScreen.AdminPanel, AppScreen.Settings -> {
                navigateTo(AppScreen.Dashboard)
            }
            is AppScreen.GroupSettings -> {
                val current = _currentScreen.value as AppScreen.GroupSettings
                navigateTo(AppScreen.ChatDetail(current.groupId, "Group Details", true))
            }
            else -> {
                navigateTo(AppScreen.Dashboard)
            }
        }
    }

    // ---- REAL FIREBASE AUTHENTICATION ACTIONS ----
    // These replace the old fake completeOnboarding() flow for Login/Signup screens.

    fun signUpWithEmail(email: String, name: String, password: String) {
        viewModelScope.launch {
            isAuthLoading.value = true
            authError.value = null
            when (val result = authRepository.signUpWithEmail(email, name, password)) {
                is AuthRepository.AuthResult.Success -> {
                    myUserId.value = result.userId
                    myName.value = result.name
                    myPhone.value = result.emailOrPhone
                    myBio.value = "Hey there! I am using ConnectChat."
                    isAuthenticated.value = true
                    isAuthLoading.value = false
                    navigateTo(AppScreen.Dashboard)
                }
                is AuthRepository.AuthResult.Error -> {
                    authError.value = result.message
                    isAuthLoading.value = false
                }
                else -> {
                    isAuthLoading.value = false
                }
            }
        }
    }

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            isAuthLoading.value = true
            authError.value = null
            when (val result = authRepository.loginWithEmail(email, password)) {
                is AuthRepository.AuthResult.Success -> {
                    myUserId.value = result.userId
                    myName.value = result.name
                    myPhone.value = result.emailOrPhone
                    isAuthenticated.value = true
                    isAuthLoading.value = false
                    navigateTo(AppScreen.Dashboard)
                }
                is AuthRepository.AuthResult.Error -> {
                    authError.value = result.message
                    isAuthLoading.value = false
                }
                else -> {
                    isAuthLoading.value = false
                }
            }
        }
    }

    fun clearAuthError() {
        authError.value = null
    }

    fun logout() {
        authRepository.logout()
        isAuthenticated.value = false
        _currentScreen.value = AppScreen.Login
    }

    // Onboarding Actions (kept for backwards compatibility with any remaining callers)
    fun completeOnboarding(userId: String, name: String, phone: String, bio: String) {
        val cleanId = if (userId.startsWith("@")) userId else "@$userId"
        myUserId.value = cleanId
        myName.value = name
        myPhone.value = phone
        myBio.value = bio
        isAuthenticated.value = true
        
        viewModelScope.launch {
            // Save inside SharedPreferences and trigger subscription
            repository.saveUserIdentity(cleanId, name, phone, bio)
            
            repository.userDao.insertUser(
                UserEntity(cleanId, name, "", bio, "Online", true, System.currentTimeMillis())
            )
            navigateTo(AppScreen.Dashboard)
        }
    }

    // Friend Request Actions
    fun sendFriendRequest(receiverId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.sendFriendRequest(myUserId.value, myName.value, receiverId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to send friend request.")
            }
        }
    }

    fun acceptFriendRequest(requestId: Long) {
        viewModelScope.launch {
            try {
                repository.acceptFriendRequest(requestId, myUserId.value)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error accepting request", e)
            }
        }
    }

    fun declineFriendRequest(requestId: Long) {
        viewModelScope.launch {
            try {
                repository.declineFriendRequest(requestId)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error declining request", e)
            }
        }
    }

    // Message Actions
    fun sendMessage(text: String, replyToMessageId: Long? = null) {
        val chatId = _activeChatId.value ?: return
        if (text.trim().isEmpty()) return
        viewModelScope.launch {
            repository.sendMessage(chatId, text, replyToMessageId = replyToMessageId)
        }
    }

    fun sendMediaMessage(mediaType: String, contentDesc: String) {
        val chatId = _activeChatId.value ?: return
        val url = when (mediaType) {
            "IMAGE" -> "https://images.unsplash.com/photo-1579202673506-ca3ce28943ef"
            "VIDEO" -> "https://assets.mixkit.co/videos/preview/mixkit-stars-in-space-background-1611-large.mp4"
            "AUDIO" -> "voice_note_placeholder"
            else -> "document_attachment"
        }
        viewModelScope.launch {
            repository.sendMessage(chatId, contentDesc, mediaUrl = url, mediaType = mediaType)
        }
    }

    fun deleteMessage(messageId: Long) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            repository.deleteMessageForEveryone(messageId, chatId)
        }
    }

    fun starMessage(messageId: Long, isStarred: Boolean) {
        viewModelScope.launch {
            repository.starMessage(messageId, isStarred)
        }
    }

    fun addMessageReaction(messageId: Long, reactions: String, emoji: String) {
        viewModelScope.launch {
            repository.addReaction(messageId, reactions, emoji)
        }
    }

    fun clearActiveChat() {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            repository.clearChat(chatId)
        }
    }

    // Call Simulation Actions
    fun startCall(callerName: String, isVideo: Boolean, chatId: String) {
        _callingState.value = CallingState(
            isActive = true,
            callerName = callerName,
            isVideo = isVideo,
            isIncoming = false,
            status = "CONNECTING",
            chatId = chatId
        )
        navigateTo(AppScreen.Calling(callerName, isVideo, false, chatId))

        // Simulate outgoing connection delay
        viewModelScope.launch {
            delay(1500)
            if (_callingState.value.isActive && _callingState.value.status == "CONNECTING") {
                _callingState.value = _callingState.value.copy(status = "RINGING")
                delay(2000)
                if (_callingState.value.isActive && _callingState.value.status == "RINGING") {
                    acceptCall()
                }
            }
        }
    }

    fun receiveIncomingCall(callerName: String, isVideo: Boolean, chatId: String) {
        _callingState.value = CallingState(
            isActive = true,
            callerName = callerName,
            isVideo = isVideo,
            isIncoming = true,
            status = "RINGING",
            chatId = chatId
        )
        navigateTo(AppScreen.Calling(callerName, isVideo, true, chatId))
    }

    fun acceptCall() {
        _callingState.value = _callingState.value.copy(status = "CONNECTED")
        startCallTimer()
    }

    fun rejectOrEndCall() {
        val finalState = _callingState.value
        stopCallTimer()
        _callingState.value = _callingState.value.copy(status = "ENDED")
        
        viewModelScope.launch {
            // Log to database
            repository.logCall(
                callerId = if (finalState.isIncoming) finalState.chatId else myUserId.value,
                callerName = if (finalState.isIncoming) finalState.callerName else "You (Me)",
                receiverId = if (finalState.isIncoming) myUserId.value else finalState.chatId,
                receiverName = if (finalState.isIncoming) "You (Me)" else finalState.callerName,
                isVideo = finalState.isVideo,
                status = if (finalState.status == "CONNECTED") "COMPLETED" else if (finalState.isIncoming) "MISSED" else "REJECTED",
                durationSec = finalState.durationSec
            )
            delay(1000)
            _callingState.value = CallingState() // Reset
            navigateBack()
        }
    }

    private fun startCallTimer() {
        stopCallTimer()
        callTimerHandler = Handler(Looper.getMainLooper())
        callTimerRunnable = object : Runnable {
            override fun run() {
                _callingState.value = _callingState.value.copy(
                    durationSec = _callingState.value.durationSec + 1
                )
                callTimerHandler?.postDelayed(this, 1000)
            }
        }
        callTimerHandler?.post(callTimerRunnable!!)
    }

    private fun stopCallTimer() {
        callTimerRunnable?.let { callTimerHandler?.removeCallbacks(it) }
        callTimerHandler = null
        callTimerRunnable = null
    }

    // Group Management
    fun createGroup(name: String, description: String, selectedMembers: List<String>) {
        viewModelScope.launch {
            repository.createGroup(name, description, selectedMembers)
            navigateTo(AppScreen.Dashboard)
        }
    }

    // Chat custom wallpaper
    fun selectWallpaper(wallpaperPath: String?) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            repository.updateChatWallpaper(chatId, wallpaperPath)
            activeChatWallpaper.value = wallpaperPath
        }
    }

    // Toggle Preferences
    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _settings.value.copy(isDarkMode = enabled)
            repository.saveSettings(updated)
            _settings.value = updated
        }
    }

    fun toggleTwoFactor(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _settings.value.copy(isTwoFactorEnabled = enabled)
            repository.saveSettings(updated)
            _settings.value = updated
        }
    }

    // Block User
    fun blockUser(userId: String) {
        viewModelScope.launch {
            repository.blockUser(userId)
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            repository.unblockUser(userId)
        }
    }

    // Admin Broadcast System
    fun broadcastAdminNotification(title: String, message: String) {
        viewModelScope.launch {
            // Insert notification
            val id = "broadcast_${System.currentTimeMillis()}"
            repository.notificationDao.insertNotification(
                NotificationEntity(id, "all", title, message, System.currentTimeMillis())
            )
            
            // Add notification text to all chats
            val activeChats = chats.value
            for (chat in activeChats) {
                repository.sendMessage(chat.id, "📢 BROADCAST: $title - $message")
            }
        }
    }

    fun removeReportedUser(username: String) {
        adminReportedUsers.value = adminReportedUsers.value.filterNot { it.first == username }
    }
}

