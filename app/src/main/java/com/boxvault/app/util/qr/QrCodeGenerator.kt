package com.boxvault.app.util.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.boxvault.app.data.local.entity.BoxEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 100% offline QR code bitmap generator powered by ZXing Core.
 * Produces high-contrast, sharp-edged bitmaps with an explicit white quiet zone
 * suitable for both on-screen Compose previews and printable A4/Letter PDF label sheets.
 */
object QrCodeGenerator {

    const val DEFAULT_SIZE_PX = 512
    const val MIN_SIZE_PX = 64
    const val DEFAULT_QUIET_ZONE_MODULES = 2

    fun buildBoxQrPayload(boxId: String): String {
        val trimmed = boxId.trim()
        require(trimmed.isNotEmpty()) { "Box ID must not be blank." }
        return if (trimmed.startsWith(BoxEntity.QR_URI_PREFIX, ignoreCase = true)) {
            "${BoxEntity.QR_URI_PREFIX}${trimmed.substring(BoxEntity.QR_URI_PREFIX.length).trim()}"
        } else {
            "${BoxEntity.QR_URI_PREFIX}$trimmed"
        }
    }

    fun extractBoxId(rawPayload: String): String? =
        BoxEntity.parseBoxIdFromQrPayload(rawPayload)

    fun generateBoxQrBitmap(
        boxId: String,
        sizePx: Int = DEFAULT_SIZE_PX,
        marginModules: Int = DEFAULT_QUIET_ZONE_MODULES,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val payload = buildBoxQrPayload(boxId)
        return generateQrBitmap(
            payload = payload,
            sizePx = sizePx,
            marginModules = marginModules,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor
        )
    }

    fun generateQrBitmap(
        payload: String,
        sizePx: Int = DEFAULT_SIZE_PX,
        marginModules: Int = DEFAULT_QUIET_ZONE_MODULES,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val trimmedPayload = payload.trim()
        require(trimmedPayload.isNotEmpty()) { "QR payload must not be blank." }

        val clampedSize = sizePx.coerceAtLeast(MIN_SIZE_PX)
        val clampedMargin = marginModules.coerceAtLeast(1)

        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to errorCorrectionLevel,
            EncodeHintType.MARGIN to clampedMargin
        )

        val bitMatrix = QRCodeWriter().encode(
            trimmedPayload,
            BarcodeFormat.QR_CODE,
            clampedSize,
            clampedSize,
            hints
        )

        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = if (bitMatrix.get(x, y)) {
                    foregroundColor
                } else {
                    backgroundColor
                }
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}

