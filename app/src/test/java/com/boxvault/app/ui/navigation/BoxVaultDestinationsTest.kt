package com.boxvault.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoxVaultDestinationsTest {

    @Test
    fun routes_haveExpectedPatterns() {
        assertEquals("dashboard", BoxVaultDestinations.DASHBOARD_ROUTE)
        assertEquals("qr_scanner", BoxVaultDestinations.QR_SCANNER_ROUTE)
        assertEquals("backup_restore", BoxVaultDestinations.BACKUP_RESTORE_ROUTE)
        assertEquals("box_detail/{boxId}", BoxVaultDestinations.BOX_DETAIL_ROUTE)
        assertEquals("bulk_entry/{boxId}", BoxVaultDestinations.BULK_ENTRY_ROUTE)
    }

    @Test
    fun boxEditRoute_buildsCorrectQueryStrings() {
        assertEquals("box_edit", BoxVaultDestinations.boxEditRoute())
        assertEquals("box_edit?boxId=box-123", BoxVaultDestinations.boxEditRoute(boxId = "box-123"))
        assertEquals("box_edit?presetBoxId=preset-456", BoxVaultDestinations.boxEditRoute(presetBoxId = "preset-456"))
        assertEquals(
            "box_edit?boxId=box-123&presetBoxId=preset-456",
            BoxVaultDestinations.boxEditRoute(boxId = "box-123", presetBoxId = "preset-456")
        )
    }

    @Test
    fun boxDetailRoute_encodesBoxId() {
        assertEquals("box_detail/BOX-001", BoxVaultDestinations.boxDetailRoute("BOX-001"))
    }

    @Test
    fun bulkEntryRoute_encodesBoxId() {
        assertEquals("bulk_entry/BOX-001", BoxVaultDestinations.bulkEntryRoute("BOX-001"))
    }

    @Test
    fun pdfGeneratorRoute_handlesNullAndBoxId() {
        assertEquals("pdf_generator", BoxVaultDestinations.pdfGeneratorRoute(null))
        assertEquals("pdf_generator?boxId=BOX-001", BoxVaultDestinations.pdfGeneratorRoute("BOX-001"))
    }
}
