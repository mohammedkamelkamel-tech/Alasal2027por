package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.AlasalDatabase
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.ArrearsEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CreditBalanceEntity
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.KwhTariffEntity
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.PermissionEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.TariffChangeLogEntity
import com.example.data.local.entity.UserEntity
import com.example.data.repository.AlasalRepository
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
class AlasalDatabaseAndRelationsTest {

    private lateinit var database: AlasalDatabase
    private lateinit var repository: AlasalRepository

    @Before
    fun setup() {
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
        repository = AlasalRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `all 14 tables exist and baseline seed succeeds idempotently`() = runTest {
        repository.ensureBaselineSeeded()
        repository.ensureBaselineSeeded() // Idempotency check

        val verification = repository.verifySchemaIntegrity()
        assertTrue(verification.isSuccess)
        val tableStatuses = verification.getOrThrow()
        assertEquals(14, tableStatuses.size)

        val counts = repository.getAllTableCountsOnce()
        assertEquals(2, counts.usersCount)
        assertEquals(9, counts.permissionsCount)
        assertEquals(2, counts.kwhTariffsCount)
        assertEquals(1, counts.tariffChangeLogsCount)
        assertEquals(4, counts.appSettingsCount)
        assertTrue(counts.financialAuditLogsCount >= 2)
    }

    @Test
    fun `full relational chain across all 14 tables works and retrieves SubscriberFullHistory`() = runTest {
        val userId = database.systemDao().insertUser(
            UserEntity(
                username = "accountant_ali",
                fullName = "علي المحاسب",
                roleCode = "ACCOUNTANT",
                pinHash = AlasalRepository.sha256("9999")
            )
        )
        database.systemDao().insertPermissions(
            listOf(
                PermissionEntity(
                    roleCode = "ACCOUNTANT",
                    permissionKey = "ISSUE_INVOICE",
                    isAllowed = true,
                    descriptionAr = "إصدار الفواتير"
                )
            )
        )
        database.systemDao().upsertSetting(
            AppSettingEntity(
                settingKey = "invoice_prefix",
                settingValue = "INV-2027",
                category = "BILLING",
                descriptionAr = "بادئة الفواتير"
            )
        )

        val tariffId = database.tariffDao().insertTariff(
            KwhTariffEntity(
                tariffCode = "TRF-TEST-01",
                title = "تعرفة اختبارية",
                subscriptionType = "منزلي",
                pricePerKwh = 0.50,
                fixedFee = 5.0
            )
        )
        database.tariffDao().insertTariffChangeLog(
            TariffChangeLogEntity(
                tariffId = tariffId,
                oldPricePerKwh = 0.40,
                newPricePerKwh = 0.50,
                oldFixedFee = 5.0,
                newFixedFee = 5.0,
                changedByUserId = userId,
                reason = "تحديث موسمي"
            )
        )

        val subscriberId = database.subscriberDao().insert(
            SubscriberEntity(
                subscriberCode = "SUB-1001",
                fullName = "أحمد صالح العسل",
                phoneNumber = "0500000001",
                meterNumber = "MTR-9001",
                address = "الحي الشرقي",
                subscriptionType = "منزلي"
            )
        )

        val readingId = database.billingDao().insertMeterReading(
            MeterReadingEntity(
                subscriberId = subscriberId,
                billingPeriod = "2027-01",
                previousReading = 1000.0,
                currentReading = 1200.0,
                consumedKwh = 200.0,
                recordedByUserId = userId
            )
        )

        val invoiceId = database.billingDao().insertInvoice(
            InvoiceEntity(
                invoiceNumber = "INV-2027-0001",
                subscriberId = subscriberId,
                meterReadingId = readingId,
                tariffId = tariffId,
                billingPeriod = "2027-01",
                consumedKwh = 200.0,
                unitPriceApplied = 0.50,
                fixedFeeApplied = 5.0,
                energyAmount = 100.0,
                netPayableAmount = 105.0,
                paidAmount = 105.0,
                status = "PAID"
            )
        )

        val collectionId = database.collectionDao().insertCollection(
            CollectionEntity(
                collectionReference = "COL-2027-0001",
                subscriberId = subscriberId,
                invoiceId = invoiceId,
                collectedByUserId = userId,
                amountPaid = 125.0,
                appliedToInvoice = 105.0,
                appliedToArrears = 0.0,
                addedToCredit = 20.0
            )
        )

        database.collectionDao().insertReceipt(
            ReceiptEntity(
                receiptNumber = "RCP-2027-0001",
                collectionId = collectionId,
                subscriberId = subscriberId,
                issuedByUserId = userId,
                amount = 125.0,
                balanceAfterReceipt = 0.0,
                creditAfterReceipt = 20.0
            )
        )

        database.billingDao().insertArrears(
            ArrearsEntity(
                subscriberId = subscriberId,
                sourceInvoiceId = invoiceId,
                originalAmount = 30.0,
                remainingAmount = 0.0,
                status = "SETTLED"
            )
        )

        database.billingDao().insertCreditTransaction(
            CreditBalanceEntity(
                subscriberId = subscriberId,
                sourceCollectionId = collectionId,
                transactionType = "DEPOSIT_OVERPAYMENT",
                amountDelta = 20.0,
                balanceAfter = 20.0,
                notes = "فائض سداد"
            )
        )

        database.systemDao().insertReminder(
            ReminderEntity(
                subscriberId = subscriberId,
                invoiceId = invoiceId,
                title = "تذكير فاتورة",
                message = "موعد السداد",
                scheduledAt = System.currentTimeMillis() + 86_400_000L
            )
        )

        database.systemDao().insertAuditLog(
            FinancialAuditLogEntity(
                subscriberId = subscriberId,
                actorUserId = userId,
                operationType = "COLLECTION_RECORDED",
                entityName = "collections",
                entityId = collectionId,
                amountBefore = 105.0,
                amountAfter = 0.0,
                detailsAr = "سداد كامل الفاتورة وإضافة 20 رصيد دائن"
            )
        )

        val fullHistory = repository.getSubscriberFullHistory(subscriberId)
        assertNotNull(fullHistory)
        assertEquals("SUB-1001", fullHistory!!.subscriber.subscriberCode)
        assertEquals(1, fullHistory.meterReadings.size)
        assertEquals(1, fullHistory.invoices.size)
        assertEquals(1, fullHistory.collections.size)
        assertEquals(1, fullHistory.receipts.size)
        assertEquals(1, fullHistory.arrears.size)
        assertEquals(1, fullHistory.creditBalances.size)
        assertEquals(1, fullHistory.reminders.size)
        assertEquals(1, fullHistory.auditLogs.size)

        val tariffsWithHistory = database.tariffDao().observeTariffsWithHistory().first()
        assertEquals(1, tariffsWithHistory.first().changeLogs.size)

        val collectionsWithReceipts = database.collectionDao().observeCollectionsWithReceipts(subscriberId).first()
        assertNotNull(collectionsWithReceipts.first().receipt)
        assertEquals("RCP-2027-0001", collectionsWithReceipts.first().receipt!!.receiptNumber)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `duplicate meter number is rejected by unique index`() = runTest {
        database.subscriberDao().insert(
            SubscriberEntity(
                subscriberCode = "SUB-A",
                fullName = "مشترك أول",
                phoneNumber = "051",
                meterNumber = "MTR-DUP",
                address = "عنوان 1",
                subscriptionType = "منزلي"
            )
        )
        database.subscriberDao().insert(
            SubscriberEntity(
                subscriberCode = "SUB-B",
                fullName = "مشترك ثان",
                phoneNumber = "052",
                meterNumber = "MTR-DUP",
                address = "عنوان 2",
                subscriptionType = "منزلي"
            )
        )
    }

    @Test
    fun `multiple readings for same subscriber and billing period are allowed in version 6`() = runTest {
        val subscriberId = database.subscriberDao().insert(
            SubscriberEntity(
                subscriberCode = "SUB-R1",
                fullName = "مشترك قراءة",
                phoneNumber = "053",
                meterNumber = "MTR-R1",
                address = "عنوان",
                subscriptionType = "منزلي"
            )
        )
        database.billingDao().insertMeterReading(
            MeterReadingEntity(
                subscriberId = subscriberId,
                billingPeriod = "2027-02",
                previousReading = 0.0,
                currentReading = 50.0,
                consumedKwh = 50.0
            )
        )
        database.billingDao().insertMeterReading(
            MeterReadingEntity(
                subscriberId = subscriberId,
                billingPeriod = "2027-02",
                previousReading = 50.0,
                currentReading = 90.0,
                consumedKwh = 40.0
            )
        )
        assertEquals(2, database.billingDao().getAllMeterReadingsOnce().size)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `foreign key constraint blocks inserting invoice for nonexistent subscriber`() = runTest {
        val tariffId = database.tariffDao().insertTariff(
            KwhTariffEntity(
                tariffCode = "TRF-FK",
                title = "تعرفة",
                subscriptionType = "منزلي",
                pricePerKwh = 0.5
            )
        )
        database.billingDao().insertInvoice(
            InvoiceEntity(
                invoiceNumber = "INV-BAD-FK",
                subscriberId = 99999L,
                tariffId = tariffId,
                billingPeriod = "2027-03",
                consumedKwh = 10.0,
                unitPriceApplied = 0.5,
                fixedFeeApplied = 0.0,
                energyAmount = 5.0,
                netPayableAmount = 5.0
            )
        )
    }

    @Test
    fun `MIGRATION_1_2 upgrades v1 tables non-destructively and preserves historical records`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v1_v2_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE subscribers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriber_code TEXT NOT NULL, full_name TEXT NOT NULL, phone_number TEXT NOT NULL, meter_number TEXT NOT NULL, address TEXT NOT NULL, subscription_type TEXT NOT NULL, is_active INTEGER NOT NULL, current_arrears_amount REAL NOT NULL, current_credit_balance REAL NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE tariff_change_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tariff_id INTEGER NOT NULL, old_price_per_kwh REAL NOT NULL, new_price_per_kwh REAL NOT NULL, old_fixed_fee REAL NOT NULL, new_fixed_fee REAL NOT NULL, changed_by_user_id INTEGER, reason TEXT NOT NULL, changed_at INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE meter_readings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriber_id INTEGER NOT NULL, billing_period TEXT NOT NULL, previous_reading REAL NOT NULL, current_reading REAL NOT NULL, consumed_kwh REAL NOT NULL, recorded_by_user_id INTEGER, reading_date INTEGER NOT NULL, notes TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE invoices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoice_number TEXT NOT NULL, subscriber_id INTEGER NOT NULL, meter_reading_id INTEGER, tariff_id INTEGER NOT NULL, billing_period TEXT NOT NULL, consumed_kwh REAL NOT NULL, unit_price_applied REAL NOT NULL, fixed_fee_applied REAL NOT NULL, energy_amount REAL NOT NULL, previous_arrears_applied REAL NOT NULL, credit_deducted REAL NOT NULL, net_payable_amount REAL NOT NULL, paid_amount REAL NOT NULL, status TEXT NOT NULL, issued_at INTEGER NOT NULL, due_date INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE credit_balances (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriber_id INTEGER NOT NULL, source_collection_id INTEGER, transaction_type TEXT NOT NULL, amount_delta REAL NOT NULL, balance_after REAL NOT NULL, recorded_at INTEGER NOT NULL, notes TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE arrears (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriber_id INTEGER NOT NULL, source_invoice_id INTEGER, original_amount REAL NOT NULL, remaining_amount REAL NOT NULL, status TEXT NOT NULL, recorded_at INTEGER NOT NULL, notes TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE financial_audit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriber_id INTEGER, actor_user_id INTEGER, operation_type TEXT NOT NULL, entity_name TEXT NOT NULL, entity_id INTEGER, amount_before REAL, amount_after REAL, details_ar TEXT NOT NULL, created_at INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO subscribers VALUES (1, 'V1-001', 'مشترك قديم', '0501', 'MTR-V1', 'العنوان', 'منزلي', 1, 25.0, 10.0, 1000, 1000)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        AlasalDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT subscriber_code, custom_kwh_price, meter_seal_number, notes FROM subscribers WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("V1-001", cursor.getString(0))
            assertEquals(0.45, cursor.getDouble(1), 0.001)
            assertEquals("", cursor.getString(2))
        }

        val invoiceColumns = mutableSetOf<String>()
        db.query("PRAGMA table_info(invoices)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                invoiceColumns.add(cursor.getString(nameIdx))
            }
        }
        assertTrue("carried_over_to_invoice_id" in invoiceColumns)
        assertTrue("remaining_amount" in invoiceColumns)
        assertTrue("price_modification_reason" in invoiceColumns)

        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `MIGRATION_2_3 upgrades v2 tables non-destructively and preserves collections, receipts, and reminders`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v2_v3_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE subscribers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriber_code TEXT NOT NULL, full_name TEXT NOT NULL, phone_number TEXT NOT NULL, meter_number TEXT NOT NULL, meter_seal_number TEXT NOT NULL, address TEXT NOT NULL, subscription_type TEXT NOT NULL, custom_kwh_price REAL NOT NULL, previous_kwh_price REAL, subscription_date INTEGER NOT NULL, is_active INTEGER NOT NULL, current_arrears_amount REAL NOT NULL, current_credit_balance REAL NOT NULL, notes TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE invoices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoice_number TEXT NOT NULL, subscriber_id INTEGER NOT NULL, meter_reading_id INTEGER, tariff_id INTEGER NOT NULL, billing_period TEXT NOT NULL, previous_reading REAL NOT NULL, current_reading REAL NOT NULL, consumed_kwh REAL NOT NULL, unit_price_applied REAL NOT NULL, fixed_fee_applied REAL NOT NULL, energy_amount REAL NOT NULL, previous_arrears_applied REAL NOT NULL, credit_deducted REAL NOT NULL, net_payable_amount REAL NOT NULL, paid_amount REAL NOT NULL, remaining_amount REAL NOT NULL, carried_over_to_invoice_id INTEGER, status TEXT NOT NULL, notes TEXT NOT NULL, price_modified_at INTEGER, price_modified_by TEXT, price_modification_reason TEXT, issued_at INTEGER NOT NULL, due_date INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE collections (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, collection_reference TEXT NOT NULL, subscriber_id INTEGER NOT NULL, invoice_id INTEGER, collected_by_user_id INTEGER, amount_paid REAL NOT NULL, applied_to_invoice REAL NOT NULL, applied_to_arrears REAL NOT NULL, added_to_credit REAL NOT NULL, payment_method TEXT NOT NULL, collected_at INTEGER NOT NULL, notes TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE receipts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, receipt_number TEXT NOT NULL, collection_id INTEGER NOT NULL, subscriber_id INTEGER NOT NULL, issued_by_user_id INTEGER, amount REAL NOT NULL, balance_after_receipt REAL NOT NULL, credit_after_receipt REAL NOT NULL, issued_at INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriber_id INTEGER NOT NULL, invoice_id INTEGER, title TEXT NOT NULL, message TEXT NOT NULL, scheduled_at INTEGER NOT NULL, status TEXT NOT NULL, channel TEXT NOT NULL, created_at INTEGER NOT NULL)")

                        db.execSQL("INSERT INTO subscribers VALUES (1, 'V2-001', 'مشترك قديم 2', '0502', 'MTR-V2', 'S2', 'العنوان', 'منزلي', 0.45, NULL, 1000, 1, 0.0, 0.0, '', 1000, 1000)")
                        db.execSQL("INSERT INTO collections VALUES (1, 'COL-V2-001', 1, NULL, NULL, 50.0, 50.0, 0.0, 0.0, 'CASH', 2000, '')")
                        db.execSQL("INSERT INTO receipts VALUES (1, 'RCP-V2-001', 1, 1, NULL, 50.0, 0.0, 0.0, 2000)")
                        db.execSQL("INSERT INTO reminders VALUES (1, 1, NULL, 'تذكير قديم', 'رسالة قديمة', 3000, 'PENDING', 'LOCAL_NOTIFICATION', 2000)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        AlasalDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT collection_reference, collector_name_snapshot FROM collections WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("COL-V2-001", c.getString(0))
            assertEquals("مدير محطة العسل", c.getString(1))
        }
        db.query("SELECT receipt_number, station_name_snapshot FROM receipts WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("RCP-V2-001", c.getString(0))
            assertEquals("محطة العسل 2027", c.getString(1))
        }
        db.query("SELECT title, reminder_type, snooze_count FROM reminders WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("تذكير قديم", c.getString(0))
            assertEquals("METER_READING", c.getString(1))
            assertEquals(0, c.getInt(2))
        }

        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `MIGRATION_3_4 upgrades v3 users table non-destructively preserving accounts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v3_v4_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL, full_name TEXT NOT NULL, role_code TEXT NOT NULL, pin_hash TEXT NOT NULL, is_active INTEGER NOT NULL, created_at INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO users VALUES (1, 'admin', 'مدير المحطة', 'ADMIN', 'hash123', 1, 5000)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        AlasalDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT username, custom_permissions_json, updated_at, sync_id FROM users WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("admin", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(5000L, c.getLong(2))
            assertEquals("user_admin", c.getString(3))
        }

        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `MIGRATION_4_5 upgrades v4 users table non-destructively adding biometric_enabled defaulting to 0`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v4_v5_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL, full_name TEXT NOT NULL, role_code TEXT NOT NULL, pin_hash TEXT NOT NULL, is_active INTEGER NOT NULL, custom_permissions_json TEXT NOT NULL, updated_at INTEGER NOT NULL, sync_id TEXT NOT NULL, created_at INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO users VALUES (1, 'admin', 'مدير المحطة', 'ADMIN', 'hash123', 1, '', 5000, 'user_admin', 5000)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        AlasalDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT username, biometric_enabled FROM users WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("admin", c.getString(0))
            assertEquals(0, c.getInt(1))
        }

        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `MIGRATION_5_6 upgrades v5 meter_readings preserving rows, classifying halves by day, and allowing both halves per month`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v5_v6_test.db"
        context.deleteDatabase(dbName)

        val calDay5 = java.util.Calendar.getInstance().apply {
            set(2027, java.util.Calendar.JANUARY, 5, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val calDay22 = java.util.Calendar.getInstance().apply {
            set(2027, java.util.Calendar.JANUARY, 22, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE subscribers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriber_code TEXT NOT NULL, full_name TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE meter_readings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriber_id INTEGER NOT NULL, billing_period TEXT NOT NULL, previous_reading REAL NOT NULL, current_reading REAL NOT NULL, consumed_kwh REAL NOT NULL, recorded_by_user_id INTEGER, reading_date INTEGER NOT NULL, photo_uri TEXT, is_low_reading_override INTEGER NOT NULL DEFAULT 0, override_reason TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')")
                        db.execSQL("INSERT INTO subscribers VALUES (1, 'SUB-1', 'عادل'), (2, 'SUB-2', 'حسين')")
                        db.execSQL("INSERT INTO meter_readings VALUES (1, 1, '2027-01', 100.0, 150.0, 50.0, NULL, $calDay5, NULL, 0, '', 'قراءة أولى')")
                        db.execSQL("INSERT INTO meter_readings VALUES (2, 2, '2027-01', 200.0, 260.0, 60.0, NULL, $calDay22, NULL, 0, '', 'قراءة ثانية')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        AlasalDatabase.MIGRATION_5_6.migrate(db)

        db.query("SELECT id, reading_half, consumed_kwh FROM meter_readings ORDER BY id ASC").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals(1, c.getInt(1))
            assertEquals(50.0, c.getDouble(2), 0.001)

            assertTrue(c.moveToNext())
            assertEquals(2L, c.getLong(0))
            assertEquals(2, c.getInt(1))
            assertEquals(60.0, c.getDouble(2), 0.001)
        }

        // Same subscriber (id=1) can now also insert a Second Half reading in the same month '2027-01'
        db.execSQL("INSERT INTO meter_readings (subscriber_id, billing_period, reading_half, previous_reading, current_reading, consumed_kwh, recorded_by_user_id, reading_date, photo_uri, is_low_reading_override, override_reason, notes) VALUES (1, '2027-01', 2, 150.0, 180.0, 30.0, NULL, $calDay22, NULL, 0, '', 'نصف ثاني')")
        db.query("SELECT COUNT(*) FROM meter_readings WHERE subscriber_id = 1 AND billing_period = '2027-01'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }

        helper.close()
        context.deleteDatabase(dbName)
    }
}
