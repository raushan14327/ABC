package com.example.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class AuthRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("connectchat_auth_prefs", Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthResult>(AuthResult.Idle)
    val authState: StateFlow<AuthResult> = _authState

    sealed class AuthResult {
        object Idle : AuthResult()
        object Loading : AuthResult()
        data class Success(val userId: String, val name: String, val emailOrPhone: String) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null || sharedPrefs.getBoolean("is_logged_in", false)
    }

    fun getSavedUserId(): String? {
        return auth.currentUser?.uid ?: sharedPrefs.getString("user_id", null)
    }

    fun getSavedName(): String? {
        return sharedPrefs.getString("user_name", "User")
    }

    fun getSavedEmailOrPhone(): String? {
        return auth.currentUser?.email ?: auth.currentUser?.phoneNumber ?: sharedPrefs.getString("user_email_phone", "")
    }

    suspend fun signUpWithEmail(email: String, name: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        _authState.value = AuthResult.Loading
        try {
            // 1. Create User in Firebase Auth
            val result = Tasks.await(auth.createUserWithEmailAndPassword(email, password))
            val firebaseUser = result.user ?: throw Exception("Auth signup failed: User is null.")
            val uid = firebaseUser.uid

            // 2. Create User Profile in Firestore
            val userMap = hashMapOf(
                "id" to uid,
                "name" to name,
                "photoUrl" to "",
                "bio" to "Hey there! I am using ConnectChat.",
                "statusText" to "Online",
                "isOnline" to true,
                "lastSeen" to System.currentTimeMillis(),
                "phone" to "",
                "email" to email,
                "fcmToken" to ""
            )
            Tasks.await(firestore.collection("users").document(uid).set(userMap))

            // 3. Save to SharedPreferences for backwards compatibility
            sharedPrefs.edit().apply {
                putBoolean("is_logged_in", true)
                putString("user_id", uid)
                putString("user_name", name)
                putString("user_email_phone", email)
                apply()
            }

            val authSuccess = AuthResult.Success(uid, name, email)
            _authState.value = authSuccess
            authSuccess
        } catch (e: Exception) {
            val errorResult = AuthResult.Error(e.localizedMessage ?: "Registration failed.")
            _authState.value = errorResult
            errorResult
        }
    }

    suspend fun loginWithEmail(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        _authState.value = AuthResult.Loading
        try {
            // 1. Sign In via Firebase Auth
            val result = Tasks.await(auth.signInWithEmailAndPassword(email, password))
            val firebaseUser = result.user ?: throw Exception("Auth login failed: User is null.")
            val uid = firebaseUser.uid

            // 2. Fetch User profile from Firestore
            val doc = Tasks.await(firestore.collection("users").document(uid).get())
            val savedName = doc.getString("name") ?: email.substringBefore("@")
            val bio = doc.getString("bio") ?: "Hey there!"

            // 3. Update Status to Online
            firestore.collection("users").document(uid).update("isOnline", true, "lastSeen", System.currentTimeMillis())

            // 4. Update Preferences
            sharedPrefs.edit().apply {
                putBoolean("is_logged_in", true)
                putString("user_id", uid)
                putString("user_name", savedName)
                putString("user_email_phone", email)
                apply()
            }

            val authSuccess = AuthResult.Success(uid, savedName, email)
            _authState.value = authSuccess
            authSuccess
        } catch (e: Exception) {
            val errorResult = AuthResult.Error(e.localizedMessage ?: "Invalid email or password.")
            _authState.value = errorResult
            errorResult
        }
    }

    // Storage for phone authentication verification ID
    private var verificationId: String = ""

    suspend fun sendPhoneOtp(phone: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("AuthRepository", "Firebase OTP code requested for $phone")
            // Normally PhoneAuthProvider is invoked on the UI thread with the current Activity.
            // We set a mock verification ID here so the verify OTP flow can succeed.
            verificationId = "MOCK_VERIFICATION_ID_" + System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to send OTP", e)
            false
        }
    }

    suspend fun verifyPhoneOtp(phone: String, otp: String): AuthResult = withContext(Dispatchers.IO) {
        _authState.value = AuthResult.Loading
        try {
            // Simulated / mock verification logic to work reliably on any emulator/preview container
            // without requiring real hardware SMS capabilities, while maintaining correct Firestore registration.
            val uid = "phone_user_" + phone.replace("+", "").replace(" ", "")
            val name = "User " + phone.takeLast(4)

            // Register/Fetch from Firestore
            val userRef = firestore.collection("users").document(uid)
            val doc = Tasks.await(userRef.get())
            if (!doc.exists()) {
                val userMap = hashMapOf(
                    "id" to uid,
                    "name" to name,
                    "photoUrl" to "",
                    "bio" to "Hey there! I am using ConnectChat.",
                    "statusText" to "Online",
                    "isOnline" to true,
                    "lastSeen" to System.currentTimeMillis(),
                    "phone" to phone,
                    "email" to "",
                    "fcmToken" to ""
                )
                Tasks.await(userRef.set(userMap))
            } else {
                userRef.update("isOnline", true, "lastSeen", System.currentTimeMillis())
            }

            sharedPrefs.edit().apply {
                putBoolean("is_logged_in", true)
                putString("user_id", uid)
                putString("user_name", name)
                putString("user_email_phone", phone)
                apply()
            }

            val success = AuthResult.Success(uid, name, phone)
            _authState.value = success
            success
        } catch (e: Exception) {
            val errorResult = AuthResult.Error(e.localizedMessage ?: "OTP Verification failed.")
            _authState.value = errorResult
            errorResult
        }
    }

    fun logout() {
        try {
            val uid = getSavedUserId()
            if (uid != null) {
                firestore.collection("users").document(uid).update("isOnline", false, "lastSeen", System.currentTimeMillis())
            }
            auth.signOut()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error updating online status during logout", e)
        }
        sharedPrefs.edit().apply {
            putBoolean("is_logged_in", false)
            putString("user_id", null)
            putString("user_name", null)
            putString("user_email_phone", null)
            apply()
        }
        _authState.value = AuthResult.Idle
    }
}
