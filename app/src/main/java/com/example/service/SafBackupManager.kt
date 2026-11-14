package com.example.service

import android.content.Context
import android.net.Uri
import com.example.data.repository.AlasalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Storage Access Framework (SAF) local backup service.
 * Operates completely offline with zero broad storage permissions.
 */
class SafBackupManager(
    private val context: Context,
    private val repository: AlasalRepository
) {

    suspend fun exportFoundationBackupToUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val counts = repository.getAllTableCountsOnce()
            val tariffs = repository.observeTariffs().first()
            val settings = repository.observeSettings().first()
            val subscribers = repository.observeSubscribers().first()

            val root = JSONObject().apply {
                put("appName", "محطة العسل 2027")
                put("projectCode", "Alasal2027")
                put("schemaVersion", 1)
                put("exportedAt", System.currentTimeMillis())
                put("tableSummary", JSONObject().apply {
                    put("subscribers", counts.subscribersCount)
                    put("invoices", counts.invoicesCount)
                    put("collections", counts.collectionsCount)
                    put("receipts", counts.receiptsCount)
                    put("kwh_tariffs", counts.kwhTariffsCount)
                    put("tariff_change_logs", counts.tariffChangeLogsCount)
                    put("arrears", counts.arrearsCount)
                    put("credit_balances", counts.creditBalancesCount)
                    put("meter_readings", counts.meterReadingsCount)
                    put("reminders", counts.remindersCount)
                    put("users", counts.usersCount)
                    put("permissions", counts.permissionsCount)
                    put("app_settings", counts.appSettingsCount)
                    put("financial_audit_logs", counts.financialAuditLogsCount)
                })
                put("tariffs", JSONArray().apply {
                    tariffs.forEach { t ->
                        put(JSONObject().apply {
                            put("tariffCode", t.tariffCode)
                            put("title", t.title)
                            put("subscriptionType", t.subscriptionType)
                            put("pricePerKwh", t.pricePerKwh)
                            put("fixedFee", t.fixedFee)
                            put("isActive", t.isActive)
                        })
                    }
                })
                put("settings", JSONArray().apply {
                    settings.forEach { s ->
                        put(JSONObject().apply {
                            put("key", s.settingKey)
                            put("value", s.settingValue)
                            put("category", s.category)
                        })
                    }
                })
                put("subscribers", JSONArray().apply {
                    subscribers.forEach { sub ->
                        put(JSONObject().apply {
                            put("code", sub.subscriberCode)
                            put("fullName", sub.fullName)
                            put("meterNumber", sub.meterNumber)
                            put("arrears", sub.currentArrearsAmount)
                            put("credit", sub.currentCreditBalance)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("تعذر فتح مسار الحفظ المختار")

            repository.recordAuditLog(
                operationType = "BACKUP_EXPORTED",
                entityName = "saf_backup",
                detailsAr = "تصدير نسخة احتياطية محلية بصيغة JSON عبر نظام SAF"
            )

            "تم تصدير النسخة الاحتياطية بنجاح (${tariffs.size} تعرفات، ${settings.size} إعدادات)"
        }
    }
}
