package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.dao.TableRowCounts
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.repository.FoundationDashboardSnapshot
import com.example.domain.finance.ConsumptionPeriodHelper
import com.example.service.AppThemeMode
import com.example.service.BluetoothThermalPrinterService
import com.example.service.DocumentPdfAndThermalService
import com.example.service.InvoiceShareAndPrintService
import com.example.service.NotificationAndPrintPrefs
import com.example.service.PairedBluetoothPrinter
import com.example.service.PrinterConnectionState
import com.example.service.ThemePreferencesRepository
import com.example.ui.screens.InvoicesManagementScreen
import com.example.ui.screens.MeterReadingsScreen
import com.example.ui.screens.ReportsAndStatementsScreen
import com.example.ui.screens.SystemDiagnosticsScreen
import com.example.ui.theme.Alasal2027Theme
import com.example.ui.viewmodel.FoundationUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase7PickerBluetoothAndPeriodTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        BluetoothThermalPrinterService.pairedPrintersProviderForTest = null
        BluetoothThermalPrinterService.connectionCheckerForTest = null
        BluetoothThermalPrinterService.directByteSenderForTest = null
    }

    private fun dateMillis(year: Int, month1Based: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month1Based - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 10)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun sampleSubscribers(): List<SubscriberEntity> = listOf(
        SubscriberEntity(
            id = 1L,
            subscriberCode = "USER-2026-081",
            fullName = "أحمد صالح العسل",
            phoneNumber = "772656610",
            address = "حي الصافية - شارع العسل",
            meterNumber = "MTR-081",
            customKwhPrice = 0.50,
            subscriptionDate = dateMillis(2025, 12, 1)
        ),
        SubscriberEntity(
            id = 2L,
            subscriberCode = "USER-2026-082",
            fullName = "خالد منصور الحميري",
            phoneNumber = "771234567",
            address = "حي حدة - المدينة",
            meterNumber = "MTR-082",
            customKwhPrice = 0.60,
            subscriptionDate = dateMillis(2026, 1, 1)
        )
    )

    @Test
    fun unifiedAvailableSubscribersDialog_searchesByNameCodePhone_andSelectsSubscriber() {
        val subs = sampleSubscribers()
        val invoices = listOf(
            InvoiceEntity(
                id = 10L,
                invoiceNumber = "INV-2026-0001",
                subscriberId = 1L,
                tariffId = 1L,
                billingPeriod = "2026-01",
                previousReading = 0.0,
                currentReading = 50.0,
                consumedKwh = 50.0,
                unitPriceApplied = 0.50,
                energyAmount = 25.0,
                fixedFeeApplied = 0.0,
                netPayableAmount = 25.0,
                remainingAmount = 25.0,
                status = "UNPAID",
                issuedAt = dateMillis(2026, 1, 1)
            ),
            InvoiceEntity(
                id = 11L,
                invoiceNumber = "INV-2026-0002",
                subscriberId = 2L,
                tariffId = 1L,
                billingPeriod = "2026-01",
                previousReading = 0.0,
                currentReading = 80.0,
                consumedKwh = 80.0,
                unitPriceApplied = 0.60,
                energyAmount = 48.0,
                fixedFeeApplied = 0.0,
                netPayableAmount = 48.0,
                remainingAmount = 48.0,
                status = "UNPAID",
                issuedAt = dateMillis(2026, 1, 15)
            )
        )

        composeTestRule.setContent {
            Alasal2027Theme {
                InvoicesManagementScreen(
                    invoices = invoices,
                    subscribers = subs,
                    selectedInvoiceDetail = null,
                    feedback = null,
                    onDismissFeedback = {},
                    onSelectInvoice = {},
                    onDismissDetail = {},
                    onRecordPayment = { _, _, _, _, done -> done() },
                    onRepriceInvoice = { _, _, _, done -> done() }
                )
            }
        }

        // Open "قائمة المشتركين المتاحين" dialog
        composeTestRule.onNodeWithTag("invoices_subscriber_picker_button").performClick()
        composeTestRule.onNodeWithTag("available_subscribers_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("قائمة المشتركين المتاحين").assertIsDisplayed()

        // Search by phone number
        composeTestRule.onNodeWithTag("subscriber_picker_search_input").performTextInput("772656610")
        composeTestRule.onNodeWithTag("subscriber_picker_item_USER-2026-081").assertIsDisplayed()
        composeTestRule.onNodeWithText("USER-2026-081 • 772656610").assertIsDisplayed()
        composeTestRule.onNodeWithText("حي الصافية - شارع العسل").assertIsDisplayed()

        // Search by code for second subscriber and select
        composeTestRule.onNodeWithTag("subscriber_picker_search_input").performTextReplacement("USER-2026-082")
        composeTestRule.onNodeWithTag("subscriber_picker_item_USER-2026-082").performClick()

        // Dialog closes and invoices list filters to subscriber #2
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("invoice_card_INV-2026-0002").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodesWithTag("invoice_card_INV-2026-0001").fetchSemanticsNodes().isEmpty()
        )

        // Reopen dialog -> blue checkmark indicator is shown beside USER-2026-082, and close button works
        composeTestRule.onNodeWithTag("invoices_subscriber_picker_button").performClick()
        composeTestRule.onNodeWithTag("subscriber_picker_selected_check_USER-2026-082").assertIsDisplayed()
        composeTestRule.onNodeWithTag("subscriber_picker_close_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            composeTestRule.onAllNodesWithTag("available_subscribers_dialog").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun bluetoothThermalPrinter_selectSaveStatusEscPosEncodingAndDirectTestPrint() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ThemePreferencesRepository(context)

        val pairedList = listOf(
            PairedBluetoothPrinter(name = "RPP02N-Thermal-58", macAddress = "66:32:11:AA:BB:01"),
            PairedBluetoothPrinter(name = "Xprinter-80mm-BT", macAddress = "66:32:11:AA:BB:02")
        )
        var capturedMac: String? = null
        var capturedBytes: ByteArray? = null

        BluetoothThermalPrinterService.pairedPrintersProviderForTest = { Result.success(pairedList) }
        BluetoothThermalPrinterService.connectionCheckerForTest = { PrinterConnectionState.CONNECTED }
        BluetoothThermalPrinterService.directByteSenderForTest = { mac, bytes ->
            capturedMac = mac
            capturedBytes = bytes
            Result.success(Unit)
        }

        // 1. Verify ESC/POS raster command encoding structure (ESC @, GS v 0, feed, partial cut)
        val tinyBmp = Bitmap.createBitmap(384, 32, Bitmap.Config.ARGB_8888)
        tinyBmp.eraseColor(Color.WHITE)
        for (x in 10..80) tinyBmp.setPixel(x, 10, Color.BLACK)
        val escPosBytes = BluetoothThermalPrinterService.encodeBitmapToEscPos(tinyBmp)
        tinyBmp.recycle()

        assertEquals(0x1B.toByte(), escPosBytes[0]) // ESC
        assertEquals(0x40.toByte(), escPosBytes[1]) // @ init
        // Contains GS v 0 (0x1D 0x76 0x30) raster bit image header
        var hasRasterHeader = false
        for (i in 0 until escPosBytes.size - 3) {
            if (escPosBytes[i] == 0x1D.toByte() && escPosBytes[i + 1] == 0x76.toByte() && escPosBytes[i + 2] == 0x30.toByte()) {
                hasRasterHeader = true
                break
            }
        }
        assertTrue("ESC/POS output must contain GS v 0 raster command", hasRasterHeader)
        // Ends with partial cut GS V 66 0
        assertEquals(0x1D.toByte(), escPosBytes[escPosBytes.size - 4])
        assertEquals(0x56.toByte(), escPosBytes[escPosBytes.size - 3])
        assertEquals(0x42.toByte(), escPosBytes[escPosBytes.size - 2])
        assertEquals(0x00.toByte(), escPosBytes[escPosBytes.size - 1])

        // 2. Verify UI selection of paired Bluetooth printer + saving + status + direct Test Print
        var savedPrefsSnapshot = NotificationAndPrintPrefs()
        composeTestRule.setContent {
            var prefs by remember { mutableStateOf(NotificationAndPrintPrefs()) }
            Alasal2027Theme {
                SystemDiagnosticsScreen(
                    state = FoundationUiState.Ready(
                        dashboard = FoundationDashboardSnapshot(
                            subscriberCount = 0,
                            invoiceCount = 0,
                            collectionCount = 0,
                            totalArrears = 0.0,
                            totalCredits = 0.0,
                            totalCollected = 0.0,
                            totalReceivables = 0.0,
                            paidInvoicesCount = 0,
                            unpaidInvoicesCount = 0,
                            partialInvoicesCount = 0,
                            primaryTariff = null,
                            tableCounts = TableRowCounts(
                                subscribersCount = 0,
                                invoicesCount = 0,
                                collectionsCount = 0,
                                receiptsCount = 0,
                                kwhTariffsCount = 0,
                                tariffChangeLogsCount = 0,
                                arrearsCount = 0,
                                creditBalancesCount = 0,
                                meterReadingsCount = 0,
                                remindersCount = 0,
                                usersCount = 0,
                                permissionsCount = 0,
                                appSettingsCount = 0,
                                financialAuditLogsCount = 0
                            )
                        ),
                        subscribers = emptyList(),
                        tariffs = emptyList(),
                        tariffChangeLogs = emptyList(),
                        tableStatuses = emptyList(),
                        users = emptyList(),
                        permissions = emptyList(),
                        settings = emptyList(),
                        auditLogs = emptyList()
                    ),
                    themeMode = AppThemeMode.SYSTEM,
                    feedback = null,
                    onDismissFeedback = {},
                    onSelectThemeMode = {},
                    onVerifySchema = {},
                    onRequestSafBackup = {},
                    notificationPrefs = prefs,
                    onUpdateNotificationPrefs = { transform ->
                        prefs = transform(prefs)
                        savedPrefsSnapshot = prefs
                    }
                )
            }
        }

        // Open paired Bluetooth devices dialog
        composeTestRule.onNodeWithTag("select_bluetooth_printer_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("bluetooth_devices_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("bt_device_item_66:32:11:AA:BB:02").performClick()
        composeTestRule.waitForIdle()

        // Verify saved state and connection badge "متصل"
        assertEquals("BLUETOOTH", savedPrefsSnapshot.printerConnectionType)
        assertEquals("Xprinter-80mm-BT", savedPrefsSnapshot.printerDeviceName)
        assertEquals("66:32:11:AA:BB:02", savedPrefsSnapshot.printerDeviceAddress)
        composeTestRule.onNodeWithTag("bluetooth_printer_status_badge").assertTextContains("متصل")

        // Persist through ThemePreferencesRepository and verify round-trip
        repo.updateNotificationPrefs {
            it.copy(
                printerConnectionType = savedPrefsSnapshot.printerConnectionType,
                printerDeviceName = savedPrefsSnapshot.printerDeviceName,
                printerDeviceAddress = savedPrefsSnapshot.printerDeviceAddress
            )
        }
        val persisted = repo.getNotificationPrefsOnce()
        assertEquals("BLUETOOTH", persisted.printerConnectionType)
        assertEquals("Xprinter-80mm-BT", persisted.printerDeviceName)
        assertEquals("66:32:11:AA:BB:02", persisted.printerDeviceAddress)

        // Trigger direct Test Print ("طباعة تجريبية")
        composeTestRule.onNodeWithTag("bluetooth_test_print_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { capturedBytes != null }
        assertEquals("66:32:11:AA:BB:02", capturedMac)
        assertTrue("Direct ESC/POS payload must be non-empty", (capturedBytes?.size ?: 0) > 50)
    }

    @Test
    fun consumptionPeriodDates_usePreviousInvoiceDateOrSubscriptionDateFallback_inUiAndPrintOutputs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val subs = sampleSubscribers()
        val subWithHistory = subs[0] // subscriptionDate = 1/12/2025
        val subFirstInvoice = subs[1] // subscriptionDate = 1/1/2026

        val prevInvoice = InvoiceEntity(
            id = 100L,
            invoiceNumber = "INV-2026-0100",
            subscriberId = subWithHistory.id,
            tariffId = 1L,
            billingPeriod = "2026-01",
            previousReading = 100.0,
            currentReading = 150.0,
            consumedKwh = 50.0,
            unitPriceApplied = 0.50,
            energyAmount = 25.0,
            fixedFeeApplied = 0.0,
            netPayableAmount = 25.0,
            remainingAmount = 0.0,
            paidAmount = 25.0,
            status = "PAID",
            issuedAt = dateMillis(2026, 1, 1)
        )
        val currentInvoice = InvoiceEntity(
            id = 101L,
            invoiceNumber = "INV-2026-0101",
            subscriberId = subWithHistory.id,
            tariffId = 1L,
            billingPeriod = "2026-01",
            previousReading = 150.0,
            currentReading = 220.0,
            consumedKwh = 70.0,
            unitPriceApplied = 0.50,
            energyAmount = 35.0,
            fixedFeeApplied = 0.0,
            netPayableAmount = 35.0,
            remainingAmount = 35.0,
            status = "UNPAID",
            issuedAt = dateMillis(2026, 1, 15)
        )
        val firstEverInvoice = InvoiceEntity(
            id = 102L,
            invoiceNumber = "INV-2026-0102",
            subscriberId = subFirstInvoice.id,
            tariffId = 1L,
            billingPeriod = "2026-01",
            previousReading = 0.0,
            currentReading = 40.0,
            consumedKwh = 40.0,
            unitPriceApplied = 0.60,
            energyAmount = 24.0,
            fixedFeeApplied = 0.0,
            netPayableAmount = 24.0,
            remainingAmount = 24.0,
            status = "UNPAID",
            issuedAt = dateMillis(2026, 1, 15)
        )

        val allInvoices = listOf(prevInvoice, currentInvoice, firstEverInvoice)

        // Case A: Subscriber has previous invoice dated 1/1/2026 -> current invoice dated 15/1/2026
        val periodWithPrev = ConsumptionPeriodHelper.formatInvoicePeriodRange(
            invoice = currentInvoice,
            subscriber = subWithHistory,
            allInvoices = allInvoices
        )
        assertEquals("الفترة: من 1/1/2026 إلى 15/1/2026", periodWithPrev)

        // Case B: First invoice ever -> falls back to subscriber.subscriptionDate (1/1/2026 -> 15/1/2026)
        val periodFallbackSub = ConsumptionPeriodHelper.formatInvoicePeriodRange(
            invoice = firstEverInvoice,
            subscriber = subFirstInvoice,
            allInvoices = allInvoices
        )
        assertEquals("الفترة: من 1/1/2026 إلى 15/1/2026", periodFallbackSub)

        // Case C: Reading entry period uses latest previous invoice (15/1/2026 -> 30/1/2026)
        val readingPeriod = ConsumptionPeriodHelper.formatReadingPeriodRange(
            subscriber = subWithHistory,
            allInvoices = allInvoices,
            readingDateMillis = dateMillis(2026, 1, 30)
        )
        assertEquals("الفترة: من 15/1/2026 إلى 30/1/2026", readingPeriod)

        // Case D: Verified in Share Text, Print HTML, and A4 PDF generation
        val shareText = InvoiceShareAndPrintService.buildInvoiceShareText(currentInvoice, subWithHistory, periodWithPrev)
        assertTrue(shareText.contains("الفترة: من 1/1/2026 إلى 15/1/2026"))

        val printHtml = InvoiceShareAndPrintService.buildInvoicePrintHtml(currentInvoice, subWithHistory, 58, periodWithPrev)
        assertTrue(printHtml.contains("الفترة: من 1/1/2026 إلى 15/1/2026"))

        val pdfFile = DocumentPdfAndThermalService.generateInvoiceA4Pdf(
            context = context,
            invoice = currentInvoice,
            subscriber = subWithHistory,
            periodLabel = periodWithPrev
        )
        assertTrue("Generated invoice PDF must exist and be non-empty", pdfFile.exists() && pdfFile.length() > 100L)

        // Case E: Verified in InvoicesManagementScreen UI card + Direct Bluetooth Print trigger
        var directInvoiceBytes: ByteArray? = null
        BluetoothThermalPrinterService.directByteSenderForTest = { _, bytes ->
            directInvoiceBytes = bytes
            Result.success(Unit)
        }
        composeTestRule.setContent {
            Alasal2027Theme {
                InvoicesManagementScreen(
                    invoices = allInvoices,
                    subscribers = subs,
                    selectedInvoiceDetail = null,
                    feedback = null,
                    onDismissFeedback = {},
                    onSelectInvoice = {},
                    onDismissDetail = {},
                    onRecordPayment = { _, _, _, _, done -> done() },
                    onRepriceInvoice = { _, _, _, done -> done() },
                    notificationPrefs = NotificationAndPrintPrefs(
                        printerConnectionType = "BLUETOOTH",
                        printerDeviceName = "RPP02N",
                        printerDeviceAddress = "66:32:11:AA:BB:01",
                        thermalPaperWidthMm = 58
                    )
                )
            }
        }

        composeTestRule.onNodeWithTag("invoice_period_INV-2026-0101")
            .assertTextContains("الفترة: من 1/1/2026 إلى 15/1/2026")
        composeTestRule.onNodeWithTag("invoice_print_btn_INV-2026-0101").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { directInvoiceBytes != null }
        assertTrue("Direct Bluetooth invoice print must send ESC/POS bytes", (directInvoiceBytes?.size ?: 0) > 50)
    }

    @Test
    fun reportsScreen_usesUnifiedAvailableSubscribersDialog() {
        val subs = sampleSubscribers()
        composeTestRule.setContent {
            Alasal2027Theme {
                ReportsAndStatementsScreen(
                    subscribers = subs,
                    invoices = emptyList(),
                    collectionsWithReceipts = emptyList(),
                    readings = emptyList(),
                    arrearsRecords = emptyList(),
                    creditTransactions = emptyList(),
                    users = emptyList(),
                    notificationPrefs = NotificationAndPrintPrefs(),
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("reports_subscriber_picker_button").performClick()
        composeTestRule.onNodeWithTag("available_subscribers_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("قائمة المشتركين المتاحين").assertIsDisplayed()
        composeTestRule.onNodeWithTag("subscriber_picker_item_USER-2026-081").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reports_subscriber_picker_button").assertTextContains("أحمد صالح العسل")
    }
}
