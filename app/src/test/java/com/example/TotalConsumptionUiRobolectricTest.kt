package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.local.database.AlasalDatabase
import com.example.data.local.entity.SubscriberEntity
import com.example.data.repository.AlasalRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.MeterReadingInput
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ar-rSA-w411dp-h915dp")
class TotalConsumptionUiRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `dashboard Total Consumption card navigates to months list and displays live First Half, Second Half, and Final Monthly Summary`() {
        // 1. Unlock app with default admin password (2027)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("app_lock_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("lock_password_input").performTextReplacement("2027")
        composeTestRule.onNodeWithTag("lock_submit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("app_lock_screen").fetchSemanticsNodes().isEmpty() &&
                composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Seed current month semi-monthly readings directly through the live BillingRepository
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val periodKey = String.format(Locale.US, "%04d-%02d", year, month)
        val day5 = Calendar.getInstance().apply { set(year, month - 1, 5, 10, 0, 0) }.timeInMillis
        val day20 = Calendar.getInstance().apply { set(year, month - 1, 20, 10, 0, 0) }.timeInMillis

        runBlocking {
            val db = AlasalDatabase.getInstance(composeTestRule.activity)
            val coreRepo = AlasalRepository(db)
            val billingRepo = BillingRepository(db, coreRepo)

            val adelId = db.subscriberDao().insert(
                SubscriberEntity(
                    subscriberCode = "SUB-UI-C1",
                    fullName = "عادل العسل",
                    phoneNumber = "0775000001",
                    meterNumber = "MTR-UI-C1",
                    address = "الحي الشرقي",
                    subscriptionType = "منزلي"
                )
            )
            val husseinId = db.subscriberDao().insert(
                SubscriberEntity(
                    subscriberCode = "SUB-UI-C2",
                    fullName = "حسين العسل",
                    phoneNumber = "0775000002",
                    meterNumber = "MTR-UI-C2",
                    address = "الحي الشرقي",
                    subscriptionType = "منزلي"
                )
            )
            val naderId = db.subscriberDao().insert(
                SubscriberEntity(
                    subscriberCode = "SUB-UI-C3",
                    fullName = "نادر العسل",
                    phoneNumber = "0775000003",
                    meterNumber = "MTR-UI-C3",
                    address = "الحي الغربي",
                    subscriptionType = "تجاري"
                )
            )

            // First Half: عادل (50) + حسين (30) = 80 kWh
            billingRepo.saveMeterReading(
                MeterReadingInput(adelId, periodKey, 50.0, day5, readingHalf = 1)
            ).getOrThrow()
            billingRepo.saveMeterReading(
                MeterReadingInput(husseinId, periodKey, 30.0, day5, readingHalf = 1)
            ).getOrThrow()

            // Second Half: عادل (50->80 = +30) + حسين (30->50 = +20) + نادر (0->50 = +50) = 100 kWh
            billingRepo.saveMeterReading(
                MeterReadingInput(adelId, periodKey, 80.0, day20, readingHalf = 2)
            ).getOrThrow()
            billingRepo.saveMeterReading(
                MeterReadingInput(husseinId, periodKey, 50.0, day20, readingHalf = 2)
            ).getOrThrow()
            billingRepo.saveMeterReading(
                MeterReadingInput(naderId, periodKey, 50.0, day20, readingHalf = 2)
            ).getOrThrow()
        }

        // 3. Verify Dashboard featured card "إجمالي الاستهلاك" shows live total and opens TotalConsumptionScreen
        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("dashboard_total_consumption_card"))
        composeTestRule.onNodeWithTag("dashboard_total_consumption_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_total_consumption_card").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("total_consumption_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // 4. Scroll to the current month card and open its detail screen
        val monthCardTag = "consumption_month_card_$periodKey"
        composeTestRule.onNodeWithTag("total_consumption_screen")
            .performScrollToNode(hasTestTag(monthCardTag))
        composeTestRule.onNodeWithTag(monthCardTag).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("monthly_consumption_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }

        val arFmt = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("ar")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }

        // 5. Verify First Half (80), Second Half (100), and Final Monthly Summary (180, counts 2, 3, 5)
        composeTestRule.onNodeWithTag("monthly_consumption_detail_screen")
            .performScrollToNode(hasTestTag("first_half_section_card"))
        composeTestRule.onNodeWithTag("first_half_total_kwh").assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_half_total_kwh").assertTextContains(arFmt.format(80.0), substring = true)

        composeTestRule.onNodeWithTag("monthly_consumption_detail_screen")
            .performScrollToNode(hasTestTag("second_half_section_card"))
        composeTestRule.onNodeWithTag("second_half_total_kwh").assertIsDisplayed()
        composeTestRule.onNodeWithTag("second_half_total_kwh").assertTextContains(arFmt.format(100.0), substring = true)

        composeTestRule.onNodeWithTag("monthly_consumption_detail_screen")
            .performScrollToNode(hasTestTag("monthly_final_summary_card"))
        composeTestRule.onNodeWithTag("monthly_grand_total_kwh").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthly_grand_total_kwh").assertTextContains(arFmt.format(180.0), substring = true)
        composeTestRule.onNodeWithTag("monthly_first_half_count").assertTextContains("2")
        composeTestRule.onNodeWithTag("monthly_second_half_count").assertTextContains("3")
        composeTestRule.onNodeWithTag("monthly_total_readings_count").assertTextContains("5")

        // Verify Export PDF & Print buttons exist on top
        composeTestRule.onNodeWithTag("monthly_consumption_detail_screen")
            .performScrollToNode(hasTestTag("export_monthly_consumption_pdf_button"))
        composeTestRule.onNodeWithTag("export_monthly_consumption_pdf_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("print_monthly_consumption_pdf_button").assertIsDisplayed()
    }
}
