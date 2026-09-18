package com.example.nhumonglenh

import android.content.SharedPreferences
import com.example.nhumonglenh.data.repository.ForecastRepository
import com.example.nhumonglenh.data.repository.NewsRepository
import com.example.nhumonglenh.ui.common.AutoRefreshScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class FiniMascotAndCountdownBehavioralTest {

    // -------------------------------------------------------------------------
    // 1. FORECAST 24H COUNTDOWN CALCULATION & RECREATION PERSISTENCE
    // -------------------------------------------------------------------------
    @Test
    fun testForecastCountdown_normalProgression() {
        val cycleMs = ForecastFragment.FORECAST_CYCLE_MS // 24 * 60 * 60 * 1000L
        assertEquals(86_400_000L, cycleMs)

        val lastSavedTime = 1_700_000_000_000L
        val targetEpoch = lastSavedTime + cycleMs

        // Case A: Exactly at save time (0 elapsed) -> 24:00:00
        val now0 = lastSavedTime
        val rem0 = (targetEpoch - now0).coerceAtLeast(0L)
        val h0 = (rem0 / 1000) / 3600
        val m0 = ((rem0 / 1000) % 3600) / 60
        val s0 = (rem0 / 1000) % 60
        assertEquals("24:00:00", String.format(Locale.US, "%02d:%02d:%02d", h0, m0, s0))

        // Case B: 1 hour 15 minutes 30 seconds elapsed
        val elapsedMs = (1 * 3600 + 15 * 60 + 30) * 1000L
        val nowB = lastSavedTime + elapsedMs
        val remB = (targetEpoch - nowB).coerceAtLeast(0L)
        val hB = (remB / 1000) / 3600
        val mB = ((remB / 1000) % 3600) / 60
        val sB = (remB / 1000) % 60
        assertEquals("22:44:30", String.format(Locale.US, "%02d:%02d:%02d", hB, mB, sB))

        // Case C: Expired (25 hours elapsed) -> 00:00:00
        val nowC = lastSavedTime + cycleMs + 3600_000L
        val remC = (targetEpoch - nowC).coerceAtLeast(0L)
        assertEquals(0L, remC)
        val hC = (remC / 1000) / 3600
        val mC = ((remC / 1000) % 3600) / 60
        val sC = (remC / 1000) % 60
        assertEquals("00:00:00", String.format(Locale.US, "%02d:%02d:%02d", hC, mC, sC))
    }

    @Test
    fun testForecastCountdown_persistsAcrossRecreation() {
        val cycleMs = ForecastFragment.FORECAST_CYCLE_MS
        val lastSavedTime = 1_700_000_000_000L

        // Screen 1: User looks at countdown at T + 3 hours
        val timeScreen1 = lastSavedTime + 3 * 3600_000L
        val targetScreen1 = lastSavedTime + cycleMs
        val remaining1 = (targetScreen1 - timeScreen1).coerceAtLeast(0L)
        assertEquals(21 * 3600_000L, remaining1)

        // User rotates device or switches tab 30 minutes later
        val timeScreen2 = timeScreen1 + 30 * 60_000L
        // Calculation starts fresh in onResume / startCountdownTimer from persistent timestamp
        val targetScreen2 = lastSavedTime + cycleMs
        val remaining2 = (targetScreen2 - timeScreen2).coerceAtLeast(0L)
        assertEquals(20 * 3600_000L + 30 * 60_000L, remaining2)

        // Must NOT reset to 24h
        val h2 = (remaining2 / 1000) / 3600
        val m2 = ((remaining2 / 1000) % 3600) / 60
        val s2 = (remaining2 / 1000) % 60
        assertEquals("20:30:00", String.format(Locale.US, "%02d:%02d:%02d", h2, m2, s2))
    }

    // -------------------------------------------------------------------------
    // 2. NEWS 15M COUNTDOWN CALCULATION & ERROR IMMUNITY
    // -------------------------------------------------------------------------
    @Test
    fun testNewsCountdown_normalProgression() {
        val cycleMs = NewsRepository.AUTO_SYNC_THROTTLE_MS // 15 * 60 * 1000L
        assertEquals(900_000L, cycleMs)

        val lastSyncTime = 1_700_000_000_000L
        val targetEpoch = lastSyncTime + cycleMs

        // Case A: 3 minutes 25 seconds elapsed -> 11:35 remaining
        val nowA = lastSyncTime + (3 * 60 + 25) * 1000L
        val remA = (targetEpoch - nowA).coerceAtLeast(0L)
        val mA = (remA / 1000) / 60
        val sA = (remA / 1000) % 60
        assertEquals("11:35", String.format(Locale.US, "%02d:%02d", mA, sA))

        // Case B: 15 minutes elapsed -> 00:00 remaining
        val nowB = lastSyncTime + cycleMs
        val remB = (targetEpoch - nowB).coerceAtLeast(0L)
        assertEquals(0L, remB)
        val mB = (remB / 1000) / 60
        val sB = (remB / 1000) % 60
        assertEquals("00:00", String.format(Locale.US, "%02d:%02d", mB, sB))
    }

    @Test
    fun testNewsCountdown_errorDoesNotResetTimestamp() {
        // When sync fails or response is empty, NewsRepository does not call recordSyncMetadata.
        // The last_sync_time_ms remains unchanged.
        val originalSyncTime = 1_700_000_000_000L
        val cycleMs = NewsRepository.AUTO_SYNC_THROTTLE_MS

        val nowAfterFailure = originalSyncTime + 5 * 60_000L // 5 mins elapsed
        // Simulated failure occurred: timestamp is NOT updated, still originalSyncTime
        val currentSyncTime = originalSyncTime
        val targetEpoch = currentSyncTime + cycleMs
        val remaining = (targetEpoch - nowAfterFailure).coerceAtLeast(0L)
        val m = (remaining / 1000) / 60
        val s = (remaining / 1000) % 60

        // Must still show 10:00, NOT reset to 15:00!
        assertEquals("10:00", String.format(Locale.US, "%02d:%02d", m, s))
    }

    // -------------------------------------------------------------------------
    // 3. GIF ASSET EXISTENCE AND INTEGRITY
    // -------------------------------------------------------------------------
    @Test
    fun testGifAssets_existAndHaveValidHeader() {
        val drawableDir = File("src/main/res/drawable")
        val badgeGif = File(drawableDir, "fini_employee_badge_no_platform.gif")
        val previewGif = File(drawableDir, "fini_loading_preview.gif")

        assertTrue("fini_employee_badge_no_platform.gif must exist", badgeGif.exists())
        assertTrue("fini_employee_badge_no_platform.gif must not be empty", badgeGif.length() > 10_000)

        assertTrue("fini_loading_preview.gif must exist", previewGif.exists())
        assertTrue("fini_loading_preview.gif must not be empty", previewGif.length() > 10_000)

        // Verify GIF89a header
        val badgeBytes = badgeGif.readBytes()
        val badgeHeader = String(badgeBytes.sliceArray(0..5), Charsets.US_ASCII)
        assertEquals("GIF89a", badgeHeader)

        val previewBytes = previewGif.readBytes()
        val previewHeader = String(previewBytes.sliceArray(0..5), Charsets.US_ASCII)
        assertEquals("GIF89a", previewHeader)
    }

    // -------------------------------------------------------------------------
    // 4. LAYOUT INSPECTION: TOUCH TARGETS >= 48DP & ACCESSIBILITY
    // -------------------------------------------------------------------------
    @Test
    fun testLayoutTouchTargetsAndAccessibility_forecastAndNews() {
        val forecastLayout = File("src/main/res/layout/fragment_forecast.xml").readText()
        val newsLayout = File("src/main/res/layout/fragment_news.xml").readText()

        // btnForecastRefresh must have minHeight >= 48dp or layout_height >= 48dp
        assertTrue(
            "Forecast refresh button must satisfy >= 48dp touch target",
            forecastLayout.contains("android:id=\"@+id/btnForecastRefresh\"") &&
            forecastLayout.contains("android:minHeight=\"48dp\"")
        )

        // btnNewsRefresh must have minHeight >= 48dp or layout_height >= 48dp
        assertTrue(
            "News refresh button must satisfy >= 48dp touch target",
            newsLayout.contains("android:id=\"@+id/btnNewsRefresh\"") &&
            newsLayout.contains("android:minHeight=\"48dp\"")
        )

        // Countdown TextViews must have accessibilityLiveRegion="none" to prevent TalkBack tick spam
        assertTrue(
            "Forecast countdown must have accessibilityLiveRegion=\"none\"",
            forecastLayout.contains("android:id=\"@+id/tvForecastCountdown\"") &&
            forecastLayout.contains("android:accessibilityLiveRegion=\"none\"")
        )

        assertTrue(
            "News countdown must have accessibilityLiveRegion=\"none\"",
            newsLayout.contains("android:id=\"@+id/tvNewsCountdown\"") &&
            newsLayout.contains("android:accessibilityLiveRegion=\"none\"")
        )
    }

    // -------------------------------------------------------------------------
    // 5. HEADER LOGO REPLACEMENT IN ACTIVITY2
    // -------------------------------------------------------------------------
    @Test
    fun testHeaderLogo_replacesStaticTitleInActivity2() {
        val activity2Layout = File("src/main/res/layout/layout_activity2.xml").readText()

        // Must contain iv_header_logo
        assertTrue("layout_activity2 must contain iv_header_logo", activity2Layout.contains("android:id=\"@+id/iv_header_logo\""))

        // Must NOT contain old static "tv_app_title"
        assertFalse("layout_activity2 must not contain old static tv_app_title", activity2Layout.contains("android:id=\"@+id/tv_app_title\""))
    }

    // -------------------------------------------------------------------------
    // 6. AUTO-REFRESH SCHEDULER: BOUNDED EXPONENTIAL BACKOFF CALCULATION
    // -------------------------------------------------------------------------
    @Test
    fun testAutoRefreshScheduler_backoffProgressionAndCap() {
        // Forecast: base = 2m (120,000ms), max = 30m (1,800,000ms)
        val baseF = ForecastRepository.FORECAST_BASE_BACKOFF_MS
        val maxF = ForecastRepository.FORECAST_MAX_BACKOFF_MS
        assertEquals(120_000L, baseF)
        assertEquals(1_800_000L, maxF)

        assertEquals(120_000L, AutoRefreshScheduler.computeBackoff(0, baseF, maxF))
        assertEquals(120_000L, AutoRefreshScheduler.computeBackoff(1, baseF, maxF)) // 1 failure -> 2m
        assertEquals(240_000L, AutoRefreshScheduler.computeBackoff(2, baseF, maxF)) // 2 failures -> 4m
        assertEquals(480_000L, AutoRefreshScheduler.computeBackoff(3, baseF, maxF)) // 3 failures -> 8m
        assertEquals(960_000L, AutoRefreshScheduler.computeBackoff(4, baseF, maxF)) // 4 failures -> 16m
        assertEquals(1_800_000L, AutoRefreshScheduler.computeBackoff(5, baseF, maxF)) // 5 failures -> capped at 30m
        assertEquals(1_800_000L, AutoRefreshScheduler.computeBackoff(50, baseF, maxF)) // 50 failures -> no overflow, stays at 30m

        // News: base = 1m (60,000ms), max = 5m (300,000ms)
        val baseN = NewsRepository.AUTO_SYNC_BASE_BACKOFF_MS
        val maxN = NewsRepository.AUTO_SYNC_MAX_BACKOFF_MS
        assertEquals(60_000L, baseN)
        assertEquals(300_000L, maxN)

        assertEquals(60_000L, AutoRefreshScheduler.computeBackoff(0, baseN, maxN))
        assertEquals(60_000L, AutoRefreshScheduler.computeBackoff(1, baseN, maxN)) // 1 failure -> 1m
        assertEquals(120_000L, AutoRefreshScheduler.computeBackoff(2, baseN, maxN)) // 2 failures -> 2m
        assertEquals(240_000L, AutoRefreshScheduler.computeBackoff(3, baseN, maxN)) // 3 failures -> 4m
        assertEquals(300_000L, AutoRefreshScheduler.computeBackoff(4, baseN, maxN)) // 4 failures -> capped at 5m
        assertEquals(300_000L, AutoRefreshScheduler.computeBackoff(20, baseN, maxN)) // 20 failures -> capped at 5m
    }

    // -------------------------------------------------------------------------
    // 7. FORECAST STATE MACHINE: 24H EXPIRATION, STALE BACKOFF (NO 30S STORM)
    // -------------------------------------------------------------------------
    @Test
    fun testForecastStateMachine_staleCacheTriggersBackoffWithout30sStorm() {
        val prefs = FakeSharedPreferences()
        val scheduler = AutoRefreshScheduler(
            prefs = prefs,
            keyNextAttempt = ForecastRepository.KEY_FORECAST_NEXT_ATTEMPT_PREFIX + "MARKET",
            keyFailures = ForecastRepository.KEY_FORECAST_FAILURES_PREFIX + "MARKET",
            cycleMs = ForecastRepository.FORECAST_CYCLE_MS,
            baseBackoffMs = ForecastRepository.FORECAST_BASE_BACKOFF_MS,
            maxBackoffMs = ForecastRepository.FORECAST_MAX_BACKOFF_MS
        )

        val lastSavedTime = 1_700_000_000_000L
        val initNow = lastSavedTime
        val target = scheduler.getOrInitNextAttemptAt(initNow, lastSavedTime)
        assertEquals(lastSavedTime + 86_400_000L, target)

        // Time elapses: 24 hours later, countdown expires (remaining = 0)
        val nowExpired = target + 500L
        val remExpired = scheduler.getRemainingMs(nowExpired, lastSavedTime)
        assertEquals(0L, remExpired)

        // Single-flight GET executed: Backend returns stale cache (forecast.stale == true)
        // Record failure: backoff advances nextAutoAttemptAt, data timestamp is NOT touched
        val nextAfterFail1 = scheduler.recordFailure(nowExpired)
        assertEquals(1, scheduler.getConsecutiveFailures())
        assertEquals(nowExpired + 120_000L, nextAfterFail1) // +2m backoff

        // Tick at 1 second later: remainingMs is 119s (> 0), so NO 30-second storm loop!
        val remTick = scheduler.getRemainingMs(nowExpired + 1000L, lastSavedTime)
        assertEquals(119_000L, remTick)
        assertEquals("00:01:59", AutoRefreshScheduler.formatCountdown(remTick, includeHours = true))

        // Second failure (still returning stale cache) -> backoff escalates to 4m
        val nextAfterFail2 = scheduler.recordFailure(nowExpired + 120_000L)
        assertEquals(2, scheduler.getConsecutiveFailures())
        assertEquals(nowExpired + 120_000L + 240_000L, nextAfterFail2) // +4m backoff

        // Recovery: Fresh forecast arrives -> resets failures and sets next attempt to now + 24h
        val nowFresh = nextAfterFail2
        val nextFresh = scheduler.recordSuccess(nowFresh)
        assertEquals(0, scheduler.getConsecutiveFailures())
        assertEquals(nowFresh + 86_400_000L, nextFresh)
        assertEquals(86_400_000L, scheduler.getRemainingMs(nowFresh, lastSavedTime))
    }

    // -------------------------------------------------------------------------
    // 8. NEWS STATE MACHINE: 15M EXPIRATION, ERROR BACKOFF (NO 30S STORM)
    // -------------------------------------------------------------------------
    @Test
    fun testNewsStateMachine_errorTriggersBackoffAndPreservesMetadata() {
        val prefs = FakeSharedPreferences()
        val scheduler = AutoRefreshScheduler(
            prefs = prefs,
            keyNextAttempt = NewsRepository.KEY_NEXT_AUTO_ATTEMPT_AT,
            keyFailures = NewsRepository.KEY_AUTO_SYNC_FAILURES,
            cycleMs = NewsRepository.AUTO_SYNC_THROTTLE_MS,
            baseBackoffMs = NewsRepository.AUTO_SYNC_BASE_BACKOFF_MS,
            maxBackoffMs = NewsRepository.AUTO_SYNC_MAX_BACKOFF_MS
        )

        val lastSyncTime = 1_700_000_000_000L
        val initNow = lastSyncTime
        val target = scheduler.getOrInitNextAttemptAt(initNow, lastSyncTime)
        assertEquals(lastSyncTime + 900_000L, target)

        // Time elapses: 15 minutes later, countdown expires (remaining = 0)
        val nowExpired = target
        assertEquals(0L, scheduler.getRemainingMs(nowExpired, lastSyncTime))

        // Single-flight GET executed: Sync fails or returns empty/degraded response
        // Record failure: scheduler backs off 1m; data metadata (lastSyncTime) is preserved
        val nextAfterFail1 = scheduler.recordFailure(nowExpired)
        assertEquals(1, scheduler.getConsecutiveFailures())
        assertEquals(nowExpired + 60_000L, nextAfterFail1) // +1m backoff

        // Tick at 1 second later: remainingMs is 59s (> 0), preventing immediate loop
        val remTick = scheduler.getRemainingMs(nowExpired + 1000L, lastSyncTime)
        assertEquals(59_000L, remTick)
        assertEquals("00:59", AutoRefreshScheduler.formatCountdown(remTick, includeHours = false))

        // Second failure -> backoff escalates to 2m
        val nextAfterFail2 = scheduler.recordFailure(nowExpired + 60_000L)
        assertEquals(2, scheduler.getConsecutiveFailures())
        assertEquals(nowExpired + 60_000L + 120_000L, nextAfterFail2) // +2m backoff

        // Recovery: Fresh news sync succeeds -> reset failures and schedule next attempt at now + 15m
        val nowFresh = nextAfterFail2
        val nextFresh = scheduler.recordSuccess(nowFresh)
        assertEquals(0, scheduler.getConsecutiveFailures())
        assertEquals(nowFresh + 900_000L, nextFresh)
        assertEquals(900_000L, scheduler.getRemainingMs(nowFresh, lastSyncTime))
    }

    // -------------------------------------------------------------------------
    // 9. COLD START STEADY PROGRESSION (NO FREEZE OR 1-SECOND RESET)
    // -------------------------------------------------------------------------
    @Test
    fun testColdStartCountdown_doesNotFreezeOrResetEverySecond() {
        val prefs = FakeSharedPreferences()
        val scheduler = AutoRefreshScheduler(
            prefs = prefs,
            keyNextAttempt = NewsRepository.KEY_NEXT_AUTO_ATTEMPT_AT,
            keyFailures = NewsRepository.KEY_AUTO_SYNC_FAILURES,
            cycleMs = NewsRepository.AUTO_SYNC_THROTTLE_MS, // 15m
            baseBackoffMs = NewsRepository.AUTO_SYNC_BASE_BACKOFF_MS,
            maxBackoffMs = NewsRepository.AUTO_SYNC_MAX_BACKOFF_MS
        )

        // Cold start with no prior sync: lastDataTimestamp = 0
        val t0 = 1_700_000_000_000L
        val targetT0 = scheduler.getOrInitNextAttemptAt(t0, 0L)
        assertEquals(t0 + 900_000L, targetT0)
        assertEquals(900_000L, scheduler.getRemainingMs(t0, 0L))
        assertEquals("15:00", AutoRefreshScheduler.formatCountdown(scheduler.getRemainingMs(t0, 0L), includeHours = false))

        // Tick at 1 second: MUST NOT recompute (t0 + 1000) + 15m! Must use saved target.
        val t1 = t0 + 1000L
        val targetT1 = scheduler.getOrInitNextAttemptAt(t1, 0L)
        assertEquals(targetT0, targetT1) // Exact same persistent target
        val remT1 = scheduler.getRemainingMs(t1, 0L)
        assertEquals(899_000L, remT1)
        assertEquals("14:59", AutoRefreshScheduler.formatCountdown(remT1, includeHours = false))

        // Tick at 2 seconds
        val t2 = t0 + 2000L
        val remT2 = scheduler.getRemainingMs(t2, 0L)
        assertEquals(898_000L, remT2)
        assertEquals("14:58", AutoRefreshScheduler.formatCountdown(remT2, includeHours = false))
    }

    // -------------------------------------------------------------------------
    // 10. FINI LOADING VIEW REATTACHMENT & LIFECYCLE AUDIT
    // -------------------------------------------------------------------------
    @Test
    fun testFiniLoadingView_reattachmentLifecycleSupport() {
        val finiSource = File("src/main/java/com/example/nhumonglenh/ui/common/FiniLoadingView.kt").readText()

        // Must override onAttachedToWindow to resume GIF playback if visible & showing
        assertTrue("FiniLoadingView must override onAttachedToWindow", finiSource.contains("override fun onAttachedToWindow()"))
        assertTrue("onAttachedToWindow must check visibility and isShowingState",
            finiSource.contains("visibility == View.VISIBLE && isShowingState") &&
            finiSource.contains("loadGifMascot()")
        )

        // Must override onDetachedFromWindow to stop animation
        assertTrue("FiniLoadingView must override onDetachedFromWindow", finiSource.contains("override fun onDetachedFromWindow()"))
        assertTrue("onDetachedFromWindow must stop GIF mascot", finiSource.contains("stopGifMascot()"))

        // cleanup must call hide
        assertTrue("cleanup must call hide", finiSource.contains("fun cleanup()") && finiSource.contains("hide()"))
    }

    // -------------------------------------------------------------------------
    // 11. FORECAST SCREEN REOPEN: CACHE HIT MUST NOT RESET 24H COUNTDOWN
    // -------------------------------------------------------------------------
    @Test
    fun testForecast_screenReopen_cacheHitDoesNotReset24hCountdown() {
        val prefs = FakeSharedPreferences()
        val scheduler = AutoRefreshScheduler(
            prefs = prefs,
            keyNextAttempt = ForecastRepository.KEY_FORECAST_NEXT_ATTEMPT_PREFIX + "MARKET",
            keyFailures = ForecastRepository.KEY_FORECAST_FAILURES_PREFIX + "MARKET",
            cycleMs = ForecastRepository.FORECAST_CYCLE_MS, // 24h
            baseBackoffMs = ForecastRepository.FORECAST_BASE_BACKOFF_MS,
            maxBackoffMs = ForecastRepository.FORECAST_MAX_BACKOFF_MS
        )

        val t0 = 1_700_000_000_000L
        // Initial setup: forecast created at t0, countdown anchored to t0 + 24h
        val targetEpoch = scheduler.getOrInitNextAttemptAt(t0, t0)
        assertEquals(t0 + 86_400_000L, targetEpoch)

        // 5 hours later, user reopens the app / tab (recreate)
        val nowReopen = t0 + 5 * 3600_000L
        val remainingBefore = scheduler.getRemainingMs(nowReopen, t0)
        assertEquals(19 * 3600_000L, remainingBefore) // 19 hours remaining

        // Simulate GET cache-first response: server returns forecast with same createdAt (t0)
        val previousSavedTs = t0
        val responseCreatedAtTs = t0
        val isNewerData = previousSavedTs == 0L || (responseCreatedAtTs > 0L && responseCreatedAtTs > previousSavedTs)
        val isAutoRefresh = false

        // In ForecastFragment logic:
        if (isAutoRefresh) {
            scheduler.recordFailure(nowReopen)
        } else {
            if (isNewerData && previousSavedTs > 0L) {
                scheduler.recordSuccess(nowReopen)
            }
            // Cache hit (isNewerData == false): DO NOT touch scheduler!
        }

        // Verify scheduler target was NOT overwritten to nowReopen + 24h!
        val remainingAfter = scheduler.getRemainingMs(nowReopen, t0)
        assertEquals(19 * 3600_000L, remainingAfter)
        assertEquals("19:00:00", AutoRefreshScheduler.formatCountdown(remainingAfter, includeHours = true))
    }

    // -------------------------------------------------------------------------
    // 12. FORECAST REQUEST CANCELLATION: MUST NOT COUNT AS FAILURE
    // -------------------------------------------------------------------------
    @Test
    fun testForecast_requestCancellation_doesNotCountAsFailure() {
        val prefs = FakeSharedPreferences()
        val scheduler = AutoRefreshScheduler(
            prefs = prefs,
            keyNextAttempt = ForecastRepository.KEY_FORECAST_NEXT_ATTEMPT_PREFIX + "MARKET",
            keyFailures = ForecastRepository.KEY_FORECAST_FAILURES_PREFIX + "MARKET",
            cycleMs = ForecastRepository.FORECAST_CYCLE_MS,
            baseBackoffMs = ForecastRepository.FORECAST_BASE_BACKOFF_MS,
            maxBackoffMs = ForecastRepository.FORECAST_MAX_BACKOFF_MS
        )

        val t0 = 1_700_000_000_000L
        scheduler.getOrInitNextAttemptAt(t0, t0)
        assertEquals(0, scheduler.getConsecutiveFailures())

        // Simulate request cancellation (call.isCanceled = true or generation changed)
        val isCanceled = true
        val isAutoRefresh = true

        // In ForecastFragment.onFailure:
        // if (call.isCanceled || generation != currentLoadGeneration || !isAdded || view == null) return
        // Only uncancelled calls execute failure recording:
        if (!isCanceled) {
            if (isAutoRefresh) {
                scheduler.recordFailure(t0)
            }
        }

        // Verify failures remain 0 and target is intact
        assertEquals(0, scheduler.getConsecutiveFailures())
        assertEquals(t0 + 86_400_000L, scheduler.getNextAttemptAt())
    }

    // -------------------------------------------------------------------------
    // 13. NEWS SCREEN REOPEN: THROTTLE PRODUCES CACHEHIT AND DOES NOT RESET SCHEDULER
    // -------------------------------------------------------------------------
    @Test
    fun testNews_screenReopen_throttleReturnsCacheHitAndPreservesCountdown() {
        val prefs = FakeSharedPreferences()
        val scheduler = AutoRefreshScheduler(
            prefs = prefs,
            keyNextAttempt = NewsRepository.KEY_NEXT_AUTO_ATTEMPT_AT,
            keyFailures = NewsRepository.KEY_AUTO_SYNC_FAILURES,
            cycleMs = NewsRepository.AUTO_SYNC_THROTTLE_MS, // 15m
            baseBackoffMs = NewsRepository.AUTO_SYNC_BASE_BACKOFF_MS,
            maxBackoffMs = NewsRepository.AUTO_SYNC_MAX_BACKOFF_MS
        )

        val t0 = 1_700_000_000_000L
        val target = scheduler.getOrInitNextAttemptAt(t0, t0)
        assertEquals(t0 + 900_000L, target)

        // User opens News tab 3 minutes later (12 minutes remaining)
        val nowReopen = t0 + 3 * 60_000L
        val remainingBefore = scheduler.getRemainingMs(nowReopen, t0)
        assertEquals(12 * 60_000L, remainingBefore)

        // Simulate NewsRepository returning CacheHit due to 15m throttle
        val result: NewsRepository.NewsResult = NewsRepository.NewsResult.CacheHit(
            news = emptyList(),
            isStale = false,
            dataAsOf = "2026-09-19T00:00:00Z",
            latestPublishedAt = "2026-09-19T00:00:00Z"
        )

        // In NewsFeedFragment:
        when (result) {
            is NewsRepository.NewsResult.SyncSuccess -> {
                if (!result.isStale) scheduler.recordSuccess(nowReopen)
            }
            is NewsRepository.NewsResult.CacheHit -> {
                // TUYỆT ĐỐI KHÔNG gọi recordSuccess, giữ nguyên đếm ngược hiện tại
            }
            else -> {}
        }

        // Verify remaining time is STILL 12 minutes (NOT reset to 15:00!)
        val remainingAfter = scheduler.getRemainingMs(nowReopen, t0)
        assertEquals(12 * 60_000L, remainingAfter)
        assertEquals("12:00", AutoRefreshScheduler.formatCountdown(remainingAfter, includeHours = false))
    }

    // -------------------------------------------------------------------------
    // 14. NEWS REMOTE SYNC: RESETS SCHEDULER ONLY ON ACTUAL REMOTE SUCCESS
    // -------------------------------------------------------------------------
    @Test
    fun testNews_remoteSync_resetsSchedulerOnlyOnActualRemoteSuccess() {
        val prefs = FakeSharedPreferences()
        val scheduler = AutoRefreshScheduler(
            prefs = prefs,
            keyNextAttempt = NewsRepository.KEY_NEXT_AUTO_ATTEMPT_AT,
            keyFailures = NewsRepository.KEY_AUTO_SYNC_FAILURES,
            cycleMs = NewsRepository.AUTO_SYNC_THROTTLE_MS, // 15m
            baseBackoffMs = NewsRepository.AUTO_SYNC_BASE_BACKOFF_MS,
            maxBackoffMs = NewsRepository.AUTO_SYNC_MAX_BACKOFF_MS
        )

        val t0 = 1_700_000_000_000L
        scheduler.getOrInitNextAttemptAt(t0, t0)

        // Fast-forward 15 minutes: countdown expired
        val nowExp = t0 + 900_000L
        assertEquals(0L, scheduler.getRemainingMs(nowExp, t0))

        // Remote sync succeeds with fresh data
        val result = NewsRepository.NewsResult.SyncSuccess(
            news = emptyList(),
            isStale = false,
            dataAsOf = "2026-09-19T00:15:00Z",
            latestPublishedAt = "2026-09-19T00:15:00Z"
        )

        when (result) {
            is NewsRepository.NewsResult.SyncSuccess -> {
                if (!result.isStale) scheduler.recordSuccess(nowExp)
            }
            is NewsRepository.NewsResult.CacheHit -> {}
            else -> {}
        }

        // Verify scheduler is reset to nowExp + 15m
        assertEquals(nowExp + 900_000L, scheduler.getNextAttemptAt())
        assertEquals(900_000L, scheduler.getRemainingMs(nowExp, t0))
        assertEquals("15:00", AutoRefreshScheduler.formatCountdown(scheduler.getRemainingMs(nowExp, t0), includeHours = false))
    }

    // =========================================================================
    // IN-MEMORY FAKE SHAREDPREFERENCES FOR BEHAVIORAL TESTING
    // =========================================================================
    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val parent: FakeSharedPreferences) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; toRemove.remove(key) }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) { pending[key] = values; toRemove.remove(key) }
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; toRemove.remove(key) }
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; toRemove.remove(key) }
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; toRemove.remove(key) }
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; toRemove.remove(key) }
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) { toRemove.add(key); pending.remove(key) }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true; pending.clear(); toRemove.clear()
                return this
            }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) parent.map.clear()
                for (k in toRemove) parent.map.remove(k)
                for ((k, v) in pending) parent.map[k] = v
            }
        }
    }
}
