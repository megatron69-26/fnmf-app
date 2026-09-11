package com.example.nhumonglenh.ui.news

import java.text.SimpleDateFormat
import java.util.Locale

object NewsCardPresentationMapper {

    private val GENERIC_AUTHORS = setOf(
        "tin thị trường",
        "financial news",
        "tổng hợp",
        "market news",
        "unknown",
        "n/a",
        "none"
    )

    fun shouldShowAuthor(author: String?, source: String?): Boolean {
        if (author.isNullOrBlank()) return false
        val trimmedAuthor = author.trim()
        val lowerAuthor = trimmedAuthor.lowercase(Locale.ROOT)
        if (GENERIC_AUTHORS.contains(lowerAuthor)) return false
        if (!source.isNullOrBlank() && trimmedAuthor.equals(source.trim(), ignoreCase = true)) {
            return false
        }
        return true
    }

    fun formatAuthor(author: String?, source: String? = null): String? {
        if (!shouldShowAuthor(author, source)) return null
        return "Tác giả: ${author!!.trim()}"
    }

    fun formatBullets(bullets: List<String>?, maxBullets: Int = 4): List<String> {
        if (bullets.isNullOrEmpty()) return emptyList()
        return bullets
            .asSequence()
            .map { it.trim() }
            .map { stripLeadingBullet(it) }
            .filter { it.isNotBlank() }
            .take(maxBullets)
            .toList()
    }

    private fun stripLeadingBullet(text: String): String {
        var s = text.trim()
        while (s.startsWith("•") || s.startsWith("-") || s.startsWith("*") || s.startsWith("–")) {
            s = s.substring(1).trim()
        }
        return s
    }

    fun formatReleaseDate(rawDate: String?): String {
        if (rawDate.isNullOrBlank()) return "--"
        return runCatching {
            val trimmed = rawDate.trim()
            val date = when {
                trimmed.contains("T") && !trimmed.contains("-") -> {
                    val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                    fmt.isLenient = false
                    fmt.parse(trimmed)
                }
                trimmed.contains("-") -> {
                    val clean = if (trimmed.contains("T")) trimmed.substringBefore("T") else trimmed
                    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    fmt.isLenient = false
                    fmt.parse(clean)
                }
                trimmed.length == 8 && trimmed.all { it.isDigit() } -> {
                    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
                    fmt.isLenient = false
                    fmt.parse(trimmed)
                }
                else -> null
            }
            if (date != null) {
                SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
            } else {
                "--"
            }
        }.getOrDefault("--")
    }
}
