package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.domain.finance.GeneratedReport
import com.example.domain.finance.StationReportGenerator
import com.example.domain.finance.SubscriberAccountStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PairedBluetoothPrinter(
    val name: String,
    val macAddress: String
)

enum class PrinterConnectionState {
    CONNECTED,
    DISCONNECTED,
    NOT_CONFIGURED,
    BLUETOOTH_DISABLED,
    PERMISSION_REQUIRED
}

/**
 * Direct Bluetooth Thermal Printer service using RFCOMM SPP + ESC/POS Raster Bit Image (GS v 0).
 *
 * Rendering Arabic RTL receipts, invoices, statements, and the Alasal logo to high-contrast
 * monochrome Bitmaps (384px for 58mm, 576px for 80mm) and encoding them to ESC/POS `GS v 0`
 * raster commands guarantees crisp Arabic shaping and logo fidelity across all 58mm/80mm
 * Bluetooth thermal printers without codepage corruption.
 */
object BluetoothThermalPrinterService {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Test hooks for JVM/Robolectric acceptance tests (null in production)
    internal var pairedPrintersProviderForTest: (() -> Result<List<PairedBluetoothPrinter>>)? = null
    internal var connectionCheckerForTest: ((String) -> PrinterConnectionState)? = null
    internal var directByteSenderForTest: ((String, ByteArray) -> Result<Unit>)? = null

