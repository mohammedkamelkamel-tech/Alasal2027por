package com.example.service

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.AlasalApplication
import com.example.data.local.database.AlasalDatabase
import com.example.data.repository.AlasalRepository
import java.util.concurrent.TimeUnit

class DailyAutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefsRepo = (applicationContext as? AlasalApplication)?.themePreferences
                ?: ThemePreferencesRepository(applicationContext)
            val sessionPrefs = prefsRepo.getBackupAndSessionPrefsOnce()
            if (!sessionPrefs.dailyAutoBackupEnabled && !inputData.getBoolean(KEY_FORCE_RUN, false)) {
                return Result.success(
                    Data.Builder()
                        .putBoolean(KEY_BACKUP_SKIPPED, true)
                        .build()
                )
            }

            val app = applicationContext as? AlasalApplication
            app?.ensureActiveDatabaseDependencies()
            val manager = app?.backupAndRestoreManager ?: run {
                val db = AlasalDatabase.getInstance(applicationContext)
                BackupAndRestoreManager(applicationContext, db, AlasalRepository(db))
            }

            val customUri = sessionPrefs.customBackupFolderUri?.let { Uri.parse(it) }
            val backupInfo = manager.createBackup(
                backupType = "AUTO_DAILY",
                customTreeUri = customUri
            ).getOrThrow()

            prefsRepo.recordLastAutoBackupAt(backupInfo.createdAt)

            Result.success(
                Data.Builder()
                    .putString(KEY_BACKUP_FILE_NAME, backupInfo.fileName)
                    .putString(KEY_BACKUP_FILE_PATH, backupInfo.absolutePath)
                    .putLong(KEY_BACKUP_SIZE_BYTES, backupInfo.fileSizeBytes)
                    .putBoolean(KEY_BACKUP_VERIFIED, backupInfo.isVerified)
                    .build()
            )
        } catch (ex: Exception) {
            if (runAttemptCount < 2) Result.retry()
            else Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, ex.message ?: "فشل النسخ الاحتياطي التلقائي")
                    .build()
            )
        }
    }

    companion object {
        const val WORK_NAME_DAILY = "alasal_daily_auto_backup_2027"
        const val KEY_FORCE_RUN = "force_run"
        const val KEY_BACKUP_SKIPPED = "backup_skipped"
        const val KEY_BACKUP_FILE_NAME = "backup_file_name"
        const val KEY_BACKUP_FILE_PATH = "backup_file_path"
        const val KEY_BACKUP_SIZE_BYTES = "backup_size_bytes"
        const val KEY_BACKUP_VERIFIED = "backup_verified"
        const val KEY_ERROR_MESSAGE = "error_message"

        fun scheduleDailyBackup(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyAutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediateBackup(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailyAutoBackupWorker>()
                .setInputData(Data.Builder().putBoolean(KEY_FORCE_RUN, true).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancelDailyBackup(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DAILY)
        }
    }
}
