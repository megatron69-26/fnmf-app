package com.example.nhumonglenh.ui.common

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.example.nhumonglenh.R

/**
 * Component Fini Loading dùng chung toàn hệ thống FNMF (hỗ trợ minSdk 24+):
 * - Phát GIF thật (47 frames, 3.14s) với nền trong suốt từ R.drawable.fini_employee_badge_no_platform.
 * - Text "Đang tải..." căn giữa ngay bên dưới GIF, màu sắc chuẩn dark theme (#9CA3AF).
 * - Hai kích thước linh hoạt: FULL (72-96dp) cho empty/cold start, INLINE (40-56dp) cho refresh ngầm.
 * - Show/Hide hoàn toàn idempotent, không tạo animation loop lặp lại.
 * - Tự động dừng animation và giải phóng tài nguyên khi View bị detach hoặc Fragment destroyed.
 * - Hỗ trợ TalkBack accessibility và fallback ảnh tĩnh khi hệ thống tắt animation ("Remove animations").
 */
class FiniLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class FiniSize {
        FULL,
        INLINE
    }

    private val ivMascot: ImageView
    private val tvMessage: TextView

    private var currentSize: FiniSize = FiniSize.FULL
    private var isShowingState: Boolean = false
    private var defaultText: String

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.TRANSPARENT)

        ivMascot = ImageView(context).apply {
            id = View.generateViewId()
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        tvMessage = TextView(context).apply {
            id = View.generateViewId()
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(Color.parseColor("#9CA3AF"))
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        addView(ivMascot)
        addView(tvMessage)

        defaultText = context.getString(R.string.fini_loading_text)
        tvMessage.text = defaultText

        // Read XML custom attributes
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.FiniLoadingView, defStyleAttr, 0)
            try {
                val sizeIndex = a.getInt(R.styleable.FiniLoadingView_finiSize, 0)
                currentSize = if (sizeIndex == 1) FiniSize.INLINE else FiniSize.FULL

                val text = a.getString(R.styleable.FiniLoadingView_loadingText)
                if (!text.isNullOrBlank()) {
                    defaultText = text
                    tvMessage.text = defaultText
                }
            } finally {
                a.recycle()
            }
        }

        applySizeLayout(currentSize)
        updateAccessibility()
    }

    fun setFiniSize(size: FiniSize) {
        if (currentSize != size) {
            currentSize = size
            applySizeLayout(size)
        }
    }

    fun getFiniSize(): FiniSize = currentSize

    private fun applySizeLayout(size: FiniSize) {
        val mascotWidthDp: Float
        val mascotHeightDp: Float
        val textSizeSp: Float
        val marginTopDp: Float

        when (size) {
            FiniSize.FULL -> {
                mascotWidthDp = 88f
                mascotHeightDp = 64f
                textSizeSp = 13f
                marginTopDp = 8f
            }
            FiniSize.INLINE -> {
                mascotWidthDp = 48f
                mascotHeightDp = 35f
                textSizeSp = 11f
                marginTopDp = 4f
            }
        }

        val mascotWidthPx = dpToPx(mascotWidthDp)
        val mascotHeightPx = dpToPx(mascotHeightDp)
        val marginPx = dpToPx(marginTopDp)

        ivMascot.layoutParams = LayoutParams(mascotWidthPx, mascotHeightPx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }

        tvMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        tvMessage.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = marginPx
        }
    }

    /**
     * Hiển thị loading với cơ chế Idempotent:
     * Nếu đã đang hiển thị, không tạo lại animation loop hay tải lại GIF từ đầu.
     */
    fun show(message: CharSequence? = null) {
        val newText = if (!message.isNullOrBlank()) message else defaultText
        tvMessage.text = newText

        if (isShowingState && visibility == View.VISIBLE) {
            return
        }

        isShowingState = true
        visibility = View.VISIBLE

        loadGifMascot()
    }

    /**
     * Ẩn loading với cơ chế Idempotent:
     * Dừng animation Glide và giải phóng target để tiết kiệm CPU/pin.
     */
    fun hide() {
        if (!isShowingState && visibility == View.GONE) {
            return
        }

        isShowingState = false
        visibility = View.GONE
        stopGifMascot()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == View.VISIBLE) {
            if (!isShowingState) {
                isShowingState = true
                loadGifMascot()
            }
        } else {
            if (isShowingState) {
                isShowingState = false
                stopGifMascot()
            }
        }
    }

    fun isLoading(): Boolean = isShowingState && visibility == View.VISIBLE

    fun setMessage(message: CharSequence?) {
        tvMessage.text = if (!message.isNullOrBlank()) message else defaultText
    }

    fun getMessage(): CharSequence = tvMessage.text

    /**
     * Giải phóng Glide target và dừng animation loop hoàn toàn khi View bị tháo rời.
     */
    fun cleanup() {
        hide()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE && isShowingState) {
            loadGifMascot()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopGifMascot()
    }

    private fun loadGifMascot() {
        val ctx = context ?: return
        if (ctx is Activity && (ctx.isDestroyed || ctx.isFinishing)) return

        try {
            val animationsDisabled = areAnimationsDisabled(ctx)
            val requestOptions = RequestOptions()
                .format(DecodeFormat.PREFER_ARGB_8888)
                .transform(FiniCropTransformation(cropLeft = 52, cropTop = 56, cropWidth = 112, cropHeight = 70))

            if (animationsDisabled) {
                // Hệ thống bật "Remove animations": Hiển thị frame tĩnh đầu tiên của GIF
                Glide.with(this)
                    .asBitmap()
                    .load(R.drawable.fini_employee_badge_no_platform)
                    .apply(requestOptions)
                    .into(ivMascot)
            } else {
                // Phát GIF động hoạt hình đầy đủ 47 khung hình
                Glide.with(this)
                    .asGif()
                    .load(R.drawable.fini_employee_badge_no_platform)
                    .apply(requestOptions)
                    .into(ivMascot)
            }
        } catch (_: Exception) {
            // An toàn trong môi trường Unit Test headless
        }
    }

    private fun stopGifMascot() {
        try {
            Glide.with(this).clear(ivMascot)
        } catch (_: Exception) {
            // An toàn trong môi trường Unit Test
        }
    }

    private fun areAnimationsDisabled(ctx: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                ctx.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    private fun updateAccessibility() {
        contentDescription = context.getString(R.string.fini_loading_content_desc)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun dpToPx(dp: Float): Int {
        val metrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics).toInt()
    }
}
