package com.example.nhumonglenh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nhumonglenh.data.local.SecureTokenStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented runtime test cho SecureTokenStore trên Android OS thực / máy ảo.
 * Kiểm tra AndroidKeyStore phần cứng/hệ thống thực tế, sinh khóa AES-GCM và mã hóa/giải mã token.
 */
@RunWith(AndroidJUnit4::class)
class SecureTokenStoreAndroidTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecureTokenStore.clearToken(context)
    }

    @Test
    fun testRealAndroidKeyStoreEncryptAndDecrypt() {
        val sampleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.realAndroidRuntimeToken.signature"
        val encrypted = SecureTokenStore.encrypt(sampleJwt)

        assertTrue("Encrypted string phải không rỗng", encrypted.isNotBlank())
        assertNotEquals("Ciphertext không được trùng với Plaintext", sampleJwt, encrypted)

        val decrypted = SecureTokenStore.decrypt(encrypted)
        assertEquals("Decrypted token trên AndroidKeyStore thật phải trùng khớp 100%", sampleJwt, decrypted)
    }

    @Test
    fun testRealAndroidKeyStoreSaveAndGetTokenPersistence() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGZubWYuY29tIn0.sig"
        val saved = SecureTokenStore.saveToken(context, jwt)
        assertTrue("saveToken phải thành công trên runtime", saved)

        val retrieved = SecureTokenStore.getToken(context)
        assertEquals("getToken phải đọc và giải mã đúng token", jwt, retrieved)

        SecureTokenStore.clearToken(context)
        assertEquals("Sau khi clearToken, getToken phải trả về rỗng", "", SecureTokenStore.getToken(context))
    }
}
