package com.example.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.WeakHashMap

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class NotificationAndPrintPrefs(
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val invoiceNotificationsEnabled: Boolean = true,
    val collectionNotificationsEnabled: Boolean = true,
    val readingRemindersEnabled: Boolean = true,
    val thermalPaperWidthMm: Int = 80,
    val printerConnectionType: String = "SYSTEM",
    val printerDeviceName: String = "",
    val printerDeviceAddress: String = "",
    val stationPhone: String = "770002027",
    val stationAddress: String = "محطة العسل لتوليد وتوزيع الكهرباء",
    val defaultCountryCode: String = "967",
    val bulkInvoiceSmsEnabled: Boolean = false
)

data class BackupAndSessionPrefs(
    val activeUserId: Long? = null,
    val dailyAutoBackupEnabled: Boolean = true,
    val customBackupFolderUri: String? = null,
    val lastAutoBackupAt: Long = 0L,
    val appLockEnabled: Boolean = true,
    val inactivityLockTimeoutSeconds: Int = 120
)

private val themeDataStoreCacheLock = Any()
private val themeDataStoreByApp = WeakHashMap<Context, DataStore<Preferences>>()

private val Context.themeDataStore: DataStore<Preferences>
    get() {
        val appCtx = applicationContext
        return synchronized(themeDataStoreCacheLock) {
            themeDataStoreByApp.getOrPut(appCtx) {
                PreferenceDataStoreFactory.create(
                    produceFile = { appCtx.preferencesDataStoreFile("alasal_theme_prefs") }
                )
            }
        }
    }

class ThemePreferencesRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val soundEnabledKey = booleanPreferencesKey("notification_sound_enabled")
    private val invoiceNotifKey = booleanPreferencesKey("invoice_notifications_enabled")
    private val collectionNotifKey = booleanPreferencesKey("collection_notifications_enabled")
    private val reminderNotifKey = booleanPreferencesKey("reading_reminders_enabled")
    private val thermalWidthKey = intPreferencesKey("thermal_paper_width_mm")
    private val printerConnectionTypeKey = stringPreferencesKey("printer_connection_type")
    private val printerDeviceNameKey = stringPreferencesKey("printer_device_name")
    private val printerDeviceAddressKey = stringPreferencesKey("printer_device_address")
    private val stationPhoneKey = stringPreferencesKey("station_phone")
    private val stationAddressKey = stringPreferencesKey("station_address")
    private val defaultCountryCodeKey = stringPreferencesKey("default_country_code")
    private val bulkInvoiceSmsEnabledKey = booleanPreferencesKey("bulk_invoice_sms_enabled")

    private val activeUserIdKey = longPreferencesKey("active_session_user_id")
    private val dailyAutoBackupKey = booleanPreferencesKey("daily_auto_backup_enabled")
    private val customBackupFolderUriKey = stringPreferencesKey("custom_backup_folder_uri")
    private val lastAutoBackupAtKey = longPreferencesKey("last_auto_backup_at")
    private val appLockEnabledKey = booleanPreferencesKey("app_lock_enabled")
    private val inactivityLockTimeoutKey = intPreferencesKey("inactivity_lock_timeout_seconds")

    val themeMode: Flow<AppThemeMode> = context.themeDataStore.data.map { prefs ->
        when (prefs[themeModeKey]) {
            AppThemeMode.LIGHT.name -> AppThemeMode.LIGHT
            AppThemeMode.DARK.name -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    }

    val notificationAndPrintPrefs: Flow<NotificationAndPrintPrefs> = context.themeDataStore.data.map { prefs ->
        NotificationAndPrintPrefs(
            notificationsEnabled = prefs[notificationsEnabledKey] ?: true,
            soundEnabled = prefs[soundEnabledKey] ?: true,
            invoiceNotificationsEnabled = prefs[invoiceNotifKey] ?: true,
            collectionNotificationsEnabled = prefs[collectionNotifKey] ?: true,
            readingRemindersEnabled = prefs[reminderNotifKey] ?: true,
            thermalPaperWidthMm = when (prefs[thermalWidthKey]) { 58 -> 58; 210 -> 210; else -> 80 },
            printerConnectionType = prefs[printerConnectionTypeKey] ?: "SYSTEM",
            printerDeviceName = prefs[printerDeviceNameKey] ?: "",
            printerDeviceAddress = prefs[printerDeviceAddressKey] ?: "",
            stationPhone = prefs[stationPhoneKey] ?: "770002027",
            stationAddress = prefs[stationAddressKey] ?: "محطة العسل لتوليد وتوزيع الكهرباء",
            defaultCountryCode = prefs[defaultCountryCodeKey]?.filter { it.isDigit() }?.ifBlank { "967" } ?: "967",
            bulkInvoiceSmsEnabled = prefs[bulkInvoiceSmsEnabledKey] ?: false
        )
    }

    val backupAndSessionPrefs: Flow<BackupAndSessionPrefs> = context.themeDataStore.data.map { prefs ->
        val uid = prefs[activeUserIdKey]
        BackupAndSessionPrefs(
            activeUserId = uid?.takeIf { it > 0L },
            dailyAutoBackupEnabled = prefs[dailyAutoBackupKey] ?: true,
            customBackupFolderUri = prefs[customBackupFolderUriKey]?.takeIf { it.isNotBlank() },
            lastAutoBackupAt = prefs[lastAutoBackupAtKey] ?: 0L,
            appLockEnabled = prefs[appLockEnabledKey] ?: true,
            inactivityLockTimeoutSeconds = (prefs[inactivityLockTimeoutKey] ?: 120).coerceAtLeast(0)
        )
    }

    suspend fun getNotificationPrefsOnce(): NotificationAndPrintPrefs =
        notificationAndPrintPrefs.first()

    suspend fun getBackupAndSessionPrefsOnce(): BackupAndSessionPrefs =
        backupAndSessionPrefs.first()

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setActiveUserId(userId: Long?) {
        context.themeDataStore.edit { prefs ->
            if (userId != null && userId > 0L) {
                prefs[activeUserIdKey] = userId
            } else {
                prefs.remove(activeUserIdKey)
            }
        }
    }

    suspend fun setDailyAutoBackupEnabled(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[dailyAutoBackupKey] = enabled
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[appLockEnabledKey] = enabled
        }
    }

    suspend fun setInactivityLockTimeoutSeconds(seconds: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[inactivityLockTimeoutKey] = seconds.coerceAtLeast(0)
        }
    }

    suspend fun setCustomBackupFolderUri(uriString: String?) {
        context.themeDataStore.edit { prefs ->
            if (!uriString.isNullOrBlank()) {
                prefs[customBackupFolderUriKey] = uriString
            } else {
                prefs.remove(customBackupFolderUriKey)
            }
        }
    }

    suspend fun recordLastAutoBackupAt(timestamp: Long) {
        context.themeDataStore.edit { prefs ->
            prefs[lastAutoBackupAtKey] = timestamp
        }
    }

    suspend fun updateNotificationPrefs(transform: (NotificationAndPrintPrefs) -> NotificationAndPrintPrefs) {
        context.themeDataStore.edit { prefs ->
            val current = NotificationAndPrintPrefs(
                notificationsEnabled = prefs[notificationsEnabledKey] ?: true,
                soundEnabled = prefs[soundEnabledKey] ?: true,
                invoiceNotificationsEnabled = prefs[invoiceNotifKey] ?: true,
                collectionNotificationsEnabled = prefs[collectionNotifKey] ?: true,
                readingRemindersEnabled = prefs[reminderNotifKey] ?: true,
                thermalPaperWidthMm = when (prefs[thermalWidthKey]) { 58 -> 58; 210 -> 210; else -> 80 },
                printerConnectionType = prefs[printerConnectionTypeKey] ?: "SYSTEM",
                printerDeviceName = prefs[printerDeviceNameKey] ?: "",
                printerDeviceAddress = prefs[printerDeviceAddressKey] ?: "",
                stationPhone = prefs[stationPhoneKey] ?: "770002027",
                stationAddress = prefs[stationAddressKey] ?: "محطة العسل لتوليد وتوزيع الكهرباء",
                defaultCountryCode = prefs[defaultCountryCodeKey]?.filter { it.isDigit() }?.ifBlank { "967" } ?: "967",
                bulkInvoiceSmsEnabled = prefs[bulkInvoiceSmsEnabledKey] ?: false
            )
            val next = transform(current)
            prefs[notificationsEnabledKey] = next.notificationsEnabled
            prefs[soundEnabledKey] = next.soundEnabled
            prefs[invoiceNotifKey] = next.invoiceNotificationsEnabled
            prefs[collectionNotifKey] = next.collectionNotificationsEnabled
            prefs[reminderNotifKey] = next.readingRemindersEnabled
            prefs[thermalWidthKey] = when (next.thermalPaperWidthMm) { 58 -> 58; 210 -> 210; else -> 80 }
            prefs[printerConnectionTypeKey] = next.printerConnectionType
            prefs[printerDeviceNameKey] = next.printerDeviceName
            prefs[printerDeviceAddressKey] = next.printerDeviceAddress
            prefs[stationPhoneKey] = next.stationPhone
            prefs[stationAddressKey] = next.stationAddress
            prefs[defaultCountryCodeKey] = next.defaultCountryCode.filter { it.isDigit() }.ifBlank { "967" }
            prefs[bulkInvoiceSmsEnabledKey] = next.bulkInvoiceSmsEnabled
        }
    }
}
