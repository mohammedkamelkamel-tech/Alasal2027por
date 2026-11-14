package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.TableRowCounts
import com.example.data.local.database.AlasalDatabase
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.CreditBalanceEntity
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.local.entity.KwhTariffEntity
import com.example.data.local.entity.PermissionEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.SubscriberFullHistory
import com.example.data.local.entity.TariffChangeLogEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.auth.PermissionCatalog
import com.example.domain.auth.StationPermission
import com.example.domain.auth.StationRoles
import com.example.domain.finance.FinancialCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

data class TableSchemaStatus(
    val tableName: String,
    val arabicTitle: String,
    val rowCount: Int,
    val relationshipNote: String
)

data class FoundationDashboardSnapshot(
    val subscriberCount: Int,
    val invoiceCount: Int,
    val collectionCount: Int,
    val totalArrears: Double,
    val totalCredits: Double,
    val totalCollected: Double,
    val totalReceivables: Double,
    val paidInvoicesCount: Int,
    val unpaidInvoicesCount: Int,
    val partialInvoicesCount: Int,
    val primaryTariff: KwhTariffEntity?,
    val tableCounts: TableRowCounts
)

data class SubscriberInput(
    val id: Long = 0L,
    val subscriberCode: String,
    val fullName: String,
    val phoneNumber: String,
    val meterNumber: String,
    val meterSealNumber: String,
    val address: String,
    val subscriptionType: String,
    val customKwhPrice: Double,
    val subscriptionDate: Long,
    val isActive: Boolean,
    val initialCreditBalance: Double = 0.0,
    val initialArrearsAmount: Double = 0.0,
    val initialMeterReading: Double = 0.0,
    val notes: String = ""
)

data class TariffPricePreviewItem(
    val subscriberId: Long,
    val subscriberName: String,
    val subscriberCode: String,
    val oldPrice: Double,
    val newPrice: Double
)

class AlasalRepository(private val database: AlasalDatabase) {

    private val subscriberDao = database.subscriberDao()
    private val tariffDao = database.tariffDao()
    private val billingDao = database.billingDao()
    private val collectionDao = database.collectionDao()
    private val systemDao = database.systemDao()

    fun observeDashboardSnapshot(): Flow<FoundationDashboardSnapshot> {
        val countsFlow = combine(
            subscriberDao.observeSubscriberCount(),
            billingDao.observeInvoiceCount(),
            collectionDao.observeCollectionCount(),
            subscriberDao.observeTotalArrears(),
            subscriberDao.observeTotalCreditBalances()
        ) { subscribers, invoices, collections, arrears, credits ->
            listOf(subscribers, invoices, collections, arrears, credits)
        }

        val invoiceBreakdownFlow = combine(
            billingDao.observeTotalReceivables(),
            billingDao.observePaidInvoicesCount(),
            billingDao.observeUnpaidInvoicesCount(),
            billingDao.observePartialInvoicesCount()
        ) { receivables, paidCount, unpaidCount, partialCount ->
            listOf(receivables, paidCount, unpaidCount, partialCount)
        }

        return combine(
            countsFlow,
            invoiceBreakdownFlow,
            collectionDao.observeTotalCollectedAmount(),
            tariffDao.observePrimaryActiveTariff(),
            systemDao.observeAllTableCounts()
        ) { metrics, invMetrics, totalCollected, primaryTariff, tableCounts ->
            FoundationDashboardSnapshot(
                subscriberCount = metrics[0] as Int,
                invoiceCount = metrics[1] as Int,
                collectionCount = metrics[2] as Int,
                totalArrears = metrics[3] as Double,
                totalCredits = metrics[4] as Double,
                totalCollected = totalCollected,
                totalReceivables = invMetrics[0] as Double,
                paidInvoicesCount = invMetrics[1] as Int,
                unpaidInvoicesCount = invMetrics[2] as Int,
                partialInvoicesCount = invMetrics[3] as Int,
                primaryTariff = primaryTariff,
                tableCounts = tableCounts
            )
        }
    }

    fun observeAllReminders(): Flow<List<ReminderEntity>> = systemDao.observeAllReminders()

    fun observeSubscribers(): Flow<List<SubscriberEntity>> = subscriberDao.observeAllSubscribers()

    fun observeSubscriberFullHistory(subscriberId: Long): Flow<SubscriberFullHistory?> =
        subscriberDao.observeSubscriberFullHistory(subscriberId)

    fun observeTariffs(): Flow<List<KwhTariffEntity>> = tariffDao.observeAllTariffs()

    fun observeTariffChangeLogs(): Flow<List<TariffChangeLogEntity>> =
        tariffDao.observeAllTariffChangeLogs()

    fun observeUsers(): Flow<List<UserEntity>> = systemDao.observeAllUsers()

    fun observePermissions(): Flow<List<PermissionEntity>> = systemDao.observeAllPermissions()

    fun observeSettings(): Flow<List<AppSettingEntity>> = systemDao.observeAllSettings()

    fun observeRecentAuditLogs(): Flow<List<FinancialAuditLogEntity>> =
        systemDao.observeRecentAuditLogs()

    fun observeSchemaTableStatuses(): Flow<List<TableSchemaStatus>> =
        systemDao.observeAllTableCounts().map { counts -> buildSchemaStatusList(counts) }

