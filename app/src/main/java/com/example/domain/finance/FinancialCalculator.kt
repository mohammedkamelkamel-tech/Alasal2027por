package com.example.domain.finance

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Pure Kotlin financial calculation engine for Alasal2027.
 * Strictly decoupled from UI and database layers for deterministic testing.
 */
object FinancialCalculator {

    data class InvoiceCalculationResult(
        val consumedKwh: Double,
        val energyAmount: Double,
        val totalDueBeforeCredit: Double,
        val creditDeducted: Double,
        val netPayableAmount: Double
    )

    data class CollectionAllocationResult(
        val amountPaid: Double,
        val appliedToInvoice: Double,
        val appliedToArrears: Double,
        val addedToCredit: Double,
        val remainingInvoiceBalance: Double,
        val remainingArrearsBalance: Double,
        val resultingCreditBalance: Double
    )

    data class InvoiceRepriceResult(
        val newEnergyAmount: Double,
        val newTotalDueBeforeCredit: Double,
        val adjustedCreditDeducted: Double,
        val creditRefundedToSubscriber: Double,
        val newNetPayableAmount: Double,
        val newRemainingAmount: Double,
        val overpaymentCreditedToSubscriber: Double,
        val newStatus: String
    )

    fun roundCurrency(value: Double): Double {
        return (value * 100.0).roundToLong() / 100.0
    }

    fun calculateConsumption(
        previousReading: Double,
        currentReading: Double,
        allowLowReadingOverride: Boolean = false
    ): Double {
        require(previousReading >= 0.0) { "القراءة السابقة لا يمكن أن تكون سالبة" }
        require(currentReading >= 0.0) { "القراءة الحالية لا يمكن أن تكون سالبة" }

        if (currentReading < previousReading) {
            require(allowLowReadingOverride) {
                "القراءة الحالية ($currentReading) أقل من القراءة السابقة ($previousReading)؛ يلزم صلاحية وتأكيد المدير"
            }
            // On authorized meter reset/replacement, current reading represents consumption since reset
            return roundCurrency(currentReading)
        }
        return roundCurrency(currentReading - previousReading)
    }

    fun calculateInvoice(
        consumedKwh: Double,
        pricePerKwh: Double,
        fixedFee: Double,
        previousArrears: Double,
        availableCredit: Double
    ): InvoiceCalculationResult {
        require(consumedKwh >= 0.0) { "كمية الاستهلاك لا يمكن أن تكون سالبة" }
        require(pricePerKwh >= 0.0) { "سعر الكيلوواط لا يمكن أن يكون سالباً" }
        require(fixedFee >= 0.0) { "الرسوم الثابتة لا يمكن أن تكون سالبة" }
        require(previousArrears >= 0.0) { "المتأخرات لا يمكن أن تكون سالبة" }
        require(availableCredit >= 0.0) { "الرصيد الدائن لا يمكن أن يكون سالباً" }

        val energyAmount = roundCurrency(consumedKwh * pricePerKwh)
        val totalDueBeforeCredit = roundCurrency(energyAmount + fixedFee + previousArrears)
        val creditDeducted = roundCurrency(minOf(totalDueBeforeCredit, availableCredit))
        val netPayable = roundCurrency(max(0.0, totalDueBeforeCredit - creditDeducted))

        return InvoiceCalculationResult(
            consumedKwh = roundCurrency(consumedKwh),
            energyAmount = energyAmount,
            totalDueBeforeCredit = totalDueBeforeCredit,
            creditDeducted = creditDeducted,
            netPayableAmount = netPayable
        )
    }

    fun allocateCollection(
        amountPaid: Double,
        currentInvoiceRemaining: Double,
        existingArrearsRemaining: Double,
        existingCreditBalance: Double
    ): CollectionAllocationResult {
        require(amountPaid > 0.0) { "مبلغ التحصيل يجب أن يكون أكبر من الصفر" }
        require(currentInvoiceRemaining >= 0.0) { "رصيد الفاتورة المتبقي غير صالح" }
        require(existingArrearsRemaining >= 0.0) { "رصيد المتأخرات غير صالح" }
        require(existingCreditBalance >= 0.0) { "الرصيد الدائن غير صالح" }

        var remainingPayment = roundCurrency(amountPaid)

        val appliedToInvoice = roundCurrency(minOf(remainingPayment, currentInvoiceRemaining))
        remainingPayment = roundCurrency(remainingPayment - appliedToInvoice)

        val appliedToArrears = roundCurrency(minOf(remainingPayment, existingArrearsRemaining))
        remainingPayment = roundCurrency(remainingPayment - appliedToArrears)

        val addedToCredit = roundCurrency(remainingPayment)

        return CollectionAllocationResult(
            amountPaid = roundCurrency(amountPaid),
            appliedToInvoice = appliedToInvoice,
            appliedToArrears = appliedToArrears,
            addedToCredit = addedToCredit,
            remainingInvoiceBalance = roundCurrency(max(0.0, currentInvoiceRemaining - appliedToInvoice)),
            remainingArrearsBalance = roundCurrency(max(0.0, existingArrearsRemaining - appliedToArrears)),
            resultingCreditBalance = roundCurrency(existingCreditBalance + addedToCredit)
        )
    }

    fun recalculateInvoiceAfterPriceEdit(
        consumedKwh: Double,
        newPricePerKwh: Double,
        fixedFeeApplied: Double,
        previousArrearsApplied: Double,
        existingCreditDeducted: Double,
        alreadyPaidAmount: Double
    ): InvoiceRepriceResult {
        require(newPricePerKwh >= 0.0) { "سعر الكيلوواط الجديد لا يمكن أن يكون سالباً" }
        require(consumedKwh >= 0.0) { "كمية الاستهلاك غير صالحة" }
        require(alreadyPaidAmount >= 0.0) { "المبلغ المدفوع غير صالح" }

        val newEnergy = roundCurrency(consumedKwh * newPricePerKwh)
        val newTotalDueBeforeCredit = roundCurrency(newEnergy + fixedFeeApplied + previousArrearsApplied)
        val adjustedCreditDeducted = roundCurrency(minOf(existingCreditDeducted, newTotalDueBeforeCredit))
        val creditRefunded = roundCurrency(max(0.0, existingCreditDeducted - adjustedCreditDeducted))
        val newNetPayable = roundCurrency(max(0.0, newTotalDueBeforeCredit - adjustedCreditDeducted))

        val overpaymentCredited: Double
        val newRemaining: Double
        val newStatus: String

        if (alreadyPaidAmount >= newNetPayable) {
            overpaymentCredited = roundCurrency(alreadyPaidAmount - newNetPayable)
            newRemaining = 0.0
            newStatus = "PAID"
        } else {
            overpaymentCredited = 0.0
            newRemaining = roundCurrency(newNetPayable - alreadyPaidAmount)
            newStatus = if (alreadyPaidAmount > 0.0) "PARTIAL" else "UNPAID"
        }

        return InvoiceRepriceResult(
            newEnergyAmount = newEnergy,
            newTotalDueBeforeCredit = newTotalDueBeforeCredit,
            adjustedCreditDeducted = adjustedCreditDeducted,
            creditRefundedToSubscriber = creditRefunded,
            newNetPayableAmount = newNetPayable,
            newRemainingAmount = newRemaining,
            overpaymentCreditedToSubscriber = overpaymentCredited,
            newStatus = newStatus
        )
    }
}
