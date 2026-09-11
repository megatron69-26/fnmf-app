package com.example.nhumonglenh.ui.news

import com.example.nhumonglenh.data.local.NewsEntity
import java.util.Locale
import java.util.regex.Pattern

object NewsLocalizationPolicy {

    private val VIETNAMESE_DIACRITICS = Pattern.compile(
        "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđÀÁẠẢÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐÃ]"
    )

    private val BOILERPLATE_PATTERNS = listOf(
        "bài viết này được tạo bởi",
        "chúng tôi không chịu trách nhiệm",
        "vui lòng tham khảo ý kiến chuyên gia",
        "không phải lời khuyên đầu tư",
        "đọc thêm tại",
        "nguồn tin từ",
        "bản quyền thuộc về",
        "all rights reserved",
        "disclaimer"
    )

    fun hasExcessiveEnglishTokens(text: String?): Boolean {
        return false
    }

    fun hasVietnameseCharacteristics(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return VIETNAMESE_DIACRITICS.matcher(text.trim()).find()
    }

    fun isLikelyEnglish(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return !hasVietnameseCharacteristics(text)
    }

    fun isBoilerplate(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val lower = text.lowercase(Locale.ROOT)
        return BOILERPLATE_PATTERNS.any { lower.contains(it) }
    }

    fun isValidDisplayTitleVi(displayTitleVi: String?, originalTitle: String?): Boolean {
        if (displayTitleVi.isNullOrBlank()) return false
        val trimmedDisplay = displayTitleVi.trim()
        if (isBoilerplate(trimmedDisplay)) return false
        if (!hasVietnameseCharacteristics(trimmedDisplay)) return false

        if (!originalTitle.isNullOrBlank()) {
            val trimmedOrig = originalTitle.trim()
            if (trimmedDisplay.equals(trimmedOrig, ignoreCase = true) && trimmedOrig.length > 5) {
                return false
            }
        }
        return true
    }

    fun isValidDisplaySummaryVi(
        displaySummaryVi: String?,
        originalSummary: String?,
        displayTitleVi: String?
    ): Boolean {
        if (displaySummaryVi.isNullOrBlank()) return false
        val trimmedSummary = displaySummaryVi.trim()
        if (isBoilerplate(trimmedSummary)) return false
        if (!hasVietnameseCharacteristics(trimmedSummary)) return false

        if (!originalSummary.isNullOrBlank()) {
            val trimmedOrig = originalSummary.trim()
            if (trimmedSummary.equals(trimmedOrig, ignoreCase = true) && trimmedOrig.length > 10) {
                return false
            }
        }
        if (!displayTitleVi.isNullOrBlank()) {
            if (trimmedSummary.equals(displayTitleVi.trim(), ignoreCase = true)) return false
        }
        return true
    }

    private fun stripLeadingBullet(text: String): String {
        var s = text.trim()
        while (s.startsWith("•") || s.startsWith("-") || s.startsWith("*") || s.startsWith("–")) {
            s = s.substring(1).trim()
        }
        return s
    }

    fun isValidBullets(bullets: List<String>?, title: String?): Boolean {
        if (bullets == null || bullets.size < 2 || bullets.size > 4) return false
        for (b in bullets) {
            if (b.isBlank()) return false
            val cleanB = stripLeadingBullet(b)
            if (cleanB.isBlank()) return false
            if (isBoilerplate(cleanB)) return false
            if (!hasVietnameseCharacteristics(cleanB)) return false
            if (!title.isNullOrBlank() && cleanB.equals(title.trim(), ignoreCase = true)) return false
        }
        return true
    }

    fun isEntityFullyLocalized(entity: NewsEntity): Boolean {
        val effectiveTitle = if (entity.displayTitleVi.isNotBlank()) entity.displayTitleVi else entity.title
        val origTitle = if (entity.originalTitle.isNotBlank()) entity.originalTitle else ""
        if (!isValidDisplayTitleVi(effectiveTitle, origTitle)) return false

        val rawBullets = if (entity.bulletPointsVi.isNotBlank()) {
            entity.bulletPointsVi.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        } else if (entity.bulletPoints.isNotBlank()) {
            entity.bulletPoints.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        if (!isValidBullets(rawBullets, effectiveTitle)) return false

        return true
    }

    fun isNewsFullyLocalized(news: News): Boolean {
        val title = news.getEffectiveTitle()
        val origTitle = news.originalTitle ?: ""
        if (!isValidDisplayTitleVi(title, origTitle)) return false

        val bullets = news.getEffectiveBullets()
        if (!isValidBullets(bullets, title)) return false

        return true
    }
}
