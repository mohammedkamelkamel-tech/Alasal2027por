package com.example.domain.sync

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class SyncConnectionState {
    IDLE,
    HOSTING,
    DISCOVERING,
    SYNCING,
    SUCCEEDED,
    FAILED
}

data class SyncPeerInfo(
    val serviceName: String,
    val deviceName: String,
    val hostAddress: String,
    val port: Int,
    val discoveredAt: Long = System.currentTimeMillis()
)

data class SyncMergeSummary(
    val insertedCount: Int,
    val updatedCount: Int,
    val skippedOlderOrDuplicateCount: Int,
    val conflictsResolvedCount: Int,
    val financialOpsPreservedCount: Int,
    val syncedUsersCount: Int,
    val syncedPermissionsCount: Int
)

data class SyncLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val peerLabel: String,
    val isSuccess: Boolean,
    val message: String,
    val details: String = ""
)

data class SyncUserDto(
    val username: String,
    val fullName: String,
    val roleCode: String,
    val pinHash: String,
    val isActive: Boolean,
    val customPermissionsJson: String,
    val biometricEnabled: Boolean = false,
    val updatedAt: Long,
    val syncId: String,
    val createdAt: Long
)

data class SyncPermissionDto(
    val roleCode: String,
    val permissionKey: String,
    val isAllowed: Boolean,
    val descriptionAr: String
)

data class SyncSettingDto(
    val settingKey: String,
    val settingValue: String,
    val category: String,
    val descriptionAr: String,
    val updatedAt: Long
)

data class SyncTariffDto(
    val tariffCode: String,
    val title: String,
    val subscriptionType: String,
    val pricePerKwh: Double,
    val fixedFee: Double,
    val effectiveFrom: Long,
    val isActive: Boolean,
    val createdAt: Long
)

data class SyncTariffLogDto(
    val tariffCode: String,
    val subscriberCode: String?,
    val subscriberNameSnapshot: String?,
    val oldPricePerKwh: Double,
    val newPricePerKwh: Double,
    val oldFixedFee: Double,
    val newFixedFee: Double,
    val effectiveFrom: Long,
    val actorNameSnapshot: String,
    val reason: String,
    val changedAt: Long
)

