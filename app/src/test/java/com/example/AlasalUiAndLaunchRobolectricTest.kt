package com.example

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.service.StationMaintenanceWorker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar-rSA-w411dp-h915dp")
class AlasalUiAndLaunchRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app metadata, adaptive icons, and transparent logo and watermark pngs are valid`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("محطة العسل 2027", context.getString(R.string.app_name))

        val launcherDrawable = context.getDrawable(R.mipmap.ic_launcher)
        val roundLauncherDrawable = context.getDrawable(R.mipmap.ic_launcher_round)
        assertNotNull(launcherDrawable)
        assertNotNull(roundLauncherDrawable)

        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_alasal_logo_transparent)
        assertNotNull(logoBitmap)
        assertEquals(0, Color.alpha(logoBitmap.getPixel(0, 0)))
        assertTrue(Color.alpha(logoBitmap.getPixel(logoBitmap.width / 2, logoBitmap.height / 2)) > 200)

        val watermarkBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_alasal_watermark)
        assertNotNull(watermarkBitmap)
        assertEquals(0, Color.alpha(watermarkBitmap.getPixel(0, 0)))
        val centerAlpha = Color.alpha(watermarkBitmap.getPixel(watermarkBitmap.width / 2, watermarkBitmap.height / 2))
        assertTrue("Watermark center should have subtle non-zero transparency", centerAlpha in 10..120)
    }

    private fun unlockIfLockedWithDefaultAdmin() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithTag("app_lock_screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodesWithTag("app_lock_screen").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithTag("lock_password_input").performTextReplacement("2027")
            composeTestRule.onNodeWithTag("lock_submit_button").performClick()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithTag("app_lock_screen").fetchSemanticsNodes().isEmpty() &&
                    composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun openDashboardShortcut(shortcutTag: String) {
        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("dashboard_shortcuts_row"))
        composeTestRule.onNodeWithTag("dashboard_shortcuts_row")
            .performScrollToNode(hasTestTag(shortcutTag))
        composeTestRule.onNodeWithTag(shortcutTag).performClick()
    }

    @Test
    fun `main activity launches in RTL and completes end-to-end subscriber, reading, invoice, payment, and reprice UI flow`() {
        unlockIfLockedWithDefaultAdmin()

        // 1. Verify RTL brand header, transparent logo, and watermark
        composeTestRule.onNodeWithTag("brand_header_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("official_logo_image").assertIsDisplayed()
        composeTestRule.onNodeWithTag("alasal_watermark_image").assertExists()

        val headerBounds = composeTestRule.onNodeWithTag("brand_header_card").getUnclippedBoundsInRoot()
        val logoBounds = composeTestRule.onNodeWithTag("official_logo_image").getUnclippedBoundsInRoot()
        val themeBtnBounds = composeTestRule.onNodeWithTag("toggle_theme_button").getUnclippedBoundsInRoot()
        assertTrue(logoBounds.left > themeBtnBounds.right)
        assertTrue(headerBounds.right > logoBounds.right && (logoBounds.right - logoBounds.left).value > 0f)

        // 2. Navigate to Subscribers & add a new subscriber via UI
        composeTestRule.onNodeWithTag("nav_item_subscribers").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("subscribers_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("add_subscriber_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sub_input_name").performTextInput("سعيد العسل")
        composeTestRule.onNodeWithTag("sub_input_code").performTextInput("SUB-UI-1")
        composeTestRule.onNodeWithTag("sub_input_meter").performTextInput("MTR-UI-1")
        composeTestRule.onNodeWithTag("sub_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("subscriber_card_SUB-UI-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("subscriber_card_SUB-UI-1").assertIsDisplayed()

        // 3. Navigate to Meter Readings & save reading + issue invoice immediately
        composeTestRule.onNodeWithTag("nav_item_readings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("readings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reading_current_input").performTextInput("100")
        composeTestRule.onNodeWithTag("reading_save_and_invoice_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("status_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("status_feedback_banner").assertIsDisplayed()

        // 4. Navigate to Invoices, inspect invoice, record partial payment, and reprice
        val expectedInvoiceNumber = "INV-${java.time.Year.now().value}-0001"
        composeTestRule.onNodeWithTag("nav_item_invoices").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("invoice_card_$expectedInvoiceNumber").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("invoice_detail_btn_$expectedInvoiceNumber").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("invoice_pay_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Record partial payment (20.0)
        composeTestRule.onNodeWithTag("invoice_pay_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("payment_amount_input").performTextReplacement("20")
        composeTestRule.onNodeWithTag("payment_confirm_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("payment_confirm_button").fetchSemanticsNodes().isEmpty()
        }

        // Reprice existing invoice while preserving partial payment
        composeTestRule.onNodeWithTag("invoice_reprice_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reprice_new_price_input").performTextReplacement("0.60")
        composeTestRule.onNodeWithTag("reprice_reason_input").performTextInput("تصحيح تعرفة")
        composeTestRule.onNodeWithTag("reprice_confirm_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("reprice_confirm_button").fetchSemanticsNodes().isEmpty()
        }

        // Dismiss invoice detail dialog before navigating via bottom bar
        composeTestRule.onNodeWithContentDescription("إغلاق").performClick()
        composeTestRule.waitForIdle()

        // Navigate to System screen and verify 14 tables
        composeTestRule.onNodeWithTag("nav_item_system").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("system_diagnostics_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("system_diagnostics_screen")
            .performScrollToNode(hasTestTag("table_status_financial_audit_logs"))
        composeTestRule.onNodeWithTag("table_status_financial_audit_logs").assertIsDisplayed()
    }

    @Test
    fun `phase 3 UI flows cover dashboard live charts, collections, receipt dialog, reports, and reminders`() {
        unlockIfLockedWithDefaultAdmin()

        // 1. Add a subscriber & issue an invoice via UI so Dashboard/Collections/Reports have live records
        composeTestRule.onNodeWithTag("nav_item_subscribers").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_subscriber_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sub_input_name").performTextInput("فاطمة العسل")
        composeTestRule.onNodeWithTag("sub_input_code").performTextInput("SUB-P3-1")
        composeTestRule.onNodeWithTag("sub_input_meter").performTextInput("MTR-P3-1")
        composeTestRule.onNodeWithTag("sub_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("subscriber_card_SUB-P3-1").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("nav_item_readings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reading_current_input").performTextInput("200")
        composeTestRule.onNodeWithTag("reading_save_and_invoice_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("status_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Dashboard live stats & Compose charts
        composeTestRule.onNodeWithTag("nav_item_dashboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("dashboard_revenue_chart"))
        composeTestRule.onNodeWithTag("dashboard_revenue_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("dashboard_invoice_status_chart"))
        composeTestRule.onNodeWithTag("dashboard_invoice_status_chart").assertIsDisplayed()

        // 3. Open Collections & Receipts via Dashboard shortcut & record partial collection
        openDashboardShortcut("shortcut_collections")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("collections_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("collection_submit_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("collection_amount_input").performTextReplacement("35")
        composeTestRule.onNodeWithTag("collection_submit_button").performClick()

        // Receipt detail dialog auto-opens with thermal 58mm/80mm & A4/Image/WhatsApp options
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("receipt_print_a4_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("thermal_width_58").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("thermal_width_80").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("receipt_print_a4_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_print_thermal_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_share_pdf_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_share_image_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_share_whatsapp_button").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("إغلاق").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("رجوع").performClick()
        composeTestRule.waitForIdle()

        // 4. Open Reports & Statements via Dashboard shortcut
        openDashboardShortcut("shortcut_reports")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("reports_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("reports_categories_row")
            .performScrollToNode(hasTestTag("report_cat_COLLECTIONS"))
        composeTestRule.onNodeWithTag("report_cat_COLLECTIONS").performClick()
        composeTestRule.onNodeWithTag("reports_categories_row")
            .performScrollToNode(hasTestTag("report_cat_SUBSCRIBER_STATEMENT"))
        composeTestRule.onNodeWithTag("report_cat_SUBSCRIBER_STATEMENT").performClick()
        composeTestRule.onNodeWithTag("report_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("report_export_pdf_button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("رجوع").performClick()
        composeTestRule.waitForIdle()

        // 5. Open Reminders & Notifications via Dashboard shortcut & schedule reminder
        openDashboardShortcut("shortcut_reminders")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("reminders_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("switch_notifications_sound").performClick()
        composeTestRule.onNodeWithTag("reminder_notes_input").performTextInput("فحص عداد الحي")
        composeTestRule.onNodeWithTag("schedule_reminder_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("status_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("status_feedback_banner").assertIsDisplayed()
    }

    @Test
    fun `station maintenance WorkManager worker executes successfully offline`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<StationMaintenanceWorker>(context).build()
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(14, output.getInt(StationMaintenanceWorker.KEY_TABLES_VERIFIED, 0))
        assertNotEquals(0, output.getInt(StationMaintenanceWorker.KEY_TOTAL_TARIFFS, 0))
    }

    @Test
    fun `phase 4 UI flows cover accounts creation, permissions customization, password switch, and backup creation`() {
        unlockIfLockedWithDefaultAdmin()

        // 1. Navigate to Accounts & Permissions via Dashboard shortcut
        openDashboardShortcut("shortcut_accounts")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("accounts_permissions_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Create a new Collector account with custom password & permissions
        composeTestRule.onNodeWithTag("create_account_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("account_username_input").performTextInput("cashier2")
        composeTestRule.onNodeWithTag("account_fullname_input").performTextInput("أمينة صندوق 2")
        composeTestRule.onNodeWithTag("account_password_input").performTextInput("4321")
        composeTestRule.onNodeWithTag("role_chip_COLLECTOR").performClick()
        composeTestRule.onNodeWithTag("save_account_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("save_account_button").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("accounts_permissions_screen")
            .performScrollToNode(hasTestTag("account_card_cashier2"))
        composeTestRule.onNodeWithTag("account_card_cashier2").assertIsDisplayed()

        // 3. Switch active session to newly created cashier2 using credentials
        composeTestRule.onNodeWithTag("accounts_permissions_screen")
            .performScrollToNode(hasTestTag("switch_account_button"))
        composeTestRule.onNodeWithTag("switch_account_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("login_username_input").performTextReplacement("cashier2")
        composeTestRule.onNodeWithTag("login_password_input").performTextInput("4321")
        composeTestRule.onNodeWithTag("login_submit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("login_submit_button").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("active_user_banner").assertIsDisplayed()

        // Switch back to primary admin before visiting Backup (since collector lacks MANAGE_BACKUPS)
        composeTestRule.onNodeWithTag("switch_account_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("login_username_input").performTextReplacement("admin")
        composeTestRule.onNodeWithTag("login_password_input").performTextInput("2027")
        composeTestRule.onNodeWithTag("login_submit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("login_submit_button").fetchSemanticsNodes().isEmpty()
        }

        // 4. Navigate back to Dashboard and open Wi-Fi Sync & Safe Backup Screen
        composeTestRule.onNodeWithContentDescription("رجوع").performClick()
        composeTestRule.waitForIdle()
        openDashboardShortcut("shortcut_sync_backup")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("wifi_sync_backup_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Check Wi-Fi Sync tab elements
        composeTestRule.onNodeWithTag("toggle_wifi_hosting_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_wifi_sync_button").assertIsDisplayed()

        // Switch to Backup & Safe Restore tab and create a manual backup
        composeTestRule.onNodeWithTag("tab_backup_restore").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_manual_backup_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("status_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("status_feedback_banner").assertIsDisplayed()
    }
}
