package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.local.database.AlasalDatabase
import com.example.data.local.entity.UserEntity
import com.example.data.repository.AlasalRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.MeterReadingInput
import com.example.data.repository.SubscriberInput
import com.example.domain.finance.DateRangePreset
import com.example.domain.finance.ReportCategory
import com.example.domain.finance.ReportFilter
import com.example.domain.finance.StationReportGenerator
import com.example.service.AlasalNotificationService
import com.example.service.DocumentPdfAndThermalService
import com.example.service.NotificationAndPrintPrefs
import com.example.service.StationMaintenanceWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase3CollectionsReportsAndPrintTest {

    private lateinit var context: Context
    private lateinit var database: AlasalDatabase
    private lateinit var coreRepo: AlasalRepository
    private lateinit var billingRepo: BillingRepository

    @Before
    fun setup() {
        runCatching {
            val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AlasalDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys=ON;")
                }
            })
            .build()
        coreRepo = AlasalRepository(database)
        billingRepo = BillingRepository(database, coreRepo)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `multiple collections on single invoice preserve prior collections and persist sequential receipt numbers across reopen`() = runTest {
        coreRepo.ensureBaselineSeeded()
        val collectorId = database.systemDao().insertUser(
            UserEntity(
                username = "collector_tariq",
                fullName = "طارق المحصل",
                roleCode = "COLLECTOR",
                pinHash = AlasalRepository.sha256("2222")
            )
        )
        val subId = coreRepo.saveSubscriber(
            SubscriberInput(
                subscriberCode = "SUB-C1",
                fullName = "محمد أمين العسل",
                phoneNumber = "967771234567",
                meterNumber = "MTR-C1",
                meterSealNumber = "SL-C1",
                address = "الشارع الرئيسي",
                subscriptionType = "منزلي",
                customKwhPrice = 1.0,
                subscriptionDate = System.currentTimeMillis(),
                isActive = true
            )
        ).getOrThrow()

        val readingId = billingRepo.saveMeterReading(
            MeterReadingInput(
                subscriberId = subId,
                billingPeriod = "2027-03",
                currentReading = 100.0,
                readingDate = System.currentTimeMillis()
            )
        ).getOrThrow()
        val invoiceId = billingRepo.issueInvoiceForReading(readingId).getOrThrow()

        // 1st partial collection: 30
        val col1Id = billingRepo.recordInvoicePayment(
            invoiceId = invoiceId,
            amountPaid = 30.0,
            paymentMethod = "CASH",
            actorUserId = collectorId
        ).getOrThrow()
        // 2nd partial collection: 45
        val col2Id = billingRepo.recordInvoicePayment(
            invoiceId = invoiceId,
            amountPaid = 45.0,
            paymentMethod = "BANK_TRANSFER",
            actorUserId = collectorId
        ).getOrThrow()
        // 3rd final collection: 25
        val col3Id = billingRepo.recordInvoicePayment(
            invoiceId = invoiceId,
            amountPaid = 25.0,
            paymentMethod = "MOBILE_WALLET",
            actorUserId = null
        ).getOrThrow()

        val bundle = billingRepo.getInvoiceDetailBundle(invoiceId)!!
        assertEquals(3, bundle.collections.size)
        assertEquals("PAID", bundle.invoice.status)
        assertEquals(100.0, bundle.invoice.paidAmount, 0.001)
        assertEquals(0.0, bundle.invoice.remainingAmount, 0.001)

        val r1 = billingRepo.getReceiptByCollectionId(col1Id)!!
        val r2 = billingRepo.getReceiptByCollectionId(col2Id)!!
        val r3 = billingRepo.getReceiptByCollectionId(col3Id)!!
        assertEquals("RCP-2027-0001", r1.receiptNumber)
        assertEquals("RCP-2027-0002", r2.receiptNumber)
        assertEquals("RCP-2027-0003", r3.receiptNumber)

        assertEquals(100.0, r1.previousBalance, 0.001)
        assertEquals(70.0, r1.balanceAfterReceipt, 0.001)
        assertEquals("طارق المحصل", r1.collectorNameSnapshot)
        assertEquals(70.0, r2.previousBalance, 0.001)
        assertEquals(25.0, r2.balanceAfterReceipt, 0.001)
        assertEquals("BANK_TRANSFER", r2.paymentMethod)
        assertEquals(25.0, r3.previousBalance, 0.001)
        assertEquals(0.0, r3.balanceAfterReceipt, 0.001)

        // Verify persisted receipt sequence in app_settings survives DB reopen on disk
        val diskDbName = "receipt_seq_persist_test.db"
        context.deleteDatabase(diskDbName)
        val diskDb1 = Room.databaseBuilder(context, AlasalDatabase::class.java, diskDbName)
            .allowMainThreadQueries().build()
        val diskCore1 = AlasalRepository(diskDb1)
        val diskBilling1 = BillingRepository(diskDb1, diskCore1)
        diskCore1.ensureBaselineSeeded()
        val dSub = diskCore1.saveSubscriber(
            SubscriberInput(0, "SUB-DISK", "مشترك قرص", "770", "MTR-DSK", "S1", "حي", "منزلي", 1.0, System.currentTimeMillis(), true)
        ).getOrThrow()
        val dRead1 = diskBilling1.saveMeterReading(MeterReadingInput(dSub, "2027-01", 50.0, System.currentTimeMillis())).getOrThrow()
        val dInv1 = diskBilling1.issueInvoiceForReading(dRead1).getOrThrow()
        val dCol1 = diskBilling1.recordInvoicePayment(dInv1, 20.0).getOrThrow()
        assertEquals("RCP-2027-0001", diskBilling1.getReceiptByCollectionId(dCol1)!!.receiptNumber)
        diskDb1.close()

        // Reopen database cleanly and verify next receipt increments to RCP-2027-0002
        val diskDb2 = Room.databaseBuilder(context, AlasalDatabase::class.java, diskDbName)
            .allowMainThreadQueries().build()
        val diskBilling2 = BillingRepository(diskDb2, AlasalRepository(diskDb2))
        val dCol2 = diskBilling2.recordInvoicePayment(dInv1, 30.0).getOrThrow()
        assertEquals("RCP-2027-0002", diskBilling2.getReceiptByCollectionId(dCol2)!!.receiptNumber)
        diskDb2.close()
        context.deleteDatabase(diskDbName)
    }

    @Test
    fun `dashboard snapshot, all 9 report categories, date filters, and subscriber statement match database state`() = runTest {
        coreRepo.ensureBaselineSeeded()
        val collectorId = database.systemDao().insertUser(
            UserEntity(username = "col_user", fullName = "ياسر المحصل", roleCode = "COLLECTOR", pinHash = "x")
        )
        val now = System.currentTimeMillis()
        val twoMonthsAgo = now - 65L * 86_400_000L

        val subA = coreRepo.saveSubscriber(
            SubscriberInput(0, "SUB-A", "أحمد", "771111111", "MTR-A", "SA", "عنوان أ", "منزلي", 1.0, now, true, initialCreditBalance = 10.0)
        ).getOrThrow()
        val subB = coreRepo.saveSubscriber(
            SubscriberInput(0, "SUB-B", "خالد", "772222222", "MTR-B", "SB", "عنوان ب", "تجاري", 2.0, now, true)
        ).getOrThrow()

        // Sub A reading + invoice (100 kWh * 1.0 = 100 - 10 credit = 90 net; pay 40 -> PARTIAL, remaining 50)
        val rA = billingRepo.saveMeterReading(MeterReadingInput(subA, "2027-04", 100.0, now)).getOrThrow()
        val invA = billingRepo.issueInvoiceForReading(rA).getOrThrow()
        billingRepo.recordInvoicePayment(invA, 40.0, "CASH", actorUserId = collectorId, collectedAt = now).getOrThrow()

        // Sub B reading + invoice (50 kWh * 2.0 = 100 net; UNPAID -> 100)
        val rB = billingRepo.saveMeterReading(MeterReadingInput(subB, "2027-04", 50.0, twoMonthsAgo)).getOrThrow()
        val invB = billingRepo.issueInvoiceForReading(rB).getOrThrow()

        // Sub B 2nd reading + fully paid invoice (20 kWh * 2.0 = 40 + 100 arrears = 140 net -> pay 150 -> PAID + 10 surplus credit)
        val rB2 = billingRepo.saveMeterReading(MeterReadingInput(subB, "2027-05", 70.0, now)).getOrThrow()
        val invB2 = billingRepo.issueInvoiceForReading(rB2).getOrThrow()
        billingRepo.recordInvoicePayment(invB2, 150.0, "BANK_TRANSFER", actorUserId = collectorId, collectedAt = now).getOrThrow()

        val snapshot = coreRepo.observeDashboardSnapshot().first()
        assertEquals(2, snapshot.subscriberCount)
        assertEquals(3, snapshot.invoiceCount)
        assertEquals(1, snapshot.paidInvoicesCount)
        assertEquals(1, snapshot.partialInvoicesCount)
        assertEquals(0, snapshot.unpaidInvoicesCount) // invB is CARRIED_OVER into invB2
        assertEquals(190.0, snapshot.totalCollected, 0.001)
        assertEquals(50.0, snapshot.totalArrears, 0.001)
        assertEquals(10.0, snapshot.totalCredits, 0.001)

        val subs = coreRepo.observeSubscribers().first()
        val invs = billingRepo.observeAllInvoices().first()
        val cols = billingRepo.observeAllCollectionsWithReceipts().first()
        val reads = billingRepo.observeAllMeterReadings().first()
        val arrs = billingRepo.observeAllArrears().first()
        val creds = billingRepo.observeAllCreditTransactions().first()
        val users = coreRepo.observeUsers().first()

        // Verify all 9 report categories execute accurately
        ReportCategory.entries.forEach { category ->
            val rep = StationReportGenerator.generateReport(
                filter = ReportFilter(
                    category = category,
                    subscriberId = if (category == ReportCategory.SUBSCRIBER_STATEMENT) subA else null,
                    datePreset = DateRangePreset.ALL
                ),
                subscribers = subs,
                invoices = invs,
                collectionsWithReceipts = cols,
                readings = reads,
                arrears = arrs,
                creditTransactions = creds,
                users = users,
                nowMillis = now
            )
            assertTrue("Report ${category.name} should contain columns", rep.columns.isNotEmpty())
            assertTrue("Report ${category.name} should contain summary", rep.summaryTotals.isNotEmpty())
        }

        // Verify Sub A Statement math
        val subAEntity = subs.first { it.id == subA }
        val stA = StationReportGenerator.buildSubscriberStatement(subAEntity, invs, cols, reads, creds)
        assertEquals(100.0, stA.totalConsumedKwh, 0.001)
        assertEquals(100.0, stA.totalBilledEnergy, 0.001)
        assertEquals(40.0, stA.totalCollected, 0.001)
        assertEquals(50.0, stA.activeArrears, 0.001)
        assertEquals(0.0, stA.creditBalance, 0.001)
        assertEquals(50.0, stA.netFinalBalance, 0.001)

        // Verify collector & date filtering
        val todayColsReport = StationReportGenerator.generateReport(
            filter = ReportFilter(
                category = ReportCategory.COLLECTIONS,
                collectorUserId = collectorId,
                datePreset = DateRangePreset.TODAY
            ),
            subscribers = subs,
            invoices = invs,
            collectionsWithReceipts = cols,
            readings = reads,
            arrears = arrs,
            creditTransactions = creds,
            users = users,
            nowMillis = now
        )
        assertEquals(2, todayColsReport.tableRows.size)

        val customRangeReadings = StationReportGenerator.generateReport(
            filter = ReportFilter(
                category = ReportCategory.CONSUMPTION_READINGS,
                datePreset = DateRangePreset.CUSTOM_RANGE,
                customStartMillis = twoMonthsAgo - 1000L,
                customEndMillis = twoMonthsAgo + 1000L
            ),
            subscribers = subs,
            invoices = invs,
            collectionsWithReceipts = cols,
            readings = reads,
            arrears = arrs,
            creditTransactions = creds,
            users = users,
            nowMillis = now
        )
        assertEquals(1, customRangeReadings.tableRows.size)
    }

    @Test
    fun `offline A4 PDFs, receipt PNG image, 58mm and 80mm thermal bitmaps and PDFs, and FileProvider URI generate cleanly`() = runTest {
        coreRepo.ensureBaselineSeeded()
        val subId = coreRepo.saveSubscriber(
            SubscriberInput(0, "SUB-PDF", "سالم بن علي", "967770123456", "MTR-PDF", "SL-9", "حي النهضة", "تجاري", 0.5, System.currentTimeMillis(), true)
        ).getOrThrow()
        val rId = billingRepo.saveMeterReading(MeterReadingInput(subId, "2027-05", 200.0, System.currentTimeMillis())).getOrThrow()
        val invId = billingRepo.issueInvoiceForReading(rId).getOrThrow()
        val colId = billingRepo.recordInvoicePayment(invId, 60.0, "CASH").getOrThrow()
        val receipt = billingRepo.getReceiptByCollectionId(colId)!!

        // 1. Receipt A4 PDF
        val a4Receipt = DocumentPdfAndThermalService.generateReceiptA4Pdf(context, receipt)
        assertPdfValid(a4Receipt)

        // 2. Receipt PNG Image
        val pngFile = DocumentPdfAndThermalService.generateReceiptPngImage(context, receipt)
        assertTrue(pngFile.exists() && pngFile.length() > 1000)
        val decodedPng = BitmapFactory.decodeFile(pngFile.absolutePath)
        assertNotNull(decodedPng)
        assertEquals(820, decodedPng.width)
        assertEquals(1040, decodedPng.height)
        decodedPng.recycle()

        // 3. Thermal 58mm (384px) & 80mm (576px) Bitmaps + Thermal PDFs
        val bm58 = DocumentPdfAndThermalService.generateThermalReceiptBitmap(context, receipt, paperWidthMm = 58)
        assertEquals(384, bm58.width)
        assertEquals(Color.WHITE, bm58.getPixel(0, 0))
        bm58.recycle()

        val bm80 = DocumentPdfAndThermalService.generateThermalReceiptBitmap(context, receipt, paperWidthMm = 80)
        assertEquals(576, bm80.width)
        assertEquals(Color.WHITE, bm80.getPixel(0, 0))
        bm80.recycle()

        val thermalPdf58 = DocumentPdfAndThermalService.generateThermalReceiptPdf(context, receipt, paperWidthMm = 58)
        assertPdfValid(thermalPdf58)
        val thermalPdf80 = DocumentPdfAndThermalService.generateThermalReceiptPdf(context, receipt, paperWidthMm = 80)
        assertPdfValid(thermalPdf80)

        // 4. Statement A4 PDF & General Report A4 PDF
        val sub = coreRepo.observeSubscribers().first().first { it.id == subId }
        val statement = StationReportGenerator.buildSubscriberStatement(
            subscriber = sub,
            invoices = billingRepo.observeAllInvoices().first(),
            collectionsWithReceipts = billingRepo.observeAllCollectionsWithReceipts().first(),
            readings = billingRepo.observeAllMeterReadings().first(),
            creditTransactions = billingRepo.observeAllCreditTransactions().first()
        )
        val statementPdf = DocumentPdfAndThermalService.generateStatementA4Pdf(context, statement)
        assertPdfValid(statementPdf)

        val report = StationReportGenerator.generateReport(
            filter = ReportFilter(category = ReportCategory.ALL_INVOICES),
            subscribers = listOf(sub),
            invoices = billingRepo.observeAllInvoices().first(),
            collectionsWithReceipts = billingRepo.observeAllCollectionsWithReceipts().first(),
            readings = billingRepo.observeAllMeterReadings().first(),
            arrears = billingRepo.observeAllArrears().first(),
            creditTransactions = billingRepo.observeAllCreditTransactions().first(),
            users = coreRepo.observeUsers().first()
        )
        val reportPdf = DocumentPdfAndThermalService.generateReportA4Pdf(context, report)
        assertPdfValid(reportPdf)

        // 5. FileProvider URI verification
        val shareUri = DocumentPdfAndThermalService.getShareUri(context, a4Receipt)
        assertEquals("content", shareUri.scheme)
        assertTrue(shareUri.toString().contains(context.packageName))
    }

    @Test
    fun `reminders lifecycle, sound or silent notifications, and WorkManager due-reminder worker execute cleanly`() = runTest {
        coreRepo.ensureBaselineSeeded()
        val now = System.currentTimeMillis()

        // 1. Schedule, snooze, and complete a reminder
        val rId = coreRepo.scheduleReminder(
            subscriberId = null,
            reminderType = "METER_READING",
            title = "جولة قراءة الحي الشمالي",
            message = "قراءة عدادات الشهر",
            notes = "ملاحظة اختبارية",
            scheduledAt = now + 3_600_000L
        ).getOrThrow()

        val snoozed = coreRepo.snoozeReminder(rId, 1_800_000L).getOrThrow()
        assertEquals("SNOOZED", snoozed.status)
        assertEquals(1, snoozed.snoozeCount)

        coreRepo.completeReminder(rId).getOrThrow()
        val afterComplete = coreRepo.observeAllReminders().first().first { it.id == rId }
        assertEquals("COMPLETED", afterComplete.status)
        assertNotNull(afterComplete.completedAt)

        // 2. Notification sound vs silent channel routing
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotifManager = Shadows.shadowOf(notifManager)

        assertTrue(
            AlasalNotificationService.dispatchNotification(
                context = context,
                notificationId = 8001,
                title = "تنبيه بصوت",
                body = "رسالة اختبار",
                prefs = NotificationAndPrintPrefs(notificationsEnabled = true, soundEnabled = true)
            )
        )
        assertTrue(
            AlasalNotificationService.dispatchNotification(
                context = context,
                notificationId = 8002,
                title = "تنبيه صامت",
                body = "رسالة صامتة",
                prefs = NotificationAndPrintPrefs(notificationsEnabled = true, soundEnabled = false)
            )
        )
        val n1 = shadowNotifManager.getNotification(8001)
        val n2 = shadowNotifManager.getNotification(8002)
        assertNotNull(n1)
        assertNotNull(n2)
        assertEquals(AlasalNotificationService.CHANNEL_ID_SOUND, n1.channelId)
        assertEquals(AlasalNotificationService.CHANNEL_ID_SILENT, n2.channelId)

        // 3. Test WorkManager triggering due reminder stored in real singleton DB
        val app = context as AlasalApplication
        app.repository.ensureBaselineSeeded()
        val dueAt = System.currentTimeMillis() - 5_000L
        app.repository.scheduleReminder(
            subscriberId = null,
            reminderType = "METER_READING",
            title = "موعد مستحق الآن",
            message = "حان وقت قراءة العداد",
            notes = "",
            scheduledAt = dueAt
        ).getOrThrow()

        val worker = TestListenableWorkerBuilder<StationMaintenanceWorker>(context).build()
        val workerResult = worker.doWork()
        assertTrue(workerResult is ListenableWorker.Result.Success)
        val output = (workerResult as ListenableWorker.Result.Success).outputData
        assertTrue(output.getInt(StationMaintenanceWorker.KEY_DUE_REMINDERS_TRIGGERED, 0) >= 1)
    }

    private fun assertPdfValid(file: File) {
        assertTrue("File ${file.name} should exist", file.exists())
        assertTrue("File ${file.name} should be non-empty", file.length() > 100)
        val header = file.inputStream().use { ByteArray(5).apply { it.read(this) } }
        assertEquals("%PDF-", String(header, Charsets.US_ASCII))
    }
}
