package com.example.nhumonglenh

import com.example.nhumonglenh.ui.news.NewsAdapter
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsDateFormatUnitTest {

    @Test
    fun testFormatReleaseDate_alphaVantageStandard() {
        val raw = "20260908T031438"
        val formatted = NewsAdapter.formatReleaseDate(raw)
        assertEquals("08/09/2026", formatted)
    }

    @Test
    fun testFormatReleaseDate_nullOrEmpty() {
        assertEquals("--", NewsAdapter.formatReleaseDate(null))
        assertEquals("--", NewsAdapter.formatReleaseDate(""))
        assertEquals("--", NewsAdapter.formatReleaseDate("   "))
    }

    @Test
    fun testFormatReleaseDate_malformed() {
        assertEquals("--", NewsAdapter.formatReleaseDate("invalid_date"))
        assertEquals("--", NewsAdapter.formatReleaseDate("2026/09/08"))
        assertEquals("--", NewsAdapter.formatReleaseDate("abcTxyz"))
        assertEquals("--", NewsAdapter.formatReleaseDate("123"))
    }

    @Test
    fun testFormatReleaseDate_isoFallback() {
        assertEquals("08/09/2026", NewsAdapter.formatReleaseDate("2026-09-08T03:14:38"))
        assertEquals("08/09/2026", NewsAdapter.formatReleaseDate("2026-09-08"))
    }
}