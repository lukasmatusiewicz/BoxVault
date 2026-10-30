package com.boxvault.app.ui.navigation

import android.net.Uri

object BoxVaultDestinations {
    const val ARG_BOX_ID = "boxId"

    const val DASHBOARD_ROUTE = "dashboard"
    const val BOX_EDIT_ROUTE = "box_edit?boxId={$ARG_BOX_ID}"
    const val BOX_DETAIL_ROUTE = "box_detail/{$ARG_BOX_ID}"
    const val BULK_ENTRY_ROUTE = "bulk_entry/{$ARG_BOX_ID}"

    // Milestone 4 & 5 integration routes
    const val QR_SCANNER_ROUTE = "qr_scanner"
    const val PDF_GENERATOR_ROUTE = "pdf_generator?boxId={$ARG_BOX_ID}"
    const val BACKUP_RESTORE_ROUTE = "backup_restore"

    fun boxEditRoute(boxId: String? = null): String {
        return if (boxId.isNullOrBlank()) {
            "box_edit"
        } else {
            "box_edit?boxId=${Uri.encode(boxId)}"
        }
    }

    fun boxDetailRoute(boxId: String): String =
        "box_detail/${Uri.encode(boxId)}"

    fun bulkEntryRoute(boxId: String): String =
        "bulk_entry/${Uri.encode(boxId)}"

    fun pdfGeneratorRoute(boxId: String? = null): String {
        return if (boxId.isNullOrBlank()) {
            "pdf_generator"
        } else {
            "pdf_generator?boxId=${Uri.encode(boxId)}"
        }
    }
}
