package com.example.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class AuthRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("connectchat_auth_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthResult>(AuthResult.Idle)
    val authState: StateFlow<AuthResult> = _authState

    sealed class AuthResult {
        object Idle : AuthResult()
        object Loading : AuthResult()
        data class Success(val userId: String, val name: String, val emailOrPhone: String) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    fun isUserLoggedIn(): Boolean {
        return sharedPrefs.getBoolean("is_logged_in", false)
    }

    fun getSavedUserId(): String? {
        return sharedPrefs.getString("user_id", null)
    }

    fun getSavedName(): String? {
        return sharedPrefs.getString("user_name", "User")
    }

    fun getSavedEmailOrPhone(): String? {
        return sharedPrefs.getString("user_email_phone", "")
    }

    suspend fun signUpWithEmail(email: String, name: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        _authState.value = AuthResult.Loading
        delay(1500) // Simulated network latency for Firebase Auth
        
        if (email.contains("@") && password.length >= 6) {
            val cleanId = email.substringBefore("@").replace(".", "").lowercase()
            sharedPrefs.edit().apply {
                putBoolean("is_logged_in", true)
                putString("user_id", cleanId)
                putString("user_name", name)
                putString("user_email_phone", email)
                apply()
            }
            val result = AuthResult.Success(cleanId, name, email)
            _authState.value = result
            result
        } else {
            val result = AuthResult.Error("Password must be at least 6 characters & email must be valid.")
            _authState.value = result
            result
        }
    }

    suspend fun loginWithEmail(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        _authState.value = AuthResult.Loading
        delay(1200) // Simulated network latency
        
        if (email.contains("@") && password.length >= 6) {
            val cleanId = email.substringBefore("@").replace(".", "").lowercase()
            val savedName = sharedPrefs.getString("user_name", null) ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
            sharedPrefs.edit().apply {
                putBoolean("is_logged_in", true)
                putString("user_id", cleanId)
                putString("user_name", savedName)
                putString("user_email_phone", email)
                apply()
            }
            val result = AuthResult.Success(cleanId, savedName, email)
            _authState.value = result
            result
        } else {
            val result = AuthResult.Error("Invalid email or password.")
            _authState.value = result
            result
        }
    }

    suspend fun sendPhoneOtp(phone: String): Boolean = withContext(Dispatchers.IO) {
        delay(1000)
        Log.d("AuthRepository", "Firebase OTP code sent to $phone")
        true
    }

    suspend fun verifyPhoneOtp(phone: String, otp: String): AuthResult = withContext(Dispatchers.IO) {
        _authState.value = AuthResult.Loading
        delay(1200)
        if (otp.length >= 4) {
            val cleanId = "usr_" + phone.takeLast(4)
            val name = "User " + phone.takeLast(4)
            sharedPrefs.edit().apply {
                putBoolean("is_logged_in", true)
                putString("user_id", cleanId)
                putString("user_name", name)
                putString("user_email_phone", phone)
                apply()
            }
            val result = AuthResult.Success(cleanId, name, phone)
            _authState.value = result
            result
        } else {
            val result = AuthResult.Error("Invalid OTP entered.")
            _authState.value = result
            result
        }
    }

    fun logout() {
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
