# توثيق قاعدة البيانات — محطة العسل 2027 (Alasal2027)

- **اسم قاعدة البيانات:** `alasal_2027.db`
- **الإصدار الحالي للمخطط:** `version = 6`
- **تفعيل القيود المرجعية:** `PRAGMA foreign_keys = ON` عند فتح الاتصال
- **عدد الجداول:** 14 جدولاً مترابطاً بالكامل

---

## 1. الجداول الأربعة عشر (14 Tables)

1. **`subscribers` (المشتركين):**
   - الحقول الأساسية: `id`, `subscriber_code` (فريد), `full_name`, `phone_number`, `meter_number` (فريد), `meter_seal_number`, `address`, `subscription_type`, `custom_kwh_price`, `previous_kwh_price`, `subscription_date`, `is_active`, `current_arrears_amount`, `current_credit_balance`, `notes`, `created_at`, `updated_at`.
   - الفهارس: فهرس فريد على `subscriber_code` و`meter_number`، وفهارس بحث على `full_name` و`phone_number`.

2. **`kwh_tariffs` (تعرفات الكيلوواط):**
   - `id`, `tariff_code` (فريد), `title`, `subscription_type`, `price_per_kwh`, `fixed_fee`, `effective_from`, `is_active`, `created_at`.

3. **`tariff_change_logs` (سجل تغييرات الأسعار):**
   - يوثّق كل تعديل سعر (مباشر من ملف المشترك، فردي، جماعي، شامل، أو تعديل فاتورة): `tariff_id`, `subscriber_id`, `subscriber_name_snapshot`, `old_price_per_kwh`, `new_price_per_kwh`, `effective_from`, `changed_by_user_id`, `actor_name_snapshot`, `reason`, `changed_at`.

4. **`meter_readings` (قراءات العدادات):**
   - `id`, `subscriber_id` (FK -> `subscribers`), `billing_period`, **`reading_half`** (`1` للنصف الأول 1-15، `2` للنصف الثاني 16-نهاية الشهر), `previous_reading`, `current_reading`, `consumed_kwh`, `recorded_by_user_id`, `reading_date`, `photo_uri`, `is_low_reading_override`, `override_reason`, `notes`, `created_at`.
   - قيد فريد مركب على `(subscriber_id, billing_period, reading_half)` لمنع تكرار قراءة لنفس المشترك في نفس النصف من الشهر، مع السماح بقراءة في النصف الأول وأخرى في النصف الثاني من نفس الشهر، وفهرس `(billing_period, reading_half)` لتسريع تجميع الاستهلاك.

5. **`invoices` (الفواتير):**
   - `id`, `invoice_number` (فريد `INV-2027-XXXX`), `subscriber_id` (FK), `meter_reading_id` (FK فريد), `tariff_id` (FK), `billing_period`, `previous_reading`, `current_reading`, `consumed_kwh`, `unit_price_applied`, `fixed_fee_applied`, `energy_amount`, `previous_arrears_applied`, `credit_deducted`, `net_payable_amount`, `paid_amount`, `remaining_amount`, `carried_over_to_invoice_id`, `status` (`UNPAID`, `PARTIAL`, `PAID`, `CARRIED_OVER`, `CANCELLED`), `price_modified_at`, `price_modified_by`, `price_modification_reason`, `issued_at`, `due_date`.

6. **`collections` (التحصيلات):**
   - `id`, `collection_reference` (فريد `COL-2027-XXXX`), `subscriber_id` (FK), `invoice_id` (FK), `collected_by_user_id`, `collector_name_snapshot`, `amount_paid`, `previous_balance_snapshot`, `remaining_after_snapshot`, `applied_to_invoice`, `applied_to_arrears`, `added_to_credit`, `payment_method`, `collected_at`, `notes`.
   - كل تحصيل يُحفظ كسجل مستقل غير قابل للاستبدال.

7. **`receipts` (الإيصالات):**
   - `id`, `receipt_number` (فريد `RCP-2027-XXXX`), `collection_id` (FK فريد 1:1), `subscriber_id` (FK), `issued_by_user_id`, `collector_name_snapshot`, `subscriber_name_snapshot`, `subscriber_code_snapshot`, `invoice_number_snapshot`, `payment_method`, `previous_balance`, `amount`, `balance_after_receipt`, `credit_after_receipt`, `station_name_snapshot`, `station_phone_snapshot`, `issued_at`.

