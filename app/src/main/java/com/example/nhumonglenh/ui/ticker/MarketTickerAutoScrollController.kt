package com.example.nhumonglenh.ui.ticker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Controller điều khiển dải Market Ticker tự động di chuyển liên tục từ Phải sang Trái (RTL movement / positive dx).
 *
 * Tính năng chính:
 * 1. Tự động cuộn 1-2 pixel theo chu kỳ nhịp nhàng (mặc định 1px mỗi 20ms).
 * 2. Vòng lặp vô hạn liền mạch (seamless circular loop) bằng cách hoán đổi Set vị trí khi vượt ngưỡng,
 *    không gây giật hình, không tạo khoảng trống sau DOGE.
 * 3. Tự động tạm dừng khi:
 *    - Người dùng chạm giữ/kéo vuốt dải ticker (chế độ tương tác bằng tay).
 *    - TalkBack/chế độ trợ năng (Touch Exploration) đang kích hoạt.
 *    - Tùy chọn hệ thống "Remove animations" (animator_duration_scale == 0) được bật.
 *    - Activity chuyển sang onStop/background.
 * 4. Tự động tiếp tục khi người dùng nhấc tay hoặc Activity onStart.
 * 5. Ngăn chặn triệt để rò rỉ bộ nhớ (leak) bằng cách dọn sạch callback và listener trong onDestroy.
 * 6. Không phát sinh bất kỳ yêu cầu mạng (REST API/WebSocket) nào.
 */
