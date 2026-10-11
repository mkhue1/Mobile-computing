package com.example.gamercalendar.ui.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import com.example.gamercalendar.ui.theme.PurplePrimary
import com.example.gamercalendar.ui.theme.SurfaceDark
import kotlin.math.roundToInt

/**
 * A map pin whose head is the game cover. Sessions without a cover get the same pin filled
 * with the surface color, so every session still has a marker.
 */
fun sessionMarkerBitmap(cover: Bitmap?, density: Float): Bitmap {
    val width = (44f * density).roundToInt().coerceAtLeast(1)
    val coverHeight = (58f * density).roundToInt().coerceAtLeast(1)
    val pointerHeight = (8f * density).roundToInt().coerceAtLeast(1)
    val border = (2f * density).coerceAtLeast(1f)
    val radius = 8f * density
    val height = coverHeight + pointerHeight

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val coverRect = RectF(
        border / 2f,
        border / 2f,
        width - border / 2f,
        coverHeight - border / 2f
    )

    canvas.save()
    canvas.clipPath(Path().apply {
        addRoundRect(coverRect, radius, radius, Path.Direction.CW)
    })
    if (cover != null) {
        canvas.drawBitmap(
            cover,
            coverCenterCrop(cover, coverRect),
            coverRect,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    } else {
        canvas.drawRect(coverRect, Paint().apply { color = SurfaceDark.toArgb() })
    }
    canvas.restore()

    canvas.drawRoundRect(
        coverRect,
        radius,
        radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = border
            color = PurplePrimary.toArgb()
        }
    )

    val tip = height.toFloat()
    val pointer = Path().apply {
        moveTo(width / 2f - pointerHeight, coverRect.bottom)
        lineTo(width / 2f + pointerHeight, coverRect.bottom)
        lineTo(width / 2f, tip)
        close()
    }
    canvas.drawPath(pointer, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PurplePrimary.toArgb() })

    return bitmap
}

private fun coverCenterCrop(cover: Bitmap, dest: RectF): Rect {
    val destRatio = dest.width() / dest.height()
    val srcRatio = cover.width.toFloat() / cover.height
    return if (srcRatio > destRatio) {
        val cropWidth = cover.height * destRatio
        val left = ((cover.width - cropWidth) / 2f).roundToInt()
        Rect(left, 0, (left + cropWidth).roundToInt(), cover.height)
    } else {
        val cropHeight = cover.width / destRatio
        val top = ((cover.height - cropHeight) / 2f).roundToInt()
        Rect(0, top, cover.width, (top + cropHeight).roundToInt())
    }
}
