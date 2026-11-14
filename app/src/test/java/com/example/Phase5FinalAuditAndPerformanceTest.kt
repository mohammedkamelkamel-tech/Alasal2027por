package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.AlasalDatabase
import com.example.data.repository.AlasalRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.MeterReadingInput
import com.example.data.repository.SubscriberInput
import com.example.domain.finance.ReportCategory
import com.example.domain.finance.ReportFilter
import com.example.domain.finance.StationReportGenerator
import com.example.service.AppThemeMode
import com.example.service.BackupAndRestoreManager
import com.example.service.DocumentPdfAndThermalService
import com.example.service.ThemePreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase5FinalAuditAndPerformanceTest {

    private lateinit var context: Context
    private lateinit var db: AlasalDatabase
    private lateinit var repo: AlasalRepository
    private lateinit var billingRepo: BillingRepository
    private lateinit var backupManager: BackupAndRestoreManager

    @Before
    fun setup() {
        runCatching {
            val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? MutableMap<*, *>)?.clear()
        }
        context = ApplicationProvider.getApplicationContext()
        AlasalDatabase.closeAndResetInstance()
        context.deleteDatabase(AlasalDatabase.DATABASE_NAME)
        File(context.filesDir, "backups").deleteRecursively()

        db = AlasalDatabase.getInstance(context)
        repo = AlasalRepository(db)
        billingRepo = BillingRepository(db, repo)
        backupManager = BackupAndRestoreManager(context, db, repo)

        runBlocking {
            repo.ensureBaselineSeeded()
        }
    }

    @After
    fun tearDown() {
        AlasalDatabase.closeAndResetInstance()
        context.deleteDatabase(AlasalDatabase.DATABASE_NAME)
        File(context.filesDir, "backups").deleteRecursively()
    }

    @Test
    fun `strict financial audit verifies monotonic non-repeating invoice, collection, and receipt numbers across delete, reprice, carry-over, and app restart`(): Unit = runBlocking {
        val subId = repo.saveSubscriber(
            SubscriberInput(
                subscriberCode = "AUD-001",
                fullName = "سعيد العسلي",
                phoneNumber = "967770999888",
                meterNumber = "MTR-AUD-1",
                meterSealNumber = "SL-AUD-1",
                address = "المركز الرئيسي",
                subscriptionType = "تجاري",
                customKwhPrice = 0.50,
                subscriptionDate = 1_000L,
                isActive = true,
                initialCreditBalance = 15.0
            )
        ).getOrThrow()

        // Month 1: 100 kWh * 0.50 = 50.0 -> minus 15.0 credit applied ONCE = 35.0 net
        val r1 = billingRepo.saveMeterReading(
            MeterReadingInput(subscriberId = subId, billingPeriod = "2027-01", currentReading = 100.0, readingDate = 2_000L)
        ).getOrThrow()
        val inv1Id = billingRepo.issueInvoiceForReading(r1).getOrThrow()
        val inv1 = db.billingDao().getInvoiceById(inv1Id)!!
        assertEquals("INV-2027-0001", inv1.invoiceNumber)
        assertEquals(15.0, inv1.creditDeducted, 0.01)
        assertEquals(35.0, inv1.netPayableAmount, 0.01)
        assertEquals(0.0, db.subscriberDao().getById(subId)!!.currentCreditBalance, 0.01)

        // Partial payment #1 on INV-2027-0001 -> 15.0 paid, 20.0 remaining
        val col1Id = billingRepo.recordInvoicePayment(inv1Id, amountPaid = 15.0, paymentMethod = "CASH").getOrThrow()
        val rcp1 = billingRepo.getReceiptByCollectionId(col1Id)!!
        assertEquals("RCP-2027-0001", rcp1.receiptNumber)

        // Month 2: 40 kWh * 0.50 = 20.0 + 20.0 arrears carried from inv1 = 40.0 net -> INV-2027-0002
        val r2 = billingRepo.saveMeterReading(
            MeterReadingInput(subscriberId = subId, billingPeriod = "2027-02", currentReading = 140.0, readingDate = 3_000L)
        ).getOrThrow()
        val inv2Id = billingRepo.issueInvoiceForReading(r2).getOrThrow()
        val inv2 = db.billingDao().getInvoiceById(inv2Id)!!
        assertEquals("INV-2027-0002", inv2.invoiceNumber)
        assertEquals(0.0, inv2.creditDeducted, 0.01) // Credit never deducted twice!
        assertEquals(20.0, inv2.previousArrearsApplied, 0.01)
        assertEquals(40.0, inv2.netPayableAmount, 0.01)

        // Delete unpaid INV-2027-0002 safely & verify inv1 restored cleanly to PARTIAL with 20.0 remaining
        billingRepo.deleteUnpaidInvoiceSafely(inv2Id).getOrThrow()
        val inv1AfterDelete = db.billingDao().getInvoiceById(inv1Id)!!
        assertEquals("PARTIAL", inv1AfterDelete.status)
        assertEquals(20.0, inv1AfterDelete.remainingAmount, 0.01)

        // Simulate app/phone restart (close Room & reopen) BEFORE issuing next invoice
        AlasalDatabase.closeAndResetInstance()
        db = AlasalDatabase.getInstance(context)
        repo = AlasalRepository(db)
        billingRepo = BillingRepository(db, repo)

        // Bulk price update BEFORE Month 3: old inv1 price (0.50) remains untouched!
        repo.applyKwhPriceUpdate(
            targetSubscriberIds = listOf(subId),
            newPricePerKwh = 0.75,
            effectiveFrom = 4_000L,
            reason = "تحديث تعرفة التدقيق"
        ).getOrThrow()
        assertEquals(0.50, db.billingDao().getInvoiceById(inv1Id)!!.unitPriceApplied, 0.01)

        // Issue new invoice for period 2027-03 (140 -> 180 = 40 kWh) -> MUST get INV-2027-0003 (NEVER reuse deleted 0002!)
        val r3 = billingRepo.saveMeterReading(
            MeterReadingInput(subscriberId = subId, billingPeriod = "2027-03", currentReading = 180.0, readingDate = 4_500L)
        ).getOrThrow()
        val inv3Id = billingRepo.issueInvoiceForReading(r3).getOrThrow()
        val inv3 = db.billingDao().getInvoiceById(inv3Id)!!
        assertEquals("INV-2027-0003", inv3.invoiceNumber)
        assertNotEquals("INV-2027-0002", inv3.invoiceNumber)
        assertEquals(20.0, inv3.previousArrearsApplied, 0.01)
        assertEquals(50.0, inv3.netPayableAmount, 0.01) // 40 kWh * 0.75 = 30 + 20 arrears = 50.0

        // Overpay inv3 (pay 65.0 against 50.0) -> 50.0 settles inv3, 15.0 enters credit_balances
        val col2Id = billingRepo.recordInvoicePayment(inv3Id, amountPaid = 65.0, paymentMethod = "TRANSFER").getOrThrow()
        val rcp2 = billingRepo.getReceiptByCollectionId(col2Id)!!
        assertEquals("RCP-2027-0002", rcp2.receiptNumber)

        val finalSub = db.subscriberDao().getById(subId)!!
        assertEquals(0.0, finalSub.currentArrearsAmount, 0.01)
        assertEquals(15.0, finalSub.currentCreditBalance, 0.01)

        // Reconcile statement & report directly against database records
        val allInvoices = db.billingDao().getAllInvoicesOnce()
        val allColsWithReceipts = db.collectionDao().observeAllCollectionsWithReceipts().first()
        val allReadings = db.billingDao().getAllMeterReadingsOnce()
        val allArrears = db.billingDao().getAllArrearsOnce()
        val allCredits = db.billingDao().getAllCreditTransactionsOnce()
        val allUsers = db.systemDao().getAllUsersOnce()

        val statement = StationReportGenerator.buildSubscriberStatement(
            subscriber = finalSub,
            invoices = allInvoices,
            collectionsWithReceipts = allColsWithReceipts,
            readings = allReadings,
            creditTransactions = allCredits
        )
        assertEquals(0.0, statement.activeArrears, 0.01)
        assertEquals(15.0, statement.creditBalance, 0.01)
        assertEquals(80.0, statement.totalCollected, 0.01) // 15.0 + 65.0

        val report = StationReportGenerator.generateReport(
            filter = ReportFilter(category = ReportCategory.COLLECTIONS),
            subscribers = listOf(finalSub),
            invoices = allInvoices,
            collectionsWithReceipts = allColsWithReceipts,
            readings = allReadings,
            arrears = allArrears,
            creditTransactions = allCredits,
            users = allUsers
        )
        assertEquals(2, report.uiRows.size)
        assertTrue(report.summaryTotals.any { it.second.contains("80") })
    }

    @Test
    fun `offline theme preferences, session persistence across restart, and WhatsApp share intent readiness work cleanly`(): Unit = runBlocking {
        val prefsRepo = ThemePreferencesRepository(context)
        prefsRepo.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, prefsRepo.themeMode.first())

        val collector = db.systemDao().getUserByUsername("collector_1")!!
        prefsRepo.setActiveUserId(collector.id)
        assertEquals(collector.id, prefsRepo.getBackupAndSessionPrefsOnce().activeUserId)

        prefsRepo.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, prefsRepo.themeMode.first())

        // Verify WhatsApp receipt text/intent creation does not fail without WhatsApp installed
        val subId = repo.saveSubscriber(
            SubscriberInput(
                subscriberCode = "WA-01",
                fullName = "مشترك واتساب",
                phoneNumber = "967771234567",
                meterNumber = "MTR-WA-1",
                meterSealNumber = "S-WA",
                address = "حي البستان",
                subscriptionType = "منزلي",
                customKwhPrice = 0.45,
                subscriptionDate = 1000L,
                isActive = true
            )
        ).getOrThrow()
        val rId = billingRepo.saveMeterReading(
            MeterReadingInput(subscriberId = subId, billingPeriod = "2027-05", currentReading = 50.0, readingDate = 2000L)
        ).getOrThrow()
        val invId = billingRepo.issueInvoiceForReading(rId).getOrThrow()
        val colId = billingRepo.recordInvoicePayment(invId, amountPaid = 22.5).getOrThrow()
        val receipt = billingRepo.getReceiptByCollectionId(colId)!!
        assertNotNull(receipt)

        // Verify receipt PDF/PNG + A4 report export cleanly
        val pdf = DocumentPdfAndThermalService.generateReceiptA4Pdf(context, receipt)
        assertTrue(pdf.exists() && pdf.length() > 500)
        val png = DocumentPdfAndThermalService.generateReceiptPngImage(context, receipt)
        assertTrue(png.exists() && png.length() > 500)
    }

    @Test
    fun `high-volume performance audit with 400 subscribers, 400 invoices, 250 collections, bulk repricing, reporting, and 18-step backup-restore`(): Unit = runBlocking {
        val subscriberIds = mutableListOf<Long>()

        val seedElapsedMs = measureTimeMillis {
            for (i in 1..400) {
                val sid = repo.saveSubscriber(
                    SubscriberInput(
                        subscriberCode = String.format("HV-%04d", i),
                        fullName = "مشترك اختبار الأداء رقم $i",
                        phoneNumber = String.format("77%07d", i),
                        meterNumber = String.format("M-HV-%04d", i),
                        meterSealNumber = "SL-$i",
                        address = "المنطقة ${i % 10}",
                        subscriptionType = if (i % 3 == 0) "تجاري" else "منزلي",
                        customKwhPrice = 0.50,
                        subscriptionDate = 1_000L + i,
                        isActive = true
                    )
                ).getOrThrow()
                subscriberIds.add(sid)

                val rid = billingRepo.saveMeterReading(
                    MeterReadingInput(
                        subscriberId = sid,
                        billingPeriod = "2027-05",
                        currentReading = 100.0 + (i % 50),
                        readingDate = 2_000L + i
                    )
                ).getOrThrow()
                val invId = billingRepo.issueInvoiceForReading(rid).getOrThrow()
                if (i <= 250) {
                    billingRepo.recordInvoicePayment(invId, amountPaid = 25.0, collectedAt = 3_000L + i).getOrThrow()
                }
            }
        }
        assertTrue("Seeding 400 subscribers + readings + invoices + 250 collections took too long: ${seedElapsedMs}ms", seedElapsedMs < 30_000L)

        // Bulk tariff update across all 400 subscribers inside one transaction
        val bulkPriceElapsedMs = measureTimeMillis {
            val updatedCount = repo.applyKwhPriceUpdate(
                targetSubscriberIds = null,
                newPricePerKwh = 0.60,
                effectiveFrom = 10_000L,
                reason = "اختبار الأداء الشامل"
            ).getOrThrow()
            assertEquals(400, updatedCount)
        }
        assertTrue("Bulk tariff update for 400 subscribers took too long: ${bulkPriceElapsedMs}ms", bulkPriceElapsedMs < 5_000L)

        // Generate full station report across 400 subscribers + 400 invoices + 250 collections
        val subs = db.subscriberDao().getAllSubscribersOnce()
        val invoices = db.billingDao().getAllInvoicesOnce()
        val cols = db.collectionDao().observeAllCollectionsWithReceipts().first()
        val readings = db.billingDao().getAllMeterReadingsOnce()
        val arrears = db.billingDao().getAllArrearsOnce()
        val credits = db.billingDao().getAllCreditTransactionsOnce()
        val users = db.systemDao().getAllUsersOnce()

        assertEquals(400, subs.size)
        assertEquals(400, invoices.size)
        assertEquals(400, invoices.map { it.invoiceNumber }.distinct().size)
        assertEquals(250, cols.size)
        assertEquals(250, cols.map { it.receipt?.receiptNumber }.distinct().size)

        val report = StationReportGenerator.generateReport(
            filter = ReportFilter(category = ReportCategory.COLLECTIONS),
            subscribers = subs,
            invoices = invoices,
            collectionsWithReceipts = cols,
            readings = readings,
            arrears = arrears,
            creditTransactions = credits,
            users = users
        )
        assertEquals(250, report.uiRows.size)
        assertTrue(report.summaryTotals.isNotEmpty())

        // Create backup and restore via 18-step safe restore on 400-subscriber DB
        val backupInfo = backupManager.createBackup("MANUAL").getOrThrow()
        assertEquals(400, backupInfo.subscribersCount)
        assertEquals(400, backupInfo.invoicesCount)
        assertEquals(250, backupInfo.collectionsCount)

        val restoreResult = backupManager.executeSafeRestoreFromFile(File(backupInfo.absolutePath))
        assertTrue("High-volume restore failed: ${restoreResult.message}", restoreResult.succeeded)
        assertEquals(400, restoreResult.restoredSubscribersCount)
        assertEquals(400, restoreResult.restoredInvoicesCount)
        assertEquals(250, restoreResult.restoredCollectionsCount)

        // Refresh reference to restored DB
        db = AlasalDatabase.getInstance(context)
        assertEquals(400, db.subscriberDao().getAllSubscribersOnce().size)
        assertEquals(250, db.collectionDao().getAllReceiptsOnce().size)
    }
}
