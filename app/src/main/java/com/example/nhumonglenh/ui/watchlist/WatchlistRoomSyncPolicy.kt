package com.example.nhumonglenh.ui.watchlist

/**
 * Chính sách đồng bộ dữ liệu giữa Server Backend và Room Database local cache.
 *
 * Nguyên tắc:
 * 1. HTTP 200 (kể cả danh sách rỗng []): Server là single source of truth -> ĐỒNG BỘ ROOM (SYNC_ROOM).
 *    Điều này đảm bảo xóa bỏ watchlist "ma" nếu người dùng đã xóa hết mã trên thiết bị khác.
 * 2. HTTP Error (503, 500, 401,...) hoặc Network Error (timeout, mất kết nối):
 *    -> GIỮ NGUYÊN CACHE (KEEP_CACHE) và hiển thị dữ liệu offline đã lưu trước đó trong Room.
 */
object WatchlistRoomSyncPolicy {

    enum class Action {
        SYNC_ROOM,
        KEEP_CACHE
    }

    sealed class ResponseOutcome {
        data class HttpSuccess(val statusCode: Int = 200, val itemCount: Int) : ResponseOutcome()
        data class HttpError(val statusCode: Int, val message: String? = null) : ResponseOutcome()
        data class NetworkError(val throwable: Throwable? = null) : ResponseOutcome()
    }

    data class Decision(
        val action: Action,
        val shouldClearAndInsert: Boolean,
        val reason: String
    )

    /**
     * Xác định hành động đối với Room cache dựa trên HTTP response code và số lượng phần tử trả về.
     */
    fun evaluate(statusCode: Int, isSuccessful: Boolean, itemCount: Int?): Decision {
        return if (isSuccessful && statusCode == 200) {
            val count = itemCount ?: 0
            Decision(
                action = Action.SYNC_ROOM,
                shouldClearAndInsert = true,
                reason = "HTTP 200 OK: Đồng bộ Room DB với $count mục từ máy chủ"
            )
        } else {
            val reasonMsg = if (statusCode == 503) {
                "Nguồn dữ liệu thị trường tạm thời không khả dụng"
            } else {
                "Lỗi máy chủ ($statusCode)"
            }
            Decision(
                action = Action.KEEP_CACHE,
                shouldClearAndInsert = false,
                reason = reasonMsg
            )
        }
    }

    /**
     * Xác định hành động khi xảy ra lỗi mạng (onFailure).
     */
    fun evaluateNetworkFailure(throwable: Throwable?): Decision {
        val detail = throwable?.message ?: "Lỗi kết nối mạng"
        return Decision(
            action = Action.KEEP_CACHE,
            shouldClearAndInsert = false,
            reason = "Không thể kết nối máy chủ: $detail"
        )
    }

    /**
     * Overload nhận ResponseOutcome để tiện phân luồng.
     */
    fun evaluateOutcome(outcome: ResponseOutcome): Decision {
        return when (outcome) {
            is ResponseOutcome.HttpSuccess -> {
                if (outcome.statusCode == 200) {
                    Decision(
                        action = Action.SYNC_ROOM,
                        shouldClearAndInsert = true,
                        reason = "HTTP 200 OK: Đồng bộ Room DB với ${outcome.itemCount} mục từ máy chủ"
                    )
                } else {
                    Decision(
                        action = Action.KEEP_CACHE,
                        shouldClearAndInsert = false,
                        reason = "Mã trạng thái không mong muốn (${outcome.statusCode})"
                    )
                }
            }
            is ResponseOutcome.HttpError -> {
                val reasonMsg = if (outcome.statusCode == 503) {
                    "Nguồn dữ liệu thị trường tạm thời không khả dụng"
                } else {
                    outcome.message ?: "Lỗi máy chủ (${outcome.statusCode})"
                }
                Decision(
                    action = Action.KEEP_CACHE,
                    shouldClearAndInsert = false,
                    reason = reasonMsg
                )
            }
            is ResponseOutcome.NetworkError -> evaluateNetworkFailure(outcome.throwable)
        }
    }
}