    suspend fun getSubscriberFullHistory(subscriberId: Long): SubscriberFullHistory? =
        subscriberDao.getSubscriberFullHistory(subscriberId)

    suspend fun getAllTableCountsOnce(): TableRowCounts = systemDao.getAllTableCountsOnce()

    suspend fun getSchemaTableStatusesOnce(): List<TableSchemaStatus> =
        buildSchemaStatusList(systemDao.getAllTableCountsOnce())

    suspend fun resolveActorUser(actorUserId: Long?): UserEntity {
        return if (actorUserId != null) {
            systemDao.getUserById(actorUserId)
                ?: throw SecurityException("تعذر العثور على حساب المستخدم المنفذ (#$actorUserId)")
        } else {
            systemDao.getActiveUserByRole(StationRoles.ADMIN)
                ?: throw SecurityException("تعذر التحقق من حساب المدير النشط")
        }
    }

    suspend fun verifyPermission(actorUserId: Long?, permissionKey: String): UserEntity {
        val actor = resolveActorUser(actorUserId)
        if (!actor.isActive) {
            throw SecurityException("تم رفض العملية: حساب (${actor.fullName}) موقوف حالياً")
        }
        val rolePerms = systemDao.getPermissionsForRole(actor.roleCode)
        val allowed = PermissionCatalog.isEffectivePermissionAllowed(actor, rolePerms, permissionKey)
        if (!allowed) {
            throw SecurityException(
                "تم رفض العملية: حساب (${actor.fullName}) لا يملك صلاحية «${PermissionCatalog.arabicLabel(permissionKey)}»"
            )
        }
        return actor
    }

    suspend fun isUserPermissionAllowed(userId: Long?, permissionKey: String): Boolean {
        val actor = runCatching { resolveActorUser(userId) }.getOrNull() ?: return false
        if (!actor.isActive) return false
        val rolePerms = systemDao.getPermissionsForRole(actor.roleCode)
        return PermissionCatalog.isEffectivePermissionAllowed(actor, rolePerms, permissionKey)
    }

    suspend fun authenticateUser(username: String, rawPassword: String): Result<UserEntity> = runCatching {
        val cleanUser = username.trim()
        require(cleanUser.isNotEmpty()) { "يرجى إدخال اسم المستخدم" }
        require(rawPassword.isNotEmpty()) { "يرجى إدخال كلمة المرور" }

        val user = systemDao.getUserByUsername(cleanUser)
            ?: error("اسم المستخدم أو كلمة المرور غير صحيحة")
        if (!user.isActive) {
            throw SecurityException("الحساب (@${user.username}) موقوف حالياً؛ تواصل مع المدير الرئيسي")
        }
        val expectedHash = sha256(rawPassword)
        require(user.pinHash == expectedHash) { "اسم المستخدم أو كلمة المرور غير صحيحة" }

        recordAuditLog(
            operationType = "USER_LOGIN",
            entityName = "users",
            entityId = user.id,
            actorUserId = user.id,
            actorNameSnapshot = user.fullName,
            detailsAr = "تسجيل دخول المستخدم: ${user.fullName} (@${user.username} - ${StationRoles.arabicTitle(user.roleCode)})"
        )
        user
    }

    suspend fun authenticateUserWithBiometric(userId: Long): Result<UserEntity> = runCatching {
        val user = systemDao.getUserById(userId)
            ?: error("تعذر العثور على الحساب المختار للدخول بالبصمة")
        if (!user.isActive) {
            throw SecurityException("الحساب (@${user.username}) موقوف حالياً؛ تواصل مع المدير الرئيسي")
        }
        require(user.biometricEnabled) {
            "الدخول السريع بالبصمة غير مفعّل للحساب (@${user.username})؛ يمكنك الدخول بكلمة المرور أو تفعيل البصمة من إدارة الحسابات"
        }
        recordAuditLog(
            operationType = "USER_BIOMETRIC_LOGIN",
            entityName = "users",
            entityId = user.id,
            actorUserId = user.id,
            actorNameSnapshot = user.fullName,
            detailsAr = "تسجيل دخول بالبصمة للمستخدم: ${user.fullName} (@${user.username})"
        )
        user
    }

