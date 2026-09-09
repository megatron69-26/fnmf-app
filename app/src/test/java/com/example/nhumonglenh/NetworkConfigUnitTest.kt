package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.NetworkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConfigUnitTest {

    @Test
    fun testDefaultServerUrl() {
        assertEquals("https://fnmf-backend-production.up.railway.app/", NetworkConfig.DEFAULT_SERVER_URL)
        assertEquals(3, NetworkConfig.SERVER_CONFIG_VERSION)
    }

    @Test
    fun testDecideServerUrl_nullOrBlank() {
        val (url1, ver1) = NetworkConfig.decideServerUrl(null, 0)
        assertEquals(NetworkConfig.DEFAULT_SERVER_URL, url1)
        assertEquals(3, ver1)

        val (url2, ver2) = NetworkConfig.decideServerUrl("", 0)
        assertEquals(NetworkConfig.DEFAULT_SERVER_URL, url2)
        assertEquals(3, ver2)

        val (url3, ver3) = NetworkConfig.decideServerUrl("   ", 1)
        assertEquals(NetworkConfig.DEFAULT_SERVER_URL, url3)
        assertEquals(3, ver3)
    }

    @Test
    fun testDecideServerUrl_legacyTargets() {
        val legacyUrls = listOf(
            "http://172.18.97.109:8083/",
            "http://172.18.97.109:8083",
            "172.18.97.109:8083",
            "http://10.174.64.109:8083/",
            "http://10.174.64.59:8083/",
            "http://10.0.2.2:8083/",
            "http://localhost:8083/",
            "http://127.0.0.1:8083/",
            "http://localhost:3000",
            "http://10.174.64.109:3000/"
        )

        for (legacy in legacyUrls) {
            val (resolved, ver) = NetworkConfig.decideServerUrl(legacy, 0)
            assertEquals("Legacy target should migrate to Railway: $legacy", NetworkConfig.DEFAULT_SERVER_URL, resolved)
            assertEquals(3, ver)
        }
    }

    @Test
    fun testDecideServerUrl_customHttpsIsReplacedByProductionCloud() {
        val custom1 = "https://custom-domain.example.com/"
        val (url1, ver1) = NetworkConfig.decideServerUrl(custom1, 0)
        assertEquals(NetworkConfig.DEFAULT_SERVER_URL, url1)
        assertEquals(3, ver1)

        val custom2 = "https://custom-domain.example.com"
        val (url2, ver2) = NetworkConfig.decideServerUrl(custom2, 0)
        assertEquals(NetworkConfig.DEFAULT_SERVER_URL, url2)
        assertEquals(3, ver2)
    }

    @Test
    fun testDecideServerUrl_oldConfiguredVersionIsReplaced() {
        val custom = "https://my-cloud-api.org/"
        val (url, ver) = NetworkConfig.decideServerUrl(custom, 2)
        assertEquals(NetworkConfig.DEFAULT_SERVER_URL, url)
        assertEquals(3, ver)
    }

    @Test
    fun testDecideServerUrl_corruptedOrWeirdStrings() {
        val invalidInputs = listOf(
            "not_a_url",
            "ftp://bad-protocol.com/",
            "just random text",
            "://missing-scheme"
        )

        for (invalid in invalidInputs) {
            val (resolved, ver) = NetworkConfig.decideServerUrl(invalid, 0)
            assertEquals("Invalid input should fallback to Railway: $invalid", NetworkConfig.DEFAULT_SERVER_URL, resolved)
            assertEquals(3, ver)
        }
    }

    @Test
    fun testNormalizeUrl() {
        assertEquals("https://example.com/", NetworkConfig.normalizeUrl("https://example.com/"))
        assertEquals("https://example.com/", NetworkConfig.normalizeUrl("https://example.com"))
        assertEquals("https://example.com/", NetworkConfig.normalizeUrl("  https://example.com  "))
        assertEquals(NetworkConfig.DEFAULT_SERVER_URL, NetworkConfig.normalizeUrl(""))
        assertEquals(NetworkConfig.DEFAULT_SERVER_URL, NetworkConfig.normalizeUrl("   "))
    }

    @Test
    fun testIsLegacyUrl() {
        assertTrue(NetworkConfig.isLegacyUrl("http://172.18.97.109:8083/"))
        assertTrue(NetworkConfig.isLegacyUrl("http://10.174.64.109:8083/"))
        assertTrue(NetworkConfig.isLegacyUrl("http://10.174.64.59:8083/"))
        assertTrue(NetworkConfig.isLegacyUrl("http://10.0.2.2:8083/"))
        assertTrue(NetworkConfig.isLegacyUrl("http://localhost:8083/"))
        assertTrue(NetworkConfig.isLegacyUrl("http://127.0.0.1:8083/"))
        assertTrue(NetworkConfig.isLegacyUrl("http://any-domain:3000/"))

        assertFalse(NetworkConfig.isLegacyUrl("https://fnmf-backend-production.up.railway.app/"))
        assertFalse(NetworkConfig.isLegacyUrl("https://custom-server.com/"))
    }
}