    fun requiredBluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }

    fun hasBluetoothPermissions(context: Context): Boolean {
        val required = requiredBluetoothPermissions()
        if (required.isEmpty()) return true
        return required.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothPrinterConfigured(prefs: NotificationAndPrintPrefs): Boolean =
        prefs.printerConnectionType.equals("BLUETOOTH", ignoreCase = true) &&
            prefs.printerDeviceAddress.isNotBlank()

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): Result<List<PairedBluetoothPrinter>> {
        pairedPrintersProviderForTest?.let { return it.invoke() }

        if (!hasBluetoothPermissions(context)) {
            return Result.failure(SecurityException("يرجى منح أذونات البلوتوث لعرض الطابعات المقترنة."))
        }
        val adapter = getBluetoothAdapter(context)
            ?: return Result.failure(IllegalStateException("هذا الجهاز لا يدعم البلوتوث."))
        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("البلوتوث مغلق حالياً. يرجى تفعيله من إعدادات الهاتف."))
        }
        return runCatching {
            val bonded: Set<BluetoothDevice> = adapter.bondedDevices.orEmpty()
            bonded.map { device ->
                PairedBluetoothPrinter(
                    name = device.name?.takeIf { it.isNotBlank() } ?: "جهاز بلوتوث غير مسمى",
                    macAddress = device.address.orEmpty()
                )
            }
                .filter { it.macAddress.isNotBlank() }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun checkPrinterConnection(
        context: Context,
        macAddress: String
    ): PrinterConnectionState {
        if (macAddress.isBlank()) return PrinterConnectionState.NOT_CONFIGURED
        connectionCheckerForTest?.let { return it.invoke(macAddress) }

        if (!hasBluetoothPermissions(context)) {
            return PrinterConnectionState.PERMISSION_REQUIRED
        }
        val adapter = getBluetoothAdapter(context) ?: return PrinterConnectionState.DISCONNECTED
        if (!adapter.isEnabled) return PrinterConnectionState.BLUETOOTH_DISABLED

        return withContext(Dispatchers.IO) {
            runCatching {
                val device = adapter.getRemoteDevice(macAddress)
                if (adapter.isDiscovering) {
                    runCatching { adapter.cancelDiscovery() }
                }
                val socket = createCompatibleSocket(device)
                socket.use {
                    it.connect()
                    if (it.isConnected) PrinterConnectionState.CONNECTED else PrinterConnectionState.DISCONNECTED
                }
            }.getOrDefault(PrinterConnectionState.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createCompatibleSocket(device: BluetoothDevice): BluetoothSocket {
        return try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (_: Exception) {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            method.invoke(device, 1) as BluetoothSocket
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendEscPosBytesDirect(
        context: Context,
        macAddress: String,
        bytes: ByteArray
    ): Result<Unit> {
        if (macAddress.isBlank()) {
            return Result.failure(IllegalStateException("لم يتم تحديد طابعة بلوتوث افتراضية."))
        }
        directByteSenderForTest?.let { return it.invoke(macAddress, bytes) }

        if (!hasBluetoothPermissions(context)) {
            return Result.failure(SecurityException("أذونات البلوتوث مطلوبة للطباعة المباشرة. يرجى السماح بها من الإعدادات."))
        }
        val adapter = getBluetoothAdapter(context)
            ?: return Result.failure(IllegalStateException("هذا الجهاز لا يدعم البلوتوث."))
        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("البلوتوث مغلق. يرجى تفعيل البلوتوث وتشغيل الطابعة الحرارية."))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val device = adapter.getRemoteDevice(macAddress)
                if (adapter.isDiscovering) {
                    runCatching { adapter.cancelDiscovery() }
                }
                var socket: BluetoothSocket? = null
                try {
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    try {
                        socket.connect()
                    } catch (_: Exception) {
                        runCatching { socket.close() }
                        val fallbackMethod = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        socket = fallbackMethod.invoke(device, 1) as BluetoothSocket
                        socket.connect()
                    }
                    val out = socket.outputStream
                    val chunkSize = 1024
                    var offset = 0
                    while (offset < bytes.size) {
                        val len = minOf(chunkSize, bytes.size - offset)
                        out.write(bytes, offset, len)
                        offset += len
                        if (offset < bytes.size) {
                            Thread.sleep(15)
                        }
                    }
                    out.flush()
                    delay(180)
                } finally {
                    runCatching { socket?.close() }
                }
                Unit
            }.recoverCatching { err ->
                throw IllegalStateException(
                    "تعذر الاتصال بطابعة البلوتوث ($macAddress). تأكد من تشغيل الطابعة واقترابها من الهاتف. (${err.message ?: ""})"
                )
            }
        }
    }

    /**
     * Converts a Bitmap into standard ESC/POS binary bytes:
     * - ESC @ (Initialize printer)
     * - ESC a 1 (Center alignment)
     * - GS v 0 (Raster bit image bands, max 256px height per band for printer buffer safety)
     * - Feed lines + GS V 66 0 (Partial cut if supported)
     */
    fun encodeBitmapToEscPos(
        bitmap: Bitmap,
        feedLines: Int = 4,
        autoCut: Boolean = true
    ): ByteArray {
        val alignedWidth = ((bitmap.width + 7) / 8) * 8
        val workBitmap = if (alignedWidth == bitmap.width) {
            bitmap
        } else {
            val padded = Bitmap.createBitmap(alignedWidth, bitmap.height, Bitmap.Config.ARGB_8888)
            padded.eraseColor(Color.WHITE)
            Canvas(padded).drawBitmap(bitmap, 0f, 0f, null)
            padded
        }

        val widthBytes = alignedWidth / 8
        val height = workBitmap.height
        val pixels = IntArray(alignedWidth * height)
        workBitmap.getPixels(pixels, 0, alignedWidth, 0, 0, alignedWidth, height)

        val stream = ByteArrayOutputStream()
        // ESC @ : Initialize printer
        stream.write(byteArrayOf(0x1B, 0x40))
        // ESC a 1 : Center alignment
        stream.write(byteArrayOf(0x1B, 0x61, 0x01))

        val maxBandHeight = 256
        var yStart = 0
        while (yStart < height) {
            val bandHeight = minOf(maxBandHeight, height - yStart)
            // GS v 0 m xL xH yL yH
            stream.write(0x1D)
            stream.write(0x76)
            stream.write(0x30)
            stream.write(0x00) // Normal density
            stream.write(widthBytes and 0xFF)
            stream.write((widthBytes shr 8) and 0xFF)
            stream.write(bandHeight and 0xFF)
            stream.write((bandHeight shr 8) and 0xFF)

            for (y in yStart until (yStart + bandHeight)) {
                val rowOffset = y * alignedWidth
                for (xByte in 0 until widthBytes) {
                    var packed = 0
                    val baseX = xByte * 8
                    for (bit in 0 until 8) {
                        val argb = pixels[rowOffset + baseX + bit]
                        val alpha = (argb ushr 24) and 0xFF
                        val isBlack = if (alpha < 64) {
                            false
                        } else {
                            val r = (argb ushr 16) and 0xFF
                            val g = (argb ushr 8) and 0xFF
                            val b = argb and 0xFF
                            val lum = (r * 299 + g * 587 + b * 114) / 1000
                            lum < 175
                        }
                        if (isBlack) {
                            packed = packed or (1 shl (7 - bit))
                        }
                    }
                    stream.write(packed)
                }
            }
            yStart += bandHeight
        }

        repeat(feedLines.coerceAtLeast(1)) {
            stream.write(0x0A)
        }
        if (autoCut) {
            // GS V 66 0 : Feed and partial cut
            stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        }

        if (workBitmap !== bitmap) {
            workBitmap.recycle()
        }
        return stream.toByteArray()
    }

    private fun thermalPixelWidth(paperWidthMm: Int): Int =
        if (paperWidthMm == 58) 384 else 576

    private fun loadTypeface(context: Context): Typeface =
        runCatching { ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT }
            .getOrDefault(Typeface.DEFAULT)

    private fun renderThermalCardBitmap(
        context: Context,
        paperWidthMm: Int,
        stationTitle: String,
        docTitle: String,
        docSubtitle: String,
        rows: List<Pair<String, String>>,
        footerLines: List<String>
    ): Bitmap {
        val width = thermalPixelWidth(paperWidthMm)
        val is58 = paperWidthMm == 58
        val margin = if (is58) 12f else 18f
        val contentWidth = (width - margin * 2).toInt()
        val typeface = loadTypeface(context)

        val titleSize = if (is58) 26f else 34f
        val subSize = if (is58) 18f else 23f
        val bodySize = if (is58) 17f else 22f
        val footerSize = if (is58) 15f else 19f
        val logoSize = if (is58) 72f else 96f

        fun makeLayout(text: String, size: Float, bold: Boolean, align: Layout.Alignment): StaticLayout {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = size
                this.typeface = Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, tp, contentWidth)
                .setAlignment(align)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .build()
        }

        val titleLayout = makeLayout(stationTitle, titleSize, true, Layout.Alignment.ALIGN_CENTER)
        val docTitleLayout = makeLayout(docTitle, subSize, true, Layout.Alignment.ALIGN_CENTER)
        val docSubLayout = if (docSubtitle.isNotBlank()) makeLayout(docSubtitle, bodySize, false, Layout.Alignment.ALIGN_CENTER) else null
        val rowLayouts = rows.map { (k, v) ->
            makeLayout("$k: $v", bodySize, k.contains("صافي") || k.contains("الإجمالي") || k.contains("المتبقي") || k.contains("المحصل"), Layout.Alignment.ALIGN_OPPOSITE)
        }
        val footerLayouts = footerLines.filter { it.isNotBlank() }.map {
            makeLayout(it, footerSize, false, Layout.Alignment.ALIGN_CENTER)
        }

        val estimatedHeight = (
            20f + logoSize + 12f +
                titleLayout.height + 6f +
                docTitleLayout.height + 6f +
                (docSubLayout?.height ?: 0) + 18f +
                rowLayouts.sumOf { it.height + 10 } + 22f +
                footerLayouts.sumOf { it.height + 6 } + 36f
            ).toInt().coerceAtLeast(320)

        val bitmap = Bitmap.createBitmap(width, estimatedHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        var y = 16f
        runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.img_alasal_logo_transparent) }
            .getOrNull()?.let { logo ->
                val left = (width - logoSize) / 2f
                canvas.drawBitmap(logo, null, RectF(left, y, left + logoSize, y + logoSize), Paint(Paint.ANTI_ALIAS_FLAG))
                logo.recycle()
                y += logoSize + 10f
            }

        fun drawLayout(layout: StaticLayout) {
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
        }

        drawLayout(titleLayout)
        y += 4f
        drawLayout(docTitleLayout)
        if (docSubLayout != null) {
            y += 4f
            drawLayout(docSubLayout)
        }
        y += 10f
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 12f

        rowLayouts.forEach { layout ->
            drawLayout(layout)
            y += 8f
        }

        y += 6f
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 10f

        footerLayouts.forEach { layout ->
            drawLayout(layout)
            y += 4f
        }

        return bitmap
    }

    fun renderTestPageBitmap(
        context: Context,
        prefs: NotificationAndPrintPrefs
    ): Bitmap {
        val dateFormat = SimpleDateFormat("d/M/yyyy HH:mm", Locale.US)
        val effectiveWidth = if (prefs.thermalPaperWidthMm == 58) 58 else 80
        return renderThermalCardBitmap(
            context = context,
            paperWidthMm = effectiveWidth,
            stationTitle = "محطة العسل",
            docTitle = "اختبار الطابعة الحرارية (Bluetooth)",
            docSubtitle = "طباعة تجريبية فورية بنظام ESC/POS",
            rows = listOf(
                "اسم الطابعة" to prefs.printerDeviceName.ifBlank { "طابعة بلوتوث" },
                "عنوان MAC" to prefs.printerDeviceAddress.ifBlank { "-" },
                "مقاس الورق" to "${effectiveWidth}mm",
                "حالة الاتصال" to "متصل وجاهز للطباعة",
                "التاريخ والوقت" to dateFormat.format(Date())
            ),
            footerLines = listOf(
                prefs.stationAddress,
                "هاتف: ${prefs.stationPhone}",
                "تم نجاح اختبار الطباعة الحرارية"
            )
        )
    }

    fun renderInvoiceThermalBitmap(
        context: Context,
        invoice: InvoiceEntity,
        subscriber: SubscriberEntity?,
        periodLabel: String,
        prefs: NotificationAndPrintPrefs
    ): Bitmap {
        val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.US)
        val effectiveWidth = if (prefs.thermalPaperWidthMm == 58) 58 else 80
        return renderThermalCardBitmap(
            context = context,
            paperWidthMm = effectiveWidth,
            stationTitle = "محطة العسل",
            docTitle = "فاتورة استهلاك كهرباء #${invoice.invoiceNumber}",
            docSubtitle = periodLabel,
            rows = listOf(
                "المشترك" to (subscriber?.fullName ?: "مشترك #${invoice.subscriberId}"),
                "رقم المشترك" to (subscriber?.subscriberCode ?: "-"),
                "رقم العداد" to (subscriber?.meterNumber ?: "-"),
                "القراءة السابقة" to "${invoice.previousReading} ك.و.س",
                "القراءة الحالية" to "${invoice.currentReading} ك.و.س",
                "الاستهلاك" to "${invoice.consumedKwh} ك.و.س",
                "سعر الكيلوواط" to "${invoice.unitPriceApplied}",
                "قيمة الاستهلاك" to "${invoice.energyAmount}",
                "المتأخرات المرحلة" to "${invoice.previousArrearsApplied}",
                "الرصيد الدائن المخصوم" to "${invoice.creditDeducted}",
                "صافي الفاتورة المطلوب" to "${invoice.netPayableAmount}",
                "المدفوع" to "${invoice.paidAmount}",
                "المتبقي" to "${invoice.remainingAmount}",
                "تاريخ الإصدار" to dateFormat.format(Date(invoice.issuedAt)),
                "الحالة" to InvoiceShareAndPrintService.translateStatus(invoice.status)
            ),
            footerLines = listOf(
                prefs.stationAddress,
                "هاتف: ${prefs.stationPhone}",
                "شكراً لثقتكم بمحطة العسل"
            )
        )
    }

    fun renderReceiptThermalBitmap(
        context: Context,
        receipt: ReceiptEntity,
        prefs: NotificationAndPrintPrefs,
        paperWidthOverrideMm: Int? = null
    ): Bitmap {
        val dateFormat = SimpleDateFormat("d/M/yyyy HH:mm", Locale.US)
        val effectiveWidth = when (paperWidthOverrideMm ?: prefs.thermalPaperWidthMm) {
            58 -> 58
            else -> 80
        }
        return renderThermalCardBitmap(
            context = context,
            paperWidthMm = effectiveWidth,
            stationTitle = "محطة العسل",
            docTitle = "إيصال تحصيل رسمي #${receipt.receiptNumber}",
            docSubtitle = "رقم الفاتورة: ${receipt.invoiceNumberSnapshot.ifBlank { "-" }}",
            rows = listOf(
                "المشترك" to receipt.subscriberNameSnapshot.ifBlank { "مشترك #${receipt.subscriberId}" },
                "كود المشترك" to receipt.subscriberCodeSnapshot.ifBlank { "-" },
                "المبلغ السابق" to "${receipt.previousBalance}",
                "المبلغ المحصل" to "${receipt.amount}",
                "المتبقي بعد السداد" to "${receipt.balanceAfterReceipt}",
                "الرصيد الدائن" to "${receipt.creditAfterReceipt}",
                "طريقة التحصيل" to StationReportGenerator.translatePaymentMethod(receipt.paymentMethod),
                "المحصل" to receipt.collectorNameSnapshot,
                "التاريخ والوقت" to dateFormat.format(Date(receipt.issuedAt))
            ),
            footerLines = listOf(
                prefs.stationAddress,
                "هاتف: ${receipt.stationPhoneSnapshot.ifBlank { prefs.stationPhone }}",
                "شكراً لسدادكم المنتظم"
            )
        )
    }

    fun renderStatementThermalBitmap(
        context: Context,
        statement: SubscriberAccountStatement,
        prefs: NotificationAndPrintPrefs
    ): Bitmap {
        val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.US)
        val effectiveWidth = if (prefs.thermalPaperWidthMm == 58) 58 else 80
        val sub = statement.subscriber
        return renderThermalCardBitmap(
            context = context,
            paperWidthMm = effectiveWidth,
            stationTitle = "محطة العسل",
            docTitle = "كشف حساب مشترك",
            docSubtitle = "${sub.fullName} (${sub.subscriberCode})",
            rows = listOf(
                "رقم العداد" to sub.meterNumber,
                "سعر الكيلوواط" to "${sub.customKwhPrice}",
                "إجمالي الاستهلاك" to "${statement.totalConsumedKwh} ك.و.س",
                "إجمالي الفواتير" to "${statement.totalBilledEnergy}",
                "إجمالي التحصيلات" to "${statement.totalCollected}",
                "المتأخرات النشطة" to "${statement.activeArrears}",
                "الرصيد الدائن" to "${statement.creditBalance}",
                "الرصيد النهائي" to "${statement.netFinalBalance}",
                "تاريخ الكشف" to dateFormat.format(Date())
            ),
            footerLines = listOf(
                prefs.stationAddress,
                "هاتف: ${prefs.stationPhone}"
            )
        )
    }

    fun renderReportThermalBitmap(
        context: Context,
        report: GeneratedReport,
        prefs: NotificationAndPrintPrefs
    ): Bitmap {
        val dateFormat = SimpleDateFormat("d/M/yyyy HH:mm", Locale.US)
        val effectiveWidth = if (prefs.thermalPaperWidthMm == 58) 58 else 80
        val rows = buildList {
            report.summaryTotals.forEach { (k, v) -> add(k to v) }
            add("عدد السجلات" to "${report.uiRows.size}")
            report.uiRows.take(12).forEach { row ->
                add(row.primaryTitle to "${row.amountLabel} (${row.statusLabel})")
            }
            if (report.uiRows.size > 12) {
                add("ملاحظة" to "يوجد ${report.uiRows.size - 12} سجل إضافي في تقرير PDF")
            }
            add("تاريخ الطباعة" to dateFormat.format(Date()))
        }
        return renderThermalCardBitmap(
            context = context,
            paperWidthMm = effectiveWidth,
            stationTitle = "محطة العسل",
            docTitle = report.title,
            docSubtitle = report.filterSummary,
            rows = rows,
            footerLines = listOf(
                prefs.stationAddress,
                "هاتف: ${prefs.stationPhone}"
            )
        )
    }

    suspend fun printTestPage(
        context: Context,
        prefs: NotificationAndPrintPrefs
    ): Result<String> {
        val bmp = renderTestPageBitmap(context, prefs)
        val bytes = try {
            encodeBitmapToEscPos(bmp)
        } finally {
            bmp.recycle()
        }
        return sendEscPosBytesDirect(context, prefs.printerDeviceAddress, bytes).map {
            "تم إرسال الطباعة التجريبية بنجاح إلى ${prefs.printerDeviceName.ifBlank { prefs.printerDeviceAddress }}"
        }
    }

    suspend fun printInvoiceDirect(
        context: Context,
        invoice: InvoiceEntity,
        subscriber: SubscriberEntity?,
        periodLabel: String,
        prefs: NotificationAndPrintPrefs
    ): Result<String> {
        val bmp = renderInvoiceThermalBitmap(context, invoice, subscriber, periodLabel, prefs)
        val bytes = try {
            encodeBitmapToEscPos(bmp)
        } finally {
            bmp.recycle()
        }
        return sendEscPosBytesDirect(context, prefs.printerDeviceAddress, bytes).map {
            "تمت طباعة الفاتورة ${invoice.invoiceNumber} مباشرة على ${prefs.printerDeviceName.ifBlank { prefs.printerDeviceAddress }}"
        }
    }

    suspend fun printReceiptDirect(
        context: Context,
        receipt: ReceiptEntity,
        prefs: NotificationAndPrintPrefs,
        paperWidthOverrideMm: Int? = null
    ): Result<String> {
        val bmp = renderReceiptThermalBitmap(context, receipt, prefs, paperWidthOverrideMm)
        val bytes = try {
            encodeBitmapToEscPos(bmp)
        } finally {
            bmp.recycle()
        }
        return sendEscPosBytesDirect(context, prefs.printerDeviceAddress, bytes).map {
            "تمت طباعة الإيصال ${receipt.receiptNumber} مباشرة على ${prefs.printerDeviceName.ifBlank { prefs.printerDeviceAddress }}"
        }
    }

    suspend fun printStatementDirect(
        context: Context,
        statement: SubscriberAccountStatement,
        prefs: NotificationAndPrintPrefs
    ): Result<String> {
        val bmp = renderStatementThermalBitmap(context, statement, prefs)
        val bytes = try {
            encodeBitmapToEscPos(bmp)
        } finally {
            bmp.recycle()
        }
        return sendEscPosBytesDirect(context, prefs.printerDeviceAddress, bytes).map {
            "تمت طباعة كشف الحساب مباشرة على ${prefs.printerDeviceName.ifBlank { prefs.printerDeviceAddress }}"
        }
    }

    suspend fun printReportDirect(
        context: Context,
        report: GeneratedReport,
        prefs: NotificationAndPrintPrefs
    ): Result<String> {
        val bmp = renderReportThermalBitmap(context, report, prefs)
        val bytes = try {
            encodeBitmapToEscPos(bmp)
        } finally {
            bmp.recycle()
        }
        return sendEscPosBytesDirect(context, prefs.printerDeviceAddress, bytes).map {
            "تمت طباعة التقرير مباشرة على ${prefs.printerDeviceName.ifBlank { prefs.printerDeviceAddress }}"
        }
    }
}
