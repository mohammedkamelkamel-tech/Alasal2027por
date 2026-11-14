package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.SubscriberFullHistory
import com.example.data.repository.SubscriberInput
import com.example.data.repository.TariffPricePreviewItem
import com.example.service.NotificationAndPrintPrefs
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.LocalAlasalColors
import com.example.ui.viewmodel.FeedbackMessage
import com.example.ui.viewmodel.FoundationUiState

@Composable
fun SubscribersManagementScreen(
    state: FoundationUiState.Ready,
    feedback: FeedbackMessage?,
    selectedHistory: SubscriberFullHistory?,
    tariffPreviewItems: List<TariffPricePreviewItem>,
    onDismissFeedback: () -> Unit,
    onSaveSubscriber: (SubscriberInput, () -> Unit) -> Unit,
    onDeleteSubscriber: (Long) -> Unit,
    onOpenSubscriberDetail: (Long) -> Unit,
    onCloseSubscriberDetail: () -> Unit,
    onPrepareTariffPreview: (List<Long>?, Double) -> Unit,
    onClearTariffPreview: () -> Unit,
    onApplyTariffUpdate: (List<Long>?, Double, Long, String, () -> Unit) -> Unit,
    notificationPrefs: NotificationAndPrintPrefs = NotificationAndPrintPrefs(),
    modifier: Modifier = Modifier
) {
    val alasalColors = LocalAlasalColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showFormDialog by remember { mutableStateOf(false) }
    var editingSubscriber by remember { mutableStateOf<SubscriberEntity?>(null) }
    var showBulkTariffDialog by remember { mutableStateOf(false) }

    val defaultPrice = state.tariffs.firstOrNull { it.isActive }?.pricePerKwh ?: 0.45

    val filteredSubscribers = remember(state.subscribers, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            state.subscribers
        } else {
            state.subscribers.filter {
                it.fullName.contains(q, ignoreCase = true) ||
                    it.subscriberCode.contains(q, ignoreCase = true) ||
                    it.phoneNumber.contains(q, ignoreCase = true) ||
                    it.meterNumber.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (feedback != null) {
            StatusFeedbackBanner(
                feedback = feedback,
                onDismiss = onDismissFeedback,
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("subscribers_screen"),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header_actions") {
                Column(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "إدارة المشتركين وأسعار الكيلوواط",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("بحث بالاسم أو رقم المشترك أو الهاتف أو العداد") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("subscribers_search_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                editingSubscriber = null
                                showFormDialog = true
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("add_subscriber_button")
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.action_add_subscriber), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showBulkTariffDialog = true },
                            enabled = state.subscribers.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("manage_tariffs_button")
                        ) {
                            Icon(Icons.Filled.PriceChange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.action_manage_tariffs), maxLines = 1)
                        }
                    }
                }
            }

            if (filteredSubscribers.isEmpty()) {
                item(key = "empty") {
                    EmptyStateCard(
                        icon = Icons.Filled.PeopleOutline,
                        title = if (searchQuery.isBlank()) "لا يوجد مشتركون مسجلون بعد" else "لا توجد نتائج مطابقة للبحث",
                        description = if (searchQuery.isBlank()) {
                            "اضغط على «إضافة مشترك» لتسجيل أول مشترك برقم عداد وختم وسعر كيلوواط خاص، والبدء في القراءات والفواتير."
                        } else {
                            "جرّب البحث باسم مختلف أو رقم هاتف أو كود مشترك آخر."
                        },
                        modifier = Modifier.widthIn(max = 800.dp),
                        testTag = "subscribers_empty_state"
                    )
                }
            } else {
                items(filteredSubscribers, key = { it.id }) { sub ->
                    Card(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .testTag("subscriber_card_${sub.subscriberCode}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        border = BorderStroke(1.dp, alasalColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ElectricMeter,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = sub.fullName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "رقم: ${sub.subscriberCode} | هاتف: ${sub.phoneNumber.ifEmpty { "-" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "عداد: ${sub.meterNumber} | ختم: ${sub.meterSealNumber.ifEmpty { "-" }} | سعر ك.و.س: ${sub.customKwhPrice}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    color = if (sub.isActive) alasalColors.creditContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = if (sub.isActive) "نشط" else "غير نشط",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (sub.isActive) alasalColors.creditGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        color = alasalColors.creditContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "دائن: ${sub.currentCreditBalance}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = alasalColors.creditGreen,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        color = alasalColors.arrearsContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "متأخرات: ${sub.currentArrearsAmount}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = alasalColors.arrearsRed,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = { onOpenSubscriberDetail(sub.id) },
                                        modifier = Modifier.size(48.dp).testTag("sub_detail_btn_${sub.subscriberCode}")
                                    ) {
                                        Icon(Icons.Filled.Visibility, contentDescription = "كشف الحساب والتفاصيل")
                                    }
                                    IconButton(
                                        onClick = {
                                            editingSubscriber = sub
                                            showFormDialog = true
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "تعديل المشترك")
                                    }
                                    IconButton(
                                        onClick = { onDeleteSubscriber(sub.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "حذف المشترك",
                                            tint = alasalColors.arrearsRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        SubscriberFormDialog(
            initialSubscriber = editingSubscriber,
            defaultPricePerKwh = defaultPrice,
            onDismiss = { showFormDialog = false },
            onSave = { input ->
                onSaveSubscriber(input) { showFormDialog = false }
            }
        )
    }

    if (showBulkTariffDialog) {
        BulkTariffManagementDialog(
            subscribers = state.subscribers,
            previewItems = tariffPreviewItems,
            onPreparePreview = onPrepareTariffPreview,
            onClearPreview = onClearTariffPreview,
            onApplyUpdate = { ids, newPrice, effectiveFrom, reason ->
                onApplyTariffUpdate(ids, newPrice, effectiveFrom, reason) {
                    showBulkTariffDialog = false
                }
            },
            onDismiss = {
                onClearTariffPreview()
                showBulkTariffDialog = false
            }
        )
    }

    if (selectedHistory != null) {
        val currentSubId = selectedHistory.subscriber.id
        SubscriberDetailDialog(
            history = selectedHistory,
            notificationPrefs = notificationPrefs,
            onUpdateKwhPrice = { newPrice, reason ->
                onApplyTariffUpdate(listOf(currentSubId), newPrice, System.currentTimeMillis(), reason) {}
            },
            onDismiss = onCloseSubscriberDetail
        )
    }
}
