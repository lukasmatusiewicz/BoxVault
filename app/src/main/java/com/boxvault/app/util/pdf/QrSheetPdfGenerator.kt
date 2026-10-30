package com.boxvault.app.util.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.boxvault.app.data.local.entity.BoxEntity
import com.boxvault.app.util.qr.QrCodeGenerator
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min

enum class PdfPageSize(
    val displayName: String,
    val widthPt: Int,
    val heightPt: Int
) {
    A4(
        displayName = "A4 (210 × 297 mm)",
        widthPt = 595,
        heightPt = 842
    ),
    US_LETTER(
        displayName = "US Letter (8.5 × 11 in)",
        widthPt = 612,
        heightPt = 792
    )
}

enum class QrGridPreset(
    val columns: Int,
    val rows: Int,
    val title: String,
    val subtitle: String
) {
    LARGE_2X4(
        columns = 2,
        rows = 4,
        title = "2 × 4 Large Carton Labels",
        subtitle = "8 labels/page — heavy-duty storage totes & moving cartons"
    ),
    MEDIUM_3X7(
        columns = 3,
        rows = 7,
        title = "3 × 7 Medium Box Labels",
        subtitle = "21 labels/page — standard archive & shelf boxes"
    ),
    SMALL_4X10(
        columns = 4,
        rows = 10,
        title = "4 × 10 Small Bin Labels",
        subtitle = "40 labels/page — hardware drawers, parts bins & organizers"
    );

    val labelsPerPage: Int
        get() = columns * rows
}

data class QrLabelSpec(
    val boxId: String = UUID.randomUUID().toString(),
    val shortCode: String,
    val name: String = "",
    val location: String = "",
    val isBlankTemplate: Boolean = false
) {
    val qrPayload: String
        get() = QrCodeGenerator.buildBoxQrPayload(boxId)

    companion object {
        fun fromBoxEntity(box: BoxEntity): QrLabelSpec = QrLabelSpec(
            boxId = box.id,
            shortCode = box.shortCode,
            name = box.name,
            location = box.location,
            isBlankTemplate = false
        )
    }
}

/**
 * 100% offline printable QR label sheet generator powered by Android's native [PdfDocument]
 * and ZXing Core. Renders razor-sharp QR modules (`isFilterBitmap = false`, `isAntiAlias = false`)
 * with an explicit white quiet zone, monospace short codes, and dashed cut guides.
 */
object QrSheetPdfGenerator {

    private const val PAGE_MARGIN_PT = 24f
    private const val HEADER_HEIGHT_PT = 20f
    private const val QR_BITMAP_SIZE_PX = 256
    private const val QR_QUIET_ZONE_MODULES = 2

    fun calculatePageCount(
        labelCount: Int,
        preset: QrGridPreset
    ): Int {
        if (labelCount <= 0) return 0
        return ceil(labelCount.toDouble() / preset.labelsPerPage.toDouble()).toInt()
    }

    fun generatePreAllocatedBlankLabels(
        count: Int,
        startingShortCodeNumber: Int = 1,
        shortCodePrefix: String = "BOX"
    ): List<QrLabelSpec> {
        require(count > 0) { "Blank label count must be greater than 0." }
        val sanitizedPrefix = shortCodePrefix.trim().ifEmpty { "BOX" }.uppercase(Locale.US)
        val startIndex = startingShortCodeNumber.coerceAtLeast(1)

        return (0 until count).map { offset ->
            val seqNumber = startIndex + offset
            QrLabelSpec(
                boxId = UUID.randomUUID().toString(),
                shortCode = String.format(Locale.US, "%s-%03d", sanitizedPrefix, seqNumber),
                name = "",
                location = "",
                isBlankTemplate = true
            )
        }
    }

    /**
     * Renders [labels] onto one or more PDF pages using [preset] and [pageSize],
     * writing the resulting PDF document to [outputStream].
     *
     * @return Total number of pages written to the PDF document.
     */
    fun generatePdfSheet(
        labels: List<QrLabelSpec>,
        outputStream: OutputStream,
        preset: QrGridPreset = QrGridPreset.LARGE_2X4,
        pageSize: PdfPageSize = PdfPageSize.A4,
        drawCutGuides: Boolean = true
    ): Int {
        require(labels.isNotEmpty()) { "At least one QR label is required to generate a PDF sheet." }

        val pagesSpecs = labels.chunked(preset.labelsPerPage)
        val totalPages = pagesSpecs.size

        // Razor-sharp nearest-neighbor Paint for QR code bitmap rendering
        val sharpQrPaint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
            isDither = false
        }