class MarketTickerAutoScrollController(
    val recyclerView: RecyclerView,
    val adapter: MarketTickerAdapter,
    val scrollStepPx: Int = MarketTickerAutoScrollPolicy.DEFAULT_SCROLL_STEP_PX,
    val intervalMs: Long = MarketTickerAutoScrollPolicy.DEFAULT_INTERVAL_MS,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {

    private var isRunning: Boolean = false
    private var isPaused: Boolean = false
    private var isUserTouching: Boolean = false
    private var isScheduled: Boolean = false

    @VisibleForTesting
    var accessibilityActiveOverride: Boolean? = null

    @VisibleForTesting
    var animationsEnabledOverride: Boolean? = null

    private val touchListener = object : RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isUserTouching = true
                    pause()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isUserTouching = false
                    resume()
                }
            }
            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isUserTouching = false
                    resume()
                }
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING, RecyclerView.SCROLL_STATE_SETTLING -> {
                    pause()
                }
                RecyclerView.SCROLL_STATE_IDLE -> {
                    if (!isUserTouching) {
                        resume()
                    }
                }
            }
        }

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            checkAndResetLoop()
        }
    }

    private val scrollRunnable = Runnable {
        isScheduled = false
        if (!MarketTickerAutoScrollPolicy.canAdvance(
                isRunning = isRunning,
                isPaused = isPaused,
                isUserTouching = isUserTouching,
                isAccessibilityActive = isAccessibilityActive(),
                areAnimationsEnabled = areAnimationsEnabled()
            )
        ) {
            pause()
            if (isAccessibilityActive() || !areAnimationsEnabled()) {
                adapter.setInfiniteLoopEnabled(false)
            }
            return@Runnable
        }

        step()
        postScrollNext()
    }

    init {
        recyclerView.addOnItemTouchListener(touchListener)
        recyclerView.addOnScrollListener(scrollListener)
    }

    fun isRunning(): Boolean = isRunning

    fun isPaused(): Boolean = isPaused

    fun isUserTouching(): Boolean = isUserTouching

    fun isAccessibilityActive(): Boolean {
        accessibilityActiveOverride?.let { return it }
        return try {
            val context = recyclerView.context ?: return false
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            am != null && am.isEnabled && am.isTouchExplorationEnabled
        } catch (e: Exception) {
            false
        }
    }

    fun areAnimationsEnabled(): Boolean {
        animationsEnabledOverride?.let { return it }
        return try {
            val context = recyclerView.context ?: return true
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale > 0.0f
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Bắt đầu vòng lặp auto-scroll. Nếu đã đang chạy, không tạo thêm loop mới.
     */
    fun start() {
        if (isRunning) return

        if (isAccessibilityActive() || !areAnimationsEnabled()) {
            adapter.setInfiniteLoopEnabled(false)
            return
        }

        adapter.setInfiniteLoopEnabled(true)
        isRunning = true
        isPaused = false

        ensureInitialAnchorPosition()
        postScrollNext()
    }

    /**
     * Dừng hoàn toàn vòng lặp (gọi tại onStop của Activity).
     */
    fun stop() {
        isRunning = false
        isPaused = true
        cancelScheduled()
    }

    /**
     * Tạm dừng tạm thời khi chạm hoặc cuộn tay.
     */
    fun pause() {
        if (!isRunning) return
        isPaused = true
        cancelScheduled()
    }

    /**
     * Tiếp tục vòng lặp sau khi nhấc tay.
     */
    fun resume() {
        if (!isRunning || isUserTouching) return

        if (isAccessibilityActive() || !areAnimationsEnabled()) {
            pause()
            adapter.setInfiniteLoopEnabled(false)
            return
        }

        isPaused = false
        postScrollNext()
    }

    /**
     * Hủy hoàn toàn các listener và callback (gọi tại onDestroy của Activity).
     */
    fun destroy() {
        stop()
        recyclerView.removeOnItemTouchListener(touchListener)
        recyclerView.removeOnScrollListener(scrollListener)
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Thực hiện một bước cuộn đơn lẻ và kiểm tra ngưỡng lặp lại.
     */
    fun step(): Boolean {
        if (!MarketTickerAutoScrollPolicy.canAdvance(
                isRunning = isRunning,
                isPaused = isPaused,
                isUserTouching = isUserTouching,
                isAccessibilityActive = isAccessibilityActive(),
                areAnimationsEnabled = areAnimationsEnabled()
            )
        ) {
            return false
        }
        val baseCount = adapter.getBaseItemCount()
        if (baseCount <= 0) return false

        // Hướng chuyển động: Nội dung chạy từ phải sang trái (dx > 0 trong LTR)
        recyclerView.scrollBy(scrollStepPx, 0)
        checkAndResetLoop()
        return true
    }

    /**
     * Đảm bảo vị trí ban đầu nằm ở Set 1 để có thể cuộn lùi mượt mà nếu người dùng vuốt ngược.
     */
    private fun ensureInitialAnchorPosition() {
        val baseCount = adapter.getBaseItemCount()
        if (baseCount <= 0 || !adapter.isInfiniteLoopEnabled()) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstPos = layoutManager.findFirstVisibleItemPosition()
        if (firstPos == 0) {
            layoutManager.scrollToPositionWithOffset(baseCount, 0)
        }
    }

    /**
     * Thuật toán dịch chuyển vị trí liền mạch:
     * Sử dụng MarketTickerAutoScrollPolicy để tính toán targetPosition và offset pixel chính xác.
     */
    fun checkAndResetLoop() {
        val baseCount = adapter.getBaseItemCount()
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstPos = layoutManager.findFirstVisibleItemPosition()
        if (firstPos == RecyclerView.NO_POSITION) return

        val firstView = layoutManager.findViewByPosition(firstPos) ?: return
        val offset = firstView.left

        val reset = MarketTickerAutoScrollPolicy.computeSeamlessLoopReset(
            firstVisiblePos = firstPos,
            currentViewLeft = offset,
            baseItemCount = baseCount,
            isInfiniteLoopEnabled = adapter.isInfiniteLoopEnabled()
        )

        if (reset != null && reset.isResetApplied) {
            layoutManager.scrollToPositionWithOffset(reset.targetPosition, reset.pixelOffset)
        }
    }

    private fun postScrollNext() {
        if (isScheduled || !isRunning || isPaused || isUserTouching) return
        isScheduled = true
        handler.postDelayed(scrollRunnable, intervalMs)
    }

    private fun cancelScheduled() {
        handler.removeCallbacks(scrollRunnable)
        isScheduled = false
    }
}
