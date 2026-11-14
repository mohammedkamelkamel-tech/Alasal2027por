package com.example.domain.auth

import com.example.data.local.entity.PermissionEntity
import com.example.data.local.entity.UserEntity
import org.json.JSONObject

object StationRoles {
    const val ADMIN = "ADMIN"
    const val SUB_ADMIN = "SUB_ADMIN"
    const val COLLECTOR = "COLLECTOR"

    val allRoles = listOf(ADMIN, SUB_ADMIN, COLLECTOR)

    fun arabicTitle(roleCode: String): String = when (roleCode) {
        ADMIN -> "المدير الرئيسي"
        SUB_ADMIN -> "مدير فرعي / مشرف"
        COLLECTOR -> "أمين صندوق / محصّل"
        else -> roleCode
    }
}

enum class StationPermission(
    val key: String,
    val titleAr: String,
    val groupAr: String
) {
    VIEW_SUBSCRIBERS("VIEW_SUBSCRIBERS", "عرض المشتركين", "المشتركون"),
    ADD_SUBSCRIBER("ADD_SUBSCRIBER", "إضافة مشترك", "المشتركون"),
    EDIT_SUBSCRIBER("EDIT_SUBSCRIBER", "تعديل مشترك", "المشتركون"),
    DELETE_SUBSCRIBER("DELETE_SUBSCRIBER", "حذف مشترك", "المشتركون"),

    ISSUE_INVOICE("ISSUE_INVOICE", "إصدار فاتورة", "الفواتير والقراءات"),
    EDIT_INVOICE("EDIT_INVOICE", "تعديل فاتورة", "الفواتير والقراءات"),
    DELETE_INVOICE("DELETE_INVOICE", "حذف / إلغاء فاتورة", "الفواتير والقراءات"),
    OVERRIDE_LOW_READING("OVERRIDE_LOW_READING", "اعتماد قراءة أقل من السابقة", "الفواتير والقراءات"),

    EDIT_KWH_PRICE("EDIT_KWH_PRICE", "تعديل سعر الكيلوواط", "التسعير"),
    BULK_UPDATE_TARIFFS("BULK_UPDATE_TARIFFS", "تحديث الأسعار", "التسعير"),

    RECORD_COLLECTION("RECORD_COLLECTION", "تسجيل تحصيل", "التحصيل والتقارير"),
    VIEW_COLLECTIONS("VIEW_COLLECTIONS", "عرض التحصيلات", "التحصيل والتقارير"),
    VIEW_RECEIPTS("VIEW_RECEIPTS", "عرض الإيصالات", "التحصيل والتقارير"),
    VIEW_REPORTS("VIEW_REPORTS", "عرض التقارير", "التحصيل والتقارير"),
    EXPORT_PDF("EXPORT_PDF", "تصدير PDF", "التحصيل والتقارير"),

    MANAGE_USERS("MANAGE_USERS", "إدارة الحسابات والصلاحيات", "النظام والأمان"),
    EXPORT_BACKUP("EXPORT_BACKUP", "إدارة النسخ الاحتياطية والاستعادة", "النظام والأمان"),
    MANAGE_SETTINGS("MANAGE_SETTINGS", "إدارة الإعدادات", "النظام والأمان"),
    MANAGE_SYNC("MANAGE_SYNC", "تشغيل المزامنة عبر Wi-Fi", "النظام والأمان");

    companion object {
        fun fromKey(key: String): StationPermission? = when (key) {
            "MANAGE_TARIFFS" -> EDIT_KWH_PRICE
            "MANAGE_ACCOUNTS" -> MANAGE_USERS
            "MANAGE_BACKUPS" -> EXPORT_BACKUP
            else -> entries.firstOrNull { it.key == key }
        }
    }
}

object PermissionCatalog {
    const val PERM_MANAGE_TARIFFS_LEGACY = "MANAGE_TARIFFS"

