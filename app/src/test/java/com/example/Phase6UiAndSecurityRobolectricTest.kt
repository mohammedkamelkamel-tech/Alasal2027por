package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar-rSA-w411dp-h915dp")
class Phase6UiAndSecurityRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun openDashboardShortcut(shortcutTag: String) {
        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("dashboard_shortcuts_row"))
        composeTestRule.onNodeWithTag("dashboard_shortcuts_row")
            .performScrollToNode(hasTestTag(shortcutTag))
        composeTestRule.onNodeWithTag(shortcutTag).performClick()
    }

    @Test
    fun `phase 6 full UI flow verifies app lock, direct kWh edit, instant WhatsApp invoice banner, field collection PDF CSV, and biometric toggle`() {
        // 1. App starts locked; wrong password shows Arabic error banner; 2027 unlocks & dismisses lock screen completely
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("app_lock_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("lock_password_input").performTextReplacement("9999")
        composeTestRule.onNodeWithTag("lock_submit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("lock_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("lock_error_banner").assertIsDisplayed()

        composeTestRule.onNodeWithTag("lock_password_input").performTextReplacement("2027")
        composeTestRule.onNodeWithTag("lock_submit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("app_lock_screen").fetchSemanticsNodes().isEmpty() &&
                composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 2. Add subscriber with phone & area, then edit kWh price directly inside subscriber detail file
        composeTestRule.onNodeWithTag("nav_item_subscribers").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("add_subscriber_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_subscriber_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("sub_input_name").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("sub_input_name").performTextInput("ناصر الميداني")
        composeTestRule.onNodeWithTag("sub_input_code").performTextInput("SUB-P6-1")
        composeTestRule.onNodeWithTag("sub_input_phone").performTextInput("0771234567")
        composeTestRule.onNodeWithTag("sub_input_address").performTextInput("حي حدة")
        composeTestRule.onNodeWithTag("sub_input_meter").performTextInput("MTR-P6-1")
        composeTestRule.onNodeWithTag("sub_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("sub_save_button").fetchSemanticsNodes().isEmpty() &&
                composeTestRule.onAllNodesWithTag("subscriber_card_SUB-P6-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("subscribers_screen")
            .performScrollToNode(hasTestTag("subscriber_card_SUB-P6-1"))
        composeTestRule.onNodeWithTag("sub_detail_btn_SUB-P6-1").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("detail_kwh_price_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("detail_kwh_price_input").performTextReplacement("0.75")
        composeTestRule.onNodeWithTag("detail_kwh_price_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("detail_inline_feedback").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("detail_inline_feedback").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sub_statement_whatsapp_btn").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("إغلاق").performClick()
        composeTestRule.waitForIdle()

        // 3. Issue reading + invoice for SUB-P6-1 -> verify instant post-issue WhatsApp dispatch banner appears
        composeTestRule.onNodeWithTag("nav_item_readings").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("reading_subscriber_picker_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("reading_subscriber_picker_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("subscriber_picker_search_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("subscriber_picker_search_input").performTextInput("SUB-P6-1")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("subscriber_picker_item_SUB-P6-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("subscriber_picker_item_SUB-P6-1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reading_current_input").performTextInput("120")
        composeTestRule.onNodeWithTag("reading_save_and_invoice_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("instant_invoice_whatsapp_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("instant_invoice_whatsapp_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reading_instant_whatsapp_button").assertIsDisplayed()

        // 4. Open Field Collection Sheet from Dashboard shortcut
        composeTestRule.onNodeWithTag("nav_item_dashboard").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
        }
        openDashboardShortcut("shortcut_field_collection")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("field_collection_section").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("field_area_filter_input").performTextInput("حدة")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("field_item_SUB-P6-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("field_item_SUB-P6-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("field_export_pdf_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("field_export_csv_button").assertIsDisplayed()

        // 5. Return to Dashboard, open Accounts, enable Biometric for admin, and test manual lock button
        composeTestRule.onNodeWithContentDescription("رجوع").performClick()
        composeTestRule.waitForIdle()
        openDashboardShortcut("shortcut_accounts")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("biometric_switch_admin").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("biometric_switch_admin").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("رجوع").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("lock_app_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("app_lock_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("lock_biometric_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lock_password_input").assertIsDisplayed()
    }
}
