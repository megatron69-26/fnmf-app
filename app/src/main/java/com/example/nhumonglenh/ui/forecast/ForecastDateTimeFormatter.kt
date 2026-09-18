package com.example.nhumonglenh.ui.forecast

import android.content.Context
import com.example.nhumonglenh.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tiện ích định dạng thời gian cho bản tin Forecast:
 * - Chuẩn hóa timestamp của Forecast từ backend (UTC / ISO-8601).
 * - Chuyển đổi chính xác sang múi giờ Việt Nam (Asia/Ho_Chi_Minh - UTC+7).
 * - Tuyệt đối không dùng thời gian hiện tại của thiết bị để giả mạo độ tươi mới.
 * - Khi timestamp null hoặc lỗi parse: hiển thị "Chưa rõ thời điểm cập nhật" an toàn, không crash.
 */
object ForecastDateTimeFormatter {

    val TARGET_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)

    /**
     * Chuyển đổi timestamp ISO-8601 sang chuỗi hiển thị theo giờ Việt Nam.
     * Sử dụng chuỗi định dạng từ strings.xml.
     */
    fun formatForecastTimestamp(rawTimestamp: String?, context: Context): String {
        val parsedZonedDateTime = parseToVietnamTime(rawTimestamp)
            ?: return context.getString(R.string.forecast_timestamp_unknown)

        val timeStr = parsedZonedDateTime.format(TIME_FORMATTER)
        val dateStr = parsedZonedDateTime.format(DATE_FORMATTER)
        return context.getString(R.string.forecast_updated_at, timeStr, dateStr)
    }

    /**
     * Phương thức thuần (Pure function) hỗ trợ kiểm thử không cần phụ thuộc Android context.
     */
    fun formatToVietnamString(rawTimestamp: String?): String {
        val parsedZonedDateTime = parseToVietnamTime(rawTimestamp)
            ?: return "Chưa rõ thời điểm cập nhật"

        val timeStr = parsedZonedDateTime.format(TIME_FORMATTER)
        val dateStr = parsedZonedDateTime.format(DATE_FORMATTER)
        return "Cập nhật lúc $timeStr, $dateStr"
    }

    /**
     * Parse timestamp linh hoạt:
     * - Có Zone/Offset (Z, +00:00, +07:00, ...) -> chuyển sang Asia/Ho_Chi_Minh giữ nguyên instant.
     * - Không có Zone/Offset (chuẩn LocalDateTime mặc định của Spring container UTC) -> gán UTC instant rồi chuyển sang Asia/Ho_Chi_Minh.
     * - Date-only (yyyy-MM-dd) -> gán đầu ngày.
     */
    fun parseToVietnamTime(rawTimestamp: String?): ZonedDateTime? {
        if (rawTimestamp.isNullOrBlank()) return null
        val clean = rawTimestamp.trim()
        if (clean.isEmpty()) return null

        // 1. Parse với Offset/Zone đầy đủ
        try {
            val zdt = ZonedDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME)
            return zdt.withZoneSameInstant(TARGET_ZONE)
        } catch (_: Exception) {
        }

        try {
            val instant = Instant.parse(clean)
            return instant.atZone(TARGET_ZONE)
        } catch (_: Exception) {
        }

        // 2. Parse ISO LocalDateTime (chuẩn backend Spring container UTC)
        try {
            val ldt = LocalDateTime.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            return ldt.atZone(ZoneOffset.UTC).withZoneSameInstant(TARGET_ZONE)
        } catch (_: Exception) {
        }

        // 3. Parse Date-only
        try {
            val date = LocalDate.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE)
            return date.atStartOfDay(TARGET_ZONE)
        } catch (_: Exception) {
        }

        return null
    }
}
