package com.example.nhumonglenh.ui.common

import android.content.SharedPreferences
import java.util.Locale
import kotlin.math.min

/**
 * Quản lý lịch tự động thử lại (Auto-Refresh / Auto-Revalidation Scheduler):
 * - Độc lập hoàn toàn với timestamp dữ liệu (data freshness metadata).
 * - Khi thành công (dữ liệu mới): đặt mốc tiếp theo là now + cycleMs (24h cho Forecast, 15m cho News), reset failures = 0.
 * - Khi lỗi / stale / empty: áp dụng bounded exponential backoff, tuyệt đối KHÔNG sửa giả metadata dữ liệu.
 * - Khi chưa có timestamp (cold start): khởi tạo mốc duy nhất vào SharedPreferences để đếm ngược ổn định,
 *   không tính lại now + cycle mỗi giây gây kẹt countdown.
 */
class AutoRefreshScheduler(
    private val prefs: SharedPreferences,
    private val keyNextAttempt: String,
    private val keyFailures: String,
    val cycleMs: Long,
    val baseBackoffMs: Long,
    val maxBackoffMs: Long
) {
    fun getOrInitNextAttemptAt(now: Long, lastDataTimestamp: Long): Long {
        val saved = prefs.getLong(keyNextAttempt, 0L)
        if (saved > 0L) return saved

        val initial = if (lastDataTimestamp > 0L) {
            lastDataTimestamp + cycleMs
        } else {
            now + cycleMs
        }
        prefs.edit().putLong(keyNextAttempt, initial).apply()
        return initial
    }

    fun getNextAttemptAt(): Long = prefs.getLong(keyNextAttempt, 0L)

    fun setNextAttemptAt(epochMs: Long) {
        prefs.edit().putLong(keyNextAttempt, epochMs).apply()
    }

    fun getConsecutiveFailures(): Int = prefs.getInt(keyFailures, 0)

    fun recordSuccess(now: Long = System.currentTimeMillis()): Long {
        val nextAttempt = now + cycleMs
        prefs.edit()
            .putLong(keyNextAttempt, nextAttempt)
            .putInt(keyFailures, 0)
            .apply()
        return nextAttempt
    }

    fun recordFailure(now: Long = System.currentTimeMillis()): Long {
        val currentFailures = getConsecutiveFailures()
        val nextFailures = currentFailures + 1
        val backoff = computeBackoff(nextFailures, baseBackoffMs, maxBackoffMs)
        val nextAttempt = now + backoff

        prefs.edit()
            .putLong(keyNextAttempt, nextAttempt)
            .putInt(keyFailures, nextFailures)
            .apply()

        return nextAttempt
    }

    fun getRemainingMs(now: Long = System.currentTimeMillis(), lastDataTimestamp: Long = 0L): Long {
        val target = getOrInitNextAttemptAt(now, lastDataTimestamp)
        return (target - now).coerceAtLeast(0L)
    }

    fun reset() {
        prefs.edit().remove(keyNextAttempt).remove(keyFailures).apply()
    }

    companion object {
        fun computeBackoff(failures: Int, baseMs: Long, maxMs: Long): Long {
            if (failures <= 1) return baseMs
            val shift = min(failures - 1, 10)
            val multiplier = 1L shl shift
            val computed = baseMs * multiplier
            return if (computed > maxMs || computed <= 0L) maxMs else computed
        }

        fun formatCountdown(remainingMs: Long, includeHours: Boolean): String {
            val totalSeconds = remainingMs / 1000
            val hours = totalSeconds / 3600
            val minutes = if (includeHours) (totalSeconds % 3600) / 60 else totalSeconds / 60
            val seconds = totalSeconds % 60

            return if (includeHours) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }
}
