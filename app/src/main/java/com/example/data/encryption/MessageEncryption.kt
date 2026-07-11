package com.example.data.encryption

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object MessageEncryption {
    // Standard AES E2E Encryption simulation using local symmetric key
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private val KEY_BYTES = "ConnectChatSecureKey123456789012".toByteArray(Charsets.UTF_8) // 256-bit key
    private val SECRET_KEY = SecretKeySpec(KEY_BYTES, "AES")

    fun encrypt(text: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, ivSpec)
            val encryptedBytes = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            
            // Combine IV and Encrypted Bytes for transmission/storage
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            
            "E2E:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            text // Fallback
        }
    }

    fun decrypt(encryptedText: String): String {
        if (!encryptedText.startsWith("E2E:")) return encryptedText
        return try {
            val rawBase64 = encryptedText.substring(4)
            val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
            
            val iv = ByteArray(16)
            System.arraycopy(combined, 0, iv, 0, 16)
            
            val encryptedBytes = ByteArray(combined.size - 16)
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.size)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, ivSpec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            encryptedText // Fallback
        }
    }
}
