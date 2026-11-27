package com.example.service

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.data.local.database.AlasalDatabase
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.repository.AlasalRepository
import com.example.data.repository.StationSyncRepository
import com.example.domain.auth.StationPermission
import com.example.domain.sync.StationSyncEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object RestoreFailureMessages {
    const val CORRUPT_BACKUP = "النسخة تالفة"
    const val UNSUPPORTED_VERSION = "إصدار النسخة غير مدعوم"
    const val UNREADABLE_FILE = "تعذر قراءة الملف"
    const val DB_CHECK_FAILED = "فشل فحص قاعدة البيانات"
    const val INSUFFICIENT_SPACE = "لا توجد مساحة كافية"
    const val RESTORE_FAILED_KEPT_CURRENT = "فشل الاستعادة وتم الاحتفاظ بالبيانات الحالية"
}

data class BackupRecordInfo(
    val id: String,
    val fileName: String,
    val absolutePath: String,
    val createdAt: Long,
    val fileSizeBytes: Long,
    val dbVersion: Int,
    val backupType: String, // MANUAL, AUTO_DAILY, PRE_RESTORE
    val isVerified: Boolean,
    val sha256: String,
    val subscribersCount: Int,
    val invoicesCount: Int,
    val collectionsCount: Int
)

data class BackupVerificationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val dbVersion: Int = 0,
    val sha256: String = "",
    val tableCount: Int = 0,
    val fileSizeBytes: Long = 0L,
    val createdAt: Long = 0L
)

data class RestoreStepRecord(
    val stepNumber: Int,
    val title: String,
    val succeeded: Boolean,
    val details: String
)

data class RestoreExecutionResult(
    val succeeded: Boolean,
    val message: String,
    val steps: List<RestoreStepRecord>,
    val preRestoreBackupPath: String? = null,
    val restoredSubscribersCount: Int = 0,
    val restoredInvoicesCount: Int = 0,
    val restoredCollectionsCount: Int = 0,
    val technicalError: String? = null
)

