package com.example

import com.example.domain.finance.FinancialCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FinancialCalculatorTest {

    @Test
    fun `calculateConsumption returns exact difference`() {
        val consumed = FinancialCalculator.calculateConsumption(
            previousReading = 1240.5,
            currentReading = 1390.75
        )
        assertEquals(150.25, consumed, 0.001)
    }

    @Test
    fun `calculateConsumption rejects lower current reading without override`() {
        assertThrows(IllegalArgumentException::class.java) {
            FinancialCalculator.calculateConsumption(
                previousReading = 500.0,
                currentReading = 450.0,
                allowLowReadingOverride = false
            )
        }
    }

    @Test
    fun `calculateConsumption allows lower reading when authorized override is active`() {
        val consumed = FinancialCalculator.calculateConsumption(
            previousReading = 500.0,
            currentReading = 35.0,
            allowLowReadingOverride = true
        )
        assertEquals(35.0, consumed, 0.001)
    }

    @Test
    fun `calculateInvoice applies credit balance and computes net payable`() {
        val result = FinancialCalculator.calculateInvoice(
            consumedKwh = 200.0,
            pricePerKwh = 0.45,
            fixedFee = 10.0,
            previousArrears = 50.0,
            availableCredit = 40.0
        )
        assertEquals(90.0, result.energyAmount, 0.001)
        assertEquals(150.0, result.totalDueBeforeCredit, 0.001)
        assertEquals(40.0, result.creditDeducted, 0.001)
        assertEquals(110.0, result.netPayableAmount, 0.001)
    }

    @Test
    fun `allocateCollection settles invoice then arrears then adds excess to credit`() {
        val allocation = FinancialCalculator.allocateCollection(
            amountPaid = 200.0,
            currentInvoiceRemaining = 100.0,
            existingArrearsRemaining = 60.0,
            existingCreditBalance = 15.0
        )
        assertEquals(100.0, allocation.appliedToInvoice, 0.001)
        assertEquals(60.0, allocation.appliedToArrears, 0.001)
        assertEquals(40.0, allocation.addedToCredit, 0.001)
        assertEquals(0.0, allocation.remainingInvoiceBalance, 0.001)
        assertEquals(0.0, allocation.remainingArrearsBalance, 0.001)
        assertEquals(55.0, allocation.resultingCreditBalance, 0.001)
    }

    @Test
    fun `recalculateInvoiceAfterPriceEdit preserves paid amount and refunds excess overpayment to credit`() {
        val reduced = FinancialCalculator.recalculateInvoiceAfterPriceEdit(
            consumedKwh = 100.0,
            newPricePerKwh = 0.5,
            fixedFeeApplied = 0.0,
            previousArrearsApplied = 0.0,
            existingCreditDeducted = 0.0,
            alreadyPaidAmount = 80.0
        )
        assertEquals(50.0, reduced.newNetPayableAmount, 0.001)
        assertEquals(0.0, reduced.newRemainingAmount, 0.001)
        assertEquals(30.0, reduced.overpaymentCreditedToSubscriber, 0.001)
        assertEquals("PAID", reduced.newStatus)
    }
}