data class SyncSubscriberDto(
    val subscriberCode: String,
    val fullName: String,
    val phoneNumber: String,
    val meterNumber: String,
    val meterSealNumber: String,
    val address: String,
    val subscriptionType: String,
    val customKwhPrice: Double,
    val previousKwhPrice: Double?,
    val subscriptionDate: Long,
    val isActive: Boolean,
    val currentArrearsAmount: Double,
    val currentCreditBalance: Double,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class SyncReadingDto(
    val subscriberCode: String,
    val billingPeriod: String,
    val readingHalf: Int = 1,
    val previousReading: Double,
    val currentReading: Double,
    val consumedKwh: Double,
    val readingDate: Long,
    val photoUri: String?,
    val isLowReadingOverride: Boolean,
    val overrideReason: String,
    val notes: String
)

data class SyncInvoiceDto(
    val invoiceNumber: String,
    val subscriberCode: String,
    val readingPeriod: String?,
    val readingHalf: Int? = null,
    val tariffCode: String,
    val billingPeriod: String,
    val previousReading: Double,
    val currentReading: Double,
    val consumedKwh: Double,
    val unitPriceApplied: Double,
    val fixedFeeApplied: Double,
    val energyAmount: Double,
    val previousArrearsApplied: Double,
    val creditDeducted: Double,
    val netPayableAmount: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
    val carriedOverToInvoiceNumber: String?,
    val status: String,
    val notes: String,
    val priceModifiedAt: Long?,
    val priceModifiedBy: String?,
    val priceModificationReason: String?,
    val issuedAt: Long,
    val dueDate: Long
)

data class SyncCollectionDto(
    val collectionReference: String,
    val subscriberCode: String,
    val invoiceNumber: String?,
    val invoiceBillingPeriod: String?,
    val collectorUsername: String?,
    val collectorNameSnapshot: String,
    val amountPaid: Double,
    val previousBalanceSnapshot: Double,
    val remainingAfterSnapshot: Double,
    val appliedToInvoice: Double,
    val appliedToArrears: Double,
    val addedToCredit: Double,
    val paymentMethod: String,
    val collectedAt: Long,
    val notes: String
)

data class SyncReceiptDto(
    val receiptNumber: String,
    val collectionReference: String,
    val subscriberCode: String,
    val collectorNameSnapshot: String,
    val subscriberNameSnapshot: String,
    val subscriberCodeSnapshot: String,
    val invoiceNumberSnapshot: String,
    val paymentMethod: String,
    val previousBalance: Double,
    val amount: Double,
    val balanceAfterReceipt: Double,
    val creditAfterReceipt: Double,
    val stationNameSnapshot: String,
    val stationPhoneSnapshot: String,
    val issuedAt: Long,
    val isVoided: Boolean
)

data class SyncCreditDto(
    val subscriberCode: String,
    val transactionType: String,
    val amountDelta: Double,
    val balanceAfter: Double,
    val recordedAt: Long,
    val notes: String
)

data class SyncReminderDto(
    val subscriberCode: String?,
    val reminderType: String,
    val title: String,
    val message: String,
    val notes: String,
    val scheduledAt: Long,
    val status: String,
    val channel: String,
    val snoozeCount: Int,
    val completedAt: Long?,
    val createdAt: Long
)

data class StationSyncEnvelope(
    val stationCode: String = "Alasal2027",
    val protocolVersion: Int = 1,
    val deviceId: String,
    val deviceName: String,
    val exportedAt: Long,
    val checksumSha256: String = "",
    val users: List<SyncUserDto> = emptyList(),
    val permissions: List<SyncPermissionDto> = emptyList(),
    val settings: List<SyncSettingDto> = emptyList(),
    val tariffs: List<SyncTariffDto> = emptyList(),
    val tariffLogs: List<SyncTariffLogDto> = emptyList(),
    val subscribers: List<SyncSubscriberDto> = emptyList(),
    val readings: List<SyncReadingDto> = emptyList(),
    val invoices: List<SyncInvoiceDto> = emptyList(),
    val collections: List<SyncCollectionDto> = emptyList(),
    val receipts: List<SyncReceiptDto> = emptyList(),
    val credits: List<SyncCreditDto> = emptyList(),
    val reminders: List<SyncReminderDto> = emptyList()
) {
    fun withComputedChecksum(): StationSyncEnvelope {
        val hash = computePayloadDigest()
        return copy(checksumSha256 = hash)
    }

    fun verifyChecksum(): Boolean {
        if (checksumSha256.isBlank()) return false
        return checksumSha256 == computePayloadDigest()
    }

    private fun computePayloadDigest(): String {
        val canonicalJson = toPayloadJson().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun toJsonString(): String {
        val obj = toPayloadJson()
        obj.put("checksumSha256", checksumSha256)
        return obj.toString()
    }

    private fun toPayloadJson(): JSONObject {
        return JSONObject().apply {
            put("stationCode", stationCode)
            put("protocolVersion", protocolVersion)
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("exportedAt", exportedAt)
            put("users", JSONArray().apply {
                users.forEach { u ->
                    put(JSONObject().apply {
                        put("username", u.username)
                        put("fullName", u.fullName)
                        put("roleCode", u.roleCode)
                        put("pinHash", u.pinHash)
                        put("isActive", u.isActive)
                        put("customPermissionsJson", u.customPermissionsJson)
                        put("biometricEnabled", u.biometricEnabled)
                        put("updatedAt", u.updatedAt)
                        put("syncId", u.syncId)
                        put("createdAt", u.createdAt)
                    })
                }
            })
            put("permissions", JSONArray().apply {
                permissions.forEach { p ->
                    put(JSONObject().apply {
                        put("roleCode", p.roleCode)
                        put("permissionKey", p.permissionKey)
                        put("isAllowed", p.isAllowed)
                        put("descriptionAr", p.descriptionAr)
                    })
                }
            })
            put("settings", JSONArray().apply {
                settings.forEach { s ->
                    put(JSONObject().apply {
                        put("settingKey", s.settingKey)
                        put("settingValue", s.settingValue)
                        put("category", s.category)
                        put("descriptionAr", s.descriptionAr)
                        put("updatedAt", s.updatedAt)
                    })
                }
            })
            put("tariffs", JSONArray().apply {
                tariffs.forEach { t ->
                    put(JSONObject().apply {
                        put("tariffCode", t.tariffCode)
                        put("title", t.title)
                        put("subscriptionType", t.subscriptionType)
                        put("pricePerKwh", t.pricePerKwh)
                        put("fixedFee", t.fixedFee)
                        put("effectiveFrom", t.effectiveFrom)
                        put("isActive", t.isActive)
                        put("createdAt", t.createdAt)
                    })
                }
            })
            put("tariffLogs", JSONArray().apply {
                tariffLogs.forEach { l ->
                    put(JSONObject().apply {
                        put("tariffCode", l.tariffCode)
                        put("subscriberCode", l.subscriberCode ?: JSONObject.NULL)
                        put("subscriberNameSnapshot", l.subscriberNameSnapshot ?: JSONObject.NULL)
                        put("oldPricePerKwh", l.oldPricePerKwh)
                        put("newPricePerKwh", l.newPricePerKwh)
                        put("oldFixedFee", l.oldFixedFee)
                        put("newFixedFee", l.newFixedFee)
                        put("effectiveFrom", l.effectiveFrom)
                        put("actorNameSnapshot", l.actorNameSnapshot)
                        put("reason", l.reason)
                        put("changedAt", l.changedAt)
                    })
                }
            })
            put("subscribers", JSONArray().apply {
                subscribers.forEach { s ->
                    put(JSONObject().apply {
                        put("subscriberCode", s.subscriberCode)
                        put("fullName", s.fullName)
                        put("phoneNumber", s.phoneNumber)
                        put("meterNumber", s.meterNumber)
                        put("meterSealNumber", s.meterSealNumber)
                        put("address", s.address)
                        put("subscriptionType", s.subscriptionType)
                        put("customKwhPrice", s.customKwhPrice)
                        put("previousKwhPrice", s.previousKwhPrice ?: JSONObject.NULL)
                        put("subscriptionDate", s.subscriptionDate)
                        put("isActive", s.isActive)
                        put("currentArrearsAmount", s.currentArrearsAmount)
                        put("currentCreditBalance", s.currentCreditBalance)
                        put("notes", s.notes)
                        put("createdAt", s.createdAt)
                        put("updatedAt", s.updatedAt)
                    })
                }
            })
            put("readings", JSONArray().apply {
                readings.forEach { r ->
                    put(JSONObject().apply {
                        put("subscriberCode", r.subscriberCode)
                        put("billingPeriod", r.billingPeriod)
                        put("readingHalf", r.readingHalf)
                        put("previousReading", r.previousReading)
                        put("currentReading", r.currentReading)
                        put("consumedKwh", r.consumedKwh)
                        put("readingDate", r.readingDate)
                        put("photoUri", r.photoUri ?: JSONObject.NULL)
                        put("isLowReadingOverride", r.isLowReadingOverride)
                        put("overrideReason", r.overrideReason)
                        put("notes", r.notes)
                    })
                }
            })
            put("invoices", JSONArray().apply {
                invoices.forEach { inv ->
                    put(JSONObject().apply {
                        put("invoiceNumber", inv.invoiceNumber)
                        put("subscriberCode", inv.subscriberCode)
                        put("readingPeriod", inv.readingPeriod ?: JSONObject.NULL)
                        put("readingHalf", inv.readingHalf ?: JSONObject.NULL)
                        put("tariffCode", inv.tariffCode)
                        put("billingPeriod", inv.billingPeriod)
                        put("previousReading", inv.previousReading)
                        put("currentReading", inv.currentReading)
                        put("consumedKwh", inv.consumedKwh)
                        put("unitPriceApplied", inv.unitPriceApplied)
                        put("fixedFeeApplied", inv.fixedFeeApplied)
                        put("energyAmount", inv.energyAmount)
                        put("previousArrearsApplied", inv.previousArrearsApplied)
                        put("creditDeducted", inv.creditDeducted)
                        put("netPayableAmount", inv.netPayableAmount)
                        put("paidAmount", inv.paidAmount)
                        put("remainingAmount", inv.remainingAmount)
                        put("carriedOverToInvoiceNumber", inv.carriedOverToInvoiceNumber ?: JSONObject.NULL)
                        put("status", inv.status)
                        put("notes", inv.notes)
                        put("priceModifiedAt", inv.priceModifiedAt ?: JSONObject.NULL)
                        put("priceModifiedBy", inv.priceModifiedBy ?: JSONObject.NULL)
                        put("priceModificationReason", inv.priceModificationReason ?: JSONObject.NULL)
                        put("issuedAt", inv.issuedAt)
                        put("dueDate", inv.dueDate)
                    })
                }
            })
            put("collections", JSONArray().apply {
                collections.forEach { c ->
                    put(JSONObject().apply {
                        put("collectionReference", c.collectionReference)
                        put("subscriberCode", c.subscriberCode)
                        put("invoiceNumber", c.invoiceNumber ?: JSONObject.NULL)
                        put("invoiceBillingPeriod", c.invoiceBillingPeriod ?: JSONObject.NULL)
                        put("collectorUsername", c.collectorUsername ?: JSONObject.NULL)
                        put("collectorNameSnapshot", c.collectorNameSnapshot)
                        put("amountPaid", c.amountPaid)
                        put("previousBalanceSnapshot", c.previousBalanceSnapshot)
                        put("remainingAfterSnapshot", c.remainingAfterSnapshot)
                        put("appliedToInvoice", c.appliedToInvoice)
                        put("appliedToArrears", c.appliedToArrears)
                        put("addedToCredit", c.addedToCredit)
                        put("paymentMethod", c.paymentMethod)
                        put("collectedAt", c.collectedAt)
                        put("notes", c.notes)
                    })
                }
            })
            put("receipts", JSONArray().apply {
                receipts.forEach { r ->
                    put(JSONObject().apply {
                        put("receiptNumber", r.receiptNumber)
                        put("collectionReference", r.collectionReference)
                        put("subscriberCode", r.subscriberCode)
                        put("collectorNameSnapshot", r.collectorNameSnapshot)
                        put("subscriberNameSnapshot", r.subscriberNameSnapshot)
                        put("subscriberCodeSnapshot", r.subscriberCodeSnapshot)
                        put("invoiceNumberSnapshot", r.invoiceNumberSnapshot)
                        put("paymentMethod", r.paymentMethod)
                        put("previousBalance", r.previousBalance)
                        put("amount", r.amount)
                        put("balanceAfterReceipt", r.balanceAfterReceipt)
                        put("creditAfterReceipt", r.creditAfterReceipt)
                        put("stationNameSnapshot", r.stationNameSnapshot)
                        put("stationPhoneSnapshot", r.stationPhoneSnapshot)
                        put("issuedAt", r.issuedAt)
                        put("isVoided", r.isVoided)
                    })
                }
            })
            put("credits", JSONArray().apply {
                credits.forEach { cr ->
                    put(JSONObject().apply {
                        put("subscriberCode", cr.subscriberCode)
                        put("transactionType", cr.transactionType)
                        put("amountDelta", cr.amountDelta)
                        put("balanceAfter", cr.balanceAfter)
                        put("recordedAt", cr.recordedAt)
                        put("notes", cr.notes)
                    })
                }
            })
            put("reminders", JSONArray().apply {
                reminders.forEach { rem ->
                    put(JSONObject().apply {
                        put("subscriberCode", rem.subscriberCode ?: JSONObject.NULL)
                        put("reminderType", rem.reminderType)
                        put("title", rem.title)
                        put("message", rem.message)
                        put("notes", rem.notes)
                        put("scheduledAt", rem.scheduledAt)
                        put("status", rem.status)
                        put("channel", rem.channel)
                        put("snoozeCount", rem.snoozeCount)
                        put("completedAt", rem.completedAt ?: JSONObject.NULL)
                        put("createdAt", rem.createdAt)
                    })
                }
            })
        }
    }

    companion object {
        private fun JSONObject.optStringOrNull(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() || has(key) }

        private fun JSONObject.optDoubleOrNull(key: String): Double? =
            if (!has(key) || isNull(key)) null else optDouble(key)

        private fun JSONObject.optLongOrNull(key: String): Long? =
            if (!has(key) || isNull(key)) null else optLong(key)

        fun fromJsonString(json: String): StationSyncEnvelope {
            val root = JSONObject(json)
            val usersArr = root.optJSONArray("users") ?: JSONArray()
            val permsArr = root.optJSONArray("permissions") ?: JSONArray()
            val settingsArr = root.optJSONArray("settings") ?: JSONArray()
            val tariffsArr = root.optJSONArray("tariffs") ?: JSONArray()
            val tariffLogsArr = root.optJSONArray("tariffLogs") ?: JSONArray()
            val subsArr = root.optJSONArray("subscribers") ?: JSONArray()
            val readingsArr = root.optJSONArray("readings") ?: JSONArray()
            val invoicesArr = root.optJSONArray("invoices") ?: JSONArray()
            val collectionsArr = root.optJSONArray("collections") ?: JSONArray()
            val receiptsArr = root.optJSONArray("receipts") ?: JSONArray()
            val creditsArr = root.optJSONArray("credits") ?: JSONArray()
            val remindersArr = root.optJSONArray("reminders") ?: JSONArray()

            return StationSyncEnvelope(
                stationCode = root.optString("stationCode", "Alasal2027"),
                protocolVersion = root.optInt("protocolVersion", 1),
                deviceId = root.optString("deviceId", ""),
                deviceName = root.optString("deviceName", ""),
                exportedAt = root.optLong("exportedAt", 0L),
                checksumSha256 = root.optString("checksumSha256", ""),
                users = buildList {
                    for (i in 0 until usersArr.length()) {
                        val o = usersArr.getJSONObject(i)
                        add(
                            SyncUserDto(
                                username = o.getString("username"),
                                fullName = o.getString("fullName"),
                                roleCode = o.getString("roleCode"),
                                pinHash = o.getString("pinHash"),
                                isActive = o.optBoolean("isActive", true),
                                customPermissionsJson = o.optString("customPermissionsJson", ""),
                                biometricEnabled = o.optBoolean("biometricEnabled", false),
                                updatedAt = o.optLong("updatedAt", 0L),
                                syncId = o.optString("syncId", ""),
                                createdAt = o.optLong("createdAt", 0L)
                            )
                        )
                    }
                },
                permissions = buildList {
                    for (i in 0 until permsArr.length()) {
                        val o = permsArr.getJSONObject(i)
                        add(
                            SyncPermissionDto(
                                roleCode = o.getString("roleCode"),
                                permissionKey = o.getString("permissionKey"),
                                isAllowed = o.optBoolean("isAllowed", true),
                                descriptionAr = o.optString("descriptionAr", "")
                            )
                        )
                    }
                },
                settings = buildList {
                    for (i in 0 until settingsArr.length()) {
                        val o = settingsArr.getJSONObject(i)
                        add(
                            SyncSettingDto(
                                settingKey = o.getString("settingKey"),
                                settingValue = o.getString("settingValue"),
                                category = o.optString("category", "GENERAL"),
                                descriptionAr = o.optString("descriptionAr", ""),
                                updatedAt = o.optLong("updatedAt", 0L)
                            )
                        )
                    }
                },
                tariffs = buildList {
                    for (i in 0 until tariffsArr.length()) {
                        val o = tariffsArr.getJSONObject(i)
                        add(
                            SyncTariffDto(
                                tariffCode = o.getString("tariffCode"),
                                title = o.getString("title"),
                                subscriptionType = o.optString("subscriptionType", "عام"),
                                pricePerKwh = o.getDouble("pricePerKwh"),
                                fixedFee = o.optDouble("fixedFee", 0.0),
                                effectiveFrom = o.optLong("effectiveFrom", 0L),
                                isActive = o.optBoolean("isActive", true),
                                createdAt = o.optLong("createdAt", 0L)
                            )
                        )
                    }
                },
                tariffLogs = buildList {
                    for (i in 0 until tariffLogsArr.length()) {
                        val o = tariffLogsArr.getJSONObject(i)
                        add(
                            SyncTariffLogDto(
                                tariffCode = o.getString("tariffCode"),
                                subscriberCode = o.optStringOrNull("subscriberCode"),
                                subscriberNameSnapshot = o.optStringOrNull("subscriberNameSnapshot"),
                                oldPricePerKwh = o.getDouble("oldPricePerKwh"),
                                newPricePerKwh = o.getDouble("newPricePerKwh"),
                                oldFixedFee = o.optDouble("oldFixedFee", 0.0),
                                newFixedFee = o.optDouble("newFixedFee", 0.0),
                                effectiveFrom = o.optLong("effectiveFrom", 0L),
                                actorNameSnapshot = o.optString("actorNameSnapshot", ""),
                                reason = o.optString("reason", ""),
                                changedAt = o.optLong("changedAt", 0L)
                            )
                        )
                    }
                },
                subscribers = buildList {
                    for (i in 0 until subsArr.length()) {
                        val o = subsArr.getJSONObject(i)
                        add(
                            SyncSubscriberDto(
                                subscriberCode = o.getString("subscriberCode"),
                                fullName = o.getString("fullName"),
                                phoneNumber = o.optString("phoneNumber", ""),
                                meterNumber = o.optString("meterNumber", ""),
                                meterSealNumber = o.optString("meterSealNumber", ""),
                                address = o.optString("address", ""),
                                subscriptionType = o.optString("subscriptionType", "منزلي"),
                                customKwhPrice = o.optDouble("customKwhPrice", 0.45),
                                previousKwhPrice = o.optDoubleOrNull("previousKwhPrice"),
                                subscriptionDate = o.optLong("subscriptionDate", 0L),
                                isActive = o.optBoolean("isActive", true),
                                currentArrearsAmount = o.optDouble("currentArrearsAmount", 0.0),
                                currentCreditBalance = o.optDouble("currentCreditBalance", 0.0),
                                notes = o.optString("notes", ""),
                                createdAt = o.optLong("createdAt", 0L),
                                updatedAt = o.optLong("updatedAt", 0L)
                            )
                        )
                    }
                },
                readings = buildList {
                    for (i in 0 until readingsArr.length()) {
                        val o = readingsArr.getJSONObject(i)
                        add(
                            SyncReadingDto(
                                subscriberCode = o.getString("subscriberCode"),
                                billingPeriod = o.getString("billingPeriod"),
                                readingHalf = o.optInt("readingHalf", 1),
                                previousReading = o.optDouble("previousReading", 0.0),
                                currentReading = o.getDouble("currentReading"),
                                consumedKwh = o.getDouble("consumedKwh"),
                                readingDate = o.optLong("readingDate", 0L),
                                photoUri = o.optStringOrNull("photoUri"),
                                isLowReadingOverride = o.optBoolean("isLowReadingOverride", false),
                                overrideReason = o.optString("overrideReason", ""),
                                notes = o.optString("notes", "")
                            )
                        )
                    }
                },
                invoices = buildList {
                    for (i in 0 until invoicesArr.length()) {
                        val o = invoicesArr.getJSONObject(i)
                        add(
                            SyncInvoiceDto(
                                invoiceNumber = o.getString("invoiceNumber"),
                                subscriberCode = o.getString("subscriberCode"),
                                readingPeriod = o.optStringOrNull("readingPeriod"),
                                readingHalf = if (!o.has("readingHalf") || o.isNull("readingHalf")) null else o.optInt("readingHalf", 1),
                                tariffCode = o.optString("tariffCode", "DEFAULT-RESIDENTIAL"),
                                billingPeriod = o.getString("billingPeriod"),
                                previousReading = o.optDouble("previousReading", 0.0),
                                currentReading = o.optDouble("currentReading", 0.0),
                                consumedKwh = o.optDouble("consumedKwh", 0.0),
                                unitPriceApplied = o.optDouble("unitPriceApplied", 0.0),
                                fixedFeeApplied = o.optDouble("fixedFeeApplied", 0.0),
                                energyAmount = o.optDouble("energyAmount", 0.0),
                                previousArrearsApplied = o.optDouble("previousArrearsApplied", 0.0),
                                creditDeducted = o.optDouble("creditDeducted", 0.0),
                                netPayableAmount = o.optDouble("netPayableAmount", 0.0),
                                paidAmount = o.optDouble("paidAmount", 0.0),
                                remainingAmount = o.optDouble("remainingAmount", 0.0),
                                carriedOverToInvoiceNumber = o.optStringOrNull("carriedOverToInvoiceNumber"),
                                status = o.optString("status", "UNPAID"),
                                notes = o.optString("notes", ""),
                                priceModifiedAt = o.optLongOrNull("priceModifiedAt"),
                                priceModifiedBy = o.optStringOrNull("priceModifiedBy"),
                                priceModificationReason = o.optStringOrNull("priceModificationReason"),
                                issuedAt = o.optLong("issuedAt", 0L),
                                dueDate = o.optLong("dueDate", 0L)
                            )
                        )
                    }
                },
                collections = buildList {
                    for (i in 0 until collectionsArr.length()) {
                        val o = collectionsArr.getJSONObject(i)
                        add(
                            SyncCollectionDto(
                                collectionReference = o.getString("collectionReference"),
                                subscriberCode = o.getString("subscriberCode"),
                                invoiceNumber = o.optStringOrNull("invoiceNumber"),
                                invoiceBillingPeriod = o.optStringOrNull("invoiceBillingPeriod"),
                                collectorUsername = o.optStringOrNull("collectorUsername"),
                                collectorNameSnapshot = o.optString("collectorNameSnapshot", ""),
                                amountPaid = o.optDouble("amountPaid", 0.0),
                                previousBalanceSnapshot = o.optDouble("previousBalanceSnapshot", 0.0),
                                remainingAfterSnapshot = o.optDouble("remainingAfterSnapshot", 0.0),
                                appliedToInvoice = o.optDouble("appliedToInvoice", 0.0),
                                appliedToArrears = o.optDouble("appliedToArrears", 0.0),
                                addedToCredit = o.optDouble("addedToCredit", 0.0),
                                paymentMethod = o.optString("paymentMethod", "CASH"),
                                collectedAt = o.optLong("collectedAt", 0L),
                                notes = o.optString("notes", "")
                            )
                        )
                    }
                },
                receipts = buildList {
                    for (i in 0 until receiptsArr.length()) {
                        val o = receiptsArr.getJSONObject(i)
                        add(
                            SyncReceiptDto(
                                receiptNumber = o.getString("receiptNumber"),
                                collectionReference = o.getString("collectionReference"),
                                subscriberCode = o.getString("subscriberCode"),
                                collectorNameSnapshot = o.optString("collectorNameSnapshot", ""),
                                subscriberNameSnapshot = o.optString("subscriberNameSnapshot", ""),
                                subscriberCodeSnapshot = o.optString("subscriberCodeSnapshot", ""),
                                invoiceNumberSnapshot = o.optString("invoiceNumberSnapshot", ""),
                                paymentMethod = o.optString("paymentMethod", "CASH"),
                                previousBalance = o.optDouble("previousBalance", 0.0),
                                amount = o.optDouble("amount", 0.0),
                                balanceAfterReceipt = o.optDouble("balanceAfterReceipt", 0.0),
                                creditAfterReceipt = o.optDouble("creditAfterReceipt", 0.0),
                                stationNameSnapshot = o.optString("stationNameSnapshot", "محطة العسل 2027"),
                                stationPhoneSnapshot = o.optString("stationPhoneSnapshot", ""),
                                issuedAt = o.optLong("issuedAt", 0L),
                                isVoided = o.optBoolean("isVoided", false)
                            )
                        )
                    }
                },
                credits = buildList {
                    for (i in 0 until creditsArr.length()) {
                        val o = creditsArr.getJSONObject(i)
                        add(
                            SyncCreditDto(
                                subscriberCode = o.getString("subscriberCode"),
                                transactionType = o.getString("transactionType"),
                                amountDelta = o.optDouble("amountDelta", 0.0),
                                balanceAfter = o.optDouble("balanceAfter", 0.0),
                                recordedAt = o.optLong("recordedAt", 0L),
                                notes = o.optString("notes", "")
                            )
                        )
                    }
                },
                reminders = buildList {
                    for (i in 0 until remindersArr.length()) {
                        val o = remindersArr.getJSONObject(i)
                        add(
                            SyncReminderDto(
                                subscriberCode = o.optStringOrNull("subscriberCode"),
                                reminderType = o.optString("reminderType", "METER_READING"),
                                title = o.getString("title"),
                                message = o.optString("message", ""),
                                notes = o.optString("notes", ""),
                                scheduledAt = o.optLong("scheduledAt", 0L),
                                status = o.optString("status", "PENDING"),
                                channel = o.optString("channel", "LOCAL_NOTIFICATION"),
                                snoozeCount = o.optInt("snoozeCount", 0),
                                completedAt = o.optLongOrNull("completedAt"),
                                createdAt = o.optLong("createdAt", 0L)
                            )
                        )
                    }
                }
            )
        }
    }
}
