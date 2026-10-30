package com.boxvault.app.util.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import org.junit.Assert.assertEquals
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
    fun `compressAndNormalize downsamples raw JPEG byte stream to 1600px and outputs valid JPEG`() {
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
            quality = 80
        )

        assertTrue(compressedBytes.isNotEmpty())
        val decoded = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
        assertNotNull(decoded)
        assertTrue(max(decoded.width, decoded.height) <= 1600)
        assertEquals(1600, decoded.width)
        assertEquals(1200, decoded.height)
    }

    @Test
    fun `applyExifOrientation rotates 90 and 270 degree bitmaps accurately`() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)

        val rotated90 = PhotoCompressor.applyExifOrientation(
            bitmap = source,
            exifOrientation = ExifInterface.ORIENTATION_ROTATE_90
        )
        assertEquals(200, rotated90.width)
        assertEquals(400, rotated90.height)

        val rotated180 = PhotoCompressor.applyExifOrientation(
            bitmap = source,
            exifOrientation = ExifInterface.ORIENTATION_ROTATE_180
        )
        assertEquals(400, rotated180.width)
        assertEquals(200, rotated180.height)
    }
}

