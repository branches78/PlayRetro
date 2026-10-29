package com.swordfish.lemuroid.app.shared.covers

import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Size
import coil.transform.Transformation

/**
 * Removes the transparent margins around a logo. Some logos of the libretro thumbnails server (e.g. Aladdin or
 * Bust-A-Move of the Super Nintendo) are drawn in the middle of a 16:9 canvas: the empty band below them pushed the
 * line of the system away from the logo, while the logos without margins have it right below.
 */
class TrimTransparentBordersTransformation : Transformation {
    override val cacheKey: String = "trim_transparent_borders"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)

        var left = width
        var right = -1
        var top = height
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (Color.alpha(pixels[row + x]) > ALPHA_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }

        // Fully transparent, or nothing to remove.
        if (right < 0) return input
        if (left == 0 && top == 0 && right == width - 1 && bottom == height - 1) return input
        return Bitmap.createBitmap(input, left, top, right - left + 1, bottom - top + 1)
    }

    override fun equals(other: Any?) = other is TrimTransparentBordersTransformation

    override fun hashCode() = javaClass.hashCode()

    companion object {
        // Almost invisible pixels (soft shadows, compression leftovers) count as empty.
        private const val ALPHA_THRESHOLD = 16
    }
}
