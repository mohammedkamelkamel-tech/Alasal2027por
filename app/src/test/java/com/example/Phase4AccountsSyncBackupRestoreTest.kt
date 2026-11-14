package com.example

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.local.database.AlasalDatabase
import com.example.data.repository.AlasalRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.MeterReadingInput
import com.example.data.repository.StationSyncRepository
import com.example.data.repository.SubscriberInput
import com.example.domain.auth.StationPermission
import com.example.domain.auth.StationRoles
import com.example.domain.sync.SyncConnectionState
import com.example.service.BackupAndRestoreManager
import com.example.service.DailyAutoBackupWorker
import com.example.service.LocalWifiSyncEngine
import com.example.service.RestoreFailureMessages
import com.example.service.SafBackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase4AccountsSyncBackupRestoreTest {

    private lateinit var context: Context
    private lateinit var db: AlasalDatabase
    private lateinit var repo: AlasalRepository
    private lateinit var billingRepo: BillingRepository
    private lateinit var syncRepo: StationSyncRepository
    private lateinit var backupManager: BackupAndRestoreManager

    private fun clearFileProviderStaticCacheForRobolectric() {
        runCatching {
            val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    @Before
    fun setup() {
        clearFileProviderStaticCacheForRobolectric()
        context = ApplicationProvider.getApplicationContext()
        AlasalDatabase.closeAndResetInstance()
        context.deleteDatabase(AlasalDatabase.DATABASE_NAME)
        File(context.filesDir, "backups").deleteRecursively()

        db = AlasalDatabase.getInstance(context)
        repo = AlasalRepository(db)
        billingRepo = BillingRepository(db, repo)
        syncRepo = StationSyncRepository(db, repo)
        backupManager = BackupAndRestoreManager(context, db, repo)

        runBlocking {
            repo.ensureBaselineSeeded()
        }
    }

    @After
    fun tearDown() {
        AlasalDatabase.closeAndResetInstance()
        context.deleteDatabase(AlasalDatabase.DATABASE_NAME)
        context.deleteDatabase("peer_device_b.db")
        File(context.filesDir, "backups").deleteRecursively()
        clearFileProviderStaticCacheForRobolectric()
    }

    // -----------------------------------------------------------------------
    // 1. Accounts, Permissions Matrix & Programmatic Enforcement Tests
    // -----------------------------------------------------------------------

    @Test
    fun `accounts CRUD, authentication, activation, custom permissions matrix, and programmatic enforcement`(): Unit = runBlocking {
        // Create Sub-Admin with restricted custom permissions (disallow delete & backups)
        val subAdminId = repo.createUserAccount(
            username = "sub_mgr",
            fullName = "نائب المدير",
            roleCode = StationRoles.SUB_ADMIN,
            password = "pass1234",
            isActive = true,
            customPermissions = mapOf(
                StationPermission.DELETE_SUBSCRIBER.key to false,
                StationPermission.DELETE_INVOICE.key to false,
                StationPermission.EXPORT_BACKUP.key to false,
                StationPermission.MANAGE_USERS.key to false
            )
        ).getOrThrow()

        // Create Collector account
        val collectorId = repo.createUserAccount(
            username = "collector_ali",
            fullName = "علي المحصل",
            roleCode = StationRoles.COLLECTOR,
            password = "col2027",
            isActive = true
        ).getOrThrow()

        // Authenticate valid + invalid password
        assertTrue(repo.authenticateUser("sub_mgr", "pass1234").isSuccess)
        assertTrue(repo.authenticateUser("sub_mgr", "wrong").isFailure)

        // Change password and verify new password works while old fails
        assertTrue(repo.changeUserPassword(collectorId, "new9999").isSuccess)
        assertTrue(repo.authenticateUser("collector_ali", "col2027").isFailure)
        assertTrue(repo.authenticateUser("collector_ali", "new9999").isSuccess)

        // Create subscriber + reading + invoice as Admin
        val subId = repo.saveSubscriber(
            SubscriberInput(
                subscriberCode = "PERM-01",
                fullName = "مشترك اختبار الصلاحيات",
                phoneNumber = "771001001",
                meterNumber = "MTR-P4-1",
                meterSealNumber = "S-1",
                address = "الشارع العام",
                subscriptionType = "منزلي",
                customKwhPrice = 0.50,
                subscriptionDate = System.currentTimeMillis(),
                isActive = true
            )
        ).getOrThrow()
        val rId = billingRepo.saveMeterReading(
            MeterReadingInput(subscriberId = subId, billingPeriod = "2027-04", currentReading = 100.0, readingDate = System.currentTimeMillis())
        ).getOrThrow()
        val invId = billingRepo.issueInvoiceForReading(rId).getOrThrow()

        // Programmatic denial for Collector trying unauthorized operations directly via Repository
        val addSubByCollector = repo.saveSubscriber(
            SubscriberInput(
                subscriberCode = "UNAUTH-01",
                fullName = "مرفوض",
                phoneNumber = "770",
                meterNumber = "MTR-U1",
                meterSealNumber = "",
                address = "",
                subscriptionType = "منزلي",
                customKwhPrice = 0.50,
                subscriptionDate = 1000L,
                isActive = true
            ),
            actorUserId = collectorId
        )
        assertTrue(addSubByCollector.isFailure)
        assertTrue(addSubByCollector.exceptionOrNull() is SecurityException)

        val priceEditByCollector = repo.applyKwhPriceUpdate(
            targetSubscriberIds = listOf(subId),
            newPricePerKwh = 0.90,
            effectiveFrom = System.currentTimeMillis(),
            reason = "غير مصرح",
            actorUserId = collectorId
        )
        assertTrue(priceEditByCollector.exceptionOrNull() is SecurityException)

        val deleteInvBySubAdmin = billingRepo.deleteUnpaidInvoiceSafely(invId, actorUserId = subAdminId)
        assertTrue(deleteInvBySubAdmin.exceptionOrNull() is SecurityException)

        val createUserBySubAdmin = repo.createUserAccount(
            username = "rogue",
            fullName = "غير مصرح",
            roleCode = StationRoles.ADMIN,
            password = "1234",
            actorUserId = subAdminId
        )
        assertTrue(createUserBySubAdmin.exceptionOrNull() is SecurityException)

        // Collector IS allowed to record collection
        val colRes = billingRepo.recordInvoicePayment(
            invoiceId = invId,
            amountPaid = 20.0,
            actorUserId = collectorId
        )
        assertTrue(colRes.isSuccess)

        // Grant DELETE_INVOICE dynamically to SubAdmin, verify unpaid deletion works on another unpaid invoice
        val r2 = billingRepo.saveMeterReading(
            MeterReadingInput(subscriberId = subId, billingPeriod = "2027-05", currentReading = 150.0, readingDate = System.currentTimeMillis())
        ).getOrThrow()
        val inv2 = billingRepo.issueInvoiceForReading(r2).getOrThrow()
        assertTrue(repo.updateUserPermissions(subAdminId, mapOf(StationPermission.DELETE_INVOICE.key to true)).isSuccess)
        // Deleting inv2 works & restores inv1's CARRIED_OVER status back to PARTIAL!
        assertTrue(billingRepo.deleteUnpaidInvoiceSafely(inv2, actorUserId = subAdminId).isSuccess)
        assertEquals("PARTIAL", db.billingDao().getInvoiceById(invId)?.status)

        // Deactivate collector -> login & authorized collection immediately blocked
        assertTrue(repo.toggleUserActive(collectorId, false).isSuccess)
        val loginDeactivated = repo.authenticateUser("collector_ali", "new9999")
        assertTrue(loginDeactivated.isFailure)
        val colAfterDeactivate = billingRepo.recordInvoicePayment(invId, 10.0, actorUserId = collectorId)
        assertTrue(colAfterDeactivate.exceptionOrNull() is SecurityException)

        // Prevent deactivating last active ADMIN
        val primaryAdmin = db.systemDao().getUserByUsername("admin")!!
        val disableLastAdmin = repo.toggleUserActive(primaryAdmin.id, false)
        assertTrue(disableLastAdmin.isFailure)
    }

    // -----------------------------------------------------------------------
    // 2. Peer-to-Peer Local Wi-Fi Socket Sync Tests (Conflicts & Retry)
    // -----------------------------------------------------------------------

    @Test
    fun `two-device local Wi-Fi socket sync merges bidirectionally without duplicates, resolves LWW conflicts, preserves all payments, and handles retry after failure`(): Unit = runBlocking {
        // Prepare Device A data in main DB
        val subAId = repo.saveSubscriber(
            SubscriberInput(
                subscriberCode = "SYNC-SUB-01",
                fullName = "الاسم القديم على جهاز A",
                phoneNumber = "770111111",
                meterNumber = "MTR-SYNC-1",
                meterSealNumber = "S-A",
                address = "صنعاء",
                subscriptionType = "منزلي",
                customKwhPrice = 0.45,
                subscriptionDate = 1000L,
                isActive = true
            )
        ).getOrThrow()
        // Stamp older updatedAt on Device A's subscriber
        val subA = db.subscriberDao().getById(subAId)!!
        db.subscriberDao().update(subA.copy(updatedAt = 10_000L))

        val rA = billingRepo.saveMeterReading(
            MeterReadingInput(subscriberId = subAId, billingPeriod = "2027-03", currentReading = 100.0, readingDate = 11_000L)
        ).getOrThrow()
        val invAId = billingRepo.issueInvoiceForReading(rA).getOrThrow()
        // Collection #1 on Device A: COL-2027-0001 / RCP-2027-0001
        billingRepo.recordInvoicePayment(invAId, amountPaid = 15.0, collectedAt = 12_000L).getOrThrow()

        // Create a user on Device A to verify user & permissions sync to Device B
        repo.createUserAccount(
            username = "synced_cashier",
            fullName = "محصل متزامن",
            roleCode = StationRoles.COLLECTOR,
            password = "sync777",
            customPermissions = mapOf(StationPermission.VIEW_REPORTS.key to true)
        ).getOrThrow()

        // Prepare Device B (independent SQLite database file)
        val dbB = Room.databaseBuilder(context, AlasalDatabase::class.java, "peer_device_b.db")
            .allowMainThreadQueries()
            .build()
        try {
            val repoB = AlasalRepository(dbB)
            val billingB = BillingRepository(dbB, repoB)
            val syncB = StationSyncRepository(dbB, repoB)
            repoB.ensureBaselineSeeded()

            // Device B has SAME subscriber code with NEWER updatedAt (50_000L > 10_000L) -> must win!
            val subBId = repoB.saveSubscriber(
                SubscriberInput(
                    subscriberCode = "SYNC-SUB-01",
                    fullName = "الاسم المحدث الأحدث على جهاز B",
                    phoneNumber = "770999999",
                    meterNumber = "MTR-SYNC-1",
                    meterSealNumber = "S-B",
                    address = "الحي الغربي - محدث",
                    subscriptionType = "منزلي",
                    customKwhPrice = 0.50,
                    subscriptionDate = 1000L,
                    isActive = true
                )
            ).getOrThrow()
            dbB.subscriberDao().update(dbB.subscriberDao().getById(subBId)!!.copy(updatedAt = 50_000L))

            // Device B ALSO has an offline invoice INV-2027-0001 and collection COL-2027-0001 for ANOTHER subscriber!
            val subB2Id = repoB.saveSubscriber(
                SubscriberInput(
                    subscriberCode = "SYNC-SUB-02",
                    fullName = "مشترك حصري بجهاز B",
                    phoneNumber = "770222222",
                    meterNumber = "MTR-SYNC-2",
                    meterSealNumber = "S-B2",
                    address = "إب",
                    subscriptionType = "تجاري",
                    customKwhPrice = 0.60,
                    subscriptionDate = 2000L,
                    isActive = true
                )
            ).getOrThrow()
            val rB2 = billingB.saveMeterReading(
                MeterReadingInput(subscriberId = subB2Id, billingPeriod = "2027-03", currentReading = 50.0, readingDate = 21_000L)
            ).getOrThrow()
            val invB2Id = billingB.issueInvoiceForReading(rB2).getOrThrow() // Offline INV-2027-0001 collision!
            billingB.recordInvoicePayment(invB2Id, amountPaid = 10.0, collectedAt = 22_000L).getOrThrow() // Offline COL-2027-0001 collision!

            val engineA = LocalWifiSyncEngine(context, repo, syncRepo, deviceId = "DEV-A", deviceName = "جهاز المحطة A")
            val engineB = LocalWifiSyncEngine(context, repoB, syncB, deviceId = "DEV-B", deviceName = "جهاز المحصل B")

            // Simulate connection failure to a closed port first -> assert FAILED state & log recorded
            val failAttempt = engineB.syncWithPeer("127.0.0.1", 59999)
            assertTrue(failAttempt.isFailure)
            assertEquals(SyncConnectionState.FAILED, engineB.connectionState.value)
            assertTrue(engineB.syncLogs.value.any { !it.isSuccess })
            assertTrue(engineB.hasRetryTarget())

            // Now start Engine A socket server on ephemeral port & verify real socket sync!
            val port = engineA.startHosting(port = 0, registerNsd = false).getOrThrow()
            try {
                // Retry / connect from Engine B to Engine A over real TCP socket
                val resB = engineB.syncWithPeer("127.0.0.1", port)
                assertTrue("Expected Wi-Fi sync to succeed: ${resB.exceptionOrNull()}", resB.isSuccess)
                assertEquals(SyncConnectionState.SUCCEEDED, engineB.connectionState.value)

                // Verify Device A received newer subscriber info & Device B's exclusive subscriber + colliding invoice/collection!
                val aSubs = db.subscriberDao().getAllSubscribersOnce()
                assertEquals(2, aSubs.size)
                assertEquals("الاسم المحدث الأحدث على جهاز B", aSubs.first { it.subscriberCode == "SYNC-SUB-01" }.fullName)

                val aInvoices = db.billingDao().getAllInvoicesOnce()
                assertEquals(2, aInvoices.size)
                assertEquals(2, aInvoices.map { it.invoiceNumber }.distinct().size)

                val aCollections = db.collectionDao().getAllCollectionsOnce()
                assertEquals(2, aCollections.size)
                assertEquals(25.0, aCollections.sumOf { it.amountPaid }, 0.01)

                val aReceipts = db.collectionDao().getAllReceiptsOnce()
                assertEquals(2, aReceipts.size)
                assertEquals(2, aReceipts.map { it.receiptNumber }.distinct().size)

                // Verify Device B did NOT overwrite its newer subscriber name with Device A's older name!
                val bSubs = dbB.subscriberDao().getAllSubscribersOnce()
                assertEquals("الاسم المحدث الأحدث على جهاز B", bSubs.first { it.subscriberCode == "SYNC-SUB-01" }.fullName)
                assertEquals(2, dbB.collectionDao().getAllCollectionsOnce().size)

                // Verify synced user & custom permissions authenticate cleanly on Device B
                assertTrue(repoB.authenticateUser("synced_cashier", "sync777").isSuccess)
                val syncedUserOnB = dbB.systemDao().getUserByUsername("synced_cashier")!!
                assertTrue(repoB.isUserPermissionAllowed(syncedUserOnB.id, StationPermission.VIEW_REPORTS.key))

                // Repeat sync again -> idempotent (0 new duplicate records!)
                val resRepeat = engineB.retryLastFailedSync().getOrThrow()
                assertEquals(0, resRepeat.insertedCount)
                assertEquals(2, db.collectionDao().getAllCollectionsOnce().size)
                assertEquals(2, dbB.collectionDao().getAllCollectionsOnce().size)
            } finally {
                engineA.stopHostingBlocking()
            }
        } finally {
            dbB.close()
        }
    }

    // -----------------------------------------------------------------------
    // 3. Backup Creation, Daily Worker, Share/Drive & Repeated 18-Step Safe Restore
    // -----------------------------------------------------------------------

    @Test
    fun `manual backup, daily auto backup worker, verification, share intent, and repeated 18-step safe restore succeed across v2-v4 and JSON`(): Unit = runBlocking {
        // Populate known realistic records
        val s1 = repo.saveSubscriber(
            SubscriberInput(
                subscriberCode = "RST-01",
                fullName = "عبدالله السالم",
                phoneNumber = "773333333",
                meterNumber = "MTR-RST-1",
                meterSealNumber = "SL-1",
                address = "حي العسل",
                subscriptionType = "منزلي",
                customKwhPrice = 0.50,
                subscriptionDate = 1000L,
                isActive = true,
                initialCreditBalance = 10.0
            )
        ).getOrThrow()
        val r1 = billingRepo.saveMeterReading(
            MeterReadingInput(subscriberId = s1, billingPeriod = "2027-04", currentReading = 100.0, readingDate = 2000L)
        ).getOrThrow()
        val inv1 = billingRepo.issueInvoiceForReading(r1).getOrThrow() // 50 - 10 = 40.0 net
        billingRepo.recordInvoicePayment(inv1, amountPaid = 25.0).getOrThrow() // 15.0 remaining

        // 1. Manual backup creation + verification + FileProvider share/Google Drive intent
        val manualInfo = backupManager.createBackup("MANUAL").getOrThrow()
        val manualFile = File(manualInfo.absolutePath)
        assertTrue(manualFile.exists())
        assertTrue(manualInfo.fileSizeBytes > 0L)
        assertTrue(manualInfo.isVerified)
        assertEquals(1, manualInfo.subscribersCount)
        assertEquals(1, manualInfo.invoicesCount)
        assertEquals(1, manualInfo.collectionsCount)

        val verifyManual = backupManager.verifyBackupFile(manualFile)
        assertTrue(verifyManual.isValid)
        assertEquals(14, verifyManual.tableCount)

        val driveIntent = backupManager.createShareOrDriveIntent(manualFile, targetGoogleDrive = true)
        assertNotNull(driveIntent)
        assertTrue(driveIntent.action == Intent.ACTION_SEND || driveIntent.action == Intent.ACTION_CHOOSER)

        // 2. Daily auto backup via WorkManager worker
        val worker = TestListenableWorkerBuilder<DailyAutoBackupWorker>(context).build()
        val workResult = worker.doWork()
        assertTrue(workResult is ListenableWorker.Result.Success)
        val outData = (workResult as ListenableWorker.Result.Success).outputData
        assertTrue(outData.getBoolean(DailyAutoBackupWorker.KEY_BACKUP_VERIFIED, false))

        // 3. Mutate live database significantly BEFORE restore #1
        val extraSubId = repo.saveSubscriber(
            SubscriberInput(
                subscriberCode = "EXTRA-TEMP",
                fullName = "بيانات مؤقتة ستحذف بالاستعادة",
                phoneNumber = "770000000",
                meterNumber = "MTR-TEMP",
                meterSealNumber = "",
                address = "",
                subscriptionType = "منزلي",
                customKwhPrice = 0.50,
                subscriptionDate = 3000L,
                isActive = true
            )
        ).getOrThrow()
        assertTrue(extraSubId > 0L)
        assertEquals(2, db.subscriberDao().getAllSubscribersOnce().size)

        // Execute 18-Step Safe Restore #1 from manual backup package
        val restoreRes1 = backupManager.executeSafeRestoreFromFile(manualFile)
        assertTrue("Restore #1 failed: ${restoreRes1.message}", restoreRes1.succeeded)
        assertEquals(18, restoreRes1.steps.size)
        assertTrue(restoreRes1.steps.all { it.succeeded })
        assertNotNull(restoreRes1.preRestoreBackupPath)
        assertTrue(File(restoreRes1.preRestoreBackupPath!!).exists())

        // Re-fetch active singleton database after restore swap & verify exact counts & financial balances
        db = AlasalDatabase.getInstance(context)
        repo = AlasalRepository(db)
        val restoredSubs = db.subscriberDao().getAllSubscribersOnce()
        assertEquals(1, restoredSubs.size)
        assertEquals("عبدالله السالم", restoredSubs.first().fullName)
        assertEquals(15.0, restoredSubs.first().currentArrearsAmount, 0.01)
        assertEquals(1, db.billingDao().getAllInvoicesOnce().size)
        assertEquals(1, db.collectionDao().getAllCollectionsOnce().size)
        assertEquals(1, db.collectionDao().getAllReceiptsOnce().size)

        // 4. Repeat Restore #2: Restore an older compatible Phase 1 SAF JSON backup into fresh DB
        val legacyJsonFile = File(context.cacheDir, "legacy_phase1_backup.json")
        SafBackupManager(context, repo).exportFoundationBackupToUri(Uri.fromFile(legacyJsonFile)).getOrThrow()
        assertTrue(legacyJsonFile.exists())
        val restoreRes2 = backupManager.executeSafeRestoreFromFile(legacyJsonFile)
        assertTrue("Legacy JSON restore failed: ${restoreRes2.message}", restoreRes2.succeeded)
        assertEquals(18, restoreRes2.steps.size)
        db = AlasalDatabase.getInstance(context)
        repo = AlasalRepository(db)
        assertEquals(1, db.subscriberDao().getAllSubscribersOnce().size)
        assertEquals("RST-01", db.subscriberDao().getAllSubscribersOnce().first().subscriberCode)
        legacyJsonFile.delete()
    }

    // -----------------------------------------------------------------------
    // 4. Mandatory Restore Failure Scenarios (Live DB Preserved 100%)
    // -----------------------------------------------------------------------

    @Test
    fun `mandatory restore failure tests reject corrupt, unsupported version, unreadable, broken DB, low space, and rollback safely while keeping live data intact`(): Unit = runBlocking {
        // Seed live data that MUST stay 100% preserved across every failure scenario
        val subId = repo.saveSubscriber(
            SubscriberInput(
                subscriberCode = "KEEP-SAFE-01",
                fullName = "مشترك يجب الحفاظ على بياناته",
                phoneNumber = "778888888",
                meterNumber = "MTR-KEEP-1",
                meterSealNumber = "KS-1",
                address = "تعز",
                subscriptionType = "تجاري",
                customKwhPrice = 0.55,
                subscriptionDate = 1000L,
                isActive = true
            )
        ).getOrThrow()
        val rId = billingRepo.saveMeterReading(
            MeterReadingInput(subscriberId = subId, billingPeriod = "2027-04", currentReading = 100.0, readingDate = 2000L)
        ).getOrThrow()
        val invId = billingRepo.issueInvoiceForReading(rId).getOrThrow() // 55.0 net
        billingRepo.recordInvoicePayment(invId, amountPaid = 20.0).getOrThrow() // 35.0 remaining

        val validBackup = File(backupManager.createBackup("MANUAL").getOrThrow().absolutePath)

        // Helper to assert live database was completely untouched
        suspend fun assertLiveDataIntact() {
            val activeDb = AlasalDatabase.getInstance(context)
            val subs = activeDb.subscriberDao().getAllSubscribersOnce()
            assertEquals(1, subs.size)
            assertEquals("KEEP-SAFE-01", subs.first().subscriberCode)
            assertEquals(35.0, subs.first().currentArrearsAmount, 0.01)
            assertEquals(1, activeDb.billingDao().getAllInvoicesOnce().size)
            assertEquals(1, activeDb.collectionDao().getAllCollectionsOnce().size)
            assertEquals(1, activeDb.collectionDao().getAllReceiptsOnce().size)
        }

        // Failure A: Unreadable / non-existent file
        val missingFile = File(context.cacheDir, "does_not_exist_1234.alasalbak")
        val missingRes = backupManager.executeSafeRestoreFromFile(missingFile)
        assertFalse(missingRes.succeeded)
        assertEquals(RestoreFailureMessages.UNREADABLE_FILE, missingRes.message)
        assertLiveDataIntact()

        // Failure B: Corrupt file (random non-archive bytes + tampered SHA-256)
        val garbageFile = File(context.cacheDir, "corrupt_garbage.alasalbak")
        garbageFile.writeText("NOT_A_VALID_ZIP_OR_SQLITE_FILE")
        val corruptRes1 = backupManager.executeSafeRestoreFromFile(garbageFile)
        assertFalse(corruptRes1.succeeded)
        assertEquals(RestoreFailureMessages.CORRUPT_BACKUP, corruptRes1.message)
        assertLiveDataIntact()
        garbageFile.delete()

        // Failure B2: Valid ZIP with tampered DB byte causing SHA-256 mismatch
        val tamperedZip = File(context.cacheDir, "tampered_sha.alasalbak")
        ZipFile(validBackup).use { srcZip ->
            ZipOutputStream(FileOutputStream(tamperedZip)).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(srcZip.getInputStream(srcZip.getEntry("manifest.json")).readBytes())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("alasal_2027.db"))
                val dbBytes = srcZip.getInputStream(srcZip.getEntry("alasal_2027.db")).readBytes()
                dbBytes[dbBytes.size - 10] = (dbBytes[dbBytes.size - 10].toInt() xor 0x55).toByte()
                zos.write(dbBytes)
                zos.closeEntry()
            }
        }
        val tamperedRes = backupManager.executeSafeRestoreFromFile(tamperedZip)
        assertFalse(tamperedRes.succeeded)
        assertEquals(RestoreFailureMessages.CORRUPT_BACKUP, tamperedRes.message)
        assertLiveDataIntact()
        tamperedZip.delete()

        // Failure C: Unsupported future database version (PRAGMA user_version = 99)
        val futureDbFile = File(context.cacheDir, "future_v99.db")
        val futureHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(futureDbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(99) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE dummy (id INTEGER PRIMARY KEY)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        futureHelper.writableDatabase.close()
        futureHelper.close()
        val versionRes = backupManager.executeSafeRestoreFromFile(futureDbFile)
        assertFalse(versionRes.succeeded)
        assertEquals(RestoreFailureMessages.UNSUPPORTED_VERSION, versionRes.message)
        assertLiveDataIntact()
        futureDbFile.delete()

        // Failure D: Database check failed (valid v4 SQLite file missing 14 tables)
        val incompleteDbFile = File(context.cacheDir, "incomplete_schema_v4.db")
        val incHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(incompleteDbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(AlasalDatabase.CURRENT_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE only_one_table (id INTEGER PRIMARY KEY)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        incHelper.writableDatabase.close()
        incHelper.close()
        val schemaFailRes = backupManager.executeSafeRestoreFromFile(incompleteDbFile)
        assertFalse(schemaFailRes.succeeded)
        assertEquals(RestoreFailureMessages.DB_CHECK_FAILED, schemaFailRes.message)
        assertLiveDataIntact()
        incompleteDbFile.delete()

        // Failure E: Insufficient storage space
        backupManager.overrideFreeSpaceBytesForTest = 512L
        val spaceRes = backupManager.executeSafeRestoreFromFile(validBackup)
        backupManager.overrideFreeSpaceBytesForTest = null
        assertFalse(spaceRes.succeeded)
        assertEquals(RestoreFailureMessages.INSUFFICIENT_SPACE, spaceRes.message)
        assertLiveDataIntact()

        // Failure F: Simulated failure right after file swap -> automatic rollback preserves live DB
        backupManager.simulatePostSwapFailureForTest = true
        val rollbackRes = backupManager.executeSafeRestoreFromFile(validBackup)
        backupManager.simulatePostSwapFailureForTest = false
        assertFalse(rollbackRes.succeeded)
        assertEquals(RestoreFailureMessages.RESTORE_FAILED_KEPT_CURRENT, rollbackRes.message)
        assertLiveDataIntact()
    }
}
