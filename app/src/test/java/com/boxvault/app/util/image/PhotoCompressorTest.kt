package com.boxvault.app.util.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoCompressorTest {

    @Test
    fun `webpCompressFormat is WEBP_LOSSY on API 30 or higher`() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertEquals(Bitmap.CompressFormat.WEBP_LOSSY, PhotoCompressor.webpCompressFormat)
        } else {
            @Suppress("DEPRECATION")
            assertEquals(Bitmap.CompressFormat.WEBP, PhotoCompressor.webpCompressFormat)
        }
    }

    @Test
    fun `hasEncodedImageSignature detects WebP RIFF signature and standard image formats`() {
        val webpHeader = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x20, 0x00, 0x00, 0x00,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()
        )
        assertTrue(PhotoCompressor.hasEncodedImageSignature(webpHeader))

        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertTrue(PhotoCompressor.hasEncodedImageSignature(jpegHeader))

        val pngHeader = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        assertTrue(PhotoCompressor.hasEncodedImageSignature(pngHeader))

        val gifHeader = byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte())
        assertTrue(PhotoCompressor.hasEncodedImageSignature(gifHeader))

        val bmpHeader = byteArrayOf('B'.code.toByte(), 'M'.code.toByte(), 0x00, 0x00)
        assertTrue(PhotoCompressor.hasEncodedImageSignature(bmpHeader))

        val invalidBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertFalse(PhotoCompressor.hasEncodedImageSignature(invalidBytes))

        val emptyBytes = ByteArray(0)
        assertFalse(PhotoCompressor.hasEncodedImageSignature(emptyBytes))
    }

    @Test
    fun `downsampleBitmap scales oversized landscape and portrait bitmaps to max 1600px longest edge`() {
        val oversizedLandscape = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        val downsampledLandscape = PhotoCompressor.downsampleBitmap(
            source = oversizedLandscape,
            maxEdgePx = 1600
        )

        assertEquals(1600, downsampledLandscape.width)
        assertEquals(1200, downsampledLandscape.height)

        val oversizedPortrait = Bitmap.createBitmap(1200, 3200, Bitmap.Config.ARGB_8888)
        val downsampledPortrait = PhotoCompressor.downsampleBitmap(
            source = oversizedPortrait,
            maxEdgePx = 1600
        )

        assertEquals(600, downsampledPortrait.width)
        assertEquals(1600, downsampledPortrait.height)
    }

    @Test
    fun `downsampleBitmap preserves dimensions when image is already within maxEdgePx`() {
        val compactBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val result = PhotoCompressor.downsampleBitmap(compactBitmap, maxEdgePx = 1600)

        assertEquals(800, result.width)
        assertEquals(600, result.height)
    }

    @Test
    fun `compressBitmapToWebp downsamples to max 1600px and preserves aspect ratio`() {
        val bitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        val outputBytes = PhotoCompressor.compressBitmapToWebp(bitmap, maxEdgePx = 1600, quality = 82)
        assertTrue(outputBytes.isNotEmpty())

        val decoded = BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size)
        assertNotNull(decoded)
        assertEquals(1600, decoded.width)
        assertEquals(1200, decoded.height)
    }

    @Test
    fun `compressBitmapToWebp handles already compact bitmap where scaled is source`() {
        val compact = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val outputBytes = PhotoCompressor.compressBitmapToWebp(compact, maxEdgePx = 1600, quality = 82)
        assertTrue(outputBytes.isNotEmpty())
    }

    @Test
    fun `compressBitmapToJpeg downsamples and compresses to JPEG format`() {
        val bitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val outputBytes = PhotoCompressor.compressBitmapToJpeg(bitmap, maxEdgePx = 1600, quality = 80)
        assertTrue(outputBytes.isNotEmpty())

        val decoded = BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size)
        assertNotNull(decoded)
        assertEquals(1600, decoded.width)
        assertEquals(1200, decoded.height)

        val compact = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        val compactBytes = PhotoCompressor.compressBitmapToJpeg(compact, maxEdgePx = 1600, quality = 80)
        assertTrue(compactBytes.isNotEmpty())
    }

    @Test
    fun `compressAndNormalize downsamples raw image byte stream to 1600px and outputs decodable format`() {
        val largeBitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(245, 158, 11))
        }
        val rawJpegBytes = ByteArrayOutputStream().use { out ->
            largeBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.toByteArray()
        }

        val compressedBytes = PhotoCompressor.compressAndNormalize(
            rawImageBytes = rawJpegBytes,
            maxEdgePx = 1600,
            quality = 82
        )

        assertTrue(compressedBytes.isNotEmpty())
        val decoded = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
        assertNotNull(decoded)
        assertTrue(max(decoded.width, decoded.height) <= 1600)
        assertEquals(1600, decoded.width)
        assertEquals(1200, decoded.height)
    }

    @Test
    fun `compressAndNormalize returns non-image raw bytes untouched`() {
        val nonImageBytes = byteArrayOf(1, 2, 3, 4, 5)
        val result = PhotoCompressor.compressAndNormalize(nonImageBytes)
        assertEquals(nonImageBytes, result)
    }

    @Test
    fun `applyExifOrientation handles all orientation flags accurately`() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)

        val normal = PhotoCompressor.applyExifOrientation(source, ExifInterface.ORIENTATION_NORMAL)
        assertEquals(400, normal.width)
        assertEquals(200, normal.height)

        val rotated90 = PhotoCompressor.applyExifOrientation(source, ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(200, rotated90.width)
        assertEquals(400, rotated90.height)

        val rotated180 = PhotoCompressor.applyExifOrientation(source, ExifInterface.ORIENTATION_ROTATE_180)
        assertEquals(400, rotated180.width)
        assertEquals(200, rotated180.height)

        val rotated270 = PhotoCompressor.applyExifOrientation(source, ExifInterface.ORIENTATION_ROTATE_270)
        assertEquals(200, rotated270.width)
        assertEquals(400, rotated270.height)

        val flipH = PhotoCompressor.applyExifOrientation(source, ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        assertEquals(400, flipH.width)
        assertEquals(200, flipH.height)

        val flipV = PhotoCompressor.applyExifOrientation(source, ExifInterface.ORIENTATION_FLIP_VERTICAL)
        assertEquals(400, flipV.width)
        assertEquals(200, flipV.height)

        val transpose = PhotoCompressor.applyExifOrientation(source, ExifInterface.ORIENTATION_TRANSPOSE)
        assertEquals(200, transpose.width)
        assertEquals(400, transpose.height)

        val transverse = PhotoCompressor.applyExifOrientation(source, ExifInterface.ORIENTATION_TRANSVERSE)
        assertEquals(200, transverse.width)
        assertEquals(400, transverse.height)
    }
}
