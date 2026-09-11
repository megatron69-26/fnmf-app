package com.example.nhumonglenh.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.nhumonglenh.data.remote.NetworkConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Giao diện trừu tượng cung cấp SecretKey phục vụ mã hoá Token.
 */
internal interface KeyStoreProvider {
    fun getSecretKey(): SecretKey?
}

/**
 * Provider mặc định cho Production: Sinh và lưu trữ SecretKey AES 256-bit
 * trong phần cứng/hệ điều hành AndroidKeyStore an toàn.
 * Thiết kế FAIL-CLOSED: Khi KeyStore lỗi, trả về null, tuyệt đối không dùng fallback key không an toàn.
 */
internal class AndroidKeyStoreProvider(
    private val keyAlias: String = KEY_ALIAS
) : KeyStoreProvider {

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fnmf_jwt_master_key"
    }

    override fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }

            keyStore.getKey(keyAlias, null) as? SecretKey
        } catch (e: Exception) {
            // Fail closed: Không dùng khóa cố định phần mềm trong production!
            null
        }
    }
}

/**
 * Lớp xử lý mã hoá và giải mã AES/GCM/NoPadding độc lập với SharedPreferences.
 * Hỗ trợ Dependency Injection cho KeyStoreProvider để phục vụ kiểm thử hành vi độc lập.
 */
internal class TokenCryptor(
    private val keyProvider: KeyStoreProvider = AndroidKeyStoreProvider()
) {
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
    }

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        val secretKey = keyProvider.getSecretKey() ?: return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv ?: return ""
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isBlank()) return ""
        val secretKey = keyProvider.getSecretKey() ?: return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH + 16) return ""

            val iv = ByteArray(IV_LENGTH)
            val cipherBytes = ByteArray(combined.size - IV_LENGTH)

            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherBytes, 0, cipherBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Engine điều phối lưu trữ, giải mã và di chuyển (migration) an toàn từ SharedPreferences cũ.
 * Sử dụng .commit() đồng bộ để đảm bảo dữ liệu ghi thành công trước khi dọn dẹp plaintext cũ.
 */
internal class TokenStoreEngine(
    private val cryptor: TokenCryptor = TokenCryptor()
) {
    fun getToken(context: Context): String {
        val securePrefs = context.getSharedPreferences(SecureTokenStore.SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        val enc = securePrefs.getString(SecureTokenStore.KEY_ENCRYPTED_TOKEN, null)

        if (!enc.isNullOrBlank()) {
            val decrypted = cryptor.decrypt(enc)
            if (decrypted.isNotBlank()) {
                clearLegacyPlaintextToken(context)
                return decrypted
            }
            // Ciphertext tồn tại nhưng giải mã lỗi (ví dụ key bị invalidate) -> fail-closed
            return ""
        }

        // Migration: kiểm tra token plaintext từ phiên bản cũ
        val legacyPrefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val legacyToken = legacyPrefs.getString(SecureTokenStore.LEGACY_KEY_JWT_TOKEN, null)
        if (!legacyToken.isNullOrBlank()) {
            val savedSuccessfully = saveToken(context, legacyToken)
            if (savedSuccessfully) {
                return legacyToken
            }
            // Nếu lưu/mã hóa thất bại, fail-closed: không trả về legacy token thô
            return ""
        }

        return ""
    }

    fun saveToken(context: Context, token: String): Boolean {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            clearToken(context)
            return false
        }

        val encrypted = cryptor.encrypt(cleanToken)
        if (encrypted.isBlank()) {
            // Mã hóa thất bại (KeyStore lỗi) -> không ghi và trả false
            return false
        }

        val securePrefs = context.getSharedPreferences(SecureTokenStore.SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        val committed = securePrefs.edit().putString(SecureTokenStore.KEY_ENCRYPTED_TOKEN, encrypted).commit()

        if (committed) {
            clearLegacyPlaintextToken(context)
            return true
        }

        return false
    }

    fun clearToken(context: Context) {
        val securePrefs = context.getSharedPreferences(SecureTokenStore.SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        securePrefs.edit().remove(SecureTokenStore.KEY_ENCRYPTED_TOKEN).commit()
        clearLegacyPlaintextToken(context)
    }

    private fun clearLegacyPlaintextToken(context: Context) {
        val legacyPrefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        if (legacyPrefs.contains(SecureTokenStore.LEGACY_KEY_JWT_TOKEN)) {
            legacyPrefs.edit().remove(SecureTokenStore.LEGACY_KEY_JWT_TOKEN).commit()
        }
    }

    fun encrypt(plainText: String): String = cryptor.encrypt(plainText)
    fun decrypt(encryptedBase64: String): String = cryptor.decrypt(encryptedBase64)
}

/**
 * Singleton trung tâm lưu trữ JWT Token trong ứng dụng FNMF.
 * Production singleton HOÀN TOÀN KHÔNG CÓ mutable test hook công khai nào.
 * Luôn sử dụng AndroidKeyStore phần cứng an toàn và ủy quyền cho TokenStoreEngine.
 */
object SecureTokenStore {

    const val SECURE_PREFS_NAME = "fnmf_secure_prefs"
    const val KEY_ENCRYPTED_TOKEN = "fnmf_enc_jwt_token"
    const val LEGACY_KEY_JWT_TOKEN = "jwt_token"

    private val defaultEngine = TokenStoreEngine(TokenCryptor(AndroidKeyStoreProvider()))

    fun encrypt(plainText: String): String = defaultEngine.encrypt(plainText)

    fun decrypt(encryptedBase64: String): String = defaultEngine.decrypt(encryptedBase64)

    fun getToken(context: Context): String = defaultEngine.getToken(context)

    fun saveToken(context: Context, token: String): Boolean = defaultEngine.saveToken(context, token)

    fun clearToken(context: Context) = defaultEngine.clearToken(context)
}
