package com.paoloronco.codevault.backup

import android.content.Context
import android.net.Uri
import com.paoloronco.codevault.R
import com.paoloronco.codevault.data.AccountEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(private val context: Context) {

    companion object {
        private const val PBKDF2_ITERATIONS = 65536
        private const val PBKDF2_KEY_LEN    = 256
        private const val GCM_TAG_BITS      = 128
        private const val SALT_SIZE         = 16
        private const val IV_SIZE           = 12
        private const val BACKUP_VERSION    = 1
    }

    private fun deriveKey(passcode: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec    = PBEKeySpec(passcode.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LEN)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /** Encrypt and write accounts to [uri] (from SAF ACTION_CREATE_DOCUMENT). */
    fun exportBackup(accounts: List<AccountEntity>, passcode: String, outputUri: Uri) {
        val json = JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("timestamp", System.currentTimeMillis())
            put("accounts", JSONArray().apply {
                accounts.forEach { acc ->
                    put(JSONObject().apply {
                        put("title",      acc.title)
                        put("username",   acc.username)
                        put("password",   acc.password)
                        put("notes",      acc.notes)
                        put("category",   acc.category)
                        put("isFavorite", acc.isFavorite)
                        put("updatedAt",  acc.updatedAt)
                    })
                }
            })
        }

        val plaintext = json.toString().toByteArray(Charsets.UTF_8)
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv   = ByteArray(IV_SIZE).also   { SecureRandom().nextBytes(it) }
        val key  = deriveKey(passcode, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            out.write(salt)
            out.write(iv)
            out.write(ciphertext)
        } ?: throw IllegalStateException("Cannot open output stream")
    }

    /** Decrypt and parse accounts from [uri] (from SAF ACTION_OPEN_DOCUMENT). */
    fun importBackup(inputUri: Uri, passcode: String): List<AccountEntity> {
        val bytes = context.contentResolver.openInputStream(inputUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open input stream")

        if (bytes.size <= SALT_SIZE + IV_SIZE)
            throw IllegalArgumentException(context.getString(R.string.backup_error_invalid))

        val salt       = bytes.copyOfRange(0, SALT_SIZE)
        val iv         = bytes.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val ciphertext = bytes.copyOfRange(SALT_SIZE + IV_SIZE, bytes.size)

        val key    = deriveKey(passcode, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw IllegalArgumentException(context.getString(R.string.backup_error_wrong_passcode))
        }

        val root     = JSONObject(String(plaintext, Charsets.UTF_8))
        val arr      = root.getJSONArray("accounts")

        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(AccountEntity(
                    id         = 0,
                    title      = o.getString("title"),
                    username   = o.getString("username"),
                    password   = o.getString("password"),
                    notes      = o.optString("notes", ""),
                    category   = o.optString("category", "General"),
                    isFavorite = o.optBoolean("isFavorite", false),
                    updatedAt  = o.optLong("updatedAt", System.currentTimeMillis())
                ))
            }
        }
    }
}
