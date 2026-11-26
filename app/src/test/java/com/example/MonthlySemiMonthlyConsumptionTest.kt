package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.AlasalDatabase
import com.example.data.local.entity.SubscriberEntity
import com.example.data.repository.AlasalRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.MeterReadingInput
import com.example.data.repository.MeterReadingUpdateInput
import com.example.domain.finance.ConsumptionSummaryCalculator
import com.example.service.DocumentPdfAndThermalService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MonthlySemiMonthlyConsumptionTest {

    private lateinit var context: Context
    private lateinit var database: AlasalDatabase
    private lateinit var coreRepo: AlasalRepository
    private lateinit var billingRepo: BillingRepository

    @Before
    fun setup() = runTest {
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
        coreRepo.ensureBaselineSeeded()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun dateMillis(year: Int, month1Based: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month1Based - 1, day, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `first half, second half (same and new subscribers), monthly grand total, duplicate block, edit and delete auto-update match 100 percent`() = runTest {
        // Seed 3 subscribers: عادل، حسين، نادر
        val adelId = database.subscriberDao().insert(
            SubscriberEntity(
                subscriberCode = "SUB-C1",
                fullName = "عادل",
                phoneNumber = "0771000001",
                meterNumber = "MTR-C1",
                address = "الحي الشرقي",
                subscriptionType = "منزلي"
            )
        )
        val husseinId = database.subscriberDao().insert(
            SubscriberEntity(
                subscriberCode = "SUB-C2",
                fullName = "حسين",
                phoneNumber = "0771000002",
                meterNumber = "MTR-C2",
                address = "الحي الشرقي",
                subscriptionType = "منزلي"
            )
        )
        val naderId = database.subscriberDao().insert(
            SubscriberEntity(
                subscriberCode = "SUB-C3",
                fullName = "نادر",
                phoneNumber = "0771000003",
                meterNumber = "MTR-C3",
                address = "الحي الغربي",
                subscriptionType = "تجاري"
            )
        )

        // Prior month baseline readings so cumulative meter indices are large (1000, 500, 200)
        // This explicitly verifies we aggregate NET consumption (current - previous), NOT cumulative meter indices!
        assertTrue(
            billingRepo.saveMeterReading(
                MeterReadingInput(
                    subscriberId = adelId,
                    billingPeriod = "2026-12",
                    currentReading = 1000.0,
                    readingDate = dateMillis(2026, 12, 20),
                    readingHalf = 2
                )
            ).isSuccess
        )
        assertTrue(
            billingRepo.saveMeterReading(
                MeterReadingInput(
                    subscriberId = husseinId,
                    billingPeriod = "2026-12",
                    currentReading = 500.0,
                    readingDate = dateMillis(2026, 12, 20),
                    readingHalf = 2
                )
            ).isSuccess
        )
        assertTrue(
            billingRepo.saveMeterReading(
                MeterReadingInput(
                    subscriberId = naderId,
                    billingPeriod = "2026-12",
                    currentReading = 200.0,
                    readingDate = dateMillis(2026, 12, 20),
                    readingHalf = 2
                )
            ).isSuccess
        )

        // ============================================================
        // TEST 1: First Half readings (2027-01, days 1..15 auto-classified as half 1)
        // عادل: 1000 -> 1050 (net 50 kWh)
        // حسين: 500 -> 530 (net 30 kWh)
        // Expected First Half = 80 kWh, 2 meters
        // ============================================================
        val adelH1Id = billingRepo.saveMeterReading(
            MeterReadingInput(
                subscriberId = adelId,
                billingPeriod = "2027-01",
                currentReading = 1050.0,
                readingDate = dateMillis(2027, 1, 10),
                readingHalf = null // Auto-classified from day 10 -> half 1
            )
        ).getOrThrow()

        val husseinH1Id = billingRepo.saveMeterReading(
            MeterReadingInput(
                subscriberId = husseinId,
                billingPeriod = "2027-01",
                currentReading = 530.0,
                readingDate = dateMillis(2027, 1, 15),
                readingHalf = null // Auto-classified from day 15 -> half 1
            )
        ).getOrThrow()

        // Duplicate prevention in same half (عادل again in 2027-01 half 1 must fail)
        val dupH1 = billingRepo.saveMeterReading(
            MeterReadingInput(
                subscriberId = adelId,
                billingPeriod = "2027-01",
                currentReading = 1060.0,
                readingDate = dateMillis(2027, 1, 12),
                readingHalf = 1
            )
        )
        assertTrue("Duplicate reading in same half must be blocked", dupH1.isFailure)

        val subscribers = database.subscriberDao().observeAllSubscribers().first()
        var readings = billingRepo.observeAllMeterReadings().first()
        var janSummary = ConsumptionSummaryCalculator.buildMonthSummary(2027, 1, readings, subscribers)
        assertEquals(80.0, janSummary.firstHalf.totalConsumedKwh, 0.001)
        assertEquals(2, janSummary.firstHalfMetersCount)
        assertEquals(0.0, janSummary.secondHalf.totalConsumedKwh, 0.001)
        assertEquals(0, janSummary.secondHalfMetersCount)
        assertEquals(80.0, janSummary.totalMonthlyKwh, 0.001)

        // ============================================================
        // TEST 2: Second Half readings (2027-01, days 16..31 auto-classified as half 2)
        // SAME subscribers (عادل, حسين) + NEW subscriber (نادر)
        // عادل: 1050 -> 1080 (net 30 kWh)
        // حسين: 530 -> 550 (net 20 kWh)
        // نادر: 200 -> 250 (net 50 kWh)
        // Expected Second Half = 100 kWh, 3 meters
        // ============================================================
        val adelH2Id = billingRepo.saveMeterReading(
            MeterReadingInput(
                subscriberId = adelId,
                billingPeriod = "2027-01",
                currentReading = 1080.0,
                readingDate = dateMillis(2027, 1, 16),
                readingHalf = null // Auto-classified from day 16 -> half 2
            )
        ).getOrThrow()
        assertTrue(adelH2Id > 0L)

        billingRepo.saveMeterReading(
            MeterReadingInput(
                subscriberId = husseinId,
                billingPeriod = "2027-01",
                currentReading = 550.0,
                readingDate = dateMillis(2027, 1, 25),
                readingHalf = null // Auto-classified from day 25 -> half 2
            )
        ).getOrThrow()

        val naderH2Id = billingRepo.saveMeterReading(
            MeterReadingInput(
                subscriberId = naderId,
                billingPeriod = "2027-01",
                currentReading = 250.0,
                readingDate = dateMillis(2027, 1, 28),
                readingHalf = null // Auto-classified from day 28 -> half 2
            )
        ).getOrThrow()

        // ============================================================
        // TEST 3: Monthly Grand Total = First Half (80) + Second Half (100) = 180 kWh
        // ============================================================
        readings = billingRepo.observeAllMeterReadings().first()
        janSummary = ConsumptionSummaryCalculator.buildMonthSummary(2027, 1, readings, subscribers)
        assertEquals("شهر 1 - يناير", janSummary.monthTitleAr)
        assertEquals(80.0, janSummary.firstHalf.totalConsumedKwh, 0.001)
        assertEquals(2, janSummary.firstHalfMetersCount)
        assertEquals(100.0, janSummary.secondHalf.totalConsumedKwh, 0.001)
        assertEquals(3, janSummary.secondHalfMetersCount)
        assertEquals(180.0, janSummary.totalMonthlyKwh, 0.001)
        assertEquals(5, janSummary.totalReadingsCount)

        // Verify December 2026 readings did NOT leak into January 2027
        assertFalse(janSummary.firstHalf.items.any { it.billingPeriod != "2027-01" })
        assertFalse(janSummary.secondHalf.items.any { it.billingPeriod != "2027-01" })

        // ============================================================
        // TEST 4A: Auto-update on EDIT of a reading
        // Edit عادل First Half from 1000->1050 (50) to 1000->1070 (70)
        // Expected: First Half = 100, Second Half = 100, Monthly Total = 200
        // ============================================================
        val updateRes = billingRepo.updateMeterReading(
            MeterReadingUpdateInput(
                readingId = adelH1Id,
                billingPeriod = "2027-01",
                readingHalf = 1,
                previousReading = 1000.0,
                currentReading = 1070.0,
                readingDate = dateMillis(2027, 1, 10),
                notes = "تصحيح قراءة النصف الأول"
            )
        )
        assertTrue(updateRes.isSuccess)
        assertEquals(70.0, updateRes.getOrThrow().consumedKwh, 0.001)

        readings = billingRepo.observeAllMeterReadings().first()
        janSummary = ConsumptionSummaryCalculator.buildMonthSummary(2027, 1, readings, subscribers)
        assertEquals(100.0, janSummary.firstHalf.totalConsumedKwh, 0.001)
        assertEquals(100.0, janSummary.secondHalf.totalConsumedKwh, 0.001)
        assertEquals(200.0, janSummary.totalMonthlyKwh, 0.001)

        // ============================================================
        // TEST 4B: Auto-update on DELETE of a reading
        // Delete نادر Second Half (50 kWh)
        // Expected: First Half = 100, Second Half = 50 (2 meters), Monthly Total = 150, totalReadingsCount = 4
        // ============================================================
        val deleteRes = billingRepo.deleteMeterReadingSafely(naderH2Id)
        assertTrue(deleteRes.isSuccess)

        readings = billingRepo.observeAllMeterReadings().first()
        janSummary = ConsumptionSummaryCalculator.buildMonthSummary(2027, 1, readings, subscribers)
        assertEquals(100.0, janSummary.firstHalf.totalConsumedKwh, 0.001)
        assertEquals(2, janSummary.firstHalfMetersCount)
        assertEquals(50.0, janSummary.secondHalf.totalConsumedKwh, 0.001)
        assertEquals(2, janSummary.secondHalfMetersCount)
        assertEquals(150.0, janSummary.totalMonthlyKwh, 0.001)
        assertEquals(4, janSummary.totalReadingsCount)

        // Verify deleting a reading linked to an invoice is safely blocked
        assertTrue(billingRepo.issueInvoiceForReading(husseinH1Id).isSuccess)
        val blockedDelete = billingRepo.deleteMeterReadingSafely(husseinH1Id)
        assertTrue("Deleting invoiced reading must be blocked", blockedDelete.isFailure)

        // ============================================================
        // TEST 5: Branded Monthly Consumption A4 PDF Generation
        // ============================================================
        val pdfFile = File(context.cacheDir, "test_monthly_consumption_2027_01.pdf")
        val generatedPdf = DocumentPdfAndThermalService.generateMonthlyConsumptionA4Pdf(
            context = context,
            summary = janSummary,
            stationAddress = "اليمن - محطة العسل 2027",
            stationPhone = "7770002027",
            outputFile = pdfFile
        )
        assertTrue(generatedPdf.exists())
        assertTrue(generatedPdf.length() > 1024)
        val header = generatedPdf.readBytes().take(5).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("%PDF-", header)
        generatedPdf.delete()
    }

    @Test
    fun `auto date classification splits days 1 to 15 as first half and 16 to end as second half`() {
        assertEquals(1, ConsumptionSummaryCalculator.classifyHalfFromDate(dateMillis(2027, 3, 1)))
        assertEquals(1, ConsumptionSummaryCalculator.classifyHalfFromDate(dateMillis(2027, 3, 15)))
        assertEquals(2, ConsumptionSummaryCalculator.classifyHalfFromDate(dateMillis(2027, 3, 16)))
        assertEquals(2, ConsumptionSummaryCalculator.classifyHalfFromDate(dateMillis(2027, 3, 31)))

        val yearSummaries = ConsumptionSummaryCalculator.buildYearMonthlySummaries(
            year = 2027,
            readings = emptyList(),
            subscribers = emptyList()
        )
        assertEquals(12, yearSummaries.size)
        assertEquals("شهر 1 - يناير", yearSummaries.first().monthTitleAr)
        assertEquals("شهر 12 - ديسمبر", yearSummaries.last().monthTitleAr)
    }
}
