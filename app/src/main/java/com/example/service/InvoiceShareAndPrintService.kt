package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.SubscriberEntity
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline service for zero-permission Photo Picker local image storage,
 * RTL HTML invoice printing via Android PrintManager, generic sharing, and WhatsApp dispatch.
 */
object InvoiceShareAndPrintService {

    fun copyMeterPhotoToPrivateStorage(context: Context, sourceUri: Uri, subscriberCode: String): String? {
        return runCatching {
            val dir = File(context.filesDir, "meter_photos").apply { mkdirs() }
            val safeCode = subscriberCode.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val target = File(dir, "meter_${safeCode}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    fun buildInvoiceShareText(invoice: InvoiceEntity, subscriber: SubscriberEntity?): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("ar"))
        val subName = subscriber?.fullName ?: "مشترك #${invoice.subscriberId}"
        fun amount(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
        return buildString {
            appendLine("⚡ *فاتورة كهرباء - محطة العسل*")
            appendLine("👤 المشترك: $subName")
            appendLine("📄 رقم الفاتورة: #${invoice.invoiceNumber}")
            appendLine("📊 القراءة السابقة: ${amount(invoice.previousReading)} ك.و.س")
            appendLine("📈 القراءة الحالية: ${amount(invoice.currentReading)} ك.و.س")
            appendLine("⚡ الاستهلاك: ${amount(invoice.consumedKwh)} ك.و.س")
            appendLine("💵 سعر الكيلو: ${amount(invoice.unitPriceApplied)} ريال")
            appendLine("💰 إجمالي المستحق: ${amount(invoice.netPayableAmount)} ريال يمني")
            appendLine("💵 المدفوع: ${amount(invoice.paidAmount)} ريال")
            appendLine("🔻 المتبقي: ${amount(invoice.remainingAmount)} ريال")
            append("📅 تاريخ الفاتورة: ${dateFormat.format(Date(invoice.issuedAt))}")
        }
    }

    fun shareInvoiceText(context: Context, invoice: InvoiceEntity, subscriber: SubscriberEntity?) {
        val text = buildInvoiceShareText(invoice, subscriber)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "فاتورة كهرباء ${invoice.invoiceNumber} - محطة العسل")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, "مشاركة الفاتورة").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    data class PhoneNormalizationResult(
        val normalizedDigits: String,
        val isValid: Boolean,
        val warningMessage: String? = null
    )

    fun normalizePhoneForWhatsApp(
        rawPhone: String?,
        defaultCountryCode: String = "967"
    ): PhoneNormalizationResult {
        val cleanCountry = defaultCountryCode.filter { it.isDigit() }.ifBlank { "967" }
        val trimmed = rawPhone?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return PhoneNormalizationResult(
                normalizedDigits = "",
                isValid = false,
                warningMessage = "لا يوجد رقم هاتف مسجل لهذا المشترك لإرسال رسالة واتساب."
            )
        }
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length < 7) {
            return PhoneNormalizationResult(
                normalizedDigits = digitsOnly,
                isValid = false,
                warningMessage = "رقم الهاتف المسجل ($trimmed) غير صالح أو غير مكتمل."
            )
        }
        val normalized = when {
            trimmed.startsWith("+") -> digitsOnly
            digitsOnly.startsWith("00") -> digitsOnly.drop(2)
            digitsOnly.startsWith(cleanCountry) && digitsOnly.length >= cleanCountry.length + 7 -> digitsOnly
            digitsOnly.startsWith("0") && digitsOnly.length in 9..11 -> cleanCountry + digitsOnly.drop(1)
            digitsOnly.length in 8..9 -> cleanCountry + digitsOnly
            else -> digitsOnly
        }
        if (normalized.length < 10) {
            return PhoneNormalizationResult(
                normalizedDigits = normalized,
                isValid = false,
                warningMessage = "رقم الهاتف المسجل ($trimmed) قصير ولا يصلح لفتح محادثة واتساب."
            )
        }
        return PhoneNormalizationResult(
            normalizedDigits = normalized,
            isValid = true,
            warningMessage = null
        )
    }

    fun buildWhatsAppChatUri(normalizedDigits: String, text: String): Uri {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        return Uri.parse("https://wa.me/$normalizedDigits?text=$encodedText")
    }

    fun buildStatementShareText(
        statement: com.example.domain.finance.SubscriberAccountStatement
    ): String {
        val sub = statement.subscriber
        return buildString {
            appendLine("⚡ *محطة العسل*")
            appendLine("كشف حساب المشترك: *${sub.fullName}* (${sub.subscriberCode})")
            appendLine("رقم العداد: ${sub.meterNumber} | السعر الحالي: ${sub.customKwhPrice}/ك.و.س")
            appendLine("إجمالي الاستهلاك: ${statement.totalConsumedKwh} ك.و.س")
            appendLine("إجمالي الفواتير: ${statement.totalBilledEnergy}")
            appendLine("إجمالي التحصيلات: ${statement.totalCollected}")
            appendLine("المتأخرات النشطة: ${statement.activeArrears}")
            appendLine("الرصيد الدائن: ${statement.creditBalance}")
            append("الرصيد النهائي: *${statement.netFinalBalance}*")
        }
    }