    fun defaultPermissionsForRole(roleCode: String): Map<String, Boolean> {
        return when (roleCode) {
            StationRoles.ADMIN -> StationPermission.entries.associate { it.key to true }
            StationRoles.SUB_ADMIN -> mapOf(
                StationPermission.VIEW_SUBSCRIBERS.key to true,
                StationPermission.ADD_SUBSCRIBER.key to true,
                StationPermission.EDIT_SUBSCRIBER.key to true,
                StationPermission.DELETE_SUBSCRIBER.key to false,
                StationPermission.ISSUE_INVOICE.key to true,
                StationPermission.EDIT_INVOICE.key to true,
                StationPermission.DELETE_INVOICE.key to false,
                StationPermission.OVERRIDE_LOW_READING.key to true,
                StationPermission.EDIT_KWH_PRICE.key to true,
                StationPermission.BULK_UPDATE_TARIFFS.key to false,
                StationPermission.RECORD_COLLECTION.key to true,
                StationPermission.VIEW_COLLECTIONS.key to true,
                StationPermission.VIEW_RECEIPTS.key to true,
                StationPermission.VIEW_REPORTS.key to true,
                StationPermission.EXPORT_PDF.key to true,
                StationPermission.MANAGE_USERS.key to false,
                StationPermission.EXPORT_BACKUP.key to false,
                StationPermission.MANAGE_SETTINGS.key to false,
                StationPermission.MANAGE_SYNC.key to true
            )
            StationRoles.COLLECTOR -> mapOf(
                StationPermission.VIEW_SUBSCRIBERS.key to true,
                StationPermission.ADD_SUBSCRIBER.key to false,
                StationPermission.EDIT_SUBSCRIBER.key to false,
                StationPermission.DELETE_SUBSCRIBER.key to false,
                StationPermission.ISSUE_INVOICE.key to false,
                StationPermission.EDIT_INVOICE.key to false,
                StationPermission.DELETE_INVOICE.key to false,
                StationPermission.OVERRIDE_LOW_READING.key to false,
                StationPermission.EDIT_KWH_PRICE.key to false,
                StationPermission.BULK_UPDATE_TARIFFS.key to false,
                StationPermission.RECORD_COLLECTION.key to true,
                StationPermission.VIEW_COLLECTIONS.key to true,
                StationPermission.VIEW_RECEIPTS.key to true,
                StationPermission.VIEW_REPORTS.key to false,
                StationPermission.EXPORT_PDF.key to true,
                StationPermission.MANAGE_USERS.key to false,
                StationPermission.EXPORT_BACKUP.key to false,
                StationPermission.MANAGE_SETTINGS.key to false,
                StationPermission.MANAGE_SYNC.key to false
            )
            else -> StationPermission.entries.associate { it.key to false }
        }
    }

    fun parseCustomPermissions(json: String): Map<String, Boolean> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, Boolean>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.optBoolean(k, false)
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun serializeCustomPermissions(map: Map<String, Boolean>): String {
        if (map.isEmpty()) return ""
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun resolveUserPermissionMap(
        user: UserEntity,
        rolePermissions: List<PermissionEntity>
    ): Map<String, Boolean> {
        val base = defaultPermissionsForRole(user.roleCode).toMutableMap()
        rolePermissions
            .filter { it.roleCode == user.roleCode }
            .forEach { perm ->
                base[perm.permissionKey] = perm.isAllowed
                if (perm.permissionKey == PERM_MANAGE_TARIFFS_LEGACY) {
                    base[StationPermission.EDIT_KWH_PRICE.key] = perm.isAllowed
                    base[StationPermission.BULK_UPDATE_TARIFFS.key] = perm.isAllowed
                }
            }
        val custom = parseCustomPermissions(user.customPermissionsJson)
        custom.forEach { (k, v) ->
            base[k] = v
        }
        return base
    }

    fun isEffectivePermissionAllowed(
        user: UserEntity,
        rolePermissions: List<PermissionEntity>,
        requestedKey: String
    ): Boolean {
        if (!user.isActive) return false
        val map = resolveUserPermissionMap(user, rolePermissions)
        map[requestedKey]?.let { return it }
        return when (requestedKey) {
            PERM_MANAGE_TARIFFS_LEGACY ->
                (map[StationPermission.EDIT_KWH_PRICE.key] == true) ||
                    (map[StationPermission.BULK_UPDATE_TARIFFS.key] == true)
            "MANAGE_BACKUPS" -> map[StationPermission.EXPORT_BACKUP.key] ?: false
            "MANAGE_ACCOUNTS" -> map[StationPermission.MANAGE_USERS.key] ?: false
            StationPermission.EDIT_KWH_PRICE.key ->
                map[PERM_MANAGE_TARIFFS_LEGACY]
                    ?: defaultPermissionsForRole(user.roleCode)[StationPermission.EDIT_KWH_PRICE.key]
                    ?: false
            StationPermission.BULK_UPDATE_TARIFFS.key ->
                map[PERM_MANAGE_TARIFFS_LEGACY]
                    ?: defaultPermissionsForRole(user.roleCode)[StationPermission.BULK_UPDATE_TARIFFS.key]
                    ?: false
            else -> defaultPermissionsForRole(user.roleCode)[requestedKey] ?: false
        }
    }

    fun arabicLabel(key: String): String {
        if (key == PERM_MANAGE_TARIFFS_LEGACY) return "تعديل وتحديث أسعار الكيلوواط"
        return StationPermission.fromKey(key)?.titleAr ?: key
    }
}
