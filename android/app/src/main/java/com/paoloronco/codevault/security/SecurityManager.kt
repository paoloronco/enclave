package com.paoloronco.codevault.security

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class SecurityManager(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "codevault_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        // Keystore unavailable (some emulators): wipe stale file and retry once
        context.deleteSharedPreferences("codevault_secure_prefs")
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "codevault_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            // Final fallback: plain SharedPreferences (development only)
            context.getSharedPreferences("codevault_secure_prefs_plain", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_PASSCODE_HASH   = "master_passcode_hash"
        private const val KEY_BIOMETRIC       = "biometric_enabled"
        private const val KEY_AUTO_LOCK       = "auto_lock_timeout_ms"
        private const val KEY_AUTH_TYPE       = "auth_type"   // "pin" | "password"
        private const val KEY_ONBOARDING      = "onboarding_complete"
        private const val KEY_DB_PASSPHRASE   = "db_passphrase_v1"
        private const val FIELD_KEY_ALIAS     = "codevault_field_key"

        private const val PBKDF2_ITERATIONS   = 65536
        private const val PBKDF2_KEY_LENGTH   = 256

        const val TIMEOUT_30S  = 30_000L
        const val TIMEOUT_1M   = 60_000L
        const val TIMEOUT_2M   = 120_000L
        const val TIMEOUT_5M   = 300_000L
        const val TIMEOUT_10M  = 600_000L
        const val TIMEOUT_NEVER = -1L

        val TIMEOUT_OPTIONS = listOf(
            TIMEOUT_30S  to "30 secondi",
            TIMEOUT_1M   to "1 minuto",
            TIMEOUT_2M   to "2 minuti",
            TIMEOUT_5M   to "5 minuti",
            TIMEOUT_10M  to "10 minuti",
            TIMEOUT_NEVER to "Mai"
        )
    }

    // ---- Passcode hashing ----

    private fun generateSalt(): ByteArray =
        ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun hashPasscode(passcode: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passcode.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        return factory.generateSecret(spec).encoded
    }

    fun savePasscode(passcode: String) {
        val salt  = generateSalt()
        val hash  = hashPasscode(passcode, salt)
        val value = "${Base64.encodeToString(hash, Base64.NO_WRAP)}:${Base64.encodeToString(salt, Base64.NO_WRAP)}"
        prefs.edit().putString(KEY_PASSCODE_HASH, value).apply()
    }

    fun isPasscodeSet(): Boolean = prefs.getString(KEY_PASSCODE_HASH, null) != null

    fun verifyPasscode(input: String): Boolean {
        val stored = prefs.getString(KEY_PASSCODE_HASH, null) ?: return false
        val parts  = stored.split(":")
        if (parts.size != 2) return false
        val storedHash = Base64.decode(parts[0], Base64.NO_WRAP)
        val salt       = Base64.decode(parts[1], Base64.NO_WRAP)
        return storedHash.contentEquals(hashPasscode(input, salt))
    }

    fun getPasscodeRaw(): String? = prefs.getString(KEY_PASSCODE_HASH, null)

    // ---- Field encryption (Android Keystore AES-256-GCM) ----

    private fun getOrCreateFieldKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(FIELD_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                FIELD_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    fun encryptField(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateFieldKey())
        val iv         = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
               Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun decryptField(encrypted: String): String {
        if (encrypted.isEmpty()) return ""
        val colon = encrypted.indexOf(':')
        if (colon < 0) return encrypted   // legacy plaintext value
        return try {
            val iv         = Base64.decode(encrypted.substring(0, colon), Base64.NO_WRAP)
            val ciphertext = Base64.decode(encrypted.substring(colon + 1), Base64.NO_WRAP)
            val cipher     = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateFieldKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            encrypted   // fallback: return raw value if decryption fails
        }
    }

    // ---- Biometric ----

    fun setBiometricEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    // ---- Auto-lock ----

    fun setAutoLockTimeout(ms: Long) =
        prefs.edit().putLong(KEY_AUTO_LOCK, ms).apply()

    fun getAutoLockTimeout(): Long = prefs.getLong(KEY_AUTO_LOCK, TIMEOUT_1M)

    // ---- Auth type ----

    fun setAuthType(type: String) = prefs.edit().putString(KEY_AUTH_TYPE, type).apply()
    fun getAuthType(): String = prefs.getString(KEY_AUTH_TYPE, "pin") ?: "pin"

    // ---- Onboarding ----

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)
    fun setOnboardingComplete() = prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()

    // ---- DB passphrase (SQLCipher) ----

    /** Returns the existing SQLCipher passphrase or generates and persists a new one. */
    fun getOrCreateDbPassphrase(): ByteArray {
        val stored = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) return Base64.decode(stored, Base64.NO_WRAP)
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP)).apply()
        return passphrase
    }

    // ---- Vault reset ----

    /** Clears all security data except onboarding flag. Vault DB must be wiped separately. */
    fun resetVault() {
        prefs.edit()
            .remove(KEY_PASSCODE_HASH)
            .remove(KEY_BIOMETRIC)
            .remove(KEY_AUTO_LOCK)
            .remove(KEY_AUTH_TYPE)
            .apply()
    }
}
