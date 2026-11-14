package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.database.AlasalDatabase
import com.example.data.local.entity.AppSettingEntity
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
import com.example.domain.auth.StationPermission
import com.example.domain.finance.FinancialCalculator
import com.example.domain.sync.StationSyncEnvelope
import com.example.domain.sync.SyncCollectionDto
import com.example.domain.sync.SyncCreditDto
import com.example.domain.sync.SyncInvoiceDto
import com.example.domain.sync.SyncMergeSummary
import com.example.domain.sync.SyncPermissionDto
import com.example.domain.sync.SyncReadingDto
import com.example.domain.sync.SyncReceiptDto
import com.example.domain.sync.SyncReminderDto
import com.example.domain.sync.SyncSettingDto
import com.example.domain.sync.SyncSubscriberDto
import com.example.domain.sync.SyncTariffDto
import com.example.domain.sync.SyncTariffLogDto
import com.example.domain.sync.SyncUserDto
import java.util.Locale
import kotlin.math.abs

/**
 * Conflict-aware, transaction-safe synchronization repository for merging records across
 * devices over local Wi-Fi without duplicate records, newer-overwrites-older protection,
 * and zero financial collection loss.
 */
class StationSyncRepository(
    private val database: AlasalDatabase,
    private val coreRepository: AlasalRepository
) {
    private val subscriberDao = database.subscriberDao()
    private val tariffDao = database.tariffDao()
    private val billingDao = database.billingDao()
    private val collectionDao = database.collectionDao()
    private val systemDao = database.systemDao()

    suspend fun exportSyncEnvelope(
        deviceId: String,
        deviceName: String
    ): StationSyncEnvelope {
        val users = systemDao.getAllUsersOnce()
        val permissions = systemDao.getAllPermissionsOnce()
        val settings = systemDao.getAllSettingsOnce()
        val tariffs = tariffDao.getAllTariffsOnce()
        val tariffLogs = tariffDao.getAllTariffChangeLogsOnce()
        val subscribers = subscriberDao.getAllSubscribersOnce()
        val readings = billingDao.getAllMeterReadingsOnce()
        val invoices = billingDao.getAllInvoicesOnce()
        val collections = collectionDao.getAllCollectionsOnce()
        val receipts = collectionDao.getAllReceiptsOnce()
        val credits = billingDao.getAllCreditTransactionsOnce()
        val reminders = systemDao.getAllRemindersOnce()

        val userById = users.associateBy { it.id }
        val tariffById = tariffs.associateBy { it.id }
        val subById = subscribers.associateBy { it.id }
        val readById = readings.associateBy { it.id }
        val invById = invoices.associateBy { it.id }
        val colById = collections.associateBy { it.id }

        return StationSyncEnvelope(
            deviceId = deviceId,
            deviceName = deviceName,
            exportedAt = System.currentTimeMillis(),
            users = users.map { u ->
                SyncUserDto(
                    username = u.username,
                    fullName = u.fullName,
                    roleCode = u.roleCode,
                    pinHash = u.pinHash,
                    isActive = u.isActive,
                    customPermissionsJson = u.customPermissionsJson,
                    biometricEnabled = u.biometricEnabled,
                    updatedAt = u.updatedAt,
                    syncId = u.syncId,
                    createdAt = u.createdAt
                )
            },
            permissions = permissions.map { p ->
                SyncPermissionDto(
                    roleCode = p.roleCode,
                    permissionKey = p.permissionKey,
                    isAllowed = p.isAllowed,
                    descriptionAr = p.descriptionAr
                )
            },
            settings = settings.filterNot { it.settingKey.startsWith("last_") }.map { s ->
                SyncSettingDto(
                    settingKey = s.settingKey,
                    settingValue = s.settingValue,
                    category = s.category,
                    descriptionAr = s.descriptionAr,
                    updatedAt = s.updatedAt
                )
            },
            tariffs = tariffs.map { t ->
                SyncTariffDto(
                    tariffCode = t.tariffCode,
                    title = t.title,
                    subscriptionType = t.subscriptionType,
                    pricePerKwh = t.pricePerKwh,
                    fixedFee = t.fixedFee,
                    effectiveFrom = t.effectiveFrom,
                    isActive = t.isActive,
                    createdAt = t.createdAt
                )
            },
            tariffLogs = tariffLogs.mapNotNull { l ->
                val tCode = tariffById[l.tariffId]?.tariffCode ?: return@mapNotNull null
                SyncTariffLogDto(
                    tariffCode = tCode,
                    subscriberCode = l.subscriberId?.let { subById[it]?.subscriberCode },
                    subscriberNameSnapshot = l.subscriberNameSnapshot,
                    oldPricePerKwh = l.oldPricePerKwh,
                    newPricePerKwh = l.newPricePerKwh,
                    oldFixedFee = l.oldFixedFee,
                    newFixedFee = l.newFixedFee,
                    effectiveFrom = l.effectiveFrom,
                    actorNameSnapshot = l.actorNameSnapshot,
                    reason = l.reason,
                    changedAt = l.changedAt
                )
            },
            subscribers = subscribers.map { s ->
                SyncSubscriberDto(
                    subscriberCode = s.subscriberCode,
                    fullName = s.fullName,
                    phoneNumber = s.phoneNumber,
                    meterNumber = s.meterNumber,
                    meterSealNumber = s.meterSealNumber,
                    address = s.address,
                    subscriptionType = s.subscriptionType,
                    customKwhPrice = s.customKwhPrice,
                    previousKwhPrice = s.previousKwhPrice,
                    subscriptionDate = s.subscriptionDate,
                    isActive = s.isActive,
                    currentArrearsAmount = s.currentArrearsAmount,
                    currentCreditBalance = s.currentCreditBalance,
                    notes = s.notes,
                    createdAt = s.createdAt,
                    updatedAt = s.updatedAt
                )
            },
            readings = readings.mapNotNull { r ->
                val sCode = subById[r.subscriberId]?.subscriberCode ?: return@mapNotNull null
                SyncReadingDto(
                    subscriberCode = sCode,
                    billingPeriod = r.billingPeriod,
                    previousReading = r.previousReading,
                    currentReading = r.currentReading,
                    consumedKwh = r.consumedKwh,
                    readingDate = r.readingDate,
                    photoUri = r.photoUri,
                    isLowReadingOverride = r.isLowReadingOverride,
                    overrideReason = r.overrideReason,
                    notes = r.notes
                )
            },
            invoices = invoices.mapNotNull { inv ->
                val sCode = subById[inv.subscriberId]?.subscriberCode ?: return@mapNotNull null
                val tCode = tariffById[inv.tariffId]?.tariffCode ?: return@mapNotNull null
                SyncInvoiceDto(
                    invoiceNumber = inv.invoiceNumber,
                    subscriberCode = sCode,
                    readingPeriod = inv.meterReadingId?.let { readById[it]?.billingPeriod },
                    tariffCode = tCode,
                    billingPeriod = inv.billingPeriod,
                    previousReading = inv.previousReading,
                    currentReading = inv.currentReading,
                    consumedKwh = inv.consumedKwh,
                    unitPriceApplied = inv.unitPriceApplied,
                    fixedFeeApplied = inv.fixedFeeApplied,
                    energyAmount = inv.energyAmount,
                    previousArrearsApplied = inv.previousArrearsApplied,
                    creditDeducted = inv.creditDeducted,
                    netPayableAmount = inv.netPayableAmount,
                    paidAmount = inv.paidAmount,
                    remainingAmount = inv.remainingAmount,
                    carriedOverToInvoiceNumber = inv.carriedOverToInvoiceId?.let { invById[it]?.invoiceNumber },
                    status = inv.status,
                    notes = inv.notes,
                    priceModifiedAt = inv.priceModifiedAt,
                    priceModifiedBy = inv.priceModifiedBy,
                    priceModificationReason = inv.priceModificationReason,
                    issuedAt = inv.issuedAt,
                    dueDate = inv.dueDate
                )
            },
            collections = collections.mapNotNull { c ->
                val sCode = subById[c.subscriberId]?.subscriberCode ?: return@mapNotNull null
                val inv = c.invoiceId?.let { invById[it] }
                SyncCollectionDto(
                    collectionReference = c.collectionReference,
                    subscriberCode = sCode,
                    invoiceNumber = inv?.invoiceNumber,
                    invoiceBillingPeriod = inv?.billingPeriod,
                    collectorUsername = c.collectedByUserId?.let { userById[it]?.username },
                    collectorNameSnapshot = c.collectorNameSnapshot,
                    amountPaid = c.amountPaid,
                    previousBalanceSnapshot = c.previousBalanceSnapshot,
                    remainingAfterSnapshot = c.remainingAfterSnapshot,
                    appliedToInvoice = c.appliedToInvoice,
                    appliedToArrears = c.appliedToArrears,
                    addedToCredit = c.addedToCredit,
                    paymentMethod = c.paymentMethod,
                    collectedAt = c.collectedAt,
                    notes = c.notes
                )
            },
            receipts = receipts.mapNotNull { r ->
                val sCode = subById[r.subscriberId]?.subscriberCode ?: return@mapNotNull null
                val colRef = colById[r.collectionId]?.collectionReference ?: return@mapNotNull null
                SyncReceiptDto(
                    receiptNumber = r.receiptNumber,
                    collectionReference = colRef,
                    subscriberCode = sCode,
                    collectorNameSnapshot = r.collectorNameSnapshot,
                    subscriberNameSnapshot = r.subscriberNameSnapshot,
                    subscriberCodeSnapshot = r.subscriberCodeSnapshot,
                    invoiceNumberSnapshot = r.invoiceNumberSnapshot,
                    paymentMethod = r.paymentMethod,
                    previousBalance = r.previousBalance,
                    amount = r.amount,
                    balanceAfterReceipt = r.balanceAfterReceipt,
                    creditAfterReceipt = r.creditAfterReceipt,
                    stationNameSnapshot = r.stationNameSnapshot,
                    stationPhoneSnapshot = r.stationPhoneSnapshot,
                    issuedAt = r.issuedAt,
                    isVoided = r.isVoided
                )
            },
            credits = credits.mapNotNull { cr ->
                val sCode = subById[cr.subscriberId]?.subscriberCode ?: return@mapNotNull null
                SyncCreditDto(
                    subscriberCode = sCode,
                    transactionType = cr.transactionType,
                    amountDelta = cr.amountDelta,
                    balanceAfter = cr.balanceAfter,
                    recordedAt = cr.recordedAt,
                    notes = cr.notes
                )
            },
            reminders = reminders.map { rem ->
                SyncReminderDto(
                    subscriberCode = rem.subscriberId?.let { subById[it]?.subscriberCode },
                    reminderType = rem.reminderType,
                    title = rem.title,
                    message = rem.message,
                    notes = rem.notes,
                    scheduledAt = rem.scheduledAt,
                    status = rem.status,
                    channel = rem.channel,
                    snoozeCount = rem.snoozeCount,
                    completedAt = rem.completedAt,
                    createdAt = rem.createdAt
                )
            }
        ).withComputedChecksum()
    }

    suspend fun mergeIncomingEnvelope(
        incoming: StationSyncEnvelope,
        actorUserId: Long? = null
    ): Result<SyncMergeSummary> = runCatching {
        require(incoming.stationCode == "Alasal2027") { "حزمة المزامنة لا تنتمي إلى محطة العسل 2027" }
        require(incoming.protocolVersion == 1) { "إصدار بروتوكول المزامنة غير مدعوم" }
        require(incoming.verifyChecksum()) { "فشل التحقق من سلامة حزمة المزامنة (تلف أثناء النقل)" }

        database.withTransaction {
            if (actorUserId != null) {
                coreRepository.verifyPermission(actorUserId, StationPermission.MANAGE_SYNC.key)
            }

            var inserted = 0
            var updated = 0
            var skipped = 0
            var conflicts = 0
            var financialPreserved = 0
            var syncedUsers = 0
            var syncedPerms = 0

            // 1. Merge Users (LWW by updatedAt — newer data wins; older never overwrites newer)
            val existingUsers = systemDao.getAllUsersOnce().associateBy { it.username }.toMutableMap()
            incoming.users.forEach { u ->
                val local = existingUsers[u.username]
                if (local == null) {
                    val id = systemDao.insertUser(
                        UserEntity(
                            username = u.username,
                            fullName = u.fullName,
                            roleCode = u.roleCode,
                            pinHash = u.pinHash,
                            isActive = u.isActive,
                            customPermissionsJson = u.customPermissionsJson,
                            biometricEnabled = u.biometricEnabled,
                            updatedAt = u.updatedAt,
                            syncId = u.syncId.ifBlank { "user_${u.username}" },
                            createdAt = u.createdAt
                        )
                    )
                    existingUsers[u.username] = systemDao.getUserById(id)!!
                    inserted++
                    syncedUsers++
                } else if (u.updatedAt > local.updatedAt) {
                    val safeIsActive = if (local.username == "admin") true else u.isActive
                    val updatedUser = local.copy(
                        fullName = u.fullName,
                        roleCode = u.roleCode,
                        pinHash = u.pinHash,
                        isActive = safeIsActive,
                        customPermissionsJson = u.customPermissionsJson,
                        biometricEnabled = u.biometricEnabled,
                        updatedAt = u.updatedAt
                    )
                    systemDao.updateUser(updatedUser)
                    existingUsers[u.username] = updatedUser
                    updated++
                    syncedUsers++
                } else {
                    if (u.updatedAt < local.updatedAt) conflicts++
                    skipped++
                }
            }

            // 2. Sync Role Permissions
            val existingPerms = systemDao.getAllPermissionsOnce().associateBy { it.roleCode to it.permissionKey }
            val permsToUpsert = mutableListOf<PermissionEntity>()
            incoming.permissions.forEach { p ->
                val local = existingPerms[p.roleCode to p.permissionKey]
                if (local == null || local.isAllowed != p.isAllowed) {
                    permsToUpsert.add(
                        PermissionEntity(
                            id = local?.id ?: 0L,
                            roleCode = p.roleCode,
                            permissionKey = p.permissionKey,
                            isAllowed = p.isAllowed,
                            descriptionAr = p.descriptionAr
                        )
                    )
                    syncedPerms++
                }
            }
            if (permsToUpsert.isNotEmpty()) {
                systemDao.insertPermissions(permsToUpsert)
            }

            // 3. Merge App Settings (LWW by updatedAt)
            val localSettings = systemDao.getAllSettingsOnce().associateBy { it.settingKey }
            incoming.settings.forEach { s ->
                val local = localSettings[s.settingKey]
                if (local == null || s.updatedAt > local.updatedAt) {
                    systemDao.upsertSetting(
                        AppSettingEntity(
                            settingKey = s.settingKey,
                            settingValue = s.settingValue,
                            category = s.category,
                            descriptionAr = s.descriptionAr,
                            updatedAt = s.updatedAt
                        )
                    )
                }
            }

            // 4. Merge Tariffs
            val localTariffs = tariffDao.getAllTariffsOnce().associateBy { it.tariffCode }.toMutableMap()
            incoming.tariffs.forEach { t ->
                val local = localTariffs[t.tariffCode]
                if (local == null) {
                    val id = tariffDao.insertTariff(
                        KwhTariffEntity(
                            tariffCode = t.tariffCode,
                            title = t.title,
                            subscriptionType = t.subscriptionType,
                            pricePerKwh = t.pricePerKwh,
                            fixedFee = t.fixedFee,
                            effectiveFrom = t.effectiveFrom,
                            isActive = t.isActive,
                            createdAt = t.createdAt
                        )
                    )
                    localTariffs[t.tariffCode] = tariffDao.getTariffById(id)!!
                    inserted++
                } else if (t.effectiveFrom > local.effectiveFrom) {
                    val up = local.copy(
                        title = t.title,
                        pricePerKwh = t.pricePerKwh,
                        fixedFee = t.fixedFee,
                        effectiveFrom = t.effectiveFrom,
                        isActive = t.isActive
                    )
                    tariffDao.updateTariff(up)
                    localTariffs[t.tariffCode] = up
                    updated++
                }
            }

            // 5. Merge Subscribers (LWW by updatedAt, meterNumber collision protection)
            val localSubsByCode = subscriberDao.getAllSubscribersOnce().associateBy { it.subscriberCode }.toMutableMap()
            val localSubsByMeter = localSubsByCode.values.associateBy { it.meterNumber }.toMutableMap()
            val subCodeRemap = mutableMapOf<String, SubscriberEntity>()

            incoming.subscribers.forEach { s ->
                val existingByCode = localSubsByCode[s.subscriberCode]
                if (existingByCode != null) {
                    subCodeRemap[s.subscriberCode] = existingByCode
                    if (s.updatedAt > existingByCode.updatedAt) {
                        val meterSafe = if (localSubsByMeter[s.meterNumber]?.id in setOf(null, existingByCode.id)) {
                            s.meterNumber
                        } else {
                            conflicts++
                            existingByCode.meterNumber
                        }
                        val next = existingByCode.copy(
                            fullName = s.fullName,
                            phoneNumber = s.phoneNumber,
                            meterNumber = meterSafe,
                            meterSealNumber = s.meterSealNumber,
                            address = s.address,
                            subscriptionType = s.subscriptionType,
                            customKwhPrice = s.customKwhPrice,
                            previousKwhPrice = s.previousKwhPrice,
                            isActive = s.isActive,
                            notes = s.notes,
                            updatedAt = s.updatedAt
                        )
                        subscriberDao.update(next)
                        localSubsByCode[s.subscriberCode] = next
                        updated++
                    } else {
                        if (s.updatedAt < existingByCode.updatedAt) conflicts++
                        skipped++
                    }
                } else {
                    // Check meter collision against a different subscriber code
                    val meterOwner = localSubsByMeter[s.meterNumber]
                    val resolvedMeter = if (meterOwner != null) {
                        conflicts++
                        "${s.meterNumber}-${s.subscriberCode}"
                    } else {
                        s.meterNumber
                    }
                    val newId = subscriberDao.insert(
                        SubscriberEntity(
                            subscriberCode = s.subscriberCode,
                            fullName = s.fullName,
                            phoneNumber = s.phoneNumber,
                            meterNumber = resolvedMeter,
                            meterSealNumber = s.meterSealNumber,
                            address = s.address,
                            subscriptionType = s.subscriptionType,
                            customKwhPrice = s.customKwhPrice,
                            previousKwhPrice = s.previousKwhPrice,
                            subscriptionDate = s.subscriptionDate,
                            isActive = s.isActive,
                            currentArrearsAmount = s.currentArrearsAmount,
                            currentCreditBalance = s.currentCreditBalance,
                            notes = s.notes,
                            createdAt = s.createdAt,
                            updatedAt = s.updatedAt
                        )
                    )
                    val saved = subscriberDao.getById(newId)!!
                    localSubsByCode[s.subscriberCode] = saved
                    localSubsByMeter[resolvedMeter] = saved
                    subCodeRemap[s.subscriberCode] = saved
                    inserted++
                }
            }

            // 6. Tariff Change Logs (append-only deduped)
            val existingTariffLogs = tariffDao.getAllTariffChangeLogsOnce()
            incoming.tariffLogs.forEach { log ->
                val tariff = localTariffs[log.tariffCode] ?: return@forEach
                val sub = log.subscriberCode?.let { subCodeRemap[it] ?: localSubsByCode[it] }
                val dup = existingTariffLogs.any {
                    it.tariffId == tariff.id &&
                        it.subscriberId == sub?.id &&
                        it.changedAt == log.changedAt &&
                        abs(it.newPricePerKwh - log.newPricePerKwh) < 0.001
                }
                if (!dup) {
                    tariffDao.insertTariffChangeLog(
                        TariffChangeLogEntity(
                            tariffId = tariff.id,
                            subscriberId = sub?.id,
                            subscriberNameSnapshot = log.subscriberNameSnapshot,
                            oldPricePerKwh = log.oldPricePerKwh,
                            newPricePerKwh = log.newPricePerKwh,
                            oldFixedFee = log.oldFixedFee,
                            newFixedFee = log.newFixedFee,
                            effectiveFrom = log.effectiveFrom,
                            actorNameSnapshot = log.actorNameSnapshot,
                            reason = log.reason,
                            changedAt = log.changedAt
                        )
                    )
                    inserted++
                }
            }

            // 7. Meter Readings (deduped by subscriber + billingPeriod; keeps newer if not yet invoiced)
            val localReadings = billingDao.getAllMeterReadingsOnce().toMutableList()
            val readingBySubAndPeriod = localReadings.associateBy { it.subscriberId to it.billingPeriod }.toMutableMap()
            val invoicedReadingIds = billingDao.getAllInvoicesOnce().mapNotNull { it.meterReadingId }.toSet()

            incoming.readings.forEach { r ->
                val sub = subCodeRemap[r.subscriberCode] ?: localSubsByCode[r.subscriberCode] ?: return@forEach
                val local = readingBySubAndPeriod[sub.id to r.billingPeriod]
                if (local == null) {
                    val newId = billingDao.insertMeterReading(
                        MeterReadingEntity(
                            subscriberId = sub.id,
                            billingPeriod = r.billingPeriod,
                            previousReading = r.previousReading,
                            currentReading = r.currentReading,
                            consumedKwh = r.consumedKwh,
                            readingDate = r.readingDate,
                            photoUri = r.photoUri,
                            isLowReadingOverride = r.isLowReadingOverride,
                            overrideReason = r.overrideReason,
                            notes = r.notes
                        )
                    )
                    val saved = billingDao.getReadingById(newId)!!
                    readingBySubAndPeriod[sub.id to r.billingPeriod] = saved
                    inserted++
                } else if (local.id !in invoicedReadingIds && r.readingDate > local.readingDate) {
                    val up = local.copy(
                        previousReading = r.previousReading,
                        currentReading = r.currentReading,
                        consumedKwh = r.consumedKwh,
                        readingDate = r.readingDate,
                        notes = r.notes
                    )
                    billingDao.updateMeterReading(up)
                    readingBySubAndPeriod[sub.id to r.billingPeriod] = up
                    updated++
                } else {
                    if (abs(local.currentReading - r.currentReading) > 0.001) conflicts++
                    skipped++
                }
            }

            // 8. Invoices (match by subscriber + period OR invoiceNumber; re-number colliding offline invoices without data loss)
            val allLocalInvoices = billingDao.getAllInvoicesOnce().toMutableList()
            val invByNumber = allLocalInvoices.associateBy { it.invoiceNumber }.toMutableMap()
            val invBySubPeriod = allLocalInvoices.associateBy { it.subscriberId to it.billingPeriod }.toMutableMap()
            val invoiceRemap = mutableMapOf<String, InvoiceEntity>()
            val affectedSubscriberIds = mutableSetOf<Long>()
            val affectedInvoiceIds = mutableSetOf<Long>()

            val defaultTariffId = localTariffs.values.firstOrNull()?.id ?: 1L
            var invSeqCursor = maxOf(
                billingDao.countInvoices(),
                allLocalInvoices.mapNotNull { it.invoiceNumber.substringAfterLast("-").toIntOrNull() }.maxOrNull() ?: 0
            )

            incoming.invoices.forEach { inv ->
                val sub = subCodeRemap[inv.subscriberCode] ?: localSubsByCode[inv.subscriberCode] ?: return@forEach
                affectedSubscriberIds.add(sub.id)
                val matchedLocal = invBySubPeriod[sub.id to inv.billingPeriod]
                    ?: invByNumber[inv.invoiceNumber]?.takeIf { it.subscriberId == sub.id }

                if (matchedLocal != null) {
                    invoiceRemap[inv.invoiceNumber] = matchedLocal
                    val incomingMod = maxOf(inv.issuedAt, inv.priceModifiedAt ?: 0L)
                    val localMod = maxOf(matchedLocal.issuedAt, matchedLocal.priceModifiedAt ?: 0L)
                    if (incomingMod > localMod) {
                        val up = matchedLocal.copy(
                            unitPriceApplied = inv.unitPriceApplied,
                            energyAmount = inv.energyAmount,
                            netPayableAmount = inv.netPayableAmount,
                            notes = inv.notes,
                            priceModifiedAt = inv.priceModifiedAt,
                            priceModifiedBy = inv.priceModifiedBy,
                            priceModificationReason = inv.priceModificationReason
                        )
                        billingDao.updateInvoice(up)
                        invByNumber[up.invoiceNumber] = up
                        invBySubPeriod[sub.id to inv.billingPeriod] = up
                        invoiceRemap[inv.invoiceNumber] = up
                        affectedInvoiceIds.add(up.id)
                        updated++
                    } else {
                        if (incomingMod < localMod) conflicts++
                        skipped++
                    }
                } else {
                    // Check if invoiceNumber already exists for a DIFFERENT subscriber/period
                    val targetNumber = if (invByNumber.containsKey(inv.invoiceNumber)) {
                        conflicts++
                        invSeqCursor++
                        String.format(Locale.US, "INV-2027-%04d", invSeqCursor)
                    } else {
                        inv.invoiceNumber
                    }

                    val targetReading = inv.readingPeriod?.let { period ->
                        readingBySubAndPeriod[sub.id to period]?.id
                    }?.takeIf { rId -> allLocalInvoices.none { it.meterReadingId == rId } }

                    val tariffId = localTariffs[inv.tariffCode]?.id ?: defaultTariffId
                    val newInv = InvoiceEntity(
                        invoiceNumber = targetNumber,
                        subscriberId = sub.id,
                        meterReadingId = targetReading,
                        tariffId = tariffId,
                        billingPeriod = inv.billingPeriod,
                        previousReading = inv.previousReading,
                        currentReading = inv.currentReading,
                        consumedKwh = inv.consumedKwh,
                        unitPriceApplied = inv.unitPriceApplied,
                        fixedFeeApplied = inv.fixedFeeApplied,
                        energyAmount = inv.energyAmount,
                        previousArrearsApplied = inv.previousArrearsApplied,
                        creditDeducted = inv.creditDeducted,
                        netPayableAmount = inv.netPayableAmount,
                        paidAmount = inv.paidAmount,
                        remainingAmount = inv.remainingAmount,
                        carriedOverToInvoiceId = null,
                        status = inv.status,
                        notes = inv.notes,
                        priceModifiedAt = inv.priceModifiedAt,
                        priceModifiedBy = inv.priceModifiedBy,
                        priceModificationReason = inv.priceModificationReason,
                        issuedAt = inv.issuedAt,
                        dueDate = inv.dueDate
                    )
                    val newId = billingDao.insertInvoice(newInv)
                    val saved = billingDao.getInvoiceById(newId)!!
                    allLocalInvoices.add(saved)
                    invByNumber[saved.invoiceNumber] = saved
                    invBySubPeriod[sub.id to saved.billingPeriod] = saved
                    invoiceRemap[inv.invoiceNumber] = saved
                    affectedInvoiceIds.add(saved.id)
                    inserted++
                    financialPreserved++
                }
            }

            // 9. Collections & Receipts (NEVER drop any distinct financial collection or receipt!)
            val allLocalCollections = collectionDao.getAllCollectionsOnce().toMutableList()
            val allLocalReceipts = collectionDao.getAllReceiptsOnce().toMutableList()
            val colByRef = allLocalCollections.associateBy { it.collectionReference }.toMutableMap()
            val rcpByNum = allLocalReceipts.associateBy { it.receiptNumber }.toMutableMap()
            val receiptByColRefInput = incoming.receipts.associateBy { it.collectionReference }

            var colSeqCursor = maxOf(
                systemDao.getSettingValue("last_collection_sequence")?.toIntOrNull() ?: 0,
                allLocalCollections.size,
                allLocalCollections.mapNotNull { it.collectionReference.substringAfterLast("-").toIntOrNull() }.maxOrNull() ?: 0
            )
            var rcpSeqCursor = maxOf(
                systemDao.getSettingValue("last_receipt_sequence")?.toIntOrNull() ?: 0,
                allLocalReceipts.size,
                allLocalReceipts.mapNotNull { it.receiptNumber.substringAfterLast("-").toIntOrNull() }.maxOrNull() ?: 0
            )

            incoming.collections.forEach { col ->
                val sub = subCodeRemap[col.subscriberCode] ?: localSubsByCode[col.subscriberCode] ?: return@forEach
                affectedSubscriberIds.add(sub.id)
                val targetInvoice = col.invoiceNumber?.let { invoiceRemap[it] ?: invByNumber[it] }
                    ?: col.invoiceBillingPeriod?.let { invBySubPeriod[sub.id to it] }

                // Check if exact same collection transaction already exists locally
                val exactMatch = allLocalCollections.firstOrNull { local ->
                    local.subscriberId == sub.id &&
                        abs(local.amountPaid - col.amountPaid) < 0.001 &&
                        local.collectedAt == col.collectedAt &&
                        local.paymentMethod == col.paymentMethod &&
                        local.collectorNameSnapshot == col.collectorNameSnapshot
                }
                if (exactMatch != null) {
                    skipped++
                    return@forEach
                }

                // Collision check on collection_reference (when two devices offline created COL-2027-0001)
                val safeColRef = if (colByRef.containsKey(col.collectionReference)) {
                    conflicts++
                    colSeqCursor++
                    String.format(Locale.US, "COL-2027-%04d", colSeqCursor)
                } else {
                    col.collectionReference
                }

                val collectorId = col.collectorUsername?.let { existingUsers[it]?.id }
                val newCol = CollectionEntity(
                    collectionReference = safeColRef,
                    subscriberId = sub.id,
                    invoiceId = targetInvoice?.id,
                    collectedByUserId = collectorId,
                    collectorNameSnapshot = col.collectorNameSnapshot,
                    amountPaid = col.amountPaid,
                    previousBalanceSnapshot = col.previousBalanceSnapshot,
                    remainingAfterSnapshot = col.remainingAfterSnapshot,
                    appliedToInvoice = col.appliedToInvoice,
                    appliedToArrears = col.appliedToArrears,
                    addedToCredit = col.addedToCredit,
                    paymentMethod = col.paymentMethod,
                    collectedAt = col.collectedAt,
                    notes = col.notes
                )
                val newColId = collectionDao.insertCollection(newCol)
                val savedCol = newCol.copy(id = newColId)
                allLocalCollections.add(savedCol)
                colByRef[safeColRef] = savedCol
                targetInvoice?.let { affectedInvoiceIds.add(it.id) }
                inserted++
                financialPreserved++

                // Create/sync matching 1:1 ReceiptEntity
                val inRcp = receiptByColRefInput[col.collectionReference]
                val desiredRcpNum = inRcp?.receiptNumber ?: "RCP-SYNC-$newColId"
                val safeRcpNum = if (rcpByNum.containsKey(desiredRcpNum)) {
                    conflicts++
                    rcpSeqCursor++
                    String.format(Locale.US, "RCP-2027-%04d", rcpSeqCursor)
                } else {
                    desiredRcpNum
                }
                val newRcp = ReceiptEntity(
                    receiptNumber = safeRcpNum,
                    collectionId = newColId,
                    subscriberId = sub.id,
                    issuedByUserId = collectorId,
                    collectorNameSnapshot = inRcp?.collectorNameSnapshot ?: col.collectorNameSnapshot,
                    subscriberNameSnapshot = inRcp?.subscriberNameSnapshot ?: sub.fullName,
                    subscriberCodeSnapshot = inRcp?.subscriberCodeSnapshot ?: sub.subscriberCode,
                    invoiceNumberSnapshot = targetInvoice?.invoiceNumber ?: (inRcp?.invoiceNumberSnapshot ?: ""),
                    paymentMethod = col.paymentMethod,
                    previousBalance = inRcp?.previousBalance ?: col.previousBalanceSnapshot,
                    amount = col.amountPaid,
                    balanceAfterReceipt = inRcp?.balanceAfterReceipt ?: col.remainingAfterSnapshot,
                    creditAfterReceipt = inRcp?.creditAfterReceipt ?: 0.0,
                    stationNameSnapshot = inRcp?.stationNameSnapshot ?: "محطة العسل 2027",
                    stationPhoneSnapshot = inRcp?.stationPhoneSnapshot ?: "",
                    issuedAt = inRcp?.issuedAt ?: col.collectedAt,
                    isVoided = inRcp?.isVoided ?: false
                )
                collectionDao.insertReceipt(newRcp)
                rcpByNum[safeRcpNum] = newRcp
            }

            // Update persisted sequence counters so subsequent local receipts/collections never collide
            systemDao.upsertSetting(
                AppSettingEntity(
                    settingKey = "last_collection_sequence",
                    settingValue = maxOf(colSeqCursor, allLocalCollections.size).toString(),
                    category = "BILLING",
                    descriptionAr = "آخر رقم تسلسلي محفوظ لعمليات التحصيل"
                )
            )
            systemDao.upsertSetting(
                AppSettingEntity(
                    settingKey = "last_receipt_sequence",
                    settingValue = maxOf(rcpSeqCursor, allLocalReceipts.size + inserted).toString(),
                    category = "BILLING",
                    descriptionAr = "آخر رقم تسلسلي محفوظ للإيصالات"
                )
            )

            // 10. Credit Transactions (append-only deduped)
            val existingCredits = billingDao.getAllCreditTransactionsOnce()
            incoming.credits.forEach { cr ->
                val sub = subCodeRemap[cr.subscriberCode] ?: localSubsByCode[cr.subscriberCode] ?: return@forEach
                val dup = existingCredits.any {
                    it.subscriberId == sub.id &&
                        it.transactionType == cr.transactionType &&
                        abs(it.amountDelta - cr.amountDelta) < 0.001 &&
                        it.recordedAt == cr.recordedAt
                }
                if (!dup) {
                    billingDao.insertCreditTransaction(
                        CreditBalanceEntity(
                            subscriberId = sub.id,
                            transactionType = cr.transactionType,
                            amountDelta = cr.amountDelta,
                            balanceAfter = cr.balanceAfter,
                            recordedAt = cr.recordedAt,
                            notes = cr.notes
                        )
                    )
                    inserted++
                }
            }

            // 11. Reminders (deduped by title + scheduledAt)
            val existingReminders = systemDao.getAllRemindersOnce()
            incoming.reminders.forEach { rem ->
                val sub = rem.subscriberCode?.let { subCodeRemap[it] ?: localSubsByCode[it] }
                val dup = existingReminders.any {
                    it.subscriberId == sub?.id && it.title == rem.title && it.scheduledAt == rem.scheduledAt
                }
                if (!dup) {
                    systemDao.insertReminder(
                        ReminderEntity(
                            subscriberId = sub?.id,
                            reminderType = rem.reminderType,
                            title = rem.title,
                            message = rem.message,
                            notes = rem.notes,
                            scheduledAt = rem.scheduledAt,
                            status = rem.status,
                            channel = rem.channel,
                            snoozeCount = rem.snoozeCount,
                            completedAt = rem.completedAt,
                            createdAt = rem.createdAt
                        )
                    )
                    inserted++
                }
            }

            // 12. Reconcile paid/remaining/status on affected invoices & arrears on affected subscribers
            affectedInvoiceIds.forEach { invId ->
                val inv = billingDao.getInvoiceById(invId) ?: return@forEach
                if (inv.status == "CARRIED_OVER" || inv.status == "CANCELLED") return@forEach
                val cols = collectionDao.getCollectionsForInvoice(invId)
                if (cols.isNotEmpty()) {
                    val totalApplied = FinancialCalculator.roundCurrency(
                        cols.sumOf { minOf(it.amountPaid, it.appliedToInvoice.takeIf { a -> a > 0.0 } ?: it.amountPaid) }
                    )
                    val cappedPaid = minOf(totalApplied, inv.netPayableAmount)
                    val rem = FinancialCalculator.roundCurrency((inv.netPayableAmount - cappedPaid).coerceAtLeast(0.0))
                    val st = when {
                        rem == 0.0 -> "PAID"
                        cappedPaid > 0.0 -> "PARTIAL"
                        else -> "UNPAID"
                    }
                    billingDao.updateInvoice(
                        inv.copy(paidAmount = cappedPaid, remainingAmount = rem, status = st)
                    )
                }
            }

            affectedSubscriberIds.forEach { subId ->
                val sub = subscriberDao.getById(subId) ?: return@forEach
                val activeArrears = FinancialCalculator.roundCurrency(billingDao.sumActiveUnpaidInvoiceBalances(subId))
                subscriberDao.updateFinancialBalances(
                    subscriberId = subId,
                    arrearsAmount = activeArrears,
                    creditBalance = sub.currentCreditBalance
                )
            }

            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    actorUserId = actorUserId,
                    actorNameSnapshot = "مزامنة Wi-Fi",
                    operationType = "WIFI_SYNC_COMPLETED",
                    entityName = "sync_envelope",
                    detailsAr = "مزامنة محلية مع (${incoming.deviceName}): إضافة $inserted، تحديث $updated، تعارضات محلولة $conflicts، عمليات مالية محفوظة $financialPreserved"
                )
            )

            SyncMergeSummary(
                insertedCount = inserted,
                updatedCount = updated,
                skippedOlderOrDuplicateCount = skipped,
                conflictsResolvedCount = conflicts,
                financialOpsPreservedCount = financialPreserved,
                syncedUsersCount = syncedUsers,
                syncedPermissionsCount = syncedPerms
            )
        }
    }
}
