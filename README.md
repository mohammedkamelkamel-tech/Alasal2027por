# محطة العسل 2027 (Alasal2027)

نظام متكامل لإدارة فواتير الكهرباء، المشتركين، قراءات العدادات، الأسعار المباشرة والمركزية، التحصيل والإيصالات، كشوف الحساب والتحصيل الميداني (A4 PDF وExcel CSV)، أتمتة واتساب الفورية، قفل التطبيق والبصمة، التذكيرات، الصلاحيات، المزامنة المحلية عبر Wi-Fi، والنسخ الاحتياطي والاستعادة الآمنة — يعمل محلياً دون الحاجة إلى اتصال بالإنترنت أو خادم مدفوع.

---

## 1. المواصفات التقنية (Tech Stack)
- **اللغة:** Kotlin `2.2.10`
- **واجهة المستخدم:** Jetpack Compose + Material Design 3 + دعم كامل للغة العربية واتجاه RTL + انتقالات `NavHost` متدرجة + المظهر الليلي والنهاري والتلقائي
- **المعمارية:** MVVM + Repository Pattern (`AlasalRepository`, `BillingRepository`, `StationSyncRepository`)
- **قاعدة البيانات:** Room Database (`2.7.0` / SQLite v6 — 14 جدولاً مترابطاً مع ترحيل غير هدّام `v1 → v2 → v3 → v4 → v5 → v6`)
- **الحماية والبيومترية:** AndroidX `BiometricPrompt` + `BiometricManager` مع كلمة مرور إلزامية دائماً وقفل تلقائي عند الخلفية أو انتهاء مهلة عدم النشاط وقفل فوري يدوي
- **التخزين التفضيلي:** Jetpack DataStore Preferences (بما فيها رمز الدولة الافتراضي لواتساب `defaultCountryCode` وإعدادات القفل التلقائي)
- **المهام الخلفية:** Android WorkManager (`StationMaintenanceWorker`, `ReadingReminderWorker`, `DailyAutoBackupWorker`)
- **الوصول للملفات والمشاركة:** Android Storage Access Framework (SAF) + `FileProvider` + `PrintManager` + A4 PDF + UTF-8 BOM Excel CSV + Thermal 58mm/80mm + أتمتة واتساب الفورية
- **إعدادات Gradle:** AGP `9.1.1`، Kotlin `2.2.10`، KSP `2.3.5`، `minSdk = 24`، `targetSdk = 36`، `compileSdk = 36`

---

## 2. بيانات الدخول الافتراضية
عند التشغيل الأول تتم تهيئة الحسابات الأساسية تلقائياً:
- **المدير الرئيسي (`ADMIN`):** اسم المستخدم `admin` | كلمة المرور الافتراضية `2027`
- **محصل ميداني (`COLLECTOR`):** اسم المستخدم `collector_1` | كلمة المرور الافتراضية `1234`

> يُوصى بتغيير كلمة المرور الافتراضية وتفعيل الدخول السريع بالبصمة لكل حساب راغب من شاشة **الحسابات والصلاحيات**.

---

## 3. متطلبات البناء والتشغيل
1. **Android Studio:** إصدار حديث يدعم AGP `9.1.1` وJDK 17+ (مع ضبط `sourceCompatibility = 11`).
2. **SDK:** Android SDK Platform 36 وBuild-Tools المطابقة.
3. **تنفيذ الاختبارات وبناء APK عبر سطر الأوامر:**
   - تشغيل كافة اختبارات الوحدة وRobolectric (13 مجموعة / 48 اختباراً):
     ```bash
     gradle clean :app:testDebugUnitTest --console=plain
     ```
   - بناء ملف Debug APK القابل للتثبيت:
     ```bash
     gradle :app:assembleDebug
     ```
   - بناء ملف Release APK (يتطلب ضبط مفاتيح التوقيع `KEYSTORE_PATH` و`STORE_PASSWORD` و`KEY_PASSWORD` أو توفير `my-upload-key.jks` بمستوى الجذر):
     ```bash
     gradle :app:assembleRelease
     ```

---

## 4. هيكل المشروع
- `app/src/main/java/com/example/data/` — الكيانات الـ14، الـDAOs، قاعدة البيانات وترحيل `v1..v6`، والمستودعات.
- `app/src/main/java/com/example/domain/` — المحرك المالي (`FinancialCalculator`)، حاسبة إجمالي الاستهلاك الشهري ونصف الشهري (`ConsumptionSummaryCalculator`)، مولد التقارير وكشف الحساب وكشوف النزول الميداني (`StationReportGenerator`)، كتالوج الصلاحيات (19 صلاحية)، ونماذج المزامنة.
- `app/src/main/java/com/example/service/` — خدمات PDF (بما فيها تقرير إجمالي الاستهلاك الشهري ونصف الشهري) والطباعة الحرارية، تصدير Excel CSV بترميز UTF-8 BOM، معالجة أرقام الهاتف وأتمتة واتساب، التذكيرات والإشعارات، محرك مزامنة Wi-Fi المحلي، ومدير النسخ الاحتياطي والاستعادة الآمنة عبر 18 خطوة.
- `app/src/main/java/com/example/ui/` — الشاشات (بما فيها شاشة القفل والبصمة وقسم إجمالي الاستهلاك وقسم التحصيل الميداني)، المكونات المخصصة، الهوية البصرية، التنقل المتكيف، والـViewModels.
- `app/src/test/java/com/example/` — 13 مجموعة اختبارات وحدة وتدقيق مالي وقاعدة بيانات وواجهة في Robolectric (48/48 ناجحة).
