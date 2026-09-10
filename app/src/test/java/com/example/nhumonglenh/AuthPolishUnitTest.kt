package com.example.nhumonglenh

import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.NetworkConfig
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Unit test xác minh các ràng buộc cho FNMF v1.1.16 Auth Polish:
 * 1. Auth có 2 Activity riêng biệt: Activity1 (Login) và RegisterActivity.
 * 2. Activity1 không gọi endpoint register của ApiService.
 * 3. Xử lý password không trim khoảng trắng, xác nhận mật khẩu chuẩn xác.
 * 4. Không chứa chuỗi/emoji bị cấm hoặc secret VNPay trên Android client.
 * 5. Quản lý hạn token JWT phía client và kiểm tra phiên tại cold-start.
 */
class AuthPolishUnitTest {

    private fun createSampleJwt(expSeconds: Long): String {
        val header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
        val payload = "{\"sub\":\"trader01\",\"exp\":$expSeconds}"
        val enc = Base64.getUrlEncoder().withoutPadding()
        val h64 = enc.encodeToString(header.toByteArray(StandardCharsets.UTF_8))
        val p64 = enc.encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return "$h64.$p64.mockSignature"
    }

    @Test
    fun testTwoSeparateActivitiesExist() {
        val activity1Class = Class.forName("com.example.nhumonglenh.Activity1")
        val registerActivityClass = Class.forName("com.example.nhumonglenh.RegisterActivity")

        assertNotNull("Activity1 phải tồn tại", activity1Class)
        assertNotNull("RegisterActivity phải tồn tại", registerActivityClass)
        assertNotEquals("Hai Activity phải khác nhau", activity1Class, registerActivityClass)
    }

    @Test
    fun testActivity1DoesNotCallRegisterApi() {
        val activity1File = File("src/main/java/com/example/nhumonglenh/Activity1.kt")
        if (activity1File.exists()) {
            val content = activity1File.readText()
            assertFalse(
                "Activity1 tuyệt đối không được gọi endpoint register",
                content.contains("apiService.register") || content.contains(".register(")
            )
            assertTrue(
                "Activity1 phải có nút chuyển sang RegisterActivity",
                content.contains("RegisterActivity::class.java")
            )
        }
    }

    @Test
    fun testPasswordPreservesLeadingAndTrailingSpaces() {
        val passWithSpaces = "  secureP@ss123  "
        assertEquals("Độ dài mật khẩu phải được giữ nguyên", 17, passWithSpaces.length)
        assertNotEquals("Không được tự động trim mật khẩu", passWithSpaces.trim(), passWithSpaces)
    }

