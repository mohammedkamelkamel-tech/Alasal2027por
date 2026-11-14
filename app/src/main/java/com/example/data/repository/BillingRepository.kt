package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.database.AlasalDatabase
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.ArrearsEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.CreditBalanceEntity
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.domain.auth.StationPermission
import com.example.domain.finance.FinancialCalculator
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class MeterReadingInput(
    val subscriberId: Long,
    val billingPeriod: String,
    val currentReading: Double,
    val readingDate: Long,
    val photoUri: String? = null,
    val allowLowReadingOverride: Boolean = false,
    val overrideReason: String = "",
    val notes: String = ""
)

data class InvoiceDetailBundle(
    val invoice: InvoiceEntity,
    val sourceArrearsInvoices: List<InvoiceEntity>,
    val collections: List<CollectionEntity>
)

/**
 * Dedicated repository for Meter Readings, Invoices, Arrears Carry-Over,
 * Single-Use Credit Application, Repricing, and Partial/Full Collections.
 */
class BillingRepository(
    private val database: AlasalDatabase,
    private val coreRepository: AlasalRepository
) {
    private val subscriberDao = database.subscriberDao()
    private val tariffDao = database.tariffDao()
    private val billingDao = database.billingDao()
    private val collectionDao = database.collectionDao()
    private val systemDao = database.systemDao()

    fun observeAllMeterReadings(): Flow<List<MeterReadingEntity>> =
        billingDao.observeAllMeterReadings()

    fun observeAllInvoices(): Flow<List<InvoiceEntity>> =
        billingDao.observeAllInvoices()

    fun observeAllCreditTransactions(): Flow<List<CreditBalanceEntity>> =
        billingDao.observeAllCreditTransactions()

    fun observeAllArrears(): Flow<List<ArrearsEntity>> =
        billingDao.observeAllArrears()

    fun observeAllCollectionsWithReceipts(): Flow<List<CollectionWithReceipt>> =
        collectionDao.observeAllCollectionsWithReceipts()

    fun observeAllReceipts(): Flow<List<ReceiptEntity>> =
        collectionDao.observeAllReceipts()

    suspend fun getReceiptByCollectionId(collectionId: Long): ReceiptEntity? =
        collectionDao.getReceiptByCollectionId(collectionId)

    suspend fun getLatestReadingForSubscriber(subscriberId: Long): MeterReadingEntity? =
        billingDao.getLatestReadingForSubscriber(subscriberId)

    suspend fun getInvoiceDetailBundle(invoiceId: Long): InvoiceDetailBundle? {
        val invoice = billingDao.getInvoiceById(invoiceId) ?: return null
        val sourceArrears = billingDao.getInvoicesCarriedOverInto(invoiceId)
        val collections = collectionDao.getCollectionsForInvoice(invoiceId)
        return InvoiceDetailBundle(
            invoice = invoice,
            sourceArrearsInvoices = sourceArrears,
            collections = collections
        )
    }

    suspend fun saveMeterReading(
        input: MeterReadingInput,
        actorUserId: Long? = null
    ): Result<Long> = runCatching {
        val period = input.billingPeriod.trim()
        require(period.isNotEmpty()) { "الفترة المحاسبية مطلوبة (مثال: 2027-02)" }

        database.withTransaction {
            val actor = coreRepository.resolveActorUser(actorUserId)
            require(actor.isActive) { "تم رفض العملية: حساب (${actor.fullName}) موقوف" }
            val subscriber = subscriberDao.getById(input.subscriberId) ?: error("المشترك غير موجود")
            val lastReading = billingDao.getLatestReadingForSubscriber(input.subscriberId)
            val previousReading = lastReading?.currentReading ?: subscriber.initialMeterReading

            if (input.currentReading < previousReading) {
                require(input.allowLowReadingOverride) {
                    "القراءة الحالية (${input.currentReading}) أقل من السابقة ($previousReading). يلزم صلاحية وتأكيد المدير."
                }
                coreRepository.verifyPermission(actorUserId, StationPermission.OVERRIDE_LOW_READING.key)
                require(input.overrideReason.trim().isNotEmpty()) {
                    "يرجى كتابة سبب اعتماد القراءة الأقل من السابقة"
                }
            }

            val consumedKwh = FinancialCalculator.calculateConsumption(
                previousReading = previousReading,
                currentReading = input.currentReading,
                allowLowReadingOverride = input.allowLowReadingOverride
            )

            val readingId = billingDao.insertMeterReading(
                MeterReadingEntity(
                    subscriberId = subscriber.id,
                    billingPeriod = period,
                    previousReading = previousReading,
                    currentReading = FinancialCalculator.roundCurrency(input.currentReading),
                    consumedKwh = consumedKwh,
                    recordedByUserId = actor.id,
                    readingDate = input.readingDate,
                    photoUri = input.photoUri,
                    isLowReadingOverride = input.currentReading < previousReading,
                    overrideReason = input.overrideReason.trim(),
                    notes = input.notes.trim()
                )
            )

            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    subscriberId = subscriber.id,
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "METER_READING_SAVED",
                    entityName = "meter_readings",
                    entityId = readingId,
                    amountBefore = previousReading,
                    amountAfter = input.currentReading,
                    detailsAr = "تسجيل قراءة عداد (${subscriber.fullName}): السابقة $previousReading، الحالية ${input.currentReading}، الاستهلاك $consumedKwh ك.و.س"
                )
            )
            readingId
        }
    }

    /**
     * Issues a new invoice from a saved meter reading (or creates the reading first if needed).
     * Automatically fetches subscriber's current kWh price, carries forward ONLY non-carried-over
     * unpaid balances (preventing debt duplication), and applies subscriber credit balance ONCE.
     */
    suspend fun issueInvoiceForReading(
        readingId: Long,
        notes: String = "",
        dueDays: Int = 14,
        actorUserId: Long? = null
    ): Result<Long> = runCatching {
        database.withTransaction {
            val actor = coreRepository.verifyPermission(actorUserId, StationPermission.ISSUE_INVOICE.key)
            val existingForReading = billingDao.getInvoiceByReadingId(readingId)
            require(existingForReading == null) {
                "تم إصدار فاتورة مسبقاً لهذه القراءة برقم ${existingForReading?.invoiceNumber}"
            }

            val reading = billingDao.getReadingById(readingId) ?: error("قراءة العداد غير موجودة")
            val subscriber = subscriberDao.getById(reading.subscriberId) ?: error("المشترك غير موجود")
            val tariff = tariffDao.getPrimaryActiveTariffOnce()
                ?: error("لا توجد شريحة تعرفة نشطة")

            // 1. Collect ONLY active unpaid/partial invoices not yet carried over (prevents double counting!)
            val unpaidInvoices = billingDao.getActiveUnpaidInvoicesForCarryOver(subscriber.id)
            val openStandaloneArrears = billingDao.getOpenArrearsForSubscriber(subscriber.id)
                .filter { it.sourceInvoiceId == null }

            val invoicesArrears = unpaidInvoices.sumOf { it.remainingAmount }
            val standaloneArrears = openStandaloneArrears.sumOf { it.remainingAmount }
            val previousArrears = FinancialCalculator.roundCurrency(invoicesArrears + standaloneArrears)

            val availableCredit = FinancialCalculator.roundCurrency(subscriber.currentCreditBalance)
            val appliedPrice = FinancialCalculator.roundCurrency(subscriber.customKwhPrice)

            val calc = FinancialCalculator.calculateInvoice(
                consumedKwh = reading.consumedKwh,
                pricePerKwh = appliedPrice,
                fixedFee = 0.0,
                previousArrears = previousArrears,
                availableCredit = availableCredit
            )

            val now = System.currentTimeMillis()
            val storedInvSeq = systemDao.getSettingValue("last_invoice_sequence")?.toIntOrNull() ?: 0
            var sequenceNum = maxOf(storedInvSeq, billingDao.countInvoices()) + 1
            var invoiceNumber = String.format(Locale.US, "INV-2027-%04d", sequenceNum)
            while (billingDao.getInvoiceByNumber(invoiceNumber) != null) {
                sequenceNum++
                invoiceNumber = String.format(Locale.US, "INV-2027-%04d", sequenceNum)
            }
            systemDao.upsertSetting(
                AppSettingEntity(
                    settingKey = "last_invoice_sequence",
                    settingValue = sequenceNum.toString(),
                    category = "BILLING",
                    descriptionAr = "آخر رقم تسلسلي محفوظ للفواتير"
                )
            )
            val initialStatus = if (calc.netPayableAmount == 0.0) "PAID" else "UNPAID"

            val newInvoiceId = billingDao.insertInvoice(
                InvoiceEntity(
                    invoiceNumber = invoiceNumber,
                    subscriberId = subscriber.id,
                    meterReadingId = reading.id,
                    tariffId = tariff.id,
                    billingPeriod = reading.billingPeriod,
                    previousReading = reading.previousReading,
                    currentReading = reading.currentReading,
                    consumedKwh = calc.consumedKwh,
                    unitPriceApplied = appliedPrice,
                    fixedFeeApplied = 0.0,
                    energyAmount = calc.energyAmount,
                    previousArrearsApplied = previousArrears,
                    creditDeducted = calc.creditDeducted,
                    netPayableAmount = calc.netPayableAmount,
                    paidAmount = 0.0,
                    remainingAmount = calc.netPayableAmount,
                    carriedOverToInvoiceId = null,
                    status = initialStatus,
                    notes = notes.trim(),
                    issuedAt = now,
                    dueDate = now + dueDays.toLong() * 24L * 60L * 60L * 1000L
                )
            )

            // 2. Mark source unpaid invoices as CARRIED_OVER into newInvoiceId so they are NEVER carried over again
            unpaidInvoices.forEach { oldInv ->
                billingDao.markInvoiceCarriedOver(oldInv.id, newInvoiceId)
                billingDao.insertArrears(
                    ArrearsEntity(
                        subscriberId = subscriber.id,
                        sourceInvoiceId = oldInv.id,
                        carriedOverToInvoiceId = newInvoiceId,
                        originalAmount = oldInv.remainingAmount,
                        remainingAmount = 0.0,
                        status = "CARRIED_OVER",
                        recordedAt = now,
                        notes = "ترحيل متبقي الفاتورة ${oldInv.invoiceNumber} إلى $invoiceNumber"
                    )
                )
            }
            openStandaloneArrears.forEach { arr ->
                billingDao.updateArrears(
                    arr.copy(
                        carriedOverToInvoiceId = newInvoiceId,
                        status = "CARRIED_OVER"
                    )
                )
            }

            // 3. Deduct credit ONCE and write immutable ledger entry
            val newCreditBalance = FinancialCalculator.roundCurrency(availableCredit - calc.creditDeducted)
            if (calc.creditDeducted > 0.0) {
                billingDao.insertCreditTransaction(
                    CreditBalanceEntity(
                        subscriberId = subscriber.id,
                        sourceInvoiceId = newInvoiceId,
                        transactionType = "APPLIED_TO_INVOICE",
                        amountDelta = -calc.creditDeducted,
                        balanceAfter = newCreditBalance,
                        recordedAt = now,
                        notes = "خصم رصيد دائن مرة واحدة على الفاتورة $invoiceNumber"
                    )
                )
            }

            // 4. Sync subscriber's net arrears & credit balance
            val activeTotalArrears = billingDao.sumActiveUnpaidInvoiceBalances(subscriber.id)
            subscriberDao.updateFinancialBalances(
                subscriberId = subscriber.id,
                arrearsAmount = FinancialCalculator.roundCurrency(activeTotalArrears),
                creditBalance = newCreditBalance,
                updatedAt = now
            )

            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    subscriberId = subscriber.id,
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "INVOICE_ISSUED",
                    entityName = "invoices",
                    entityId = newInvoiceId,
                    amountBefore = previousArrears,
                    amountAfter = calc.netPayableAmount,
                    detailsAr = "إصدار فاتورة $invoiceNumber (${subscriber.fullName}): استهلاك ${calc.consumedKwh} × $appliedPrice = ${calc.energyAmount}، متأخرات مرحلة $previousArrears، خصم دائن ${calc.creditDeducted}، الصافي ${calc.netPayableAmount}",
                    createdAt = now
                )
            )

            newInvoiceId
        }
    }

    /**
     * Records a partial, full, or multiple collection on an invoice, issuing a persistently sequenced
     * unique receipt with full collector, subscriber, and before/after balance snapshots.
     */
    suspend fun recordInvoicePayment(
        invoiceId: Long,
        amountPaid: Double,
        paymentMethod: String = "CASH",
        notes: String = "",
        actorUserId: Long? = null,
        collectedAt: Long = System.currentTimeMillis(),
        stationName: String = "محطة العسل 2027",
        stationPhone: String = "770002027"
    ): Result<Long> = runCatching {
        require(amountPaid > 0.0) { "مبلغ السداد يجب أن يكون أكبر من الصفر" }

        database.withTransaction {
            val actor = coreRepository.verifyPermission(actorUserId, StationPermission.RECORD_COLLECTION.key)
            val invoice = billingDao.getInvoiceById(invoiceId) ?: error("الفاتورة غير موجودة")
            require(invoice.status != "CARRIED_OVER" && invoice.status != "CANCELLED") {
                "لا يمكن التحصيل على فاتورة مرحّلة أو ملغاة؛ يرجى التحصيل على أحدث فاتورة نشطة للمشترك"
            }

            val subscriber = subscriberDao.getById(invoice.subscriberId) ?: error("المشترك غير موجود")
            val previousRemaining = FinancialCalculator.roundCurrency(invoice.remainingAmount)
            val allocation = FinancialCalculator.allocateCollection(
                amountPaid = amountPaid,
                currentInvoiceRemaining = previousRemaining,
                existingArrearsRemaining = 0.0,
                existingCreditBalance = subscriber.currentCreditBalance
            )

            val newPaid = FinancialCalculator.roundCurrency(invoice.paidAmount + allocation.appliedToInvoice)
            val newRemaining = allocation.remainingInvoiceBalance
            val newStatus = when {
                newRemaining == 0.0 -> "PAID"
                newPaid > 0.0 -> "PARTIAL"
                else -> "UNPAID"
            }

            billingDao.updateInvoice(
                invoice.copy(
                    paidAmount = newPaid,
                    remainingAmount = newRemaining,
                    status = newStatus
                )
            )

            val storedColSeq = systemDao.getSettingValue("last_collection_sequence")?.toIntOrNull() ?: 0
            var colSeq = maxOf(storedColSeq, collectionDao.countCollections()) + 1
            var colRef = String.format(Locale.US, "COL-2027-%04d", colSeq)
            while (collectionDao.getCollectionByReference(colRef) != null) {
                colSeq++
                colRef = String.format(Locale.US, "COL-2027-%04d", colSeq)
            }
            systemDao.upsertSetting(
                AppSettingEntity(
                    settingKey = "last_collection_sequence",
                    settingValue = colSeq.toString(),
                    category = "BILLING",
                    descriptionAr = "آخر رقم تسلسلي محفوظ لعمليات التحصيل"
                )
            )

            val collectionId = collectionDao.insertCollection(
                CollectionEntity(
                    collectionReference = colRef,
                    subscriberId = subscriber.id,
                    invoiceId = invoice.id,
                    collectedByUserId = actor.id,
                    collectorNameSnapshot = actor.fullName,
                    amountPaid = allocation.amountPaid,
                    previousBalanceSnapshot = previousRemaining,
                    remainingAfterSnapshot = newRemaining,
                    appliedToInvoice = allocation.appliedToInvoice,
                    appliedToArrears = allocation.appliedToArrears,
                    addedToCredit = allocation.addedToCredit,
                    paymentMethod = paymentMethod,
                    collectedAt = collectedAt,
                    notes = notes.trim()
                )
            )

            if (allocation.addedToCredit > 0.0) {
                billingDao.insertCreditTransaction(
                    CreditBalanceEntity(
                        subscriberId = subscriber.id,
                        sourceCollectionId = collectionId,
                        sourceInvoiceId = invoice.id,
                        transactionType = "DEPOSIT_OVERPAYMENT",
                        amountDelta = allocation.addedToCredit,
                        balanceAfter = allocation.resultingCreditBalance,
                        recordedAt = collectedAt,
                        notes = "فائض سداد من الفاتورة ${invoice.invoiceNumber} ($colRef)"
                    )
                )
            }

            val updatedTotalArrears = billingDao.sumActiveUnpaidInvoiceBalances(subscriber.id)
            subscriberDao.updateFinancialBalances(
                subscriberId = subscriber.id,
                arrearsAmount = FinancialCalculator.roundCurrency(updatedTotalArrears),
                creditBalance = allocation.resultingCreditBalance,
                updatedAt = collectedAt
            )

            val storedRcpSeq = systemDao.getSettingValue("last_receipt_sequence")?.toIntOrNull() ?: 0
            var rcpSeq = maxOf(storedRcpSeq, collectionDao.countReceipts()) + 1
            var rcpNum = String.format(Locale.US, "RCP-2027-%04d", rcpSeq)
            while (collectionDao.getReceiptByNumber(rcpNum) != null) {
                rcpSeq++
                rcpNum = String.format(Locale.US, "RCP-2027-%04d", rcpSeq)
            }
            systemDao.upsertSetting(
                AppSettingEntity(
                    settingKey = "last_receipt_sequence",
                    settingValue = rcpSeq.toString(),
                    category = "BILLING",
                    descriptionAr = "آخر رقم تسلسلي محفوظ للإيصالات"
                )
            )

            collectionDao.insertReceipt(
                ReceiptEntity(
                    receiptNumber = rcpNum,
                    collectionId = collectionId,
                    subscriberId = subscriber.id,
                    issuedByUserId = actor.id,
                    collectorNameSnapshot = actor.fullName,
                    subscriberNameSnapshot = subscriber.fullName,
                    subscriberCodeSnapshot = subscriber.subscriberCode,
                    invoiceNumberSnapshot = invoice.invoiceNumber,
                    paymentMethod = paymentMethod,
                    previousBalance = previousRemaining,
                    amount = allocation.amountPaid,
                    balanceAfterReceipt = newRemaining,
                    creditAfterReceipt = allocation.resultingCreditBalance,
                    stationNameSnapshot = stationName,
                    stationPhoneSnapshot = stationPhone,
                    issuedAt = collectedAt
                )
            )

            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    subscriberId = subscriber.id,
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "COLLECTION_RECORDED",
                    entityName = "collections",
                    entityId = collectionId,
                    amountBefore = previousRemaining,
                    amountAfter = newRemaining,
                    detailsAr = "سداد ${allocation.amountPaid} بواسطة ${actor.fullName} على فاتورة ${invoice.invoiceNumber} (إيصال $rcpNum) — السابق $previousRemaining، المتبقي $newRemaining، الفائض للدائن ${allocation.addedToCredit}",
                    createdAt = collectedAt
                )
            )

            collectionId
        }
    }

    /**
     * Explicitly reprices an existing (even partially or fully paid) invoice with authorization,
     * preserving all previous collections, recalculating net/remaining/status, crediting any overpayment
     * or refunding excess deducted credit, and logging the full audit trail.
     */
    suspend fun repriceOldInvoice(
        invoiceId: Long,
        newPricePerKwh: Double,
        reason: String,
        actorUserId: Long? = null
    ): Result<InvoiceEntity> = runCatching {
        require(newPricePerKwh >= 0.0) { "سعر الكيلوواط الجديد غير صالح" }
        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) { "يجب إدخال سبب تعديل سعر الفاتورة القديمة" }

        database.withTransaction {
            val actor = coreRepository.verifyPermission(actorUserId, StationPermission.EDIT_INVOICE.key)
            coreRepository.verifyPermission(actorUserId, StationPermission.EDIT_KWH_PRICE.key)
            val invoice = billingDao.getInvoiceById(invoiceId) ?: error("الفاتورة غير موجودة")
            require(invoice.status != "CARRIED_OVER") {
                "هذه الفاتورة مرحّلة بالفعل إلى فاتورة أحدث؛ يرجى تعديل الفاتورة النشطة"
            }

            val subscriber = subscriberDao.getById(invoice.subscriberId) ?: error("المشترك غير موجود")
            val roundedNewPrice = FinancialCalculator.roundCurrency(newPricePerKwh)
            val oldPrice = invoice.unitPriceApplied
            val oldNet = invoice.netPayableAmount

            val reprice = FinancialCalculator.recalculateInvoiceAfterPriceEdit(
                consumedKwh = invoice.consumedKwh,
                newPricePerKwh = roundedNewPrice,
                fixedFeeApplied = invoice.fixedFeeApplied,
                previousArrearsApplied = invoice.previousArrearsApplied,
                existingCreditDeducted = invoice.creditDeducted,
                alreadyPaidAmount = invoice.paidAmount
            )

            val now = System.currentTimeMillis()
            val updatedInvoice = invoice.copy(
                unitPriceApplied = roundedNewPrice,
                energyAmount = reprice.newEnergyAmount,
                creditDeducted = reprice.adjustedCreditDeducted,
                netPayableAmount = reprice.newNetPayableAmount,
                remainingAmount = reprice.newRemainingAmount,
                status = reprice.newStatus,
                priceModifiedAt = now,
                priceModifiedBy = actor.fullName,
                priceModificationReason = cleanReason
            )
            billingDao.updateInvoice(updatedInvoice)

            var runningCredit = subscriber.currentCreditBalance
            if (reprice.creditRefundedToSubscriber > 0.0) {
                runningCredit = FinancialCalculator.roundCurrency(runningCredit + reprice.creditRefundedToSubscriber)
                billingDao.insertCreditTransaction(
                    CreditBalanceEntity(
                        subscriberId = subscriber.id,
                        sourceInvoiceId = invoice.id,
                        transactionType = "REVERSAL_ON_PRICE_EDIT",
                        amountDelta = reprice.creditRefundedToSubscriber,
                        balanceAfter = runningCredit,
                        recordedAt = now,
                        notes = "إرجاع فائض رصيد دائن بعد تعديل سعر فاتورة ${invoice.invoiceNumber}"
                    )
                )
            }
            if (reprice.overpaymentCreditedToSubscriber > 0.0) {
                runningCredit = FinancialCalculator.roundCurrency(runningCredit + reprice.overpaymentCreditedToSubscriber)
                billingDao.insertCreditTransaction(
                    CreditBalanceEntity(
                        subscriberId = subscriber.id,
                        sourceInvoiceId = invoice.id,
                        transactionType = "DEPOSIT_OVERPAYMENT",
                        amountDelta = reprice.overpaymentCreditedToSubscriber,
                        balanceAfter = runningCredit,
                        recordedAt = now,
                        notes = "فائض تحصيلات ناتج عن تخفيض سعر فاتورة ${invoice.invoiceNumber}"
                    )
                )
            }

            val updatedTotalArrears = billingDao.sumActiveUnpaidInvoiceBalances(subscriber.id)
            subscriberDao.updateFinancialBalances(
                subscriberId = subscriber.id,
                arrearsAmount = FinancialCalculator.roundCurrency(updatedTotalArrears),
                creditBalance = runningCredit,
                updatedAt = now
            )

            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    subscriberId = subscriber.id,
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "INVOICE_REPRICED",
                    entityName = "invoices",
                    entityId = invoice.id,
                    amountBefore = oldNet,
                    amountAfter = reprice.newNetPayableAmount,
                    detailsAr = "تعديل سعر فاتورة ${invoice.invoiceNumber} من $oldPrice إلى $roundedNewPrice بواسطة ${actor.fullName} — السبب: $cleanReason (المدفوع المحفوظ: ${invoice.paidAmount}، المتبقي الجديد: ${reprice.newRemainingAmount})",
                    createdAt = now
                )
            )

            updatedInvoice
        }
    }

    /**
     * Programmatically enforces DELETE_INVOICE permission and deletes an invoice ONLY
     * if it has no collections and has not been carried over into another invoice.
     */
    suspend fun deleteUnpaidInvoiceSafely(
        invoiceId: Long,
        reason: String = "حذف فاتورة غير محصلة",
        actorUserId: Long? = null
    ): Result<String> = runCatching {
        database.withTransaction {
            val actor = coreRepository.verifyPermission(actorUserId, StationPermission.DELETE_INVOICE.key)
            val invoice = billingDao.getInvoiceById(invoiceId) ?: error("الفاتورة غير موجودة")
            val collections = collectionDao.getCollectionsForInvoice(invoiceId)
            require(collections.isEmpty() && invoice.paidAmount == 0.0) {
                "لا يمكن حذف الفاتورة ${invoice.invoiceNumber} لأنها تحتوي على تحصيلات مسجلة"
            }
            require(invoice.carriedOverToInvoiceId == null) {
                "لا يمكن حذف الفاتورة ${invoice.invoiceNumber} لأنها مرحّلة إلى فاتورة أحدث"
            }

            val subscriber = subscriberDao.getById(invoice.subscriberId) ?: error("المشترك غير موجود")
            val now = System.currentTimeMillis()
            var nextCredit = subscriber.currentCreditBalance
            if (invoice.creditDeducted > 0.0) {
                nextCredit = FinancialCalculator.roundCurrency(nextCredit + invoice.creditDeducted)
                billingDao.insertCreditTransaction(
                    CreditBalanceEntity(
                        subscriberId = subscriber.id,
                        sourceInvoiceId = null,
                        transactionType = "REVERSAL_ON_INVOICE_DELETE",
                        amountDelta = invoice.creditDeducted,
                        balanceAfter = nextCredit,
                        recordedAt = now,
                        notes = "إرجاع الرصيد الدائن بعد حذف الفاتورة ${invoice.invoiceNumber}"
                    )
                )
            }

            billingDao.restoreCarriedOverInvoicesForDeleted(invoice.id)
            billingDao.deleteCarryOverArrearsForDeletedInvoice(invoice.id)
            billingDao.deleteInvoiceById(invoice.id)

            val updatedArrears = billingDao.sumActiveUnpaidInvoiceBalances(subscriber.id)
            subscriberDao.updateFinancialBalances(
                subscriberId = subscriber.id,
                arrearsAmount = FinancialCalculator.roundCurrency(updatedArrears),
                creditBalance = nextCredit,
                updatedAt = now
            )

            systemDao.insertAuditLog(
                FinancialAuditLogEntity(
                    subscriberId = subscriber.id,
                    actorUserId = actor.id,
                    actorNameSnapshot = actor.fullName,
                    operationType = "INVOICE_DELETED",
                    entityName = "invoices",
                    entityId = invoice.id,
                    amountBefore = invoice.netPayableAmount,
                    amountAfter = 0.0,
                    detailsAr = "حذف الفاتورة ${invoice.invoiceNumber} بواسطة ${actor.fullName} — السبب: ${reason.trim().ifEmpty { "حذف يدوي" }}",
                    createdAt = now
                )
            )
            "تم حذف الفاتورة ${invoice.invoiceNumber} واسترجاع المتأخرات/الدائن المرتبط بها بنجاح"
        }
    }
}
