package com.example.nhumonglenh

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nhumonglenh.data.local.AuthSessionManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets

/**
 * Instrumented test xác minh JWT decoder trên môi trường Android thực/máy ảo.
 * Sử dụng android.util.Base64 chuẩn của Android OS (hỗ trợ API 24+).
 */
@RunWith(AndroidJUnit4::class)
class JwtDecoderInstrumentedTest {

    private fun createSampleJwt(expSeconds: Long): String {
        val header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
        val payload = "{\"sub\":\"trader01\",\"exp\":$expSeconds}"
        val h64 = android.util.Base64.encodeToString(
            header.toByteArray(StandardCharsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        val p64 = android.util.Base64.encodeToString(
            payload.toByteArray(StandardCharsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        return "$h64.$p64.mockSignature"
    }

    @Test
    fun testJwtExpirationDetectionOnAndroidRuntime() {
        val now = 1750000000L

        // Token tương lai
        val futureJwt = createSampleJwt(now + 3600)
        assertEquals(now + 3600, AuthSessionManager.extractExpirationSeconds(futureJwt))
        assertFalse(AuthSessionManager.isTokenExpired(futureJwt, now))

        // Token quá khứ
        val pastJwt = createSampleJwt(now - 60)
        assertEquals(now - 60, AuthSessionManager.extractExpirationSeconds(pastJwt))
        assertTrue(AuthSessionManager.isTokenExpired(pastJwt, now))

        // Token boundary
        val boundaryJwt = createSampleJwt(now)
        assertEquals(now, AuthSessionManager.extractExpirationSeconds(boundaryJwt))
        assertTrue(AuthSessionManager.isTokenExpired(boundaryJwt, now))
    }

    @Test
    fun testMalformedTokensSafelyRejectedOnAndroidRuntime() {
        val now = 1750000000L
        assertNull(AuthSessionManager.extractExpirationSeconds(null))
        assertTrue(AuthSessionManager.isTokenExpired(null, now))

        assertNull(AuthSessionManager.extractExpirationSeconds(""))
        assertTrue(AuthSessionManager.isTokenExpired("", now))

        assertNull(AuthSessionManager.extractExpirationSeconds("invalid.jwt"))
        assertTrue(AuthSessionManager.isTokenExpired("invalid.jwt", now))
    }
}
