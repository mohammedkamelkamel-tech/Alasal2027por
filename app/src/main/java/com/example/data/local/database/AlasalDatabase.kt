package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.BillingDao
import com.example.data.local.dao.CollectionDao
import com.example.data.local.dao.SubscriberDao
import com.example.data.local.dao.SystemDao
import com.example.data.local.dao.TariffDao
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

@Database(
    entities = [
        SubscriberEntity::class,
        InvoiceEntity::class,
        CollectionEntity::class,
        ReceiptEntity::class,
        KwhTariffEntity::class,
        TariffChangeLogEntity::class,
        ArrearsEntity::class,
        CreditBalanceEntity::class,
        MeterReadingEntity::class,
        ReminderEntity::class,
        UserEntity::class,
        PermissionEntity::class,
        AppSettingEntity::class,
        FinancialAuditLogEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AlasalDatabase : RoomDatabase() {
    abstract fun subscriberDao(): SubscriberDao
    abstract fun tariffDao(): TariffDao
    abstract fun billingDao(): BillingDao
    abstract fun collectionDao(): CollectionDao
    abstract fun systemDao(): SystemDao

    companion object {
        const val DATABASE_NAME = "alasal_2027.db"
        const val CURRENT_VERSION = 6

        /**
         * Non-destructive migration from Phase 1 (v1) to Phase 2 (v2),
         * preserving all historical subscriber, tariff, and financial records.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. subscribers new columns
                db.execSQL("ALTER TABLE subscribers ADD COLUMN meter_seal_number TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscribers ADD COLUMN custom_kwh_price REAL NOT NULL DEFAULT 0.45")
                db.execSQL("ALTER TABLE subscribers ADD COLUMN previous_kwh_price REAL")
                db.execSQL("ALTER TABLE subscribers ADD COLUMN subscription_date INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscribers ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

                // 2. tariff_change_logs new columns + index
                db.execSQL("ALTER TABLE tariff_change_logs ADD COLUMN subscriber_id INTEGER REFERENCES subscribers(id) ON DELETE SET NULL")
                db.execSQL("ALTER TABLE tariff_change_logs ADD COLUMN subscriber_name_snapshot TEXT")
                db.execSQL("ALTER TABLE tariff_change_logs ADD COLUMN effective_from INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tariff_change_logs ADD COLUMN actor_name_snapshot TEXT NOT NULL DEFAULT 'مدير محطة العسل'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tariff_change_logs_subscriber_id ON tariff_change_logs(subscriber_id)")

                // 3. meter_readings new columns
                db.execSQL("ALTER TABLE meter_readings ADD COLUMN photo_uri TEXT")
                db.execSQL("ALTER TABLE meter_readings ADD COLUMN is_low_reading_override INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meter_readings ADD COLUMN override_reason TEXT NOT NULL DEFAULT ''")

                // 4. invoices new columns + index
                db.execSQL("ALTER TABLE invoices ADD COLUMN previous_reading REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN current_reading REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN remaining_amount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN carried_over_to_invoice_id INTEGER")
                db.execSQL("ALTER TABLE invoices ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN price_modified_at INTEGER")
                db.execSQL("ALTER TABLE invoices ADD COLUMN price_modified_by TEXT")
                db.execSQL("ALTER TABLE invoices ADD COLUMN price_modification_reason TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_carried_over_to_invoice_id ON invoices(carried_over_to_invoice_id)")

                // 5. credit_balances new column + index
                db.execSQL("ALTER TABLE credit_balances ADD COLUMN source_invoice_id INTEGER REFERENCES invoices(id) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_balances_source_invoice_id ON credit_balances(source_invoice_id)")

                // 6. arrears new column + index
                db.execSQL("ALTER TABLE arrears ADD COLUMN carried_over_to_invoice_id INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_arrears_carried_over_to_invoice_id ON arrears(carried_over_to_invoice_id)")

                // 7. financial_audit_logs new column
                db.execSQL("ALTER TABLE financial_audit_logs ADD COLUMN actor_name_snapshot TEXT NOT NULL DEFAULT 'النظام'")
            }
        }

        /**
         * Non-destructive migration from Phase 2 (v2) to Phase 3 (v3),
         * adding receipt/collection snapshots and flexible reminder scheduling.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. collections Phase 3 columns
                db.execSQL("ALTER TABLE collections ADD COLUMN collector_name_snapshot TEXT NOT NULL DEFAULT 'مدير محطة العسل'")
                db.execSQL("ALTER TABLE collections ADD COLUMN previous_balance_snapshot REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE collections ADD COLUMN remaining_after_snapshot REAL NOT NULL DEFAULT 0.0")

                // 2. receipts Phase 3 columns
                db.execSQL("ALTER TABLE receipts ADD COLUMN collector_name_snapshot TEXT NOT NULL DEFAULT 'مدير محطة العسل'")
                db.execSQL("ALTER TABLE receipts ADD COLUMN subscriber_name_snapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE receipts ADD COLUMN subscriber_code_snapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE receipts ADD COLUMN invoice_number_snapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE receipts ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'CASH'")
                db.execSQL("ALTER TABLE receipts ADD COLUMN previous_balance REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE receipts ADD COLUMN station_name_snapshot TEXT NOT NULL DEFAULT 'محطة العسل 2027'")
                db.execSQL("ALTER TABLE receipts ADD COLUMN station_phone_snapshot TEXT NOT NULL DEFAULT ''")

                // 3. Rebuild reminders non-destructively so subscriber_id can be nullable
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders_v3 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subscriber_id INTEGER REFERENCES subscribers(id) ON DELETE SET NULL,
                        invoice_id INTEGER REFERENCES invoices(id) ON DELETE SET NULL,
                        reminder_type TEXT NOT NULL DEFAULT 'METER_READING',
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT '',
                        scheduled_at INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        channel TEXT NOT NULL DEFAULT 'LOCAL_NOTIFICATION',
                        snooze_count INTEGER NOT NULL DEFAULT 0,
                        completed_at INTEGER,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO reminders_v3 (id, subscriber_id, invoice_id, reminder_type, title, message, notes, scheduled_at, status, channel, snooze_count, completed_at, created_at)
                    SELECT id, subscriber_id, invoice_id, 'METER_READING', title, message, '', scheduled_at, status, channel, 0, NULL, created_at FROM reminders
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE reminders")
                db.execSQL("ALTER TABLE reminders_v3 RENAME TO reminders")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_subscriber_id ON reminders(subscriber_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_invoice_id ON reminders(invoice_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_scheduled_at ON reminders(scheduled_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_status ON reminders(status)")
            }
        }

        /**
         * Non-destructive migration from Phase 3 (v3) to Phase 4 (v4),
         * adding per-user custom permissions JSON, updated_at, and sync_id for Wi-Fi sync.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN custom_permissions_json TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE users SET updated_at = created_at WHERE updated_at = 0")
                db.execSQL("UPDATE users SET sync_id = 'user_' || username WHERE sync_id = ''")
            }
        }

        /**
         * Non-destructive migration from Phase 4/5 (v4) to Phase 6 (v5),
         * adding per-user biometric login opt-in flag while preserving existing accounts.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN biometric_enabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Non-destructive migration from v5 to v6:
         * 1. Adds initial_meter_reading to subscribers (if not already present).
         * 2. Rebuilds meter_readings with reading_half (1 = First Half days 1..15, 2 = Second Half days 16..end)
         *    and composite unique index (subscriber_id, billing_period, reading_half).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                var hasInitialReadingCol = false
                db.query("PRAGMA table_info(subscribers)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0 && cursor.getString(nameIdx) == "initial_meter_reading") {
                            hasInitialReadingCol = true
                            break
                        }
                    }
                }
                if (!hasInitialReadingCol) {
                    db.execSQL("ALTER TABLE subscribers ADD COLUMN initial_meter_reading REAL NOT NULL DEFAULT 0.0")
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meter_readings_v6 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subscriber_id INTEGER NOT NULL REFERENCES subscribers(id) ON DELETE RESTRICT,
                        billing_period TEXT NOT NULL,
                        reading_half INTEGER NOT NULL DEFAULT 1,
                        previous_reading REAL NOT NULL,
                        current_reading REAL NOT NULL,
                        consumed_kwh REAL NOT NULL,
                        recorded_by_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
                        reading_date INTEGER NOT NULL,
                        photo_uri TEXT,
                        is_low_reading_override INTEGER NOT NULL DEFAULT 0,
                        override_reason TEXT NOT NULL DEFAULT '',
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO meter_readings_v6 (
                        id, subscriber_id, billing_period, reading_half,
                        previous_reading, current_reading, consumed_kwh,
                        recorded_by_user_id, reading_date, photo_uri,
                        is_low_reading_override, override_reason, notes
                    )
                    SELECT
                        id,
                        subscriber_id,
                        billing_period,
                        CASE
                            WHEN CAST(strftime('%d', reading_date / 1000, 'unixepoch', 'localtime') AS INTEGER) >= 16 THEN 2
                            ELSE 1
                        END,
                        previous_reading,
                        current_reading,
                        consumed_kwh,
                        recorded_by_user_id,
                        reading_date,
                        photo_uri,
                        is_low_reading_override,
                        override_reason,
                        notes
                    FROM meter_readings
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE meter_readings")
                db.execSQL("ALTER TABLE meter_readings_v6 RENAME TO meter_readings")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_meter_readings_subscriber_id_billing_period_reading_half ON meter_readings(subscriber_id, billing_period, reading_half)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_meter_readings_billing_period_reading_half ON meter_readings(billing_period, reading_half)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_meter_readings_recorded_by_user_id ON meter_readings(recorded_by_user_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_meter_readings_reading_date ON meter_readings(reading_date)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AlasalDatabase? = null

        @Volatile
        private var INSTANCE_APP_CONTEXT: Context? = null

        fun getInstance(context: Context): AlasalDatabase {
            val appCtx = context.applicationContext
            val existing = INSTANCE
            if (existing != null && INSTANCE_APP_CONTEXT === appCtx) {
                return existing
            }
            return synchronized(this) {
                val current = INSTANCE
                if (current != null && INSTANCE_APP_CONTEXT === appCtx) {
                    current
                } else {
                    if (current != null) {
                        runCatching { current.close() }
                    }
                    Room.databaseBuilder(
                        appCtx,
                        AlasalDatabase::class.java,
                        DATABASE_NAME
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                        .addCallback(object : Callback() {
                            override fun onOpen(db: SupportSQLiteDatabase) {
                                super.onOpen(db)
                                db.execSQL("PRAGMA foreign_keys=ON;")
                            }
                        })
                        .build()
                        .also {
                            INSTANCE = it
                            INSTANCE_APP_CONTEXT = appCtx
                        }
                }
            }
        }

        /**
         * Flushes WAL frames cleanly, closes the Room connection pool, and clears the singleton
         * reference so database files can be safely replaced during transactional restore.
         */
        @Synchronized
        fun closeAndResetInstance() {
            val current = INSTANCE
            if (current != null) {
                runCatching {
                    if (current.isOpen) {
                        current.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c ->
                            c.moveToFirst()
                        }
                    }
                }
                runCatching {
                    current.close()
                }
                INSTANCE = null
                INSTANCE_APP_CONTEXT = null
            }
        }
    }
}