    private fun generateInvoicePngImage(
        context: Context,
        invoice: InvoiceEntity,
        subscriber: SubscriberEntity?
    ): File {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "Invoice_${invoice.invoiceNumber}.png")
        val width = 900
        val height = 1180
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val navy = Color.rgb(13, 38, 60)
        val gold = Color.rgb(212, 175, 55)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 34f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 170f, Paint().apply { color = navy })
        paint.color = Color.WHITE
        paint.textSize = 46f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        canvas.drawText("محطة العسل", width - 55f, 72f, paint)
        paint.textSize = 30f
        canvas.drawText("فاتورة كهرباء رقم: ${invoice.invoiceNumber}", width - 55f, 125f, paint)
        canvas.drawRect(0f, 170f, width.toFloat(), 180f, Paint().apply { color = gold })
        paint.color = navy
        paint.textSize = 32f
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val rows = listOf(
            "اسم المشترك: ${subscriber?.fullName ?: "مشترك #${invoice.subscriberId}"}",
            "رقم المشترك: ${subscriber?.subscriberCode ?: "-"}",
            "رقم العداد: ${subscriber?.meterNumber ?: "-"}",
            "الفترة: ${invoice.billingPeriod}",
            "القراءة السابقة: ${invoice.previousReading}",
            "القراءة الحالية: ${invoice.currentReading}",
            "الاستهلاك (ك.و.س): ${invoice.consumedKwh}",
            "سعر الكيلوواط: ${invoice.unitPriceApplied} ريال",
            "قيمة الاستهلاك: ${invoice.energyAmount} ريال",
            "المتأخرات المرحلة: ${invoice.previousArrearsApplied} ريال",
            "الرصيد الدائن المخصوم: ${invoice.creditDeducted} ريال",
            "صافي الفاتورة: ${invoice.netPayableAmount} ريال",
            "المدفوع: ${invoice.paidAmount} ريال",
            "المتبقي: ${invoice.remainingAmount} ريال",
            "الحالة: ${translateStatus(invoice.status)}",
            "تاريخ الإصدار: ${SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar")).format(Date(invoice.issuedAt))}"
        )
        var y = 250f
        rows.forEachIndexed { index, row ->
            if (index % 2 == 0) {
                canvas.drawRoundRect(35f, y - 42f, width - 35f, y + 16f, 12f, 12f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3F6F9.toInt() })
            }
            canvas.drawText(row, width - 60f, y, paint)
            y += 62f
        }
        paint.color = gold
        paint.strokeWidth = 5f
        canvas.drawLine(45f, y + 8f, width - 45f, y + 8f, paint)
        paint.color = navy
        paint.textSize = 25f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("شكراً لثقتكم بمحطة العسل", width / 2f, height - 48f, paint)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun sendImageToWhatsApp(
        context: Context,
        imageFile: File,
        caption: String
    ): String? {
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.whatsapp")
            }
            context.startActivity(intent)
            null
        }.getOrElse {
            "تعذر فتح واتساب لمشاركة الصورة. تأكد من تثبيت واتساب."
        }
    }

    /**
     * Launches direct WhatsApp chat to normalized subscriber phone with invoice summary.
     * Returns null on success or an explicit Arabic warning message when phone is missing/invalid.
     */
    fun sendViaWhatsApp(
        context: Context,
        invoice: InvoiceEntity,
        subscriber: SubscriberEntity?,
        defaultCountryCode: String = "967"
    ): String? {
        val norm = normalizePhoneForWhatsApp(subscriber?.phoneNumber, defaultCountryCode)
        if (!norm.isValid) return norm.warningMessage
        val image = runCatching { generateInvoicePngImage(context, invoice, subscriber) }.getOrElse {
            return "تعذر تجهيز صورة الفاتورة للمشاركة."
        }
        return sendImageToWhatsApp(
            context,
            image,
            "فاتورة كهرباء رقم ${invoice.invoiceNumber} للمشترك ${subscriber?.fullName.orEmpty()}"
        )
    }

    fun sendStatementViaWhatsApp(
        context: Context,
        statement: com.example.domain.finance.SubscriberAccountStatement,
        defaultCountryCode: String = "967"
    ): String? {
        val norm = normalizePhoneForWhatsApp(statement.subscriber.phoneNumber, defaultCountryCode)
        if (!norm.isValid) {
            return norm.warningMessage
        }
        val text = buildStatementShareText(statement)
        val waUri = buildWhatsAppChatUri(norm.normalizedDigits, text)
        val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(waIntent)
            null
        }.getOrElse {
            "تعذر فتح محادثة واتساب مباشرة. تأكد من تثبيت واتساب وصحة رقم المشترك."
        }
    }

    fun printInvoice(context: Context, invoice: InvoiceEntity, subscriber: SubscriberEntity?, thermalWidthMm: Int = 58) {
        val arabicLocale = Locale.forLanguageTag("ar")
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", arabicLocale)
        val html = """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
            <meta charset="utf-8" />
            <style>
              @page { size: ${if (thermalWidthMm == 80) "80mm" else "58mm"} auto; margin: 2mm; }
              html, body { width: ${if (thermalWidthMm == 80) "76mm" else "54mm"}; margin: 0; padding: 0; }
              body { position: relative; box-sizing: border-box; font-family: sans-serif; padding: 3mm; color: #0D263C; direction: rtl; font-size: 11px; }
              body:before { content: ""; position: fixed; inset: 18% 8%; background: url('file:///android_res/drawable/img_alasal_watermark.png') center center / contain no-repeat; opacity: .10; z-index: -1; }
              .header { border-bottom: 2px solid #D4AF37; padding-bottom: 7px; margin-bottom: 9px; text-align: center; }
              .logo { width: 42px; height: 42px; object-fit: contain; display: block; margin: 0 auto 5px; }
              .title { font-size: 15px; font-weight: bold; color: #0D263C; }
              .sub { color: #475569; font-size: 10px; }
              p { margin: 6px 0; line-height: 1.5; overflow-wrap: anywhere; }
              table { width: 100%; border-collapse: collapse; margin-top: 8px; table-layout: fixed; }
              th, td { border: 1px solid #CBD5E1; padding: 5px 4px; text-align: right; font-size: 10px; overflow-wrap: anywhere; }
              th { background-color: #0D263C; color: #FFFFFF; }
              .total { font-weight: bold; background-color: #FAF1D1; }
              @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
            </style>
            </head>
            <body>
              <div class="header">
                <img class="logo" src="file:///android_res/drawable/img_alasal_logo_transparent.png" /><div class="title">محطة العسل</div>
                <div class="sub">إشعار فاتورة استهلاك كهرباء — رقم ${invoice.invoiceNumber}</div>
              </div>
              <p><strong>المشترك:</strong> ${subscriber?.fullName ?: "-"} (${subscriber?.subscriberCode ?: "-"})</p>
              <p><strong>رقم العداد:</strong> ${subscriber?.meterNumber ?: "-"} | <strong>الفترة:</strong> ${invoice.billingPeriod}</p>
              <table>
                <tr><th>البيان</th><th>القيمة</th></tr>
                <tr><td>القراءة السابقة / الحالية</td><td>${invoice.previousReading} ← ${invoice.currentReading}</td></tr>
                <tr><td>الاستهلاك (ك.و.س)</td><td>${invoice.consumedKwh}</td></tr>
                <tr><td>سعر الكيلوواط المطبق</td><td>${invoice.unitPriceApplied}</td></tr>
                <tr><td>قيمة الاستهلاك الحالي</td><td>${invoice.energyAmount}</td></tr>
                <tr><td>المتأخرات المرحلة</td><td>${invoice.previousArrearsApplied}</td></tr>
                <tr><td>الرصيد الدائن المخصوم</td><td>${invoice.creditDeducted}</td></tr>
                <tr class="total"><td>صافي الفاتورة المطلوب</td><td>${invoice.netPayableAmount}</td></tr>
                <tr><td>المدفوع</td><td>${invoice.paidAmount}</td></tr>
                <tr class="total"><td>المتبقي</td><td>${invoice.remainingAmount}</td></tr>
              </table>
              <p class="sub">تاريخ الإصدار: ${dateFormat.format(Date(invoice.issuedAt))} | الحالة: ${translateStatus(invoice.status)}</p>
            </body>
            </html>
        """.trimIndent()

        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null && view != null) {
                    val jobName = "Alasal_${invoice.invoiceNumber}"
                    val adapter = view.createPrintDocumentAdapter(jobName)
                    val width = if (thermalWidthMm == 80) 3150 else 2283
                    val attrs = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize(
                            if (thermalWidthMm == 80) "ALASAL_THERMAL_80" else "ALASAL_THERMAL_58",
                            if (thermalWidthMm == 80) "Thermal 80mm" else "Thermal 58mm",
                            width,
                            12000
                        ))
                        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager.print(jobName, adapter, attrs)
                }
            }
        }
        webView.loadDataWithBaseURL("file:///android_res/drawable/", html, "text/HTML", "UTF-8", null)
    }

    fun translateStatus(status: String): String = when (status) {
        "PAID" -> "مدفوعة"
        "PARTIAL" -> "مدفوعة جزئياً"
        "UNPAID" -> "غير مدفوعة"
        "CARRIED_OVER" -> "مرحّلة لفاتورة أحدث"
        "CANCELLED" -> "ملغاة"
        else -> status
    }
}
