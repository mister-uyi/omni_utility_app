package com.omniutility.feature.finance.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyAlias = "omni_finance_db_kek"
    private val sharedPrefsFilename = "omni_finance_secure_prefs"
    private val keyPassphraseCipher = "AES/CBC/PKCS7Padding"
    
    private val prefs = context.getSharedPreferences(sharedPrefsFilename, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val existingEncrypted = prefs.getString("encrypted_passphrase", null)
        val existingIv = prefs.getString("passphrase_iv", null)
        
        if (existingEncrypted != null && existingIv != null) {
            try {
                val encryptedBytes = Base64.decode(existingEncrypted, Base64.DEFAULT)
                val ivBytes = Base64.decode(existingIv, Base64.DEFAULT)
                return decryptPassphrase(encryptedBytes, ivBytes)
            } catch (e: Exception) {
                // If decryption fails (e.g. keystore corrupted/cleared), regenerate a new key
                e.printStackTrace()
            }
        }
        
        val newPassphrase = generateRandomPassphrase()
        val encryptedResult = encryptPassphrase(newPassphrase)
        prefs.edit()
            .putString("encrypted_passphrase", Base64.encodeToString(encryptedResult.encryptedBytes, Base64.DEFAULT))
            .putString("passphrase_iv", Base64.encodeToString(encryptedResult.iv, Base64.DEFAULT))
            .apply()
            
        return newPassphrase
    }

    private fun generateRandomPassphrase(): ByteArray {
        val bytes = ByteArray(32) // 256-bit passphrase
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    private fun getSecretKey(): SecretKey {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    private fun encryptPassphrase(passphrase: ByteArray): EncryptedResult {
        val cipher = Cipher.getInstance(keyPassphraseCipher)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val encryptedBytes = cipher.doFinal(passphrase)
        return EncryptedResult(encryptedBytes, cipher.iv)
    }

    private fun decryptPassphrase(encryptedBytes: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(keyPassphraseCipher)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), IvParameterSpec(iv))
        return cipher.doFinal(encryptedBytes)
    }

    private class EncryptedResult(val encryptedBytes: ByteArray, val iv: ByteArray)
}
