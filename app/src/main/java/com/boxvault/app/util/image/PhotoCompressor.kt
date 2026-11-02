package com.boxvault.app.util.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Downsamples large camera/gallery photos (default max 1600px on the longest edge),
 * normalizes rotation/flip via [ExifInterface], and compresses to 82% WebP quality
 * before saving to internal storage so local `.zip` backups stay compact and fast.
 */
object PhotoCompressor {

    const val DEFAULT_MAX_EDGE_PX = 1600
    const val DEFAULT_JPEG_QUALITY = 80
    const val DEFAULT_WEBP_QUALITY = 82

    val webpCompressFormat: Bitmap.CompressFormat
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    fun downsampleBitmap(
        source: Bitmap,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX
    ): Bitmap {
        val clampedMaxEdge = maxEdgePx.coerceAtLeast(64)
        val width = source.width
        val height = source.height
        val longestEdge = max(width, height)

        if (longestEdge <= clampedMaxEdge) {
            return source
        }

        val scale = clampedMaxEdge.toFloat() / longestEdge.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    fun compressBitmapToWebp(
        source: Bitmap,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
        quality: Int = DEFAULT_WEBP_QUALITY
    ): ByteArray {
        val scaled = downsampleBitmap(source, maxEdgePx)
        val clampedQuality = quality.coerceIn(10, 100)
        return try {
            ByteArrayOutputStream().use { output ->
                scaled.compress(webpCompressFormat, clampedQuality, output)
                output.toByteArray()
            }
        } finally {
            if (scaled != source) {
                scaled.recycle()
            }
        }
    }

    fun compressBitmapToJpeg(
        source: Bitmap,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
        quality: Int = DEFAULT_JPEG_QUALITY
    ): ByteArray {
        val scaled = downsampleBitmap(source, maxEdgePx)
        val clampedQuality = quality.coerceIn(10, 100)
        return try {
            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, clampedQuality, output)
                output.toByteArray()
            }
        } finally {
            if (scaled != source) {
                scaled.recycle()
            }
        }
    }

    fun applyExifOrientation(
        bitmap: Bitmap,
        exifOrientation: Int
    ): Bitmap {
        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }

        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    fun readExifOrientation(rawImageBytes: ByteArray): Int {
        if (!hasEncodedImageSignature(rawImageBytes)) return ExifInterface.ORIENTATION_NORMAL
        return runCatching {
            ByteArrayInputStream(rawImageBytes).use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    /**
     * Checks whether [bytes] begins with a known image file signature (JPEG, PNG, WebP, GIF, BMP).
     * Prevents Robolectric's stub `BitmapFactory` from fabricating dummy 100x100 bitmaps
     * when arbitrary non-image byte fixtures are passed in unit tests.
     */
    internal fun hasEncodedImageSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF

        // JPEG: FF D8 FF
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return true
        // PNG: 89 50 4E 47
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return true
        // GIF: "GIF8"
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) return true
        // WebP: "RIFF"
        if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) return true
        // BMP: "BM"
        if (b0 == 0x42 && b1 == 0x4D) return true

        return false
    }

    /**
     * Decodes [rawImageBytes], normalizes EXIF orientation, downsamples so the longest edge
     * is at most [maxEdgePx], and re-encodes as WebP at [quality] (default 82).
     * If [rawImageBytes] is not an encoded image bitmap (e.g. synthetic byte fixtures in unit tests),
     * returns [rawImageBytes] unchanged.
     */
    fun compressAndNormalize(
        rawImageBytes: ByteArray,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
        quality: Int = DEFAULT_WEBP_QUALITY
    ): ByteArray {
        if (!hasEncodedImageSignature(rawImageBytes)) return rawImageBytes

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(rawImageBytes, 0, rawImageBytes.size, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return rawImageBytes
        }

        val clampedMaxEdge = maxEdgePx.coerceAtLeast(64)
        val sampleSize = calculateInSampleSize(
            width = boundsOptions.outWidth,
            height = boundsOptions.outHeight,
            maxEdgePx = clampedMaxEdge
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = BitmapFactory.decodeByteArray(
            rawImageBytes,
            0,
            rawImageBytes.size,
            decodeOptions
        ) ?: return rawImageBytes

        var oriented: Bitmap? = null
        try {
            val orientation = readExifOrientation(rawImageBytes)
            oriented = applyExifOrientation(decoded, orientation)
            return compressBitmapToWebp(
                source = oriented,
                maxEdgePx = clampedMaxEdge,
                quality = quality
            )
        } finally {
            if (oriented != null && oriented != decoded) {
                oriented.recycle()
            }
            decoded.recycle()
        }
    }

    internal fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxEdgePx: Int
    ): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        while ((longest / (inSampleSize * 2)) >= maxEdgePx) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
