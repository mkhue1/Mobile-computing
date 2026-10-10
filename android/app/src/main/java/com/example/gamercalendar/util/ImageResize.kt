package com.example.gamercalendar.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Decodes the image at [uri], scales it so its longest side is at most [maxSide] px,
 * and re-encodes it as JPEG. Used to keep avatar uploads well under the server's 2 MB limit.
 *
 * On API 28+ ImageDecoder also applies EXIF rotation; on API 24–27 the photo may come out
 * rotated, since BitmapFactory ignores EXIF.
 */
suspend fun resizeToJpeg(
    context: Context,
    uri: Uri,
    maxSide: Int = 512,
    quality: Int = 85
): ByteArray = withContext(Dispatchers.IO) {
    val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder(context, uri, maxSide)
    } else {
        decodeWithBitmapFactory(context, uri, maxSide)
    }
    val bitmap = flattenOntoWhite(decoded)

    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bitmap.recycle()
        out.toByteArray()
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWithImageDecoder(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val (width, height) = fitWithin(info.size.width, info.size.height, maxSide)
        decoder.setTargetSize(width, height)
        // Software bitmaps can always be compressed; hardware ones are not guaranteed to.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

private fun decodeWithBitmapFactory(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Couldn't read image")

    // Power-of-two downsampling while decoding keeps memory low for large photos.
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSide) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val sampled = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("Couldn't read image")

    val (width, height) = fitWithin(sampled.width, sampled.height, maxSide)
    if (width == sampled.width && height == sampled.height) return sampled

    return Bitmap.createScaledBitmap(sampled, width, height, true).also {
        if (it !== sampled) sampled.recycle()
    }
}

/** JPEG has no alpha, so transparent pixels would otherwise come out black. */
private fun flattenOntoWhite(bitmap: Bitmap): Bitmap {
    if (!bitmap.hasAlpha()) return bitmap
    val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    Canvas(flat).apply {
        drawColor(Color.WHITE)
        drawBitmap(bitmap, 0f, 0f, null)
    }
    bitmap.recycle()
    return flat
}

/** Scales (width, height) down to fit inside a maxSide square, keeping the aspect ratio. Never scales up. */
private fun fitWithin(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val scale = maxSide.toFloat() / longest
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}