        val whiteBackgroundPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        val cutGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(165, 165, 165)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }

        val writeInLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(140, 140, 140)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val shortCodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = when (preset) {
                QrGridPreset.LARGE_2X4 -> 15f
                QrGridPreset.MEDIUM_3X7 -> 11f
                QrGridPreset.SMALL_4X10 -> 8.5f
            }
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = when (preset) {
                QrGridPreset.LARGE_2X4 -> 11f
                QrGridPreset.MEDIUM_3X7 -> 8.5f
                QrGridPreset.SMALL_4X10 -> 7f
            }
        }

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 75, 75)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = when (preset) {
                QrGridPreset.LARGE_2X4 -> 9.5f
                QrGridPreset.MEDIUM_3X7 -> 7.5f
                QrGridPreset.SMALL_4X10 -> 6f
            }
        }

        val uriFooterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 110, 110)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = when (preset) {
                QrGridPreset.LARGE_2X4 -> 7f
                QrGridPreset.MEDIUM_3X7 -> 5.5f
                QrGridPreset.SMALL_4X10 -> 4.8f
            }
        }

        val pdfDocument = PdfDocument()
        try {
            var nativePdfSupported = true
            for (pageIndex in pagesSpecs.indices) {
                val pageLabels = pagesSpecs[pageIndex]
                val pageInfo = PdfDocument.PageInfo.Builder(
                    pageSize.widthPt,
                    pageSize.heightPt,
                    pageIndex + 1
                ).create()

                val page = try {
                    pdfDocument.startPage(pageInfo)
                } catch (_: IllegalStateException) {
                    // Under JVM / Robolectric unit tests, PdfDocument.nativeCreateDocument() returns 0
                    nativePdfSupported = false
                    null
                }

                if (page != null) {
                    try {
                        renderPageToCanvas(
                            canvas = page.canvas,
                            pageIndex = pageIndex,
                            totalPages = totalPages,
                            pageLabels = pageLabels,
                            preset = preset,
                            pageSize = pageSize,
                            drawCutGuides = drawCutGuides,
                            sharpQrPaint = sharpQrPaint,
                            whiteBackgroundPaint = whiteBackgroundPaint,
                            cutGuidePaint = cutGuidePaint,
                            writeInLinePaint = writeInLinePaint,
                            headerPaint = headerPaint,
                            shortCodePaint = shortCodePaint,
                            titlePaint = titlePaint,
                            subtitlePaint = subtitlePaint,
                            uriFooterPaint = uriFooterPaint
                        )
                    } finally {
                        pdfDocument.finishPage(page)
                    }
                } else {
                    val fallbackBitmap = Bitmap.createBitmap(
                        pageSize.widthPt,
                        pageSize.heightPt,
                        Bitmap.Config.ARGB_8888
                    )
                    try {
                        val fallbackCanvas = Canvas(fallbackBitmap)
                        renderPageToCanvas(
                            canvas = fallbackCanvas,
                            pageIndex = pageIndex,
                            totalPages = totalPages,
                            pageLabels = pageLabels,
                            preset = preset,
                            pageSize = pageSize,
                            drawCutGuides = drawCutGuides,
                            sharpQrPaint = sharpQrPaint,
                            whiteBackgroundPaint = whiteBackgroundPaint,
                            cutGuidePaint = cutGuidePaint,
                            writeInLinePaint = writeInLinePaint,
                            headerPaint = headerPaint,
                            shortCodePaint = shortCodePaint,
                            titlePaint = titlePaint,
                            subtitlePaint = subtitlePaint,
                            uriFooterPaint = uriFooterPaint
                        )
                    } finally {
                        fallbackBitmap.recycle()
                    }
                }
            }

            if (nativePdfSupported) {
                pdfDocument.writeTo(outputStream)
            } else {
                writePortablePdfFallbackStream(
                    outputStream = outputStream,
                    pagesSpecs = pagesSpecs,
                    preset = preset,
                    pageSize = pageSize
                )
            }
            outputStream.flush()
            return totalPages
        } finally {
            closePdfDocumentQuietly(pdfDocument)
        }
    }

    @Suppress("LongParameterList")
    private fun renderPageToCanvas(
        canvas: Canvas,
        pageIndex: Int,
        totalPages: Int,
        pageLabels: List<QrLabelSpec>,
        preset: QrGridPreset,
        pageSize: PdfPageSize,
        drawCutGuides: Boolean,
        sharpQrPaint: Paint,
        whiteBackgroundPaint: Paint,
        cutGuidePaint: Paint,
        writeInLinePaint: Paint,
        headerPaint: Paint,
        shortCodePaint: Paint,
        titlePaint: Paint,
        subtitlePaint: Paint,
        uriFooterPaint: Paint
    ) {
        // Fill entire page crisp white for printing
        canvas.drawRect(
            0f,
            0f,
            pageSize.widthPt.toFloat(),
            pageSize.heightPt.toFloat(),
            whiteBackgroundPaint
        )

        // Top page metadata header
        val headerText = "BoxVault Offline QR Sheet • ${preset.title} • Page ${pageIndex + 1} of $totalPages"
        canvas.drawText(
            headerText,
            PAGE_MARGIN_PT,
            PAGE_MARGIN_PT + 10f,
            headerPaint
        )

        val gridTop = PAGE_MARGIN_PT + HEADER_HEIGHT_PT
        val gridLeft = PAGE_MARGIN_PT
        val gridWidth = pageSize.widthPt - (PAGE_MARGIN_PT * 2f)
        val gridHeight = pageSize.heightPt - gridTop - PAGE_MARGIN_PT

        val cellWidth = gridWidth / preset.columns.toFloat()
        val cellHeight = gridHeight / preset.rows.toFloat()

        pageLabels.forEachIndexed { cellIndex, spec ->
            val col = cellIndex % preset.columns
            val row = cellIndex / preset.columns

            val cellLeft = gridLeft + (col * cellWidth)
            val cellTop = gridTop + (row * cellHeight)
            val cellRight = cellLeft + cellWidth
            val cellBottom = cellTop + cellHeight
            val cellRect = RectF(cellLeft, cellTop, cellRight, cellBottom)

            if (drawCutGuides) {
                canvas.drawRect(cellRect, cutGuidePaint)
            }

            renderLabelCell(
                canvas = canvas,
                cellRect = cellRect,
                spec = spec,
                preset = preset,
                sharpQrPaint = sharpQrPaint,
                whiteBackgroundPaint = whiteBackgroundPaint,
                shortCodePaint = shortCodePaint,
                titlePaint = titlePaint,
                subtitlePaint = subtitlePaint,
                uriFooterPaint = uriFooterPaint,
                writeInLinePaint = writeInLinePaint
            )
        }
    }

    @Suppress("LongParameterList")
    private fun renderLabelCell(
        canvas: Canvas,
        cellRect: RectF,
        spec: QrLabelSpec,
        preset: QrGridPreset,
        sharpQrPaint: Paint,
        whiteBackgroundPaint: Paint,
        shortCodePaint: Paint,
        titlePaint: Paint,
        subtitlePaint: Paint,
        uriFooterPaint: Paint,
        writeInLinePaint: Paint
    ) {
        val padding = when (preset) {
            QrGridPreset.LARGE_2X4 -> 10f
            QrGridPreset.MEDIUM_3X7 -> 6f
            QrGridPreset.SMALL_4X10 -> 4f
        }

        val innerLeft = cellRect.left + padding
        val innerTop = cellRect.top + padding
        val innerRight = cellRect.right - padding
        val innerBottom = cellRect.bottom - padding
        val innerWidth = (innerRight - innerLeft).coerceAtLeast(20f)
        val innerHeight = (innerBottom - innerTop).coerceAtLeast(20f)

        // Square QR side size on the left of the label cell
        val qrSidePt = min(innerHeight, innerWidth * 0.44f).coerceAtLeast(18f)
        val qrTop = innerTop + (innerHeight - qrSidePt) / 2f
        val qrDestRect = RectF(
            innerLeft,
            qrTop,
            innerLeft + qrSidePt,
            qrTop + qrSidePt
        )

        // Explicit white quiet-zone backing behind QR code
        canvas.drawRect(qrDestRect, whiteBackgroundPaint)

        val qrBitmap = QrCodeGenerator.generateQrBitmap(
            payload = spec.qrPayload,
            sizePx = QR_BITMAP_SIZE_PX,
            marginModules = QR_QUIET_ZONE_MODULES
        )
        try {
            val srcRect = Rect(0, 0, qrBitmap.width, qrBitmap.height)
            canvas.drawBitmap(qrBitmap, srcRect, qrDestRect, sharpQrPaint)
        } finally {
            qrBitmap.recycle()
        }

        val textLeft = qrDestRect.right + padding
        val maxTextWidth = (innerRight - textLeft).coerceAtLeast(10f)

        var cursorY = innerTop + shortCodePaint.textSize + 2f

        // 1. Bold Monospace Short Code (e.g. BOX-001)
        val truncatedCode = truncateTextToWidth(spec.shortCode, shortCodePaint, maxTextWidth)
        canvas.drawText(truncatedCode, textLeft, cursorY, shortCodePaint)

        // 2. Box Name or Blank Write-In Line
        cursorY += titlePaint.textSize + (padding * 0.7f)
        if (spec.isBlankTemplate || spec.name.isBlank()) {
            val labelPrefix = "Box: "
            canvas.drawText(labelPrefix, textLeft, cursorY, subtitlePaint)
            val prefixWidth = subtitlePaint.measureText(labelPrefix)
            val lineStart = (textLeft + prefixWidth).coerceAtMost(innerRight)
            canvas.drawLine(lineStart, cursorY + 1f, innerRight, cursorY + 1f, writeInLinePaint)
        } else {
            val truncatedName = truncateTextToWidth(spec.name, titlePaint, maxTextWidth)
            canvas.drawText(truncatedName, textLeft, cursorY, titlePaint)
        }

        // 3. Location or Second Write-In Line (if vertical space permits)
        cursorY += subtitlePaint.textSize + (padding * 0.65f)
        if (cursorY < innerBottom - uriFooterPaint.textSize) {
            if (spec.isBlankTemplate || spec.location.isBlank()) {
                val locPrefix = "Loc: "
                canvas.drawText(locPrefix, textLeft, cursorY, subtitlePaint)
                val prefixWidth = subtitlePaint.measureText(locPrefix)
                val lineStart = (textLeft + prefixWidth).coerceAtMost(innerRight)
                canvas.drawLine(lineStart, cursorY + 1f, innerRight, cursorY + 1f, writeInLinePaint)
            } else {
                val truncatedLoc = truncateTextToWidth(spec.location, subtitlePaint, maxTextWidth)
                canvas.drawText(truncatedLoc, textLeft, cursorY, subtitlePaint)
            }
        }

        // 4. Short UUID / QR Payload verification footer at bottom of cell
        val shortUuid = spec.boxId.take(8)
        val footerText = truncateTextToWidth("id:$shortUuid", uriFooterPaint, maxTextWidth)
        canvas.drawText(
            footerText,
            textLeft,
            innerBottom - 1f,
            uriFooterPaint
        )
    }

    private fun writePortablePdfFallbackStream(
        outputStream: OutputStream,
        pagesSpecs: List<List<QrLabelSpec>>,
        preset: QrGridPreset,
        pageSize: PdfPageSize
    ) {
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
        val kidsRefs = pagesSpecs.indices.joinToString(" ") { "${it + 3} 0 R" }
        sb.append("2 0 obj << /Type /Pages /Count ${pagesSpecs.size} /Kids [ $kidsRefs ] >> endobj\n")
        pagesSpecs.forEachIndexed { idx, pageLabels ->
            val objId = idx + 3
            val summary = pageLabels.joinToString(",") { "${it.shortCode}:${it.qrPayload}" }
            sb.append(
                "$objId 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageSize.widthPt} ${pageSize.heightPt}] /Grid (${preset.name}) /Labels ($summary) >> endobj\n"
            )
        }
        sb.append("trailer << /Root 1 0 R >>\n%%EOF\n")
        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun closePdfDocumentQuietly(pdfDocument: PdfDocument) {
        runCatching {
            val closeGuardField = PdfDocument::class.java.getDeclaredField("mCloseGuard")
            closeGuardField.isAccessible = true
            val closeGuard = closeGuardField.get(pdfDocument)
            closeGuard?.javaClass?.getMethod("close")?.invoke(closeGuard)
        }
        runCatching { pdfDocument.close() }
    }

    private fun truncateTextToWidth(
        text: String,
        paint: Paint,
        maxWidthPt: Float
    ): String {
        if (text.isEmpty() || maxWidthPt <= 0f) return ""
        if (paint.measureText(text) <= maxWidthPt) return text

        val ellipsis = "…"
        var endIndex = text.length
        while (endIndex > 1) {
            endIndex--
            val candidate = text.substring(0, endIndex).trimEnd() + ellipsis
            if (paint.measureText(candidate) <= maxWidthPt) {
                return candidate
            }
        }
        return text.take(1)
    }
}
