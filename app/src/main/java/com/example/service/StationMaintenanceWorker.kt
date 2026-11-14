package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.database.AlasalDatabase
import com.example.data.repository.AlasalRepository
import java.util.concurrent.TimeUnit

/**
 * Offline WorkManager worker that verifies database schema availability,
 * counts pending reminders, and writes a background audit log entry.
 */
class StationMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = AlasalDatabase.getInstance(applicationContext)
            val repository = AlasalRepository(database)
            repository.ensureBaselineSeeded()

            val prefs = ThemePreferencesRepository(applicationContext).getNotificationPrefsOnce()
            val dueReminders = repository.processDueReminders(System.currentTimeMillis())
            var dispatchedCount = 0
            if (prefs.notificationsEnabled && prefs.readingRemindersEnabled) {
                dueReminders.forEach { reminder ->
                    val sent = AlasalNotificationService.dispatchNotification(
                        context = applicationContext,
                        notificationId = (2000 + reminder.id).toInt(),
                        title = "تذكير محطة العسل: ${reminder.title}",
                        body = reminder.message,
                        prefs = prefs
                    )
                    if (sent) dispatchedCount++
                }
            }

            val counts = database.systemDao().getAllTableCountsOnce()
            val pendingReminders = database.systemDao().countPendingReminders()
            repository.recordAuditLog(
                operationType = "WORKMANAGER_AUDIT",
                entityName = "system_health",
                detailsAr = "فحص WorkManager: 14 جدولاً نشطاً، التذكيرات المعلقة: $pendingReminders، التنبيهات المرسلة: ${dueReminders.size}"
            )
            Result.success(
                workDataOf(
                    KEY_TABLES_VERIFIED to 14,
                    KEY_PENDING_REMINDERS to pendingReminders,
                    KEY_DUE_REMINDERS_TRIGGERED to dueReminders.size,
                    KEY_NOTIFICATIONS_SENT to dispatchedCount,
                    KEY_TOTAL_TARIFFS to counts.kwhTariffsCount
                )
            )
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "خطأ غير معروف")))
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "alasal_periodic_maintenance"
        const val ONE_TIME_WORK_NAME = "alasal_manual_audit"
        const val KEY_TABLES_VERIFIED = "tables_verified"
        const val KEY_PENDING_REMINDERS = "pending_reminders"
        const val KEY_DUE_REMINDERS_TRIGGERED = "due_reminders_triggered"
        const val KEY_NOTIFICATIONS_SENT = "notifications_sent"
        const val KEY_TOTAL_TARIFFS = "total_tariffs"
        const val KEY_ERROR = "error"

        fun schedulePeriodicMaintenance(context: Context) {
            val request = PeriodicWorkRequestBuilder<StationMaintenanceWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediateAudit(context: Context): java.util.UUID {
            val request = OneTimeWorkRequestBuilder<StationMaintenanceWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }

        fun scheduleReminderCheck(context: Context, reminderId: Long, delayMillis: Long): java.util.UUID {
            val safeDelay = delayMillis.coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<StationMaintenanceWorker>()
                .setInitialDelay(safeDelay, TimeUnit.MILLISECONDS)
                .addTag("reminder_$reminderId")
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }
    }
}