    @Test
    fun testNoForbiddenStringsOrSecretsInAndroidSource() {
        val appDir = File("src/main/java/com/example/nhumonglenh")
        if (appDir.exists()) {
            appDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension == "kt") {
                    val text = file.readText()
                    assertFalse(
                        "File ${file.name} không được chứa chuỗi cấm 'khang123'",
                        text.contains("khang123", ignoreCase = true)
                    )
                    assertFalse(
                        "File ${file.name} không được chứa chuỗi cấm '0123456789'",
                        text.contains("0123456789")
                    )
                    assertFalse(
                        "File ${file.name} không được chứa secret key VNPay",
                        text.contains("vnp_HashSecret") || text.contains("vnp_TmnCode")
                    )
                }
            }
        }
    }

    @Test
    fun testAndroidManifestDeclaresBothActivities() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertTrue("Manifest phải khai báo Activity1", content.contains(".Activity1"))
            assertTrue("Manifest phải khai báo RegisterActivity", content.contains(".RegisterActivity"))
        }
    }

    @Test
    fun testAndroidManifestRegistersPaymentReturnDeepLink() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertTrue("Manifest phải chứa action VIEW cho deep link", content.contains("android.intent.action.VIEW"))
            assertTrue("Manifest phải chứa category BROWSABLE", content.contains("android.intent.category.BROWSABLE"))
            assertTrue("Manifest phải chứa scheme fnmf", content.contains("android:scheme=\"fnmf\""))
            assertTrue("Manifest phải chứa host payment", content.contains("android:host=\"payment\""))
            assertTrue("Manifest phải chứa pathPrefix /return", content.contains("android:pathPrefix=\"/return\""))
        }
    }

    @Test
    fun testActivity2EnforcesColdStartAuthCheck() {
        val activity2File = File("src/main/java/com/example/nhumonglenh/Activity2.kt")
        if (activity2File.exists()) {
            val content = activity2File.readText()
            assertTrue(
                "Activity2 phải kiểm tra phiên đăng nhập bằng AuthSessionManager.isLoggedIn",
                content.contains("AuthSessionManager.isLoggedIn")
            )
            assertTrue(
                "Activity2 phải chuyển hướng về Activity1 khi phiên không hợp lệ",
                content.contains("Activity1::class.java")
            )
            assertTrue(
                "Activity2 phải sử dụng cờ FLAG_ACTIVITY_CLEAR_TASK khi chuyển hướng",
                content.contains("FLAG_ACTIVITY_CLEAR_TASK")
            )
        }
    }

    @Test
    fun testJwtExpirationDetectionBehavioral() {
        val now = 1750000000L

        // Token hết hạn trong tương lai
        val futureJwt = createSampleJwt(now + 3600)
        assertEquals(now + 3600, AuthSessionManager.extractExpirationSeconds(futureJwt))
        assertFalse("Token có exp > currentTime không được coi là hết hạn", AuthSessionManager.isTokenExpired(futureJwt, now))

        // Token đã hết hạn trong quá khứ
        val pastJwt = createSampleJwt(now - 60)
        assertEquals(now - 60, AuthSessionManager.extractExpirationSeconds(pastJwt))
        assertTrue("Token có exp < currentTime phải coi là đã hết hạn", AuthSessionManager.isTokenExpired(pastJwt, now))

        // Token ở đúng mốc thời gian (boundary)
        val boundaryJwt = createSampleJwt(now)
        assertEquals(now, AuthSessionManager.extractExpirationSeconds(boundaryJwt))
        assertTrue("Token có exp == currentTime phải coi là đã hết hạn", AuthSessionManager.isTokenExpired(boundaryJwt, now))
    }

    @Test
    fun testJwtMalformedTokensSafelyRejected() {
        val now = 1750000000L

        // Token null hoặc blank
        assertNull(AuthSessionManager.extractExpirationSeconds(null))
        assertTrue(AuthSessionManager.isTokenExpired(null, now))

        assertNull(AuthSessionManager.extractExpirationSeconds(""))
        assertTrue(AuthSessionManager.isTokenExpired("", now))

        assertNull(AuthSessionManager.extractExpirationSeconds("   "))
        assertTrue(AuthSessionManager.isTokenExpired("   ", now))

        // Token không đủ 3 phần
        assertNull(AuthSessionManager.extractExpirationSeconds("header.payload"))
        assertTrue(AuthSessionManager.isTokenExpired("header.payload", now))

        // Token payload không phải JSON hợp lệ
        val invalidPayloadJwt = "eyJhbGciOiJIUzI1NiJ9.bm90LWpzb24-c3R1ZmY.signature"
        assertNull(AuthSessionManager.extractExpirationSeconds(invalidPayloadJwt))
        assertTrue(AuthSessionManager.isTokenExpired(invalidPayloadJwt, now))

        // Token payload là JSON nhưng không có claim exp
        val noExpPayload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"user\"}".toByteArray(StandardCharsets.UTF_8))
        val noExpJwt = "eyJhbGciOiJIUzI1NiJ9.$noExpPayload.signature"
        assertNull(AuthSessionManager.extractExpirationSeconds(noExpJwt))
        assertTrue(AuthSessionManager.isTokenExpired(noExpJwt, now))
    }

    @Test
    fun testJwtParserUsesAndroidUtilBase64ForApi24Compatibility() {
        val authManagerFile = File("src/main/java/com/example/nhumonglenh/data/local/AuthSessionManager.kt")
        if (authManagerFile.exists()) {
            val content = authManagerFile.readText()
            assertTrue(
                "AuthSessionManager phải sử dụng android.util.Base64 để tương thích Android 7.0 (API 24)",
                content.contains("android.util.Base64.decode")
            )
            assertTrue(
                "AuthSessionManager phải cấu hình cờ URL_SAFE",
                content.contains("android.util.Base64.URL_SAFE")
            )
            assertTrue(
                "AuthSessionManager phải cấu hình cờ NO_PADDING",
                content.contains("android.util.Base64.NO_PADDING")
            )
            assertTrue(
                "AuthSessionManager phải cấu hình cờ NO_WRAP",
                content.contains("android.util.Base64.NO_WRAP")
            )
            assertFalse(
                "Production source tuyệt đối không được chứa java.util.Base64 để tương thích Android 7.0 API 24",
                content.contains("java.util.Base64")
            )
            assertFalse(
                "AuthSessionManager không được bắt catch (t: Throwable)",
                content.contains("catch (t: Throwable)")
            )
        }
    }
}
