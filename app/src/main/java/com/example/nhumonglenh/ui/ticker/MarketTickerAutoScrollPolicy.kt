package com.example.nhumonglenh.ui.ticker

/**
 * Pure policy and animation mathematics for MarketTicker auto-scroll.
 * Operates without framework coupling so it can be 100% verified via behavioral regression tests.
 */
object MarketTickerAutoScrollPolicy {

    const val DEFAULT_SCROLL_STEP_PX = 1
    const val DEFAULT_INTERVAL_MS = 20L
    const val DEFAULT_REPEAT_FACTOR = 4

    enum class ScrollDirection {
        RIGHT_TO_LEFT, // Content moves right to left (positive dx in LTR)
        LEFT_TO_RIGHT
    }

    /**
     * Determines whether positive dx scrolls content from right to left in LTR layout.
     * In Android RecyclerView with horizontal LinearLayoutManager (LTR):
     * dx > 0 moves the viewport right, which moves the visible content from RIGHT to LEFT.
     */
    fun resolveScrollDirection(dx: Int): ScrollDirection {
        return if (dx >= 0) ScrollDirection.RIGHT_TO_LEFT else ScrollDirection.LEFT_TO_RIGHT
    }

    /**
     * Calculates the target position and pixel offset when wrapping the circular loop.
     * Given baseCount items (e.g. 8), repeatFactor (e.g. 4 -> 32 items total):
     * - If firstVisiblePos >= baseCount * 2: shift back by baseCount keeping exact pixel offset.
     * - If firstVisiblePos < baseCount: shift forward by baseCount keeping exact pixel offset.
     * Returns null or isResetApplied=false if no shift is needed.
     */
    data class LoopResetResult(
        val targetPosition: Int,
        val pixelOffset: Int,
        val isResetApplied: Boolean
    )

    fun computeSeamlessLoopReset(
        firstVisiblePos: Int,
        currentViewLeft: Int,
        baseItemCount: Int,
        isInfiniteLoopEnabled: Boolean
    ): LoopResetResult? {
        if (!isInfiniteLoopEnabled || baseItemCount <= 0 || firstVisiblePos < 0) {
            return null
        }

        return when {
            firstVisiblePos >= baseItemCount * 2 -> {
                LoopResetResult(
                    targetPosition = firstVisiblePos - baseItemCount,
                    pixelOffset = currentViewLeft,
                    isResetApplied = true
                )
            }
            firstVisiblePos < baseItemCount -> {
                LoopResetResult(
                    targetPosition = firstVisiblePos + baseItemCount,
                    pixelOffset = currentViewLeft,
                    isResetApplied = true
                )
            }
            else -> {
                LoopResetResult(
                    targetPosition = firstVisiblePos,
                    pixelOffset = currentViewLeft,
                    isResetApplied = false
                )
            }
        }
    }

    /**
     * Resolves whether auto-scroll is allowed to advance:
     * Must be running, not paused, not user-touching, no accessibility touch exploration active,
     * and system animations enabled.
     */
    fun canAdvance(
        isRunning: Boolean,
        isPaused: Boolean,
        isUserTouching: Boolean,
        isAccessibilityActive: Boolean,
        areAnimationsEnabled: Boolean
    ): Boolean {
        if (!isRunning) return false
        if (isPaused) return false
        if (isUserTouching) return false
        if (isAccessibilityActive) return false
        if (!areAnimationsEnabled) return false
        return true
    }

    /**
     * Maps an adapter position in the repeated list back to the canonical symbol and model.
     */
    fun resolveItemAtPosition(
        position: Int,
        baseItems: List<MarketTickerUiModel>
    ): MarketTickerUiModel? {
        if (baseItems.isEmpty() || position < 0) return null
        val realIndex = position % baseItems.size
        return baseItems[realIndex]
    }
}
