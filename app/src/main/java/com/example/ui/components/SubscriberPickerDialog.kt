package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.SubscriberEntity
import com.example.ui.theme.LocalAlasalColors

private val PickerSelectedBlue = Color(0xFF1976D2)

/**
 * Unified trigger button used across Readings, Invoices, Collections, Reports, and Statements
 * to open the "قائمة المشتركين المتاحين" smart searchable dialog.
 */
@Composable
fun SubscriberPickerTriggerButton(
    selectedSubscriber: SubscriberEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "اختر مشتركاً من قائمة المشتركين المتاحين",
    allSelectedLabel: String? = null,
    testTag: String = "subscriber_picker_trigger"
) {
    val alasalColors = LocalAlasalColors.current
    val primaryLabel = when {
        selectedSubscriber != null -> selectedSubscriber.fullName
        allSelectedLabel != null -> allSelectedLabel
        else -> placeholderText
    }
    val secondaryLabel = when {
        selectedSubscriber != null -> {
            val codeAndPhone = "${selectedSubscriber.subscriberCode} • ${selectedSubscriber.phoneNumber.ifBlank { "بدون هاتف" }}"
            val area = selectedSubscriber.address.trim()
            if (area.isNotEmpty()) "$codeAndPhone • $area" else codeAndPhone
        }
        allSelectedLabel != null -> "اضغط لاختيار مشترك محدد أو البحث بالاسم أو الكود أو الهاتف"
        else -> "ابحث بالاسم أو الكود أو رقم الهاتف"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (selectedSubscriber != null) PickerSelectedBlue.copy(alpha = 0.65f) else alasalColors.cardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (selectedSubscriber != null) Icons.Filled.PersonSearch else Icons.Filled.People,
                contentDescription = "فتح قائمة المشتركين المتاحين",
                tint = if (selectedSubscriber != null) PickerSelectedBlue else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Unified searchable dialog titled "قائمة المشتركين المتاحين" with instant filtering by
 * subscriber name, code, phone number, or address, structured subscriber cards, blue checkmark
 * indicator beside the selected subscriber, and a bottom "إغلاق" button.
 */
@Composable
fun AvailableSubscribersDialog(
    subscribers: List<SubscriberEntity>,
    selectedSubscriberId: Long?,
    onSelectSubscriber: (SubscriberEntity) -> Unit,
    onDismiss: () -> Unit,
    allowAllOption: Boolean = false,
    allOptionTitle: String = "جميع المشتركين (الكل)",
    onSelectAll: (() -> Unit)? = null
) {
    val alasalColors = LocalAlasalColors.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredSubscribers = remember(subscribers, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            subscribers
        } else {
            subscribers.filter { sub ->
                sub.fullName.contains(q, ignoreCase = true) ||
                    sub.subscriberCode.contains(q, ignoreCase = true) ||
                    sub.phoneNumber.contains(q, ignoreCase = true) ||
                    sub.address.contains(q, ignoreCase = true) ||
                    sub.meterNumber.contains(q, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = 640.dp)
                .testTag("available_subscribers_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "قائمة المشتركين المتاحين",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "إجمالي المطابقين: ${filteredSubscribers.size} من ${subscribers.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث باسم المشترك أو الكود أو رقم الهاتف...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subscriber_picker_search_input")
                )

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp)
                        .testTag("subscriber_picker_list"),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (allowAllOption && onSelectAll != null && searchQuery.isBlank()) {
                        item(key = "all_subscribers_option") {
                            val isSelected = selectedSubscriberId == null
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("subscriber_picker_all_option")
                                    .clickable {
                                        onSelectAll()
                                        onDismiss()
                                    },
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        PickerSelectedBlue.copy(alpha = 0.10f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) PickerSelectedBlue else alasalColors.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = allOptionTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "عرض بيانات كافة المشتركين بدون تصفية",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "محدد",
                                            tint = PickerSelectedBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredSubscribers.isEmpty()) {
                        item(key = "no_matching_subscribers") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "لا يوجد مشترك مطابق لـ «$searchQuery».",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredSubscribers, key = { it.id }) { sub ->
                            val isSelected = sub.id == selectedSubscriberId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("subscriber_picker_item_${sub.subscriberCode}")
                                    .clickable {
                                        onSelectSubscriber(sub)
                                        onDismiss()
                                    },
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        PickerSelectedBlue.copy(alpha = 0.10f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) PickerSelectedBlue else alasalColors.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = sub.fullName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${sub.subscriberCode} • ${sub.phoneNumber.ifBlank { "بدون هاتف" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = sub.address.ifBlank { "المنطقة / العنوان غير محدد" },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "المشترك المختار",
                                            tint = PickerSelectedBlue,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("subscriber_picker_selected_check_${sub.subscriberCode}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("subscriber_picker_close_button")
                ) {
                    Text("إغلاق")
                }
            }
        }
    }
}
