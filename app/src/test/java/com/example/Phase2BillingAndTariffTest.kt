package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.AlasalDatabase
import com.example.data.repository.AlasalRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.MeterReadingInput
import com.example.data.repository.SubscriberInput
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase2BillingAndTariffTest {

    private lateinit var database: AlasalDatabase
    private lateinit var coreRepo: AlasalRepository
    private lateinit var billingRepo: BillingRepository

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        coreRepo.ensureBaselineSeeded()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createSubscriber(
        code: String,
        name: String,
        meter: String,
        price: Double = 0.50,
        credit: Double = 0.0
    ): Long {
        return coreRepo.saveSubscriber(
            SubscriberInput(
                subscriberCode = code,
                fullName = name,
                phoneNumber = "050111222",
                meterNumber = meter,
                meterSealNumber = "SEAL-$code",
                address = "حي العسل",
                subscriptionType = "منزلي",
                customKwhPrice = price,
                subscriptionDate = System.currentTimeMillis(),
                isActive = true,
                initialCreditBalance = credit,
                notes = "ملاحظة"
            )
        ).getOrThrow()
    }

    @Test
    fun `tariff updates single, group, bulk preserve old invoices and apply to new invoices`() = runTest {
        val sub1 = createSubscriber("S-1", "مشترك أول", "M-1", price = 0.50)
        val sub2 = createSubscriber("S-2", "مشترك ثان", "M-2", price = 0.50)
        val sub3 = createSubscriber("S-3", "مشترك ثالث", "M-3", price = 0.50)

        // Issue old invoice for sub1 at 0.50
        val r1 = billingRepo.saveMeterReading(
            MeterReadingInput(sub1, "2027-01", 100.0, System.currentTimeMillis() - 10_000L)
        ).getOrThrow()
        val oldInvoiceId = billingRepo.issueInvoiceForReading(r1).getOrThrow()
        val oldInvoiceBefore = database.billingDao().getInvoiceById(oldInvoiceId)!!
        assertEquals(0.50, oldInvoiceBefore.unitPriceApplied, 0.001)
        assertEquals(50.0, oldInvoiceBefore.energyAmount, 0.001)

        // 1. Single subscriber update: sub1 -> 0.80
        val singleRes = coreRepo.applyKwhPriceUpdate(
            targetSubscriberIds = listOf(sub1),
            newPricePerKwh = 0.80,
            effectiveFrom = System.currentTimeMillis(),
            reason = "تعديل فردي"
        )
        assertTrue(singleRes.isSuccess)
        assertEquals(0.80, database.subscriberDao().getById(sub1)!!.customKwhPrice, 0.001)
        assertEquals(0.50, database.subscriberDao().getById(sub1)!!.previousKwhPrice!!, 0.001)
        assertEquals(0.50, database.subscriberDao().getById(sub2)!!.customKwhPrice, 0.001)

        // 2. Group update: sub2 & sub3 -> 0.65
        val groupRes = coreRepo.applyKwhPriceUpdate(
            targetSubscriberIds = listOf(sub2, sub3),
            newPricePerKwh = 0.65,
            effectiveFrom = System.currentTimeMillis(),
            reason = "تعديل مجموعة"
        )
        assertTrue(groupRes.isSuccess)
        assertEquals(0.65, database.subscriberDao().getById(sub2)!!.customKwhPrice, 0.001)
        assertEquals(0.65, database.subscriberDao().getById(sub3)!!.customKwhPrice, 0.001)
        assertEquals(0.80, database.subscriberDao().getById(sub1)!!.customKwhPrice, 0.001)

        // 3. Bulk update all -> 0.90
        val bulkRes = coreRepo.applyKwhPriceUpdate(
            targetSubscriberIds = null,
            newPricePerKwh = 0.90,
            effectiveFrom = System.currentTimeMillis(),
            reason = "تعديل شامل للمحطة"
        )
        assertEquals(3, bulkRes.getOrThrow())
        assertEquals(0.90, database.subscriberDao().getById(sub1)!!.customKwhPrice, 0.001)
        assertEquals(0.90, database.subscriberDao().getById(sub2)!!.customKwhPrice, 0.001)
        assertEquals(0.90, database.subscriberDao().getById(sub3)!!.customKwhPrice, 0.001)

        // Verify old invoice was NOT changed automatically
        val oldInvoiceAfter = database.billingDao().getInvoiceById(oldInvoiceId)!!
        assertEquals(0.50, oldInvoiceAfter.unitPriceApplied, 0.001)
        assertEquals(50.0, oldInvoiceAfter.netPayableAmount, 0.001)

        // New invoice for sub1 automatically uses 0.90
        val r2 = billingRepo.saveMeterReading(
            MeterReadingInput(sub1, "2027-02", 200.0, System.currentTimeMillis())
        ).getOrThrow()
        val newInvoiceId = billingRepo.issueInvoiceForReading(r2).getOrThrow()
        val newInvoice = database.billingDao().getInvoiceById(newInvoiceId)!!
        assertEquals(0.90, newInvoice.unitPriceApplied, 0.001)
        assertEquals(90.0, newInvoice.energyAmount, 0.001)

        // Verify tariff_change_logs recorded actor and reason
        val logs = coreRepo.observeTariffChangeLogs().first()
        assertTrue(logs.any { it.reason == "تعديل شامل للمحطة" && it.actorNameSnapshot == "مدير محطة العسل" })

        // Unauthorized collector cannot change tariffs
        val collector = database.systemDao().getActiveUserByRole("COLLECTOR")!!
        val unauth = coreRepo.applyKwhPriceUpdate(listOf(sub1), 1.0, System.currentTimeMillis(), "غير مصرح", collector.id)
        assertTrue(unauth.isFailure)
    }

    @Test
    fun `arrears carry-over never duplicates debt and tracks source invoices across partial payments`() = runTest {
        val subId = createSubscriber("S-ARR", "مشترك متأخرات", "M-ARR", price = 1.0)
        val t0 = System.currentTimeMillis() - 30_000L

        // Period 1: 100 kWh = 100. Pay 40 -> remaining 60
        val r1 = billingRepo.saveMeterReading(MeterReadingInput(subId, "2027-01", 100.0, t0)).getOrThrow()
        val inv1Id = billingRepo.issueInvoiceForReading(r1).getOrThrow()
        billingRepo.recordInvoicePayment(inv1Id, 40.0).getOrThrow()
        assertEquals(60.0, database.subscriberDao().getById(subId)!!.currentArrearsAmount, 0.001)

        // Period 2: +100 kWh = 100, carries over 60 -> net 160
        val r2 = billingRepo.saveMeterReading(MeterReadingInput(subId, "2027-02", 200.0, t0 + 10_000L)).getOrThrow()
        val inv2Id = billingRepo.issueInvoiceForReading(r2).getOrThrow()
        val inv2Detail = billingRepo.getInvoiceDetailBundle(inv2Id)!!
        assertEquals(100.0, inv2Detail.invoice.energyAmount, 0.001)
        assertEquals(60.0, inv2Detail.invoice.previousArrearsApplied, 0.001)
        assertEquals(160.0, inv2Detail.invoice.netPayableAmount, 0.001)
        assertEquals(1, inv2Detail.sourceArrearsInvoices.size)
        assertEquals(inv1Id, inv2Detail.sourceArrearsInvoices.first().id)
        assertEquals(160.0, database.subscriberDao().getById(subId)!!.currentArrearsAmount, 0.001)

        // Partial payment 50 on inv2 -> remaining 110
        billingRepo.recordInvoicePayment(inv2Id, 50.0).getOrThrow()
        assertEquals(110.0, database.subscriberDao().getById(subId)!!.currentArrearsAmount, 0.001)

        // Period 3: +50 kWh = 50, carries over ONLY 110 (never duplicates 60 from inv1!) -> net 160
        val r3 = billingRepo.saveMeterReading(MeterReadingInput(subId, "2027-03", 250.0, t0 + 20_000L)).getOrThrow()
        val inv3Id = billingRepo.issueInvoiceForReading(r3).getOrThrow()
        val inv3 = database.billingDao().getInvoiceById(inv3Id)!!
        assertEquals(50.0, inv3.energyAmount, 0.001)
        assertEquals(110.0, inv3.previousArrearsApplied, 0.001)
        assertEquals(160.0, inv3.netPayableAmount, 0.001)
        assertEquals(160.0, database.subscriberDao().getById(subId)!!.currentArrearsAmount, 0.001)
    }

    @Test
    fun `credit balance is applied once per unit and never reused across invoices`() = runTest {
        val subId = createSubscriber("S-CRD", "مشترك دائن", "M-CRD", price = 1.0, credit = 150.0)
        val t0 = System.currentTimeMillis() - 20_000L

        // Invoice 1: 100 -> deducts 100 credit -> net 0 (PAID), remaining credit = 50
        val r1 = billingRepo.saveMeterReading(MeterReadingInput(subId, "2027-01", 100.0, t0)).getOrThrow()
        val inv1 = database.billingDao().getInvoiceById(billingRepo.issueInvoiceForReading(r1).getOrThrow())!!
        assertEquals(100.0, inv1.creditDeducted, 0.001)
        assertEquals(0.0, inv1.netPayableAmount, 0.001)
        assertEquals("PAID", inv1.status)
        assertEquals(50.0, database.subscriberDao().getById(subId)!!.currentCreditBalance, 0.001)

        // Invoice 2: 120 -> deducts remaining 50 credit -> net 70, remaining credit = 0
        val r2 = billingRepo.saveMeterReading(MeterReadingInput(subId, "2027-02", 220.0, t0 + 10_000L)).getOrThrow()
        val inv2 = database.billingDao().getInvoiceById(billingRepo.issueInvoiceForReading(r2).getOrThrow())!!
        assertEquals(50.0, inv2.creditDeducted, 0.001)
        assertEquals(70.0, inv2.netPayableAmount, 0.001)
        assertEquals(0.0, database.subscriberDao().getById(subId)!!.currentCreditBalance, 0.001)

        // Pay 100 on inv2 (which only needs 70) -> 30 overpayment added to credit!
        billingRepo.recordInvoicePayment(inv2.id, 100.0).getOrThrow()
        assertEquals(30.0, database.subscriberDao().getById(subId)!!.currentCreditBalance, 0.001)
        assertEquals(0.0, database.subscriberDao().getById(subId)!!.currentArrearsAmount, 0.001)
    }

    @Test
    fun `repricing old invoice after partial collection preserves payments and reconciles credit or arrears`() = runTest {
        val subId = createSubscriber("S-RPR", "مشترك إعادة تسعير", "M-RPR", price = 1.0)
        val r1 = billingRepo.saveMeterReading(MeterReadingInput(subId, "2027-01", 100.0, System.currentTimeMillis())).getOrThrow()
        val invId = billingRepo.issueInvoiceForReading(r1).getOrThrow()

        // Partial collection of 80 out of 100 -> remaining 20
        billingRepo.recordInvoicePayment(invId, 80.0).getOrThrow()

        // Increase price to 1.5 -> energy 150, paid preserved = 80, remaining = 70
        val repricedUp = billingRepo.repriceOldInvoice(invId, 1.5, "تصحيح تعرفة تجارية").getOrThrow()
        assertEquals(150.0, repricedUp.netPayableAmount, 0.001)
        assertEquals(80.0, repricedUp.paidAmount, 0.001)
        assertEquals(70.0, repricedUp.remainingAmount, 0.001)
        assertEquals("PARTIAL", repricedUp.status)
        assertEquals(70.0, database.subscriberDao().getById(subId)!!.currentArrearsAmount, 0.001)

        // Decrease price to 0.5 -> energy 50, paid preserved = 80 -> remaining 0 (PAID), excess 30 becomes credit!
        val repricedDown = billingRepo.repriceOldInvoice(invId, 0.5, "تخفيض استثنائي").getOrThrow()
        assertEquals(50.0, repricedDown.netPayableAmount, 0.001)
        assertEquals(80.0, repricedDown.paidAmount, 0.001)
        assertEquals(0.0, repricedDown.remainingAmount, 0.001)
        assertEquals("PAID", repricedDown.status)
        assertEquals(0.0, database.subscriberDao().getById(subId)!!.currentArrearsAmount, 0.001)
        assertEquals(30.0, database.subscriberDao().getById(subId)!!.currentCreditBalance, 0.001)
        assertNotNull(repricedDown.priceModifiedAt)
        assertEquals("مدير محطة العسل", repricedDown.priceModifiedBy)
    }

    @Test
    fun `meter reading rejects lower reading without override, allows with admin override, and blocks deleting subscriber with history`() = runTest {
        val subId = createSubscriber("S-MTR", "مشترك عداد", "M-MTR", price = 0.5)
        val t0 = System.currentTimeMillis() - 10_000L

        billingRepo.saveMeterReading(
            MeterReadingInput(subId, "2027-01", 500.0, t0, photoUri = "file:///meter_1.jpg")
        ).getOrThrow()

        // Lower reading without override fails
        val rejected = billingRepo.saveMeterReading(
            MeterReadingInput(subId, "2027-02", 40.0, t0 + 5_000L, allowLowReadingOverride = false)
        )
        assertTrue(rejected.isFailure)

        // Lower reading WITH admin override & reason succeeds
        val approved = billingRepo.saveMeterReading(
            MeterReadingInput(
                subId,
                "2027-02",
                40.0,
                t0 + 5_000L,
                allowLowReadingOverride = true,
                overrideReason = "تم استبدال العداد بعداد جديد"
            )
        )
        assertTrue(approved.isSuccess)
        val savedReading = database.billingDao().getReadingById(approved.getOrThrow())!!
        assertTrue(savedReading.isLowReadingOverride)
        assertEquals(40.0, savedReading.consumedKwh, 0.001)

        // Deleting subscriber with financial/reading history is blocked
        val deleteBlocked = coreRepo.deleteSubscriberSafely(subId)
        assertTrue(deleteBlocked.isFailure)

        // Deleting clean subscriber without history succeeds
        val cleanSub = createSubscriber("S-CLEAN", "مشترك جديد", "M-CLEAN")
        val deleteOk = coreRepo.deleteSubscriberSafely(cleanSub)
        assertTrue(deleteOk.isSuccess)
    }
}