class BackupAndRestoreManager(
    private val context: Context,
    @Volatile private var database: AlasalDatabase,
    @Volatile private var repository: AlasalRepository,
    private val onReloadApplicationDependencies: () -> Unit = {}
) {
    companion object {
        private const val TAG = "AlasalRestore"
        private const val BACKUP_EXTENSION = ".alasalbak"
        private const val ENTRY_DB = "alasal_2027.db"
        private const val ENTRY_MANIFEST = "manifest.json"
        private const val MIN_REQUIRED_FREE_BYTES = 2L * 1024L * 1024L

        val REQUIRED_14_TABLES = listOf(
            "subscribers",
            "invoices",
            "collections",
            "receipts",
            "kwh_tariffs",
            "tariff_change_logs",
            "arrears",
            "credit_balances",
            "meter_readings",
            "reminders",
            "users",
            "permissions",
            "app_settings",
            "financial_audit_logs"
        )
    }

    private val backupsDir: File
        get() = File(context.filesDir, "backups").apply { mkdirs() }

    private val sharedBackupsDir: File
        get() = File(context.cacheDir, "shared_backups").apply { mkdirs() }

    private val _backups = MutableStateFlow<List<BackupRecordInfo>>(emptyList())
    val backups: StateFlow<List<BackupRecordInfo>> = _backups.asStateFlow()

    private val _lastRestoreResult = MutableStateFlow<RestoreExecutionResult?>(null)
    val lastRestoreResult: StateFlow<RestoreExecutionResult?> = _lastRestoreResult.asStateFlow()

    // Internal test hooks for verifying exact disk-space and rollback failure paths
    internal var overrideFreeSpaceBytesForTest: Long? = null
    internal var simulatePostSwapFailureForTest: Boolean = false

    fun rebindDatabase(database: AlasalDatabase, repository: AlasalRepository) {
        this.database = database
        this.repository = repository
    }

    fun refreshBackupsList(): List<BackupRecordInfo> {
        val list = backupsDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(BACKUP_EXTENSION) || it.name.endsWith(".zip")) }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { inspectBackupFileSummary(it) }
            .orEmpty()
        _backups.value = list
        return list
    }

    suspend fun createBackup(
        backupType: String = "MANUAL",
        actorUserId: Long? = null,
        customTreeUri: Uri? = null
    ): Result<BackupRecordInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val actor = if (backupType != "PRE_RESTORE" && backupType != "AUTO_DAILY") {
                repository.verifyPermission(actorUserId, StationPermission.EXPORT_BACKUP.key)
            } else {
                null
            }

            // Flush WAL frames cleanly before copying the live SQLite database
            flushWalCheckpoint(database)

            val liveDbFile = context.getDatabasePath(AlasalDatabase.DATABASE_NAME)
            require(liveDbFile.exists() && liveDbFile.length() > 0L) {
                RestoreFailureMessages.DB_CHECK_FAILED
            }

            val now = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
            val prefix = when (backupType) {
                "AUTO_DAILY" -> "alasal_auto"
                "PRE_RESTORE" -> "alasal_pre_restore"
                else -> "alasal_manual"
            }
            val destFile = File(backupsDir, "${prefix}_${stamp}_${UUID.randomUUID().toString().take(4)}$BACKUP_EXTENSION")
            val tempDbCopy = File(context.cacheDir, "snapshot_${UUID.randomUUID()}.db")

            try {
                liveDbFile.copyTo(tempDbCopy, overwrite = true)
                val sha = computeFileSha256(tempDbCopy)
                val counts = repository.getAllTableCountsOnce()

                val manifest = JSONObject().apply {
                    put("appName", "محطة العسل 2027")
                    put("projectCode", "Alasal2027")
                    put("packageFormatVersion", 2)
                    put("dbVersion", AlasalDatabase.CURRENT_VERSION)
                    put("createdAt", now)
                    put("backupType", backupType)
                    put("dbSha256", sha)
                    put("tableCounts", JSONObject().apply {
                        put("subscribers", counts.subscribersCount)
                        put("invoices", counts.invoicesCount)
                        put("collections", counts.collectionsCount)
                        put("receipts", counts.receiptsCount)
                        put("users", counts.usersCount)
                    })
                }

                ZipOutputStream(FileOutputStream(destFile)).use { zos ->
                    zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                    zos.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry(ENTRY_DB))
                    FileInputStream(tempDbCopy).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }

                // Optionally mirror to custom SAF folder / SD card if configured
                if (customTreeUri != null) {
                    writeFileToSafTree(customTreeUri, destFile)
                }

                val info = BackupRecordInfo(
                    id = destFile.name,
                    fileName = destFile.name,
                    absolutePath = destFile.absolutePath,
                    createdAt = now,
                    fileSizeBytes = destFile.length(),
                    dbVersion = AlasalDatabase.CURRENT_VERSION,
                    backupType = backupType,
                    isVerified = true,
                    sha256 = sha,
                    subscribersCount = counts.subscribersCount,
                    invoicesCount = counts.invoicesCount,
                    collectionsCount = counts.collectionsCount
                )

                if (backupType != "PRE_RESTORE") {
                    repository.recordAuditLog(
                        operationType = "BACKUP_CREATED",
                        entityName = "backups",
                        actorUserId = actor?.id,
                        actorNameSnapshot = actor?.fullName ?: "النظام",
                        detailsAr = "إنشاء نسخة احتياطية ($backupType): ${destFile.name} (${formatBytes(destFile.length())})"
                    )
                }

                pruneOldBackups()
                refreshBackupsList()
                info
            } finally {
                tempDbCopy.delete()
            }
        }
    }

    suspend fun exportBackupToUri(
        backupFile: File? = null,
        destinationUri: Uri,
        actorUserId: Long? = null
    ): Result<BackupRecordInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceRecord = if (backupFile != null && backupFile.exists()) {
                inspectBackupFileSummary(backupFile)
                    ?: createBackup("MANUAL", actorUserId).getOrThrow()
            } else {
                createBackup("MANUAL", actorUserId).getOrThrow()
            }
            val src = File(sourceRecord.absolutePath)
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                FileInputStream(src).use { inp -> inp.copyTo(out) }
                out.flush()
            } ?: error(RestoreFailureMessages.UNREADABLE_FILE)

            repository.recordAuditLog(
                operationType = "BACKUP_EXPORTED_SAF",
                entityName = "backups",
                detailsAr = "حفظ النسخة الاحتياطية ${src.name} إلى وحدة التخزين/المجلد المختار"
            )
            sourceRecord
        }
    }

    fun writeFileToSafTree(treeUri: Uri, sourceFile: File): Uri? {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val targetUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "application/octet-stream",
                sourceFile.name
            ) ?: return null
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(sourceFile).use { inp -> inp.copyTo(out) }
                out.flush()
            }
            targetUri
        }.getOrNull()
    }

    fun createShareOrDriveIntent(backupFile: File, targetGoogleDrive: Boolean = false): Intent {
        val shareCopy = File(sharedBackupsDir, backupFile.name)
        backupFile.copyTo(shareCopy, overwrite = true)
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareCopy
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية — محطة العسل 2027 (${backupFile.name})")
            putExtra(Intent.EXTRA_TEXT, "نسخة احتياطية مشفرة البصمة لبيانات محطة العسل 2027")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (targetGoogleDrive) {
                setPackage("com.google.android.apps.docs")
            }
        }
        return if (targetGoogleDrive && sendIntent.resolveActivity(context.packageManager) != null) {
            sendIntent
        } else {
            val fallback = Intent(sendIntent).apply { setPackage(null) }
            Intent.createChooser(fallback, "مشاركة / رفع النسخة الاحتياطية (Google Drive أو تطبيقات المشاركة)")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun deleteBackup(backupFile: File): Boolean {
        val deleted = backupFile.delete()
        refreshBackupsList()
        return deleted
    }

    suspend fun verifyBackupFile(file: File): BackupVerificationResult = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead() || file.length() <= 0L) {
            return@withContext BackupVerificationResult(
                isValid = false,
                errorMessage = RestoreFailureMessages.UNREADABLE_FILE
            )
        }
        val staging = File(context.cacheDir, "verify_stage_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            verifyAndStageCandidate(file, staging)
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun verifyBackupUri(uri: Uri): BackupVerificationResult = withContext(Dispatchers.IO) {
        val tempCopy = File(context.cacheDir, "verify_uri_${UUID.randomUUID()}.tmp")
        try {
            val copied = copyUriToTempFile(context.contentResolver, uri, tempCopy)
            if (!copied) {
                return@withContext BackupVerificationResult(
                    isValid = false,
                    errorMessage = RestoreFailureMessages.UNREADABLE_FILE
                )
            }
            verifyBackupFile(tempCopy)
        } finally {
            tempCopy.delete()
        }
    }

    /**
     * Executes the mandatory 18-Step Safe Restore from a content [Uri].
     */
    suspend fun executeSafeRestoreFromUri(
        uri: Uri,
        actorUserId: Long? = null
    ): RestoreExecutionResult = withContext(Dispatchers.IO) {
        val tempIncoming = File(context.cacheDir, "restore_uri_${UUID.randomUUID()}.tmp")
        try {
            val copied = copyUriToTempFile(context.contentResolver, uri, tempIncoming)
            if (!copied) {
                val res = RestoreExecutionResult(
                    succeeded = false,
                    message = RestoreFailureMessages.UNREADABLE_FILE,
                    steps = listOf(
                        RestoreStepRecord(1, "قراءة مصدر النسخة", false, RestoreFailureMessages.UNREADABLE_FILE)
                    )
                )
                _lastRestoreResult.value = res
                return@withContext res
            }
            executeSafeRestoreFromFile(tempIncoming, actorUserId)
        } finally {
            tempIncoming.delete()
        }
    }

    /**
     * Executes the mandatory 18-Step Safe Restore protocol with isolated verification,
     * pre-restore backup, WAL flush, Room shutdown, atomic reversible swap, and post-open checks.
     */
    suspend fun executeSafeRestoreFromFile(
        sourceFile: File,
        actorUserId: Long? = null
    ): RestoreExecutionResult = withContext(Dispatchers.IO) {
        val steps = mutableListOf<RestoreStepRecord>()
        val stagingDir = File(context.cacheDir, "restore_staging_${UUID.randomUUID()}").apply { mkdirs() }
        val liveDbFile = context.getDatabasePath(AlasalDatabase.DATABASE_NAME)
        val rollbackFile = File(liveDbFile.parentFile, "${AlasalDatabase.DATABASE_NAME}.rollback")
        val incomingFile = File(liveDbFile.parentFile, "${AlasalDatabase.DATABASE_NAME}.incoming")
        var preRestorePath: String? = null
        var swapped = false

        fun fail(
            stepNo: Int,
            title: String,
            exactMessage: String,
            technicalDetail: String? = null
        ): RestoreExecutionResult {
            val stepDetails = if (!technicalDetail.isNullOrBlank()) {
                "$exactMessage — السبب التقني: $technicalDetail"
            } else {
                exactMessage
            }
            steps.add(RestoreStepRecord(stepNo, title, false, stepDetails))
            val result = RestoreExecutionResult(
                succeeded = false,
                message = exactMessage,
                steps = steps.toList(),
                preRestoreBackupPath = preRestorePath,
                technicalError = technicalDetail
            )
            _lastRestoreResult.value = result
            return result
        }

        try {
            // Step 1: Verify actor permission
            val actor = try {
                repository.verifyPermission(actorUserId, StationPermission.EXPORT_BACKUP.key)
            } catch (sec: Exception) {
                return@withContext fail(
                    1,
                    "التحقق من صلاحية الاستعادة",
                    sec.message ?: "غير مصرح بالاستعادة",
                    formatTechnicalException(sec)
                )
            }
            steps.add(RestoreStepRecord(1, "التحقق من صلاحية المستخدم", true, "تم التحقق: ${actor.fullName}"))

            // Capture current compiled Room identity_hash while live DB is still open
            val expectedIdentityHash = readExpectedRoomIdentityHash()

            // Step 2: Check source file readability
            if (!sourceFile.exists() || !sourceFile.canRead() || sourceFile.length() == 0L) {
                return@withContext fail(2, "التحقق من إمكانية قراءة الملف", RestoreFailureMessages.UNREADABLE_FILE)
            }
            steps.add(RestoreStepRecord(2, "فحص قابلية قراءة الملف", true, "الحجم: ${formatBytes(sourceFile.length())}"))

            // Step 3: Inspect file type / header signature
            val fileType = detectBackupFileType(sourceFile)
            if (fileType == DetectedBackupType.UNKNOWN) {
                return@withContext fail(3, "فحص نوع وصيغة الملف", RestoreFailureMessages.CORRUPT_BACKUP)
            }
            steps.add(RestoreStepRecord(3, "فحص صيغة الملف وبنيته", true, "النوع المكتشف: ${fileType.labelAr}"))

            // Step 4: Check available internal disk space
            val freeBytes = overrideFreeSpaceBytesForTest ?: context.filesDir.usableSpace
            val requiredBytes = maxOf(sourceFile.length() * 3L, MIN_REQUIRED_FREE_BYTES)
            if (freeBytes < requiredBytes) {
                return@withContext fail(4, "التحقق من مساحة التخزين", RestoreFailureMessages.INSUFFICIENT_SPACE)
            }
            steps.add(RestoreStepRecord(4, "فحص مساحة التخزين المتاحة", true, "المتاح: ${formatBytes(freeBytes)}"))

            // Step 5: Stage backup into isolated temp directory
            val stagedInfo = stageCandidateToTempDb(sourceFile, fileType, stagingDir)
            if (stagedInfo.errorMessage != null || stagedInfo.stagedDbFile == null) {
                return@withContext fail(
                    stepNo = 5,
                    title = "تفكيك النسخة في مجلد معزول",
                    exactMessage = stagedInfo.errorMessage ?: RestoreFailureMessages.CORRUPT_BACKUP
                )
            }
            steps.add(RestoreStepRecord(5, "تفكيك النسخة في بيئة معزولة", true, "تم عزل قاعدة البيانات المؤقتة بأمان"))

            // Step 6: Verify manifest metadata & SHA-256 checksum
            if (stagedInfo.manifestShaFailed) {
                return@withContext fail(6, "التحقق من بصمة SHA-256", RestoreFailureMessages.CORRUPT_BACKUP)
            }
            steps.add(RestoreStepRecord(6, "التحقق من بصمة SHA-256 والبيانات", true, "تطابق البصمة مؤكد"))

            val stagedDb = stagedInfo.stagedDbFile

            // Step 7: Check DB schema version compatibility
            val detectedVersion = readSqliteUserVersion(stagedDb)
            if (detectedVersion <= 0 || detectedVersion > AlasalDatabase.CURRENT_VERSION) {
                return@withContext fail(7, "فحص توافق إصدار النسخة", RestoreFailureMessages.UNSUPPORTED_VERSION)
            }
            steps.add(RestoreStepRecord(7, "فحص إصدار قاعدة البيانات", true, "إصدار متوافق: v$detectedVersion"))

            // Step 8: Upgrade older versions (v1..v5 -> v6) AND reconcile v6 schema variants & Room identity_hash in isolation
            if (detectedVersion < AlasalDatabase.CURRENT_VERSION) {
                val upgradeErr = upgradeStagedDatabaseToCurrent(stagedDb, detectedVersion)
                if (upgradeErr != null) {
                    return@withContext fail(
                        8,
                        "ترقية الإصدار السابق بالبيئة المعزولة",
                        RestoreFailureMessages.DB_CHECK_FAILED,
                        upgradeErr
                    )
                }
            }
            val reconcileOutcome = reconcileStagedV6SchemaAndIdentity(stagedDb, expectedIdentityHash)
            if (!reconcileOutcome.succeeded) {
                return@withContext fail(
                    8,
                    "مطابقة مخطط قاعدة البيانات وبصمة Room",
                    RestoreFailureMessages.DB_CHECK_FAILED,
                    reconcileOutcome.errorDetail
                )
            }
            val step8Details = when {
                detectedVersion < AlasalDatabase.CURRENT_VERSION ->
                    "تمت الترقية من v$detectedVersion إلى v${AlasalDatabase.CURRENT_VERSION} ومطابقة بصمة Room"
                reconcileOutcome.schemaAdjusted || reconcileOutcome.identitySynced ->
                    "تمت مواءمة مخطط v${AlasalDatabase.CURRENT_VERSION} ومطابقة بصمة Room بنجاح"
                else ->
                    "الإصدار الحالي v${AlasalDatabase.CURRENT_VERSION} متطابق بالكامل"
            }
            steps.add(RestoreStepRecord(8, "التحقق من تطابق إصدار المخطط", true, step8Details))

            // Step 9: SQLite PRAGMA integrity_check on staged DB
            if (!checkSqliteIntegrity(stagedDb)) {
                return@withContext fail(9, "فحص سلامة SQLite (integrity_check)", RestoreFailureMessages.DB_CHECK_FAILED)
            }
            steps.add(RestoreStepRecord(9, "فحص سلامة SQLite (integrity_check)", true, "النتيجة: OK"))

            // Step 10: Verify all 14 tables, foreign keys, AND Room dry-run open on staged DB BEFORE touching live DB
            if (!checkTablesAndForeignKeys(stagedDb)) {
                return@withContext fail(10, "فحص الجداول الـ14 والعلاقات المرجعية", RestoreFailureMessages.DB_CHECK_FAILED)
            }
            val dryRunErr = verifyStagedDbWithRoom(stagedDb)
            if (dryRunErr != null) {
                return@withContext fail(
                    10,
                    "فحص الجداول الـ14 ومطابقة Room في البيئة المعزولة",
                    RestoreFailureMessages.DB_CHECK_FAILED,
                    dryRunErr
                )
            }
            steps.add(RestoreStepRecord(10, "فحص الجداول الـ14 والمفاتيح الأجنبية", true, "14/14 جدول سليم ومطابق لـ Room مع 0 أخطاء علاقات"))

            // Step 11: Create mandatory pre-restore safety backup of current live DB
            val preBackup = createBackup(backupType = "PRE_RESTORE", actorUserId = actor.id).getOrElse { err ->
                return@withContext fail(
                    11,
                    "إنشاء نسخة أمان قبل الاستعادة",
                    RestoreFailureMessages.RESTORE_FAILED_KEPT_CURRENT,
                    formatTechnicalException(err)
                )
            }
            preRestorePath = preBackup.absolutePath
            steps.add(RestoreStepRecord(11, "إنشاء نسخة أمان قبل الاستعادة", true, preBackup.fileName))

            // Step 12: Flush WAL & cleanly close Room Database singleton (and any active handle)
            AlasalDatabase.closeAndResetInstance(extraInstance = this@BackupAndRestoreManager.database)
            steps.add(RestoreStepRecord(12, "تفريغ WAL وإغلاق اتصال Room", true, "تم إغلاق قاعدة البيانات بنظافة"))

            // Step 13: Copy current live DB safely to .rollback slot with fsync
            liveDbFile.parentFile?.mkdirs()
            if (liveDbFile.exists()) {
                rollbackFile.delete()
                copyFileWithSync(liveDbFile, rollbackFile)
            }
            steps.add(RestoreStepRecord(13, "تأمين نسخة التراجع الفورية", true, "تم حجز نسخة التراجع الآمن"))

            // Step 14: Clean stale SQLite sidecar files (-wal, -shm, -journal)
            deleteSqliteSidecarFiles(liveDbFile)
            deleteSqliteSidecarFiles(stagedDb)
            incomingFile.delete()
            steps.add(RestoreStepRecord(14, "حذف ملفات الحالة الجانبية (WAL/SHM/Journal)", true, "تم تنظيف الملفات الجانبية"))

            // Step 15: Safe atomic replacement of live DB with staged verified DB (unlink old inode + fsync)
            copyFileWithSync(stagedDb, incomingFile)
            if (liveDbFile.exists()) {
                liveDbFile.delete()
            }
            val renamed = incomingFile.renameTo(liveDbFile)
            if (!renamed) {
                copyFileWithSync(incomingFile, liveDbFile)
                incomingFile.delete()
            }
            deleteSqliteSidecarFiles(liveDbFile)
            check(liveDbFile.exists() && liveDbFile.length() == stagedDb.length()) {
                "عدم تطابق حجم الملف بعد النقل (المتوقع ${stagedDb.length()}، الفعلي ${liveDbFile.length()})"
            }
            swapped = true
            steps.add(RestoreStepRecord(15, "استبدال ملف قاعدة البيانات من النسخة المفحوصة", true, "تم النقل الآمن للمسار الأساسي"))

            // Step 16: Reopen fresh Room Database singleton & verify restored counts, records, and integrity
            if (simulatePostSwapFailureForTest) {
                error("محاكاة فشل ما بعد الاستبدال للتحقق من التراجع الآمن")
            }

            val reopenedDb = AlasalDatabase.reopenFreshInstance(context)
            val reopenCounts = reopenedDb.systemDao().getAllTableCountsOnce()
            reopenedDb.openHelper.writableDatabase.query("PRAGMA integrity_check").use { c ->
                val ok = c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
                check(ok) {
                    "فشل فحص PRAGMA integrity_check بعد إعادة الفتح: ${if (c.moveToFirst()) c.getString(0) else "empty"}"
                }
            }
            // Verify entity deserialization on live restored Room tables
            val verifiedSubsCount = reopenedDb.subscriberDao().getAllSubscribersOnce().size
            val verifiedInvoicesCount = reopenedDb.billingDao().getAllInvoicesOnce().size
            check(verifiedSubsCount == reopenCounts.subscribersCount && verifiedInvoicesCount == reopenCounts.invoicesCount) {
                "عدم تطابق أعداد السجلات بعد الفتح (المشتركين: $verifiedSubsCount/${reopenCounts.subscribersCount}، الفواتير: $verifiedInvoicesCount/${reopenCounts.invoicesCount})"
            }
            steps.add(
                RestoreStepRecord(
                    16,
                    "إعادة فتح Room والتحقق من الأعداد والأرصدة",
                    true,
                    "مشتركين: ${reopenCounts.subscribersCount}، فواتير: ${reopenCounts.invoicesCount}، تحصيلات: ${reopenCounts.collectionsCount}"
                )
            )

            // Step 17: Update internal references, log FK-safe audit entry, remove rollback file
            val reopenedRepo = AlasalRepository(reopenedDb)
            this@BackupAndRestoreManager.database = reopenedDb
            this@BackupAndRestoreManager.repository = reopenedRepo
            val resolvedActor = reopenedDb.systemDao().getUserById(actor.id)
                ?: reopenedDb.systemDao().getUserByUsername(actor.username)
            reopenedDb.systemDao().insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = resolvedActor?.id,
                    actorNameSnapshot = resolvedActor?.fullName ?: actor.fullName,
                    operationType = "RESTORE_18_STEPS_COMPLETED",
                    entityName = "backups",
                    detailsAr = "استعادة آمنة مكتملة (18 خطوة): ${reopenCounts.subscribersCount} مشترك، ${reopenCounts.invoicesCount} فاتورة، ${reopenCounts.collectionsCount} تحصيل"
                )
            )
            rollbackFile.delete()
            incomingFile.delete()
            steps.add(RestoreStepRecord(17, "تسجيل عملية الاستعادة وتنظيف الملفات المؤقتة", true, "تمت أرشفة التدقيق المالي وتنظيف المسار"))

            // Step 18: Reload application dependencies cleanly
            onReloadApplicationDependencies()
            refreshBackupsList()
            steps.add(RestoreStepRecord(18, "إعادة تهيئة مستودعات التطبيق وتحديث الجلسة", true, "تم تحديث الحالة الحية للتطبيق بنجاح"))

            val finalResult = RestoreExecutionResult(
                succeeded = true,
                message = "تمت الاستعادة الآمنة عبر 18 خطوة بنجاح (${reopenCounts.subscribersCount} مشترك، ${reopenCounts.invoicesCount} فاتورة، ${reopenCounts.collectionsCount} تحصيل)",
                steps = steps.toList(),
                preRestoreBackupPath = preRestorePath,
                restoredSubscribersCount = reopenCounts.subscribersCount,
                restoredInvoicesCount = reopenCounts.invoicesCount,
                restoredCollectionsCount = reopenCounts.collectionsCount
            )
            _lastRestoreResult.value = finalResult
            finalResult
        } catch (ex: Exception) {
            val failStepNo = (steps.size + 1).coerceIn(1, 18)
            val techDetail = formatTechnicalException(ex)
            Log.e(TAG, "Safe restore failed at step $failStepNo: $techDetail", ex)

            if (swapped) {
                runCatching {
                    AlasalDatabase.closeAndResetInstance(extraInstance = this@BackupAndRestoreManager.database)
                    deleteSqliteSidecarFiles(liveDbFile)
                    incomingFile.delete()
                    if (rollbackFile.exists()) {
                        if (liveDbFile.exists()) liveDbFile.delete()
                        copyFileWithSync(rollbackFile, liveDbFile)
                        rollbackFile.delete()
                    }
                    deleteSqliteSidecarFiles(liveDbFile)
                    val recovered = AlasalDatabase.reopenFreshInstance(context)
                    this@BackupAndRestoreManager.database = recovered
                    this@BackupAndRestoreManager.repository = AlasalRepository(recovered)
                    onReloadApplicationDependencies()
                }.onFailure { rbEx ->
                    Log.e(TAG, "Rollback recovery encountered error: ${formatTechnicalException(rbEx)}", rbEx)
                }
            }
            val errMsg = when (ex.message) {
                RestoreFailureMessages.CORRUPT_BACKUP,
                RestoreFailureMessages.UNSUPPORTED_VERSION,
                RestoreFailureMessages.UNREADABLE_FILE,
                RestoreFailureMessages.DB_CHECK_FAILED,
                RestoreFailureMessages.INSUFFICIENT_SPACE -> ex.message!!
                else -> RestoreFailureMessages.RESTORE_FAILED_KEPT_CURRENT
            }
            fail(failStepNo, "التراجع الآمن عن الاستعادة", errMsg, techDetail)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private enum class DetectedBackupType(val labelAr: String) {
        ALASAL_ZIP("حزمة محطة العسل (.alasalbak / ZIP)"),
        RAW_SQLITE("قاعدة بيانات SQLite (.db)"),
        JSON_LEGACY("نسخة JSON متوافقة"),
        UNKNOWN("غير معروف")
    }

    private data class StagedCandidateResult(
        val stagedDbFile: File? = null,
        val dbVersion: Int = 0,
        val sha256: String = "",
        val manifestShaFailed: Boolean = false,
        val errorMessage: String? = null
    )

    private suspend fun verifyAndStageCandidate(file: File, stagingDir: File): BackupVerificationResult {
        val type = detectBackupFileType(file)
        if (type == DetectedBackupType.UNKNOWN) {
            return BackupVerificationResult(isValid = false, errorMessage = RestoreFailureMessages.CORRUPT_BACKUP)
        }
        val staged = stageCandidateToTempDb(file, type, stagingDir)
        if (staged.errorMessage != null || staged.stagedDbFile == null) {
            return BackupVerificationResult(isValid = false, errorMessage = staged.errorMessage ?: RestoreFailureMessages.CORRUPT_BACKUP)
        }
        if (staged.manifestShaFailed) {
            return BackupVerificationResult(isValid = false, errorMessage = RestoreFailureMessages.CORRUPT_BACKUP)
        }
        val dbFile = staged.stagedDbFile
        val ver = readSqliteUserVersion(dbFile)
        if (ver <= 0 || ver > AlasalDatabase.CURRENT_VERSION) {
            return BackupVerificationResult(isValid = false, errorMessage = RestoreFailureMessages.UNSUPPORTED_VERSION)
        }
        val expectedHash = readExpectedRoomIdentityHash()
        if (ver < AlasalDatabase.CURRENT_VERSION && upgradeStagedDatabaseToCurrent(dbFile, ver) != null) {
            return BackupVerificationResult(isValid = false, errorMessage = RestoreFailureMessages.DB_CHECK_FAILED)
        }
        if (!reconcileStagedV6SchemaAndIdentity(dbFile, expectedHash).succeeded) {
            return BackupVerificationResult(isValid = false, errorMessage = RestoreFailureMessages.DB_CHECK_FAILED)
        }
        if (!checkSqliteIntegrity(dbFile) || !checkTablesAndForeignKeys(dbFile) || verifyStagedDbWithRoom(dbFile) != null) {
            return BackupVerificationResult(isValid = false, errorMessage = RestoreFailureMessages.DB_CHECK_FAILED)
        }
        return BackupVerificationResult(
            isValid = true,
            dbVersion = ver,
            sha256 = staged.sha256,
            tableCount = REQUIRED_14_TABLES.size,
            fileSizeBytes = file.length(),
            createdAt = file.lastModified()
        )
    }

    private fun detectBackupFileType(file: File): DetectedBackupType {
        val header = ByteArray(16)
        val read = runCatching {
            FileInputStream(file).use { it.read(header) }
        }.getOrDefault(0)
        if (read <= 0) return DetectedBackupType.UNKNOWN

        // PK\x03\x04 ZIP header
        if (read >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
            return DetectedBackupType.ALASAL_ZIP
        }
        // "SQLite format 3\0"
        val sqliteMagic = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        if (read >= sqliteMagic.size && header.copyOfRange(0, sqliteMagic.size).contentEquals(sqliteMagic)) {
            return DetectedBackupType.RAW_SQLITE
        }
        // Leading JSON '{' after optional UTF-8 BOM / whitespace
        val textStart = runCatching {
            FileInputStream(file).bufferedReader(Charsets.UTF_8).use { r ->
                val buf = CharArray(32)
                val n = r.read(buf)
                if (n > 0) String(buf, 0, n).trimStart('\uFEFF', ' ', '\t', '\r', '\n') else ""
            }
        }.getOrDefault("")
        if (textStart.startsWith("{")) {
            return DetectedBackupType.JSON_LEGACY
        }
        return DetectedBackupType.UNKNOWN
    }

    private suspend fun stageCandidateToTempDb(
        sourceFile: File,
        type: DetectedBackupType,
        stagingDir: File
    ): StagedCandidateResult {
        return when (type) {
            DetectedBackupType.ALASAL_ZIP -> {
                try {
                    ZipFile(sourceFile).use { zip ->
                        val manifestEntry = zip.getEntry(ENTRY_MANIFEST)
                            ?: return StagedCandidateResult(errorMessage = RestoreFailureMessages.CORRUPT_BACKUP)
                        val dbEntry = zip.getEntry(ENTRY_DB)
                            ?: return StagedCandidateResult(errorMessage = RestoreFailureMessages.CORRUPT_BACKUP)

                        val manifestJson = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use {
                            JSONObject(it.readText())
                        }
                        if (manifestJson.optString("projectCode") != "Alasal2027") {
                            return StagedCandidateResult(errorMessage = RestoreFailureMessages.CORRUPT_BACKUP)
                        }
                        val dbVersion = manifestJson.optInt("dbVersion", 0)
                        if (dbVersion <= 0 || dbVersion > AlasalDatabase.CURRENT_VERSION) {
                            return StagedCandidateResult(errorMessage = RestoreFailureMessages.UNSUPPORTED_VERSION)
                        }

                        val stagedDb = File(stagingDir, ENTRY_DB)
                        zip.getInputStream(dbEntry).use { inp ->
                            FileOutputStream(stagedDb).use { out -> inp.copyTo(out) }
                        }
                        val actualSha = computeFileSha256(stagedDb)
                        val expectedSha = manifestJson.optString("dbSha256", "")
                        if (expectedSha.isBlank() || expectedSha != actualSha) {
                            return StagedCandidateResult(
                                stagedDbFile = stagedDb,
                                manifestShaFailed = true,
                                errorMessage = RestoreFailureMessages.CORRUPT_BACKUP
                            )
                        }
                        StagedCandidateResult(
                            stagedDbFile = stagedDb,
                            dbVersion = dbVersion,
                            sha256 = actualSha
                        )
                    }
                } catch (_: Exception) {
                    StagedCandidateResult(errorMessage = RestoreFailureMessages.CORRUPT_BACKUP)
                }
            }

            DetectedBackupType.RAW_SQLITE -> {
                val stagedDb = File(stagingDir, ENTRY_DB)
                sourceFile.copyTo(stagedDb, overwrite = true)
                val sha = computeFileSha256(stagedDb)
                StagedCandidateResult(
                    stagedDbFile = stagedDb,
                    dbVersion = readSqliteUserVersion(stagedDb),
                    sha256 = sha
                )
            }

            DetectedBackupType.JSON_LEGACY -> {
                try {
                    val jsonText = sourceFile.readText(Charsets.UTF_8)
                    val root = JSONObject(jsonText)
                    val code = root.optString("projectCode", root.optString("stationCode", ""))
                    if (code != "Alasal2027") {
                        return StagedCandidateResult(errorMessage = RestoreFailureMessages.CORRUPT_BACKUP)
                    }
                    val ver = root.optInt("schemaVersion", root.optInt("protocolVersion", 0))
                    if (ver <= 0 || ver > AlasalDatabase.CURRENT_VERSION) {
                        return StagedCandidateResult(errorMessage = RestoreFailureMessages.UNSUPPORTED_VERSION)
                    }

                    val stagedDbFile = File(stagingDir, ENTRY_DB)
                    val tempRoom = Room.databaseBuilder(context, AlasalDatabase::class.java, stagedDbFile.absolutePath)
                        .addMigrations(
                            AlasalDatabase.MIGRATION_1_2,
                            AlasalDatabase.MIGRATION_2_3,
                            AlasalDatabase.MIGRATION_3_4,
                            AlasalDatabase.MIGRATION_4_5,
                            AlasalDatabase.MIGRATION_5_6
                        )
                        .build()
                    try {
                        val tempCore = AlasalRepository(tempRoom)
                        tempCore.ensureBaselineSeeded()
                        if (root.has("users") && root.has("subscribers") && root.has("invoices")) {
                            val env = StationSyncEnvelope.fromJsonString(jsonText)
                            val tempSync = StationSyncRepository(tempRoom, tempCore)
                            tempSync.mergeIncomingEnvelope(env).getOrThrow()
                        } else {
                            // Legacy Phase 1 SAF JSON (tariffs, settings, subscribers)
                            val subsArr = root.optJSONArray("subscribers")
                            if (subsArr != null) {
                                for (i in 0 until subsArr.length()) {
                                    val o = subsArr.getJSONObject(i)
                                    tempRoom.subscriberDao().insert(
                                        com.example.data.local.entity.SubscriberEntity(
                                            subscriberCode = o.getString("code"),
                                            fullName = o.getString("fullName"),
                                            phoneNumber = o.optString("phone", ""),
                                            meterNumber = o.optString("meterNumber", "M-${i + 1}"),
                                            address = o.optString("address", ""),
                                            subscriptionType = o.optString("subscriptionType", "منزلي"),
                                            currentArrearsAmount = o.optDouble("arrears", 0.0),
                                            currentCreditBalance = o.optDouble("credit", 0.0)
                                        )
                                    )
                                }
                            }
                        }
                        flushWalCheckpoint(tempRoom)
                    } finally {
                        tempRoom.close()
                    }
                    deleteSqliteSidecarFiles(stagedDbFile)
                    StagedCandidateResult(
                        stagedDbFile = stagedDbFile,
                        dbVersion = AlasalDatabase.CURRENT_VERSION,
                        sha256 = computeFileSha256(stagedDbFile)
                    )
                } catch (_: Exception) {
                    StagedCandidateResult(errorMessage = RestoreFailureMessages.CORRUPT_BACKUP)
                }
            }

            DetectedBackupType.UNKNOWN -> StagedCandidateResult(errorMessage = RestoreFailureMessages.CORRUPT_BACKUP)
        }
    }

    private fun readSqliteUserVersion(dbFile: File): Int {
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlDb ->
                sqlDb.version
            }
        }.getOrDefault(0)
    }

    private data class ReconcileOutcome(
        val succeeded: Boolean,
        val schemaAdjusted: Boolean = false,
        val identitySynced: Boolean = false,
        val errorDetail: String? = null
    )

    private fun readExpectedRoomIdentityHash(): String? {
        // 1. Read directly from the currently open live Room DB if available
        val fromLive = runCatching {
            val db = database.openHelper.readableDatabase
            db.query("SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1").use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
        if (!fromLive.isNullOrBlank()) return fromLive

        // 2. Fallback: read from an ephemeral in-memory AlasalDatabase compiled with the current schema
        return runCatching {
            val memDb = Room.inMemoryDatabaseBuilder(context.applicationContext, AlasalDatabase::class.java).build()
            try {
                memDb.openHelper.writableDatabase
                    .query("SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1")
                    .use { c ->
                        if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
                    }
            } finally {
                memDb.close()
            }
        }.getOrNull()
    }

    private fun upgradeStagedDatabaseToCurrent(dbFile: File, startVersion: Int): String? {
        return try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbFile.absolutePath)
                    .callback(object : SupportSQLiteOpenHelper.Callback(AlasalDatabase.CURRENT_VERSION) {
                        override fun onCreate(db: SupportSQLiteDatabase) {}
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                            var v = oldVersion
                            if (v < 2) {
                                AlasalDatabase.MIGRATION_1_2.migrate(db)
                                v = 2
                            }
                            if (v < 3) {
                                AlasalDatabase.MIGRATION_2_3.migrate(db)
                                v = 3
                            }
                            if (v < 4) {
                                AlasalDatabase.MIGRATION_3_4.migrate(db)
                                v = 4
                            }
                            if (v < 5) {
                                AlasalDatabase.MIGRATION_4_5.migrate(db)
                                v = 5
                            }
                            if (v < 6) {
                                AlasalDatabase.MIGRATION_5_6.migrate(db)
                            }
                        }
                    })
                    .build()
            )
            helper.use {
                val wdb = it.writableDatabase
                wdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c -> c.moveToFirst() }
            }
            deleteSqliteSidecarFiles(dbFile)
            val upgradedVersion = readSqliteUserVersion(dbFile)
            if (upgradedVersion == AlasalDatabase.CURRENT_VERSION && startVersion < AlasalDatabase.CURRENT_VERSION) {
                null
            } else {
                "تعذر تحديث إصدار SQLite من v$startVersion إلى v${AlasalDatabase.CURRENT_VERSION} (الحالي: v$upgradedVersion)"
            }
        } catch (ex: Exception) {
            formatTechnicalException(ex)
        }
    }

    /**
     * Reconciles any earlier v6 schema variant (e.g., backups created before reading_half or
     * initial_meter_reading were unified into v6) and synchronizes room_master_table's identity_hash
     * inside the isolated staging DB BEFORE touching the live database.
     */
    private fun reconcileStagedV6SchemaAndIdentity(
        dbFile: File,
        expectedIdentityHash: String?
    ): ReconcileOutcome {
        return try {
            // Check whether core tables exist first (if not, let Step 10 report missing tables cleanly)
            val existingTables = mutableSetOf<String>()
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { ro ->
                ro.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                    while (c.moveToNext()) existingTables.add(c.getString(0))
                }
            }
            if (!existingTables.containsAll(REQUIRED_14_TABLES)) {
                return ReconcileOutcome(succeeded = true, schemaAdjusted = false, identitySynced = false)
            }

            var schemaAdjusted = false
            var identitySynced = false

            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbFile.absolutePath)
                    .callback(object : SupportSQLiteOpenHelper.Callback(AlasalDatabase.CURRENT_VERSION) {
                        override fun onCreate(db: SupportSQLiteDatabase) {}
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )
            helper.use {
                val wdb = it.writableDatabase
                // Foreign keys MUST be disabled outside a transaction before any table rebuild/rename
                wdb.execSQL("PRAGMA foreign_keys = OFF")
                wdb.beginTransaction()
                try {
                    schemaAdjusted = AlasalDatabase.ensureV6SchemaCompatibility(wdb)

                    if (!expectedIdentityHash.isNullOrBlank()) {
                        wdb.execSQL(
                            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
                        )
                        val currentHash = wdb.query(
                            "SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1"
                        ).use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                        if (currentHash != expectedIdentityHash) {
                            wdb.execSQL(
                                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                                arrayOf<Any>(expectedIdentityHash)
                            )
                            identitySynced = true
                        }
                    }

                    wdb.version = AlasalDatabase.CURRENT_VERSION
                    wdb.setTransactionSuccessful()
                } finally {
                    wdb.endTransaction()
                    wdb.execSQL("PRAGMA foreign_keys = ON")
                }
                wdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c -> c.moveToFirst() }
            }
            deleteSqliteSidecarFiles(dbFile)
            ReconcileOutcome(
                succeeded = true,
                schemaAdjusted = schemaAdjusted,
                identitySynced = identitySynced
            )
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to reconcile staged v6 schema: ${formatTechnicalException(ex)}", ex)
            ReconcileOutcome(succeeded = false, errorDetail = formatTechnicalException(ex))
        }
    }

    /**
     * Opens the staged database through an isolated temporary Room instance BEFORE swapping the live file,
     * guaranteeing Room identity_hash validation and entity queries will succeed at Step 16.
     */
    private suspend fun verifyStagedDbWithRoom(stagedDb: File): String? {
        var tempRoom: AlasalDatabase? = null
        return try {
            tempRoom = Room.databaseBuilder(
                context.applicationContext,
                AlasalDatabase::class.java,
                stagedDb.absolutePath
            )
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(
                    AlasalDatabase.MIGRATION_1_2,
                    AlasalDatabase.MIGRATION_2_3,
                    AlasalDatabase.MIGRATION_3_4,
                    AlasalDatabase.MIGRATION_4_5,
                    AlasalDatabase.MIGRATION_5_6
                )
                .build()
            tempRoom.systemDao().getAllTableCountsOnce()
            tempRoom.subscriberDao().getAllSubscribersOnce()
            tempRoom.billingDao().getAllInvoicesOnce()
            tempRoom.openHelper.writableDatabase.query("PRAGMA integrity_check").use { c ->
                val ok = c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
                check(ok) { "PRAGMA integrity_check returned non-ok during staging dry-run" }
            }
            flushWalCheckpoint(tempRoom)
            null
        } catch (ex: Exception) {
            Log.e(TAG, "Staging Room dry-run verification failed: ${formatTechnicalException(ex)}", ex)
            formatTechnicalException(ex)
        } finally {
            runCatching { tempRoom?.close() }
            deleteSqliteSidecarFiles(stagedDb)
        }
    }

    private fun copyFileWithSync(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(destination, false).use { output ->
                val buffer = ByteArray(16 * 1024)
                var read = input.read(buffer)
                while (read != -1) {
                    output.write(buffer, 0, read)
                    read = input.read(buffer)
                }
                output.flush()
                runCatching { output.fd.sync() }
            }
        }
    }

    private fun formatTechnicalException(ex: Throwable): String {
        val base = "${ex::class.java.simpleName}: ${ex.message ?: "خطأ غير معروف"}"
        val rootCause = generateSequence(ex.cause) { it.cause }.lastOrNull()
        return if (rootCause != null && rootCause !== ex) {
            "$base | السبب: ${rootCause::class.java.simpleName}: ${rootCause.message ?: "بدون تفاصيل"}"
        } else {
            base
        }
    }

    private fun checkSqliteIntegrity(dbFile: File): Boolean {
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlDb ->
                sqlDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    }

    private fun checkTablesAndForeignKeys(dbFile: File): Boolean {
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlDb ->
                val tables = mutableSetOf<String>()
                sqlDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        tables.add(cursor.getString(0))
                    }
                }
                if (!tables.containsAll(REQUIRED_14_TABLES)) {
                    return@runCatching false
                }
                sqlDb.rawQuery("PRAGMA foreign_key_check", null).use { fkCursor ->
                    !fkCursor.moveToFirst()
                }
            }
        }.getOrDefault(false)
    }

    private fun flushWalCheckpoint(db: AlasalDatabase) {
        runCatching {
            if (db.isOpen) {
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c ->
                    c.moveToFirst()
                }
            }
        }
    }

    private fun deleteSqliteSidecarFiles(dbFile: File) {
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(dbFile.parentFile, "${dbFile.name}$suffix").delete()
        }
    }

    private fun inspectBackupFileSummary(file: File): BackupRecordInfo? {
        return runCatching {
            if (file.name.endsWith(BACKUP_EXTENSION) || file.name.endsWith(".zip")) {
                ZipFile(file).use { zip ->
                    val manifestEntry = zip.getEntry(ENTRY_MANIFEST) ?: return@runCatching null
                    val obj = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use {
                        JSONObject(it.readText())
                    }
                    val counts = obj.optJSONObject("tableCounts")
                    BackupRecordInfo(
                        id = file.name,
                        fileName = file.name,
                        absolutePath = file.absolutePath,
                        createdAt = obj.optLong("createdAt", file.lastModified()),
                        fileSizeBytes = file.length(),
                        dbVersion = obj.optInt("dbVersion", AlasalDatabase.CURRENT_VERSION),
                        backupType = obj.optString("backupType", "MANUAL"),
                        isVerified = obj.optString("dbSha256", "").isNotBlank(),
                        sha256 = obj.optString("dbSha256", ""),
                        subscribersCount = counts?.optInt("subscribers", 0) ?: 0,
                        invoicesCount = counts?.optInt("invoices", 0) ?: 0,
                        collectionsCount = counts?.optInt("collections", 0) ?: 0
                    )
                }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun pruneOldBackups(maxRegularBackups: Int = 20) {
        val regular = backupsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(BACKUP_EXTENSION) && !it.name.startsWith("alasal_pre_restore") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        if (regular.size > maxRegularBackups) {
            regular.drop(maxRegularBackups).forEach { it.delete() }
        }
    }

    private fun copyUriToTempFile(resolver: ContentResolver, uri: Uri, dest: File): Boolean {
        return runCatching {
            resolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(dest).use { out -> inp.copyTo(out) }
            } ?: return false
            dest.exists() && dest.length() > 0L
        }.getOrDefault(false)
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var read = fis.read(buf)
            while (read != -1) {
                digest.update(buf, 0, read)
                read = fis.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes بايت"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f كيلوبايت", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f ميجابايت", bytes / (1024.0 * 1024.0))
        }
    }
}
