package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.AlasalDatabase
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.repository.AlasalRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.MeterReadingInput
import com.example.data.repository.SubscriberInput
import com.example.domain.finance.FieldDebtFilter
import com.example.domain.finance.StationReportGenerator
import com.example.service.DocumentPdfAndThermalService
import com.example.service.InvoiceShareAndPrintService
import com.example.service.SafBackupManager
import com.example.service.ThemePreferencesRepository
import com.example.ui.viewmodel.FoundationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar-rSA")
class Phase6AutomationStatementsSecurityTest {

    private lateinit var context: Context
    private lateinit var database: AlasalDatabase
    private lateinit var coreRepository: AlasalRepository
    private lateinit var billingRepository: BillingRepository
    private lateinit var themePreferences: ThemePreferencesRepository

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
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys=ON;")
                }
            })
            .build()
        coreRepository = AlasalRepository(database)
        billingRepository = BillingRepository(database, coreRepository)
        themePreferences = ThemePreferencesRepository(context)
    }

    @After
    fun tearDown() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        database.close()
    }

    private suspend fun awaitMainCondition(timeoutMs: Long = 3_000L, predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            delay(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `direct subscriber kWh price update preserves old invoices and applies new rate only to future invoices`(): Unit = runBlocking {
        coreRepository.ensureBaselineSeeded()
        val admin = database.systemDao().getUserByUsername("admin")!!
        val subId = coreRepository.saveSubscriber(
            SubscriberInput(
                subscriberCode = "SUB-DIRECT-01",
                fullName = "أحمد السالمي",
                phoneNumber = "0771234567",
                meterNumber = "MTR-DIR-01",
                meterSealNumber = "SL-01",
                address = "حي السبعين",
                subscriptionType = "منزلي",
                customKwhPrice = 0.50,
                subscriptionDate = System.currentTimeMillis(),
                isActive = true
            ),
            actorUserId = admin.id
        ).getOrThrow()

        // 1. Issue first invoice at initial 0.50 price (100 kWh = 50.0)
        val r1Id = billingRepository.saveMeterReading(
            MeterReadingInput(subId, "2027-01", 100.0, System.currentTimeMillis()),
            actorUserId = admin.id
        ).getOrThrow()
        val inv1Id = billingRepository.issueInvoiceForReading(r1Id, actorUserId = admin.id).getOrThrow()
        val inv1Before = database.billingDao().getInvoiceById(inv1Id)!!
        assertEquals(0.50, inv1Before.unitPriceApplied, 0.001)
        assertEquals(50.0, inv1Before.energyAmount, 0.001)

        // 2. Direct single-subscriber kWh price update (0.50 -> 0.85)
        val updatedCount = coreRepository.applyKwhPriceUpdate(
            targetSubscriberIds = listOf(subId),
            newPricePerKwh = 0.85,
            effectiveFrom = System.currentTimeMillis(),
            reason = "تعديل مباشر من ملف المشترك",
            actorUserId = admin.id
        ).getOrThrow()
        assertEquals(1, updatedCount)

        // Verify old invoice remained completely untouched
        val inv1After = database.billingDao().getInvoiceById(inv1Id)!!
        assertEquals(0.50, inv1After.unitPriceApplied, 0.001)
        assertEquals(50.0, inv1After.energyAmount, 0.001)

        // Verify subscriber price & tariff audit log recorded
        val subAfter = database.subscriberDao().getById(subId)!!
        assertEquals(0.85, subAfter.customKwhPrice, 0.001)
        val logs = database.tariffDao().observeAllTariffChangeLogs().first()
        assertTrue(logs.any { it.subscriberId == subId && it.oldPricePerKwh == 0.50 && it.newPricePerKwh == 0.85 })

        // 3. Issue next reading/invoice -> uses new 0.85 rate (100 kWh = 85.0)
        val r2Id = billingRepository.saveMeterReading(
            MeterReadingInput(subId, "2027-02", 200.0, System.currentTimeMillis() + 1000L),
            actorUserId = admin.id
        ).getOrThrow()
        val inv2Id = billingRepository.issueInvoiceForReading(r2Id, actorUserId = admin.id).getOrThrow()
        val inv2 = database.billingDao().getInvoiceById(inv2Id)!!
        assertEquals(0.85, inv2.unitPriceApplied, 0.001)
        assertEquals(85.0, inv2.energyAmount, 0.001)
    }

    @Test
    fun `WhatsApp smart phone normalization handles local, dashed, international formats and warns cleanly on missing or invalid numbers`() {
        val localZero = InvoiceShareAndPrintService.normalizePhoneForWhatsApp("0771234567", "967")
        assertTrue(localZero.isValid)
        assertEquals("967771234567", localZero.normalizedDigits)
        assertNull(localZero.warningMessage)

        val localNoZeroDashed = InvoiceShareAndPrintService.normalizePhoneForWhatsApp("771-234-567", "967")
        assertTrue(localNoZeroDashed.isValid)
        assertEquals("967771234567", localNoZeroDashed.normalizedDigits)

        val intlPlus = InvoiceShareAndPrintService.normalizePhoneForWhatsApp("+966 50 123 4567", "967")
        assertTrue(intlPlus.isValid)
        assertEquals("966501234567", intlPlus.normalizedDigits)

        val intlDoubleZero = InvoiceShareAndPrintService.normalizePhoneForWhatsApp("00967771234567", "967")
        assertTrue(intlDoubleZero.isValid)
        assertEquals("967771234567", intlDoubleZero.normalizedDigits)

        val emptyRes = InvoiceShareAndPrintService.normalizePhoneForWhatsApp("", "967")
        assertFalse(emptyRes.isValid)
        assertNotNull(emptyRes.warningMessage)

        val shortRes = InvoiceShareAndPrintService.normalizePhoneForWhatsApp("123", "967")
        assertFalse(shortRes.isValid)
        assertNotNull(shortRes.warningMessage)
    }

    @Test
    fun `instant WhatsApp dispatch launches ACTION_VIEW https wa me intent for invoices, receipts, and statements and returns Arabic warning when missing`(): Unit = runBlocking {
        coreRepository.ensureBaselineSeeded()
        val admin = database.systemDao().getUserByUsername("admin")!!
        val subId = coreRepository.saveSubscriber(
            SubscriberInput(
                subscriberCode = "SUB-WA-01",
                fullName = "خالد الحميري",
                phoneNumber = "0771234567",
                meterNumber = "MTR-WA-01",
                meterSealNumber = "SL-WA",
                address = "شارع الزبيري",
                subscriptionType = "تجاري",
                customKwhPrice = 0.60,
                subscriptionDate = System.currentTimeMillis(),
                isActive = true
            ),
            actorUserId = admin.id
        ).getOrThrow()

        val sub = database.subscriberDao().getById(subId)!!
        val rId = billingRepository.saveMeterReading(
            MeterReadingInput(subId, "2027-03", 150.0, System.currentTimeMillis()),
            actorUserId = admin.id
        ).getOrThrow()
        val invId = billingRepository.issueInvoiceForReading(rId, actorUserId = admin.id).getOrThrow()
        val invoice = database.billingDao().getInvoiceById(invId)!!

        val shadowApp = Shadows.shadowOf(context as Application)
        while (shadowApp.nextStartedActivity != null) { /* drain */ }

        // 1. Invoice WhatsApp dispatch (PNG invoice image via ACTION_SEND to WhatsApp jid)
        val invWarn = InvoiceShareAndPrintService.sendViaWhatsApp(context, invoice, sub, "967")
        assertNull(invWarn)
        val invIntent = shadowApp.nextStartedActivity
        assertNotNull(invIntent)
        assertEquals(Intent.ACTION_SEND, invIntent.action)
        assertEquals("image/png", invIntent.type)
        assertEquals("967771234567@s.whatsapp.net", invIntent.getStringExtra("jid"))

        // 2. Receipt WhatsApp dispatch (PNG receipt image via ACTION_SEND to WhatsApp jid)
        val colId = billingRepository.recordInvoicePayment(invId, 40.0, "CASH", actorUserId = admin.id).getOrThrow()
        val receipt: ReceiptEntity = billingRepository.getReceiptByCollectionId(colId)!!
        val rcpWarn = DocumentPdfAndThermalService.shareReceiptViaWhatsApp(context, receipt, sub.phoneNumber, "967")
        assertNull(rcpWarn)
        val rcpIntent = shadowApp.nextStartedActivity
        assertNotNull(rcpIntent)
        assertEquals(Intent.ACTION_SEND, rcpIntent.action)
        assertEquals("image/png", rcpIntent.type)
        assertEquals("967771234567@s.whatsapp.net", rcpIntent.getStringExtra("jid"))

        // 3. Subscriber statement WhatsApp dispatch
        val statement = StationReportGenerator.buildSubscriberStatement(
            subscriber = sub,
            invoices = listOf(invoice),
            collectionsWithReceipts = emptyList(),
            readings = emptyList(),
            creditTransactions = emptyList()
        )
        val stWarn = InvoiceShareAndPrintService.sendStatementViaWhatsApp(context, statement, "967")
        assertNull(stWarn)
        val stIntent = shadowApp.nextStartedActivity
        assertNotNull(stIntent)
        assertTrue(stIntent.dataString!!.startsWith("https://wa.me/967771234567?text="))

        // 4. Missing phone returns explicit Arabic warning without crashing
        val missingWarn = InvoiceShareAndPrintService.sendViaWhatsApp(
            context,
            invoice,
            sub.copy(phoneNumber = ""),
            "967"
        )
        assertNotNull(missingWarn)
        assertTrue(missingWarn!!.contains("رقم"))
    }

    @Test
    fun `field collection sheet filters by area and debt status and exports valid A4 PDF and UTF8-BOM CSV`() {
        val subscribers = listOf(
            SubscriberEntity(
                id = 1L,
                subscriberCode = "SUB-A1",
                fullName = "عبدالله الحدا",
                phoneNumber = "0771112233",
                meterNumber = "M1",
                address = "حي حدة - شارع الخمسين",
                customKwhPrice = 0.55,
                currentArrearsAmount = 140.0,
                isActive = true
            ),
            SubscriberEntity(
                id = 2L,
                subscriberCode = "SUB-A2",
                fullName = "ياسين التحرير",
                phoneNumber = "0774445566",
                meterNumber = "M2",
                address = "حي التحرير",
                customKwhPrice = 0.55,
                currentArrearsAmount = 0.0,
                currentCreditBalance = 25.0,
                isActive = true
            ),
            SubscriberEntity(
                id = 3L,
                subscriberCode = "SUB-A3",
                fullName = "موقف مؤقت",
                phoneNumber = "0779998877",
                meterNumber = "M3",
                address = "حي حدة",
                customKwhPrice = 0.55,
                currentArrearsAmount = 90.0,
                isActive = false
            )
        )
        val readings = listOf(
            MeterReadingEntity(id = 10L, subscriberId = 1L, billingPeriod = "2027-01", previousReading = 0.0, currentReading = 340.0, consumedKwh = 340.0, readingDate = 1000L)
        )

        val arrearsOnlyHadda = StationReportGenerator.buildFieldCollectionSheet(
            subscribers = subscribers,
            readings = readings,
            areaFilter = "حدة",
            debtFilter = FieldDebtFilter.ARREARS_ONLY,
            onlyActiveSubscribers = true
        )
        assertEquals(1, arrearsOnlyHadda.size)
        assertEquals("SUB-A1", arrearsOnlyHadda.first().subscriberCode)
        assertEquals(340.0, arrearsOnlyHadda.first().previousMeterReading, 0.001)
        assertEquals(140.0, arrearsOnlyHadda.first().currentArrears, 0.001)

        val allActive = StationReportGenerator.buildFieldCollectionSheet(
            subscribers = subscribers,
            readings = readings,
            areaFilter = "",
            debtFilter = FieldDebtFilter.ALL,
            onlyActiveSubscribers = true
        )
        assertEquals(2, allActive.size)

        val pdfFile = File(context.cacheDir, "test_field_sheet.pdf")
        DocumentPdfAndThermalService.generateFieldCollectionA4Pdf(
            context = context,
            items = arrearsOnlyHadda,
            areaFilterLabel = "حي حدة",
            outputFile = pdfFile
        )
        assertTrue(pdfFile.exists())
        val pdfBytes = pdfFile.readBytes()
        assertTrue(pdfBytes.size > 500)
        assertTrue(String(pdfBytes.take(5).toByteArray(), Charsets.US_ASCII).startsWith("%PDF-"))

        val csvFile = File(context.cacheDir, "test_field_sheet.csv")
        DocumentPdfAndThermalService.generateFieldCollectionCsv(
            context = context,
            items = arrearsOnlyHadda,
            areaFilterLabel = "حي حدة",
            outputFile = csvFile
        )
        assertTrue(csvFile.exists())
        val csvBytes = csvFile.readBytes()
        assertEquals(0xEF.toByte(), csvBytes[0])
        assertEquals(0xBB.toByte(), csvBytes[1])
        assertEquals(0xBF.toByte(), csvBytes[2])
        val csvText = csvFile.readText(Charsets.UTF_8)
        assertTrue(csvText.contains("كود المشترك"))
        assertTrue(csvText.contains("المبلغ المحصل / التوقيع"))
        assertTrue(csvText.contains("SUB-A1"))
        assertTrue(csvText.contains("340.0"))
        assertTrue(csvText.contains("140.0"))
    }

    @Test
    fun `app lock enforces password fallback, persists per-user biometric opt-in in Room v5, auto-locks on background and inactivity timeout`(): Unit = runBlocking {
        coreRepository.ensureBaselineSeeded()
        themePreferences.setAppLockEnabled(true)
        val vm = FoundationViewModel(
            repository = coreRepository,
            themePreferences = themePreferences,
            safBackupManager = SafBackupManager(context, coreRepository)
        )
        awaitMainCondition { vm.backupSessionPrefs.value.appLockEnabled }

        // Locked initially when appLockEnabled = true
        assertTrue(vm.isAppLocked.value)

        // Wrong password stays locked
        var errorMsg: String? = null
        vm.loginWithCredentials("admin", "0000", onError = { errorMsg = it })
        awaitMainCondition { errorMsg != null }
        assertTrue(vm.isAppLocked.value)
        assertNotNull(errorMsg)

        // Valid admin password unlocks
        vm.loginWithCredentials("admin", "2027")
        awaitMainCondition { !vm.isAppLocked.value }
        assertFalse(vm.isAppLocked.value)

        // Toggle per-user biometric opt-in in Room v5
        val adminBefore = database.systemDao().getUserByUsername("admin")!!
        assertFalse(adminBefore.biometricEnabled)
        vm.toggleUserBiometric(adminBefore.id, true)
        awaitMainCondition {
            database.systemDao().getUserByUsername("admin")?.biometricEnabled == true
        }
        val adminAfter = database.systemDao().getUserByUsername("admin")!!
        assertTrue(adminAfter.biometricEnabled)

        // Background auto-lock
        vm.onAppWentToBackground(10_000L)
        assertTrue(vm.isAppLocked.value)

        // Biometric unlock works when enabled & logs USER_BIOMETRIC_LOGIN
        vm.unlockWithBiometric(adminAfter.id)
        awaitMainCondition { !vm.isAppLocked.value }
        assertFalse(vm.isAppLocked.value)
        val logs = database.systemDao().observeRecentAuditLogs().first()
        assertTrue(logs.any { it.operationType == "USER_BIOMETRIC_LOGIN" && it.actorUserId == adminAfter.id })

        // Inactivity timeout auto-lock check
        val interactionTime = 1_000_000L
        vm.recordUserInteraction(interactionTime)
        val lockedEarly = vm.checkInactivityTimeout(interactionTime + 30_000L) // default 120s
        assertFalse(lockedEarly)
        assertFalse(vm.isAppLocked.value)

        val lockedOnExpiry = vm.checkInactivityTimeout(interactionTime + 125_000L)
        assertTrue(lockedOnExpiry)
        assertTrue(vm.isAppLocked.value)
    }
}