8. **`arrears` (المتأخرات والترحيل):**
   - يتتبع مصدر الترحيل (`source_invoice_id` -> `carried_over_to_invoice_id`) لمنع احتساب أو ترحيل نفس الدين مرتين.

9. **`credit_balances` (دفتر الأرصدة الدائنة):**
   - دفتر حركات غير قابل للتكرار (`INITIAL_CREDIT`, `APPLIED_TO_INVOICE`, `DEPOSIT_OVERPAYMENT`, `REVERSAL_ON_PRICE_EDIT`, `REVERSAL_ON_INVOICE_DELETE`).

10. **`reminders` (التذكيرات):**
    - `id`, `subscriber_id` (Nullable FK), `invoice_id` (Nullable FK), `reminder_type`, `title`, `message`, `notes`, `scheduled_at`, `status` (`PENDING`, `SNOOZED`, `NOTIFIED`, `COMPLETED`), `channel`, `snooze_count`, `completed_at`, `created_at`.

11. **`users` (حسابات المستخدمين):**
    - `id`, `username` (فريد), `full_name`, `role_code` (`ADMIN`, `SUB_ADMIN`, `COLLECTOR`), `pin_hash` (SHA-256), `is_active`, `custom_permissions_json`, **`biometric_enabled`** (`INTEGER NOT NULL DEFAULT 0`), `updated_at`, `sync_id`, `created_at`.

12. **`permissions` (مصفوفة صلاحيات الأدوار):**
    - قيد فريد على `(role_code, permission_key)` مع 19 مفتاح صلاحية.

13. **`app_settings` (إعدادات النظام والتسلسلات المحفوظة):**
    - يحفظ اسم المحطة، وضع العمل دون إنترنت، وآخر أرقام التسلسل المحفوظة (`last_invoice_sequence`, `last_collection_sequence`, `last_receipt_sequence`) لضمان عدم التكرار بعد الإغلاق أو الحذف أو المزامنة.

14. **`financial_audit_logs` (سجل التدقيق المالي):**
    - سجل تاريخي متكامل لكل عملية مالية أو إدارية (بما في ذلك تسجيل الدخول بكلمة المرور `USER_LOGIN` وبالبصمة `USER_BIOMETRIC_LOGIN` وتحديث خيارات البصمة).

---

## 2. مسار الترقية غير الهدّام (Migrations)
- **`MIGRATION_1_2` (v1 -> v2):** إضافة تفاصيل العداد والسعر المستقل للمشترك، وتفاصيل ترحيل الفواتير وتعديل الأسعار وحقول التدقيق.
- **`MIGRATION_2_3` (v2 -> v3):** إضافة لقطات الإيصالات والتحصيلات وإعادة بناء جدول التذكيرات لدعم التذكيرات العامة والمرتبطة.
- **`MIGRATION_3_4` (v3 -> v4):** إضافة الصلاحيات المخصصة لكل مستخدم وطابع المزامنة `updated_at` و`sync_id`.
- **`MIGRATION_4_5` (v4 -> v5):** إضافة حقل `biometric_enabled` لجدول `users` (`ALTER TABLE users ADD COLUMN biometric_enabled INTEGER NOT NULL DEFAULT 0`) لتمكين أو إيقاف الدخول بالبصمة لكل حساب دون المساس بالبيانات السابقة.
- **`MIGRATION_5_6` (v5 -> v6):** إعادة بناء جدول `meter_readings` بشكل غير هدّام لإضافة عمود `reading_half` (مع التصنيف التلقائي للقراءات السابقة حسب يوم القراءة: `1..15 -> 1` و`16..نهاية الشهر -> 2`) وتحديث الفهرس الفريد إلى `(subscriber_id, billing_period, reading_half)` مع فهرس `(billing_period, reading_half)`.
- تدعم **الاستعادة الآمنة عبر 18 خطوة** استيراد نسخ الإصدارات `v1..v6` وترقيتها داخل مجلد عزل مؤقت قبل استبدال قاعدة البيانات الحية.
