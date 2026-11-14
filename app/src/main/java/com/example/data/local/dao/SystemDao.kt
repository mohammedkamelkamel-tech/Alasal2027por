package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.local.entity.PermissionEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

data class TableRowCounts(
    val subscribersCount: Int,
    val invoicesCount: Int,
    val collectionsCount: Int,
    val receiptsCount: Int,
    val kwhTariffsCount: Int,
    val tariffChangeLogsCount: Int,
    val arrearsCount: Int,
    val creditBalancesCount: Int,
    val meterReadingsCount: Int,
    val remindersCount: Int,
    val usersCount: Int,
    val permissionsCount: Int,
    val appSettingsCount: Int,
    val financialAuditLogsCount: Int
)

@Dao
interface SystemDao {
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY id ASC")
    suspend fun getAllUsersOnce(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE role_code = :roleCode AND is_active = 1 LIMIT 1")
    suspend fun getActiveUserByRole(roleCode: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE role_code = 'ADMIN' AND is_active = 1")
    suspend fun countActiveAdmins(): Int

    @Query("SELECT is_allowed FROM permissions WHERE role_code = :roleCode AND permission_key = :permissionKey LIMIT 1")
    suspend fun isPermissionAllowed(roleCode: String, permissionKey: String): Boolean?

    @Query("SELECT * FROM permissions WHERE role_code = :roleCode ORDER BY permission_key ASC")
    suspend fun getPermissionsForRole(roleCode: String): List<PermissionEntity>

    @Query("SELECT * FROM permissions ORDER BY role_code ASC, permission_key ASC")
    fun observeAllPermissions(): Flow<List<PermissionEntity>>

    @Query("SELECT * FROM permissions ORDER BY role_code ASC, permission_key ASC")
    suspend fun getAllPermissionsOnce(): List<PermissionEntity>

    @Query("SELECT * FROM app_settings ORDER BY category ASC, setting_key ASC")
    fun observeAllSettings(): Flow<List<AppSettingEntity>>

    @Query("SELECT * FROM app_settings ORDER BY category ASC, setting_key ASC")
    suspend fun getAllSettingsOnce(): List<AppSettingEntity>

    @Query("SELECT setting_value FROM app_settings WHERE setting_key = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?

    @Query("SELECT * FROM reminders ORDER BY scheduled_at ASC")
    fun observeAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY scheduled_at ASC")
    suspend fun getAllRemindersOnce(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE status IN ('PENDING', 'SNOOZED') AND scheduled_at <= :now ORDER BY scheduled_at ASC")
    suspend fun getDuePendingReminders(now: Long): List<ReminderEntity>

    @Query("SELECT * FROM financial_audit_logs ORDER BY created_at DESC, id DESC LIMIT 50")
    fun observeRecentAuditLogs(): Flow<List<FinancialAuditLogEntity>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Query("SELECT COUNT(*) FROM permissions")
    suspend fun countPermissions(): Int

    @Query("SELECT COUNT(*) FROM app_settings")
    suspend fun countSettings(): Int

    @Query("SELECT COUNT(*) FROM reminders WHERE status IN ('PENDING', 'SNOOZED')")
    suspend fun countPendingReminders(): Int

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM subscribers) AS subscribersCount,
            (SELECT COUNT(*) FROM invoices) AS invoicesCount,
            (SELECT COUNT(*) FROM collections) AS collectionsCount,
            (SELECT COUNT(*) FROM receipts) AS receiptsCount,
            (SELECT COUNT(*) FROM kwh_tariffs) AS kwhTariffsCount,
            (SELECT COUNT(*) FROM tariff_change_logs) AS tariffChangeLogsCount,
            (SELECT COUNT(*) FROM arrears) AS arrearsCount,
            (SELECT COUNT(*) FROM credit_balances) AS creditBalancesCount,
            (SELECT COUNT(*) FROM meter_readings) AS meterReadingsCount,
            (SELECT COUNT(*) FROM reminders) AS remindersCount,
            (SELECT COUNT(*) FROM users) AS usersCount,
            (SELECT COUNT(*) FROM permissions) AS permissionsCount,
            (SELECT COUNT(*) FROM app_settings) AS appSettingsCount,
            (SELECT COUNT(*) FROM financial_audit_logs) AS financialAuditLogsCount
        """
    )
    fun observeAllTableCounts(): Flow<TableRowCounts>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM subscribers) AS subscribersCount,
            (SELECT COUNT(*) FROM invoices) AS invoicesCount,
            (SELECT COUNT(*) FROM collections) AS collectionsCount,
            (SELECT COUNT(*) FROM receipts) AS receiptsCount,
            (SELECT COUNT(*) FROM kwh_tariffs) AS kwhTariffsCount,
            (SELECT COUNT(*) FROM tariff_change_logs) AS tariffChangeLogsCount,
            (SELECT COUNT(*) FROM arrears) AS arrearsCount,
            (SELECT COUNT(*) FROM credit_balances) AS creditBalancesCount,
            (SELECT COUNT(*) FROM meter_readings) AS meterReadingsCount,
            (SELECT COUNT(*) FROM reminders) AS remindersCount,
            (SELECT COUNT(*) FROM users) AS usersCount,
            (SELECT COUNT(*) FROM permissions) AS permissionsCount,
            (SELECT COUNT(*) FROM app_settings) AS appSettingsCount,
            (SELECT COUNT(*) FROM financial_audit_logs) AS financialAuditLogsCount
        """
    )
    suspend fun getAllTableCountsOnce(): TableRowCounts

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissions(permissions: List<PermissionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetting(setting: AppSettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: List<AppSettingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(log: FinancialAuditLogEntity): Long
}
