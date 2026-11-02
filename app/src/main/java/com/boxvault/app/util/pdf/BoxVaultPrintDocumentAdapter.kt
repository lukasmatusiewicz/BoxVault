package com.boxvault.app.util.pdf

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream

/**
 * Native Android [PrintDocumentAdapter] for streaming generated BoxVault QR label sheets
 * directly to the Android Print Spooler / PrintManager without needing external PDF viewer apps.
 */
class BoxVaultPrintDocumentAdapter(
    val labels: List<QrLabelSpec>,
    val preset: QrGridPreset = QrGridPreset.LARGE_2X4,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val drawCutGuides: Boolean = true,
    val jobName: String = "BoxVault_QR_Labels"
) : PrintDocumentAdapter() {

    fun computeTotalPages(): Int = QrSheetPdfGenerator.calculatePageCount(labels.size, preset)

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        if (labels.isEmpty()) {
            callback?.onLayoutFailed("No QR labels selected to print.")
            return
        }

        val totalPages = QrSheetPdfGenerator.calculatePageCount(labels.size, preset)

        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(totalPages)
            .build()

        val changed = newAttributes != oldAttributes
        callback?.onLayoutFinished(info, changed)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }

        if (destination == null) {
            callback?.onWriteFailed("Missing destination file descriptor.")
            return
        }

        try {
            FileOutputStream(destination.fileDescriptor).use { outputStream ->
                QrSheetPdfGenerator.generatePdfSheet(
                    labels = labels,
                    outputStream = outputStream,
                    preset = preset,
                    pageSize = pageSize,
                    drawCutGuides = drawCutGuides
                )
            }

            if (cancellationSignal?.isCanceled == true) {
                callback?.onWriteCancelled()
            } else {
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message ?: "Failed to generate print document.")
        }
    }
}