    suspend fun createUserAccount(
        username: String,
        fullName: String,
        roleCode: String,
        password: String,
        isActive: Boolean = true,
        customPermissions: Map<String, Boolean> = emptyMap(),
        biometricEnabled: Boolean = false,
        actorUserId: Long? = null
    ): Result<Long> = runCatching {
        val cleanUsername = username.trim().lowercase()
        val cleanFullName = fullName.trim()
        require(cleanUsername.length >= 3) { "اسم المستخدم يجب أن يكون 3 أحرف على الأقل" }
        require(!cleanUsername.contains(" ")) { "اسم المستخدم يجب ألا يحتوي على مسافات" }
        require(cleanFullName.isNotEmpty()) { "الاسم الكامل للمستخدم مطلوب" }
        require(roleCode in StationRoles.allRoles) { "نوع الدور غير مدعوم" }
        require(password.length >= 4) { "كلمة المرور يجب أن تكون 4 أحرف/أرقام على الأقل" }

        database.withTransaction {
            val actor = verifyPermission(actorUserId, StationPermission.MANAGE_USERS.key)
            val existing = systemDao.getUserByUsername(cleanUsername)
            require(existing == null) { "اسم المستخدم (@$cleanUsername) مسجل مسبقاً" }

            val now = System.currentTimeMillis()
            val mergedPermissions = if (customPermissions.isNotEmpty()) {
                PermissionCatalog.serializeCustomPermissions(customPermissions)
            } else {
                ""
            }
            val newId = systemDao.insertUser(
                UserEntity(
                    username = cleanUsername,
                    fullName = cleanFullName,
                    roleCode = roleCode,
                    pinHash = sha256(password),
                    isActive = isActive,
                    customPermissionsJson = mergedPermissions,
                    biometricEnabled = biometricEnabled,
                    updatedAt = now,
                    syncId = "user_$cleanUsername",
                    createdAt = now
                )
            )
            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "USER_CREATED",
                    entityName = "users",
                    entityId = newId,
                    detailsAr = "إنشاء حساب جديد: $cleanFullName (@$cleanUsername - ${StationRoles.arabicTitle(roleCode)}) بواسطة ${actor.fullName}",
                    createdAt = now
                )
            )
            newId
        }
    }

    suspend fun updateUserAccount(
        userId: Long,
        fullName: String,
        roleCode: String,
        isActive: Boolean,
        customPermissions: Map<String, Boolean>? = null,
        biometricEnabled: Boolean? = null,
        actorUserId: Long? = null
    ): Result<UserEntity> = runCatching {
        val cleanFullName = fullName.trim()
        require(cleanFullName.isNotEmpty()) { "الاسم الكامل للمستخدم مطلوب" }
        require(roleCode in StationRoles.allRoles) { "نوع الدور غير صالح" }

        database.withTransaction {
            val actor = verifyPermission(actorUserId, StationPermission.MANAGE_USERS.key)
            val existing = systemDao.getUserById(userId) ?: error("الحساب غير موجود")
            if (existing.roleCode == StationRoles.ADMIN && existing.isActive &&
                (!isActive || roleCode != StationRoles.ADMIN)
            ) {
                val activeAdmins = systemDao.countActiveAdmins()
                require(activeAdmins > 1) {
                    "لا يمكن إيقاف أو تغيير دور آخر مدير رئيسي نشط في النظام"
                }
            }

            val now = System.currentTimeMillis()
            val permJson = if (customPermissions != null) {
                PermissionCatalog.serializeCustomPermissions(customPermissions)
            } else {
                existing.customPermissionsJson
            }
            val updated = existing.copy(
                fullName = cleanFullName,
                roleCode = roleCode,
                isActive = isActive,
                customPermissionsJson = permJson,
                biometricEnabled = biometricEnabled ?: existing.biometricEnabled,
                updatedAt = now
            )
            systemDao.updateUser(updated)
            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "USER_UPDATED",
                    entityName = "users",
                    entityId = existing.id,
                    detailsAr = "تعديل حساب: $cleanFullName (@${existing.username}) — الدور: ${StationRoles.arabicTitle(roleCode)} — الحالة: ${if (isActive) "نشط" else "موقوف"}",
                    createdAt = now
                )
            )
            updated
        }
    }

    suspend fun updateUserBiometricEnabled(
        targetUserId: Long,
        enabled: Boolean,
        actorUserId: Long? = null
    ): Result<UserEntity> = runCatching {
        database.withTransaction {
            val actor = resolveActorUser(actorUserId)
            val isSelf = actor.id == targetUserId
            if (!isSelf) {
                verifyPermission(actorUserId, StationPermission.MANAGE_USERS.key)
            }
            val existing = systemDao.getUserById(targetUserId) ?: error("الحساب غير موجود")
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                biometricEnabled = enabled,
                updatedAt = now
            )
            systemDao.updateUser(updated)
            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "USER_BIOMETRIC_TOGGLED",
                    entityName = "users",
                    entityId = existing.id,
                    detailsAr = "${if (enabled) "تفعيل" else "تعطيل"} الدخول السريع بالبصمة لحساب ${existing.fullName} (@${existing.username}) بواسطة ${actor.fullName}",
                    createdAt = now
                )
            )
            updated
        }
    }

    suspend fun changeUserPassword(
        targetUserId: Long,
        newPassword: String,
        actorUserId: Long? = null
    ): Result<Unit> = runCatching {
        require(newPassword.length >= 4) { "كلمة المرور الجديدة يجب أن تكون 4 خانات على الأقل" }
        database.withTransaction {
            val actor = verifyPermission(actorUserId, StationPermission.MANAGE_USERS.key)
            val existing = systemDao.getUserById(targetUserId) ?: error("الحساب غير موجود")
            val now = System.currentTimeMillis()
            systemDao.updateUser(
                existing.copy(
                    pinHash = sha256(newPassword),
                    updatedAt = now
                )
            )
            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "USER_PASSWORD_CHANGED",
                    entityName = "users",
                    entityId = existing.id,
                    detailsAr = "تغيير كلمة المرور للحساب @${existing.username} بواسطة ${actor.fullName}",
                    createdAt = now
                )
            )
        }
    }

    suspend fun toggleUserActive(
        targetUserId: Long,
        isActive: Boolean,
        actorUserId: Long? = null
    ): Result<UserEntity> = runCatching {
        database.withTransaction {
            val actor = verifyPermission(actorUserId, StationPermission.MANAGE_USERS.key)
            val existing = systemDao.getUserById(targetUserId) ?: error("الحساب غير موجود")
            if (existing.roleCode == StationRoles.ADMIN && !isActive) {
                val activeAdmins = systemDao.countActiveAdmins()
                require(activeAdmins > 1) { "لا يمكن إيقاف آخر مدير رئيسي نشط" }
            }
            val now = System.currentTimeMillis()
            val updated = existing.copy(isActive = isActive, updatedAt = now)
            systemDao.updateUser(updated)
            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = if (isActive) "USER_ACTIVATED" else "USER_DEACTIVATED",
                    entityName = "users",
                    entityId = existing.id,
                    detailsAr = "${if (isActive) "تفعيل" else "إيقاف"} حساب ${existing.fullName} (@${existing.username}) بواسطة ${actor.fullName}",
                    createdAt = now
                )
            )
            updated
        }
    }

    suspend fun updateUserPermissions(
        targetUserId: Long,
        customPermissions: Map<String, Boolean>,
        actorUserId: Long? = null
    ): Result<UserEntity> = runCatching {
        database.withTransaction {
            val actor = verifyPermission(actorUserId, StationPermission.MANAGE_USERS.key)
            val existing = systemDao.getUserById(targetUserId) ?: error("الحساب غير موجود")
            val finalMap = if (existing.roleCode == StationRoles.ADMIN && existing.username == "admin") {
                customPermissions + (StationPermission.MANAGE_USERS.key to true)
            } else {
                customPermissions
            }
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                customPermissionsJson = PermissionCatalog.serializeCustomPermissions(finalMap),
                updatedAt = now
            )
            systemDao.updateUser(updated)
            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "USER_PERMISSIONS_UPDATED",
                    entityName = "permissions",
                    entityId = existing.id,
                    detailsAr = "تحديث مصفوفة الصلاحيات للحساب ${existing.fullName} (@${existing.username}) بواسطة ${actor.fullName}",
                    createdAt = now
                )
            )
            updated
        }
    }

    suspend fun updateAppSettingAuthorized(
        settingKey: String,
        settingValue: String,
        category: String = "GENERAL",
        descriptionAr: String = settingKey,
        actorUserId: Long? = null
    ): Result<Unit> = runCatching {
        database.withTransaction {
            val actor = verifyPermission(actorUserId, StationPermission.MANAGE_SETTINGS.key)
            val now = System.currentTimeMillis()
            systemDao.upsertSetting(
                AppSettingEntity(
                    settingKey = settingKey,
                    settingValue = settingValue,
                    category = category,
                    descriptionAr = descriptionAr,
                    updatedAt = now
                )
            )
            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "SETTING_UPDATED",
                    entityName = "app_settings",
                    detailsAr = "تحديث الإعداد ($settingKey) بواسطة ${actor.fullName}",
                    createdAt = now
                )
            )
        }
    }

    suspend fun saveSubscriber(input: SubscriberInput, actorUserId: Long? = null): Result<Long> = runCatching {
        val cleanCode = input.subscriberCode.trim()
        val cleanName = input.fullName.trim()
        val cleanMeter = input.meterNumber.trim()
        require(cleanCode.isNotEmpty()) { "رقم المشترك مطلوب" }
        require(cleanName.isNotEmpty()) { "اسم المشترك مطلوب" }
        require(cleanMeter.isNotEmpty()) { "رقم العداد مطلوب" }
        require(input.customKwhPrice >= 0.0) { "سعر الكيلوواط غير صالح" }
        require(input.initialCreditBalance >= 0.0) { "الرصيد الدائن لا يمكن أن يكون سالباً" }
        require(input.initialArrearsAmount >= 0.0) { "المبلغ السابق المستحق لا يمكن أن يكون سالباً" }
        require(input.initialMeterReading >= 0.0) { "قراءة العداد السابقة لا يمكن أن تكون سالبة" }

        database.withTransaction {
            val actor = verifyPermission(
                actorUserId = actorUserId,
                permissionKey = if (input.id == 0L) StationPermission.ADD_SUBSCRIBER.key else StationPermission.EDIT_SUBSCRIBER.key
            )
            val now = System.currentTimeMillis()
            if (input.id == 0L) {
                val credit = FinancialCalculator.roundCurrency(input.initialCreditBalance)
                val newId = subscriberDao.insert(
                    SubscriberEntity(
                        subscriberCode = cleanCode,
                        fullName = cleanName,
                        phoneNumber = input.phoneNumber.trim(),
                        meterNumber = cleanMeter,
                        meterSealNumber = input.meterSealNumber.trim(),
                        address = input.address.trim(),
                        subscriptionType = input.subscriptionType.trim().ifEmpty { "منزلي" },
                        customKwhPrice = FinancialCalculator.roundCurrency(input.customKwhPrice),
                        previousKwhPrice = null,
                        subscriptionDate = input.subscriptionDate,
                        isActive = input.isActive,
                        initialMeterReading = FinancialCalculator.roundCurrency(input.initialMeterReading),
                        currentArrearsAmount = FinancialCalculator.roundCurrency(input.initialArrearsAmount),
                        currentCreditBalance = credit,
                        notes = input.notes.trim(),
                        createdAt = now,
                        updatedAt = now
                    )
                )
                if (credit > 0.0) {
                    billingDao.insertCreditTransaction(
                        CreditBalanceEntity(
                            subscriberId = newId,
                            transactionType = "INITIAL_CREDIT",
                            amountDelta = credit,
                            balanceAfter = credit,
                            recordedAt = now,
                            notes = "رصيد دائن افتتاحي عند تسجيل المشترك"
                        )
                    )
                }
                systemDao.insertAuditLog(
                    FinancialAuditLogEntity(
                        subscriberId = newId,
                        actorUserId = actor.id,
                        actorNameSnapshot = actor.fullName,
                        operationType = "SUBSCRIBER_CREATED",
                        entityName = "subscribers",
                        entityId = newId,
                        amountAfter = input.customKwhPrice,
                        detailsAr = "إضافة مشترك جديد: $cleanName ($cleanCode) بسعر ${input.customKwhPrice} / ك.و.س",
                        createdAt = now
                    )
                )
                newId
            } else {
                val existing = subscriberDao.getById(input.id) ?: error("المشترك غير موجود")
                val newPrice = FinancialCalculator.roundCurrency(input.customKwhPrice)
                val priceChanged = newPrice != existing.customKwhPrice
                if (priceChanged) {
                    verifyPermission(actorUserId, StationPermission.EDIT_KWH_PRICE.key)
                }
                val updated = existing.copy(
                    subscriberCode = cleanCode,
                    fullName = cleanName,
                    phoneNumber = input.phoneNumber.trim(),
                    meterNumber = cleanMeter,
                    meterSealNumber = input.meterSealNumber.trim(),
                    address = input.address.trim(),
                    subscriptionType = input.subscriptionType.trim().ifEmpty { "منزلي" },
                    customKwhPrice = newPrice,
                    previousKwhPrice = if (priceChanged) existing.customKwhPrice else existing.previousKwhPrice,
                    subscriptionDate = input.subscriptionDate,
                    isActive = input.isActive,
                    notes = input.notes.trim(),
                    updatedAt = now
                )
                subscriberDao.update(updated)
                if (priceChanged) {
                    val primaryTariff = tariffDao.getPrimaryActiveTariffOnce()
                    if (primaryTariff != null) {
                        tariffDao.insertTariffChangeLog(
                            TariffChangeLogEntity(
                                tariffId = primaryTariff.id,
                                subscriberId = existing.id,
                                subscriberNameSnapshot = cleanName,
                                oldPricePerKwh = existing.customKwhPrice,
                                newPricePerKwh = newPrice,
                                oldFixedFee = primaryTariff.fixedFee,
                                newFixedFee = primaryTariff.fixedFee,
                                effectiveFrom = now,
                                changedByUserId = actor.id,
                                actorNameSnapshot = actor.fullName,
                                reason = "تحديث سعر المشترك من بطاقة البيانات",
                                changedAt = now
                            )
                        )
                    }
                }
                systemDao.insertAuditLog(
                    FinancialAuditLogEntity(
                        subscriberId = existing.id,
                        actorUserId = actor.id,
                        actorNameSnapshot = actor.fullName,
                        operationType = "SUBSCRIBER_UPDATED",
                        entityName = "subscribers",
                        entityId = existing.id,
                        amountBefore = existing.customKwhPrice,
                        amountAfter = newPrice,
                        detailsAr = "تعديل بيانات المشترك: $cleanName ($cleanCode)",
                        createdAt = now
                    )
                )
                existing.id
            }
        }
    }

    /**
     * Deletes a subscriber ONLY if they have zero financial/meter history.
     * Otherwise rejects deletion and protects financial history integrity.
     */
    suspend fun deleteSubscriberSafely(subscriberId: Long, actorUserId: Long? = null): Result<String> = runCatching {
        database.withTransaction {
            val actor = verifyPermission(actorUserId, StationPermission.DELETE_SUBSCRIBER.key)
            val existing = subscriberDao.getById(subscriberId) ?: error("المشترك غير موجود")
            val historyCount = subscriberDao.countFinancialHistoryRecords(subscriberId)
            require(historyCount == 0) {
                "لا يمكن حذف المشترك (${existing.fullName}) لوجود $historyCount سجلات مالية/قراءات مرتبطة به؛ يمكنك تعطيل حالته بدلاً من الحذف للحفاظ على التاريخ المالي."
            }
            subscriberDao.deleteById(subscriberId)
            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "SUBSCRIBER_DELETED",
                    entityName = "subscribers",
                    entityId = subscriberId,
                    detailsAr = "حذف المشترك الخالي من السجلات المالية: ${existing.fullName} (${existing.subscriberCode})"
                )
            )
            "تم حذف المشترك (${existing.fullName}) بنجاح"
        }
    }

    suspend fun previewTariffUpdate(
        targetSubscriberIds: List<Long>?,
        newPricePerKwh: Double
    ): Result<List<TariffPricePreviewItem>> = runCatching {
        require(newPricePerKwh >= 0.0) { "سعر الكيلوواط الجديد غير صالح" }
        val subscribers = if (targetSubscriberIds == null) {
            subscriberDao.getAllSubscribersOnce()
        } else {
            require(targetSubscriberIds.isNotEmpty()) { "يرجى اختيار مشترك واحد على الأقل" }
            subscriberDao.getByIds(targetSubscriberIds)
        }
        val rounded = FinancialCalculator.roundCurrency(newPricePerKwh)
        subscribers.map { sub ->
            TariffPricePreviewItem(
                subscriberId = sub.id,
                subscriberName = sub.fullName,
                subscriberCode = sub.subscriberCode,
                oldPrice = sub.customKwhPrice,
                newPrice = rounded
            )
        }
    }

    /**
     * Updates kWh tariff for one subscriber, a selected group, or all subscribers.
     * Preserves old invoices untouched and logs full audit history (actor, time, effective date, reason).
     */
    suspend fun applyKwhPriceUpdate(
        targetSubscriberIds: List<Long>?,
        newPricePerKwh: Double,
        effectiveFrom: Long,
        reason: String,
        actorUserId: Long? = null
    ): Result<Int> = runCatching {
        require(newPricePerKwh >= 0.0) { "سعر الكيلوواط يجب أن يكون صفراً أو أكبر" }
        val cleanReason = reason.trim().ifEmpty { "تحديث سعر الكيلوواط" }
        val roundedPrice = FinancialCalculator.roundCurrency(newPricePerKwh)

        database.withTransaction {
            val requiredKey = if (targetSubscriberIds == null || targetSubscriberIds.size > 1) {
                StationPermission.BULK_UPDATE_TARIFFS.key
            } else {
                StationPermission.EDIT_KWH_PRICE.key
            }
            val actor = verifyPermission(actorUserId, requiredKey)
            val primaryTariff = tariffDao.getPrimaryActiveTariffOnce()
                ?: error("لا توجد شريحة تعرفة نشطة في النظام")
            val targets = if (targetSubscriberIds == null) {
                subscriberDao.getAllSubscribersOnce()
            } else {
                require(targetSubscriberIds.isNotEmpty()) { "يرجى اختيار مشترك واحد على الأقل" }
                subscriberDao.getByIds(targetSubscriberIds)
            }
            require(targets.isNotEmpty()) { "لا يوجد مشتركون لتحديث السعر" }

            val now = System.currentTimeMillis()
            targets.forEach { sub ->
                val oldPrice = sub.customKwhPrice
                subscriberDao.updateKwhPrice(
                    subscriberId = sub.id,
                    newPrice = roundedPrice,
                    updatedAt = now
                )
                tariffDao.insertTariffChangeLog(
                    TariffChangeLogEntity(
                        tariffId = primaryTariff.id,
                        subscriberId = sub.id,
                        subscriberNameSnapshot = sub.fullName,
                        oldPricePerKwh = oldPrice,
                        newPricePerKwh = roundedPrice,
                        oldFixedFee = primaryTariff.fixedFee,
                        newFixedFee = primaryTariff.fixedFee,
                        effectiveFrom = effectiveFrom,
                        changedByUserId = actor.id,
                        actorNameSnapshot = actor.fullName,
                        reason = cleanReason,
                        changedAt = now
                    )
                )
            }

            if (targetSubscriberIds == null) {
                val oldStationPrice = primaryTariff.pricePerKwh
                tariffDao.updateTariff(
                    primaryTariff.copy(
                        pricePerKwh = roundedPrice,
                        effectiveFrom = effectiveFrom
                    )
                )
                tariffDao.insertTariffChangeLog(
                    TariffChangeLogEntity(
                        tariffId = primaryTariff.id,
                        subscriberId = null,
                        subscriberNameSnapshot = "جميع المشتركين (${targets.size})",
                        oldPricePerKwh = oldStationPrice,
                        newPricePerKwh = roundedPrice,
                        oldFixedFee = primaryTariff.fixedFee,
                        newFixedFee = primaryTariff.fixedFee,
                        effectiveFrom = effectiveFrom,
                        changedByUserId = actor.id,
                        actorNameSnapshot = actor.fullName,
                        reason = cleanReason,
                        changedAt = now
                    )
                )
            }

            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = if (targetSubscriberIds == null) "BULK_TARIFF_UPDATED" else "SUBSCRIBER_TARIFF_UPDATED",
                    entityName = "kwh_tariffs",
                    entityId = primaryTariff.id,
                    amountAfter = roundedPrice,
                    detailsAr = "اعتماد سعر جديد ($roundedPrice / ك.و.س) لعدد ${targets.size} مشترك بواسطة ${actor.fullName} — السبب: $cleanReason",
                    createdAt = now
                )
            )
            targets.size
        }
    }

    suspend fun recordAuditLog(
        operationType: String,
        entityName: String,
        detailsAr: String,
        subscriberId: Long? = null,
        actorUserId: Long? = null,
        actorNameSnapshot: String = "النظام",
        entityId: Long? = null,
        amountBefore: Double? = null,
        amountAfter: Double? = null
    ) {
        systemDao.insertAuditLog(
            FinancialAuditLogEntity(
                subscriberId = subscriberId,
                actorUserId = actorUserId,
                actorNameSnapshot = actorNameSnapshot,
                operationType = operationType,
                entityName = entityName,
                entityId = entityId,
                amountBefore = amountBefore,
                amountAfter = amountAfter,
            detailsAr = detailsAr
            )
        )
    }

    suspend fun scheduleReminder(
        subscriberId: Long?,
        invoiceId: Long? = null,
        reminderType: String = "METER_READING",
        title: String,
        message: String,
        notes: String = "",
        scheduledAt: Long
    ): Result<Long> = runCatching {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "عنوان التذكير مطلوب" }
        database.withTransaction {
            val id = systemDao.insertReminder(
                ReminderEntity(
                    subscriberId = subscriberId,
                    invoiceId = invoiceId,
                    reminderType = reminderType,
                    title = cleanTitle,
                    message = message.trim().ifEmpty { cleanTitle },
                    notes = notes.trim(),
                    scheduledAt = scheduledAt,
                    status = "PENDING"
                )
            )
            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    subscriberId = subscriberId,
                    operationType = "REMINDER_SCHEDULED",
                    entityName = "reminders",
                    entityId = id,
                    detailsAr = "جدولة تذكير: $cleanTitle"
                )
            )
            id
        }
    }

    suspend fun completeReminder(reminderId: Long): Result<Unit> = runCatching {
        database.withTransaction {
            val reminder = systemDao.getReminderById(reminderId) ?: error("التذكير غير موجود")
            systemDao.updateReminder(
                reminder.copy(
                    status = "COMPLETED",
                    completedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun snoozeReminder(reminderId: Long, delayMillis: Long = 3_600_000L): Result<ReminderEntity> = runCatching {
        database.withTransaction {
            val reminder = systemDao.getReminderById(reminderId) ?: error("التذكير غير موجود")
            val nextTime = maxOf(System.currentTimeMillis(), reminder.scheduledAt) + delayMillis
            val updated = reminder.copy(
                scheduledAt = nextTime,
                status = "SNOOZED",
                snoozeCount = reminder.snoozeCount + 1
            )
            systemDao.updateReminder(updated)
            updated
        }
    }

    suspend fun processDueReminders(now: Long = System.currentTimeMillis()): List<ReminderEntity> {
        return database.withTransaction {
            val due = systemDao.getDuePendingReminders(now)
            due.forEach { reminder ->
                systemDao.updateReminder(reminder.copy(status = "NOTIFIED"))
            }
            due
        }
    }

    suspend fun ensureBaselineSeeded() {
        database.withTransaction {
            val existingUsers = systemDao.countUsers()
            var adminId: Long? = null
            if (existingUsers == 0) {
                adminId = systemDao.insertUser(
                    UserEntity(
                        username = "admin",
                        fullName = "مدير محطة العسل",
                        roleCode = "ADMIN",
                        pinHash = sha256("2027"),
                        isActive = true
                    )
                )
                systemDao.insertUser(
                    UserEntity(
                        username = "collector_1",
                        fullName = "محصّل ميداني",
                        roleCode = "COLLECTOR",
                        pinHash = sha256("1234"),
                        isActive = true
                    )
                )
            }

            if (systemDao.countPermissions() == 0) {
                systemDao.insertPermissions(
                    listOf(
                        PermissionEntity(roleCode = "ADMIN", permissionKey = "MANAGE_TARIFFS", isAllowed = true, descriptionAr = "إدارة وتعديل أسعار الكيلوواط والفواتير"),
                        PermissionEntity(roleCode = "ADMIN", permissionKey = "OVERRIDE_LOW_READING", isAllowed = true, descriptionAr = "اعتماد قراءة عداد أقل من السابقة عند التبديل"),
                        PermissionEntity(roleCode = "ADMIN", permissionKey = "MANAGE_USERS", isAllowed = true, descriptionAr = "إدارة المستخدمين والصلاحيات"),
                        PermissionEntity(roleCode = "ADMIN", permissionKey = "RECORD_COLLECTION", isAllowed = true, descriptionAr = "تسجيل التحصيلات وإصدار الإيصالات"),
                        PermissionEntity(roleCode = "ADMIN", permissionKey = "EXPORT_BACKUP", isAllowed = true, descriptionAr = "تصدير واستيراد النسخ الاحتياطي"),
                        PermissionEntity(roleCode = "COLLECTOR", permissionKey = "MANAGE_TARIFFS", isAllowed = false, descriptionAr = "إدارة وتعديل أسعار الكيلوواط"),
                        PermissionEntity(roleCode = "COLLECTOR", permissionKey = "OVERRIDE_LOW_READING", isAllowed = false, descriptionAr = "اعتماد قراءة عداد أقل من السابقة"),
                        PermissionEntity(roleCode = "COLLECTOR", permissionKey = "RECORD_COLLECTION", isAllowed = true, descriptionAr = "تسجيل التحصيلات وإصدار الإيصالات"),
                        PermissionEntity(roleCode = "COLLECTOR", permissionKey = "EXPORT_BACKUP", isAllowed = false, descriptionAr = "تصدير واستيراد النسخ الاحتياطي")
                    )
                )
            }

            if (tariffDao.countTariffs() == 0) {
                val homeTariffId = tariffDao.insertTariff(
                    KwhTariffEntity(
                        tariffCode = "TRF-HOME-2027",
                        title = "التعرفة المنزلية القياسية",
                        subscriptionType = "منزلي",
                        pricePerKwh = 0.45,
                        fixedFee = 0.0,
                        isActive = true
                    )
                )
                tariffDao.insertTariff(
                    KwhTariffEntity(
                        tariffCode = "TRF-COM-2027",
                        title = "التعرفة التجارية",
                        subscriptionType = "تجاري",
                        pricePerKwh = 0.70,
                        fixedFee = 0.0,
                        isActive = true
                    )
                )
                tariffDao.insertTariffChangeLog(
                    TariffChangeLogEntity(
                        tariffId = homeTariffId,
                        oldPricePerKwh = 0.0,
                        newPricePerKwh = 0.45,
                        oldFixedFee = 0.0,
                        newFixedFee = 0.0,
                        changedByUserId = adminId,
                        actorNameSnapshot = "مدير محطة العسل",
                        reason = "اعتماد التعرفة التأسيسية للمحطة 2027"
                    )
                )
            }

            if (systemDao.countSettings() == 0) {
                systemDao.upsertSettings(
                    listOf(
                        AppSettingEntity(
                            settingKey = "station_name",
                            settingValue = "محطة العسل 2027",
                            category = "GENERAL",
                            descriptionAr = "الاسم الرسمي للمحطة الظاهر في الفواتير والإيصالات"
                        ),
                        AppSettingEntity(
                            settingKey = "currency_label",
                            settingValue = "وحدة نقدية",
                            category = "BILLING",
                            descriptionAr = "تسمية العملة المعتمدة في الحسابات"
                        ),
                        AppSettingEntity(
                            settingKey = "offline_mode",
                            settingValue = "ENABLED",
                            category = "GENERAL",
                            descriptionAr = "تشغيل الوظائف الأساسية محلياً دون اتصال بالإنترنت"
                        ),
                        AppSettingEntity(
                            settingKey = "backup_engine",
                            settingValue = "SAF_LOCAL_JSON",
                            category = "BACKUP",
                            descriptionAr = "محرك النسخ الاحتياطي المحلي عبر Storage Access Framework"
                        )
                    )
                )
                systemDao.insertAuditLog(
                    FinancialAuditLogEntity(
                        actorUserId = adminId,
                        actorNameSnapshot = "النظام",
                        operationType = "SYSTEM_INITIALIZED",
                        entityName = "alasal_2027.db",
                        detailsAr = "تأسيس قاعدة البيانات المحلية (14 جدولاً) واعتماد الإعدادات الأساسية لمحطة العسل 2027"
                    )
                )
            }
        }
    }

    suspend fun verifySchemaIntegrity(): Result<List<TableSchemaStatus>> = runCatching {
        val statuses = getSchemaTableStatusesOnce()
        require(statuses.size == 14) { "عدد الجداول غير مكتمل" }
        recordAuditLog(
            operationType = "SCHEMA_VERIFIED",
            entityName = "all_tables",
            detailsAr = "فحص سلامة الجداول الأربعة عشر والعلاقات المرجعية بنجاح"
        )
        statuses
    }

    private fun buildSchemaStatusList(counts: TableRowCounts): List<TableSchemaStatus> = listOf(
        TableSchemaStatus("subscribers", "المشتركين", counts.subscribersCount, "فهرس فريد لرقم المشترك ورقم العداد وسعر الكيلوواط الخاص"),
        TableSchemaStatus("invoices", "الفواتير", counts.invoicesCount, "مرتبط بالمشترك والقراءة والتعرفة وسلاسل ترحيل المتأخرات"),
        TableSchemaStatus("collections", "التحصيلات", counts.collectionsCount, "مرتبط بالمشترك والفاتورة والمحصّل"),
        TableSchemaStatus("receipts", "الإيصالات", counts.receiptsCount, "علاقة 1:1 فريدة مع التحصيل"),
        TableSchemaStatus("kwh_tariffs", "أسعار الكيلوواط", counts.kwhTariffsCount, "كود تعرفة فريد ونوع الاشتراك"),
        TableSchemaStatus("tariff_change_logs", "سجل تغييرات الأسعار", counts.tariffChangeLogsCount, "توثيق تغييرات السعر الفردية والجماعية مع المنفذ وتاريخ السريان"),
        TableSchemaStatus("arrears", "المتأخرات", counts.arrearsCount, "مرتبط بالمشترك والفاتورة المصدر لمنع تكرار الترحيل"),
        TableSchemaStatus("credit_balances", "الأرصدة الدائنة", counts.creditBalancesCount, "دفتر حركات الدائن لمنع الخصم المكرر"),
        TableSchemaStatus("meter_readings", "قراءات العدادات", counts.meterReadingsCount, "قيد فريد يمنع تكرار القراءة لنفس الفترة مع دعم الصورة"),
        TableSchemaStatus("reminders", "التذكيرات", counts.remindersCount, "مرتبط بالمشترك والفاتورة ومجدول عبر WorkManager"),
        TableSchemaStatus("users", "المستخدمين", counts.usersCount, "اسم مستخدم فريد وتخزين PIN مشفّر"),
        TableSchemaStatus("permissions", "الصلاحيات", counts.permissionsCount, "قيد فريد لكل دور ومفتاح صلاحية"),
        TableSchemaStatus("app_settings", "الإعدادات", counts.appSettingsCount, "مفاتيح إعدادات عامة ومالية ونسخ احتياطي"),
        TableSchemaStatus("financial_audit_logs", "سجل العمليات والتعديلات المالية", counts.financialAuditLogsCount, "توثيق تاريخي لكل تعديل أو عملية مالية")
    )

    companion object {
        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
