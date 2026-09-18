package com.example.nhumonglenh.ui.common

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import java.security.MessageDigest

/**
 * Custom Glide BitmapTransformation để crop phần padding trong suốt xung quanh linh vật Fini.
 * Biến đổi áp dụng trên từng khung hình (frame) của Animated GIF trong bộ nhớ mà không làm thay đổi tệp gốc,
 * giữ nguyên 100% timing, tốc độ phát và độ trong suốt của ảnh.
 */
class FiniCropTransformation(
    private val cropLeft: Int = 56,
    private val cropTop: Int = 58,
    private val cropWidth: Int = 110,
    private val cropHeight: Int = 80
) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        if (toTransform.width <= 0 || toTransform.height <= 0) return toTransform

        val validLeft = cropLeft.coerceIn(0, toTransform.width - 1)
        val validTop = cropTop.coerceIn(0, toTransform.height - 1)
        val validWidth = cropWidth.coerceIn(1, toTransform.width - validLeft)
        val validHeight = cropHeight.coerceIn(1, toTransform.height - validTop)

        val cropped = Bitmap.createBitmap(toTransform, validLeft, validTop, validWidth, validHeight)
        return TransformationUtils.fitCenter(pool, cropped, outWidth, outHeight)
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        val id = "com.example.nhumonglenh.ui.common.FiniCropTransformation_${cropLeft}_${cropTop}_${cropWidth}_${cropHeight}"
        messageDigest.update(id.toByteArray(Charsets.UTF_8))
    }

    override fun equals(other: Any?): Boolean {
        if (other is FiniCropTransformation) {
            return cropLeft == other.cropLeft &&
                    cropTop == other.cropTop &&
                    cropWidth == other.cropWidth &&
                    cropHeight == other.cropHeight
        }
        return false
    }

    override fun hashCode(): Int {
        var result = cropLeft
        result = 31 * result + cropTop
        result = 31 * result + cropWidth
        result = 31 * result + cropHeight
        return result
    }
}
