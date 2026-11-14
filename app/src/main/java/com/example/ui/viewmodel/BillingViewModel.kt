package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ArrearsEntity
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.CreditBalanceEntity
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.repository.BillingRepository
import com.example.data.repository.InvoiceDetailBundle
import com.example.data.repository.MeterReadingInput
import com.example.service.AlasalNotificationService
import com.example.service.ThemePreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BillingViewModel(
    private val billingRepository: BillingRepository,
    private val appContext: Context? = null,
    private val themePreferences: ThemePreferencesRepository? = null
) : ViewModel() {

    private val activeSessionUserId: StateFlow<Long?> =
        themePreferences?.backupAndSessionPrefs
            ?.map { it.activeUserId }
            ?.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )
            ?: MutableStateFlow(null)

    private fun currentActorUserId(): Long? = activeSessionUserId.value

    val meterReadings: StateFlow<List<MeterReadingEntity>> =
        billingRepository.observeAllMeterReadings().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val invoices: StateFlow<List<InvoiceEntity>> =
        billingRepository.observeAllInvoices().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val collectionsWithReceipts: StateFlow<List<CollectionWithReceipt>> =
        billingRepository.observeAllCollectionsWithReceipts().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val receipts: StateFlow<List<ReceiptEntity>> =
        billingRepository.observeAllReceipts().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val arrearsRecords: StateFlow<List<ArrearsEntity>> =
        billingRepository.observeAllArrears().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val creditTransactions: StateFlow<List<CreditBalanceEntity>> =
        billingRepository.observeAllCreditTransactions().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedInvoiceDetail = MutableStateFlow<InvoiceDetailBundle?>(null)
    val selectedInvoiceDetail: StateFlow<InvoiceDetailBundle?> =
        _selectedInvoiceDetail.asStateFlow()

    private val _selectedReceipt = MutableStateFlow<ReceiptEntity?>(null)
    val selectedReceipt: StateFlow<ReceiptEntity?> = _selectedReceipt.asStateFlow()

    private val _lastIssuedInvoice = MutableStateFlow<InvoiceEntity?>(null)
    val lastIssuedInvoice: StateFlow<InvoiceEntity?> = _lastIssuedInvoice.asStateFlow()

    private val _feedback = MutableStateFlow<FeedbackMessage?>(null)
    val feedback: StateFlow<FeedbackMessage?> = _feedback.asStateFlow()

    fun clearLastIssuedInvoice() {
        _lastIssuedInvoice.value = null
    }

    fun loadInvoiceDetail(invoiceId: Long) {
        viewModelScope.launch {
            _selectedInvoiceDetail.value = billingRepository.getInvoiceDetailBundle(invoiceId)
        }
    }

    fun clearInvoiceDetail() {
        _selectedInvoiceDetail.value = null
    }

    fun selectReceipt(receipt: ReceiptEntity) {
        _selectedReceipt.value = receipt
    }

    fun clearSelectedReceipt() {
        _selectedReceipt.value = null
    }

    private suspend fun notifyIfAllowed(
        isInvoice: Boolean,
        id: Int,
        title: String,
        body: String
    ) {
        val ctx = appContext ?: return
        val prefs = themePreferences?.getNotificationPrefsOnce() ?: return
        if (!prefs.notificationsEnabled) return
        if (isInvoice && !prefs.invoiceNotificationsEnabled) return
        if (!isInvoice && !prefs.collectionNotificationsEnabled) return
        AlasalNotificationService.dispatchNotification(ctx, id, title, body, prefs)
    }

    fun saveMeterReading(
        input: MeterReadingInput,
        issueInvoiceImmediately: Boolean,
        invoiceNotes: String = "",
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val actorId = currentActorUserId()
            billingRepository.saveMeterReading(input, actorUserId = actorId)
                .onSuccess { readingId ->
                    if (issueInvoiceImmediately) {
                        billingRepository.issueInvoiceForReading(
                            readingId = readingId,
                            notes = invoiceNotes,
                            actorUserId = actorId
                        )
                            .onSuccess { invoiceId ->
                                _lastIssuedInvoice.value = billingRepository.getInvoiceDetailBundle(invoiceId)?.invoice
                                notifyIfAllowed(
                                    isInvoice = true,
                                    id = (3000 + invoiceId).toInt(),
                                    title = "فاتورة جديدة — محطة العسل 2027",
                                    body = "تم إصدار فاتورة جديدة رقم #$invoiceId بنجاح"
                                )
                                _feedback.value = FeedbackMessage.Success(
                                    "تم حفظ القراءة وإصدار الفاتورة رقم #$invoiceId بنجاح"
                                )
                                onComplete()
                            }
                            .onFailure { err ->
                                _feedback.value = FeedbackMessage.Failure(
                                    "تم حفظ القراءة ولكن تعذر إصدار الفاتورة: ${err.message}"
                                )
                            }
                    } else {
                        _feedback.value = FeedbackMessage.Success("تم حفظ قراءة العداد بنجاح")
                        onComplete()
                    }
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر حفظ القراءة")
                }
        }
    }

    fun issueInvoiceForExistingReading(readingId: Long, notes: String = "") {
        viewModelScope.launch {
            billingRepository.issueInvoiceForReading(
                readingId = readingId,
                notes = notes,
                actorUserId = currentActorUserId()
            )
                .onSuccess { invoiceId ->
                    _lastIssuedInvoice.value = billingRepository.getInvoiceDetailBundle(invoiceId)?.invoice
                    notifyIfAllowed(
                        isInvoice = true,
                        id = (3000 + invoiceId).toInt(),
                        title = "فاتورة جديدة — محطة العسل 2027",
                        body = "تم إصدار الفاتورة رقم #$invoiceId بنجاح"
                    )
                    _feedback.value = FeedbackMessage.Success("تم إصدار الفاتورة بنجاح مع جلب السعر وترحيل المتأخرات وتطبيق الرصيد الدائن")
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر إصدار الفاتورة")
                }
        }
    }

    fun recordPaymentOnInvoice(
        invoiceId: Long,
        amountPaid: Double,
        paymentMethod: String = "CASH",
        notes: String = "",
        collectorUserId: Long? = null,
        collectedAt: Long = System.currentTimeMillis(),
        openReceiptOnSuccess: Boolean = false,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val prefs = themePreferences?.getNotificationPrefsOnce()
            val effectiveActorId = currentActorUserId() ?: collectorUserId
            billingRepository.recordInvoicePayment(
                invoiceId = invoiceId,
                amountPaid = amountPaid,
                paymentMethod = paymentMethod,
                notes = notes,
                actorUserId = effectiveActorId,
                collectedAt = collectedAt,
                stationPhone = prefs?.stationPhone ?: "770002027"
            )
                .onSuccess { collectionId ->
                    val receipt = billingRepository.getReceiptByCollectionId(collectionId)
                    _selectedInvoiceDetail.value = billingRepository.getInvoiceDetailBundle(invoiceId)
                    if (openReceiptOnSuccess && receipt != null) {
                        _selectedReceipt.value = receipt
                    }
                    notifyIfAllowed(
                        isInvoice = false,
                        id = (4000 + collectionId).toInt(),
                        title = "تحصيل جديد — محطة العسل 2027",
                        body = "تم تحصيل مبلغ $amountPaid (إيصال ${receipt?.receiptNumber ?: ""})"
                    )
                    _feedback.value = FeedbackMessage.Success(
                        "تم تسجيل دفعة السداد ($amountPaid) وإصدار الإيصال (${receipt?.receiptNumber ?: "-"}) بنجاح"
                    )
                    onDone()
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر تسجيل السداد")
                }
        }
    }

    fun repriceOldInvoice(
        invoiceId: Long,
        newPricePerKwh: Double,
        reason: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            billingRepository.repriceOldInvoice(
                invoiceId = invoiceId,
                newPricePerKwh = newPricePerKwh,
                reason = reason,
                actorUserId = currentActorUserId()
            )
                .onSuccess { updated ->
                    _selectedInvoiceDetail.value = billingRepository.getInvoiceDetailBundle(invoiceId)
                    _feedback.value = FeedbackMessage.Success(
                        "تم تعديل سعر الفاتورة ${updated.invoiceNumber} إلى $newPricePerKwh بنجاح مع الحفاظ على التحصيلات"
                    )
                    onDone()
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر تعديل سعر الفاتورة")
                }
        }
    }

    fun deleteUnpaidInvoice(
        invoiceId: Long,
        reason: String = "حذف فاتورة غير محصلة",
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            billingRepository.deleteUnpaidInvoiceSafely(
                invoiceId = invoiceId,
                reason = reason,
                actorUserId = currentActorUserId()
            )
                .onSuccess { msg ->
                    _selectedInvoiceDetail.value = null
                    _feedback.value = FeedbackMessage.Success(msg)
                    onDone()
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر حذف الفاتورة")
                }
        }
    }

    fun showWarningFeedback(message: String) {
        _feedback.value = FeedbackMessage.Failure(message)
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    class Factory(
        private val billingRepository: BillingRepository,
        private val appContext: Context? = null,
        private val themePreferences: ThemePreferencesRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BillingViewModel(billingRepository, appContext, themePreferences) as T
        }
    }
}
