package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.local.entity.ReceiptEntity
import com.example.domain.finance.FieldCollectionSheetItem
import com.example.domain.finance.GeneratedReport
import com.example.domain.finance.StationReportGenerator
import com.example.domain.finance.SubscriberAccountStatement
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline native PDF (A4), Thermal Print (58mm / 80mm), Receipt PNG Image,
 * and zero-permission FileProvider sharing / WhatsApp dispatch service.
 */
object DocumentPdfAndThermalService {

    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842
    private const val COLOR_NAVY = 0xFF0D263C.toInt()
    private const val COLOR_GOLD = 0xFFD4AF37.toInt()
    private const val COLOR_GREEN = 0xFF1B7F3A.toInt()
    private const val COLOR_RED = 0xFFC62828.toInt()
    private const val COLOR_LIGHT_BG = 0xFFF4F6F9.toInt()
    private const val COLOR_HIGHLIGHT_BG = 0xFFFAF1D1.toInt()
    private const val COLOR_BORDER = 0xFFCBD5E1.toInt()

    private fun loadArabicTypeface(context: Context): Typeface {
        return runCatching {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT
        }.getOrDefault(Typeface.DEFAULT)
    }

    private fun loadBitmapSafe(context: Context, resId: Int): Bitmap? =
        runCatching { BitmapFactory.decodeResource(context.resources, resId) }.getOrNull()

    private fun drawA4PageDecor(
        context: Context,
        canvas: Canvas,
        typeface: Typeface,
        title: String,
        subtitle: String,
        stationName: String,
        stationPhone: String,
        stationAddress: String,
        pageNumber: Int
    ): Float {
        // Pure crisp white background for A4 printing
        canvas.drawColor(Color.WHITE)

        // Centered transparent watermark behind content (no rectangular background)
        loadBitmapSafe(context, R.drawable.img_alasal_watermark)?.let { wm ->
            val targetSize = 270f
            val left = (A4_WIDTH_PT - targetSize) / 2f
            val top = (A4_HEIGHT_PT - targetSize) / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = 90
            }
            canvas.drawBitmap(wm, null, RectF(left, top, left + targetSize, top + targetSize), paint)
        }

        // Top RTL header: Logo on right, institution & document titles on left/right
        loadBitmapSafe(context, R.drawable.img_alasal_logo_transparent)?.let { logo ->
            val logoSize = 58f
            val rightEdge = A4_WIDTH_PT - 32f
            canvas.drawBitmap(
                logo,
                null,
                RectF(rightEdge - logoSize, 26f, rightEdge, 26f + logoSize),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_NAVY
            textSize = 17f
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt()
            textSize = 10.5f
            this.typeface = typeface
            textAlign = Paint.Align.RIGHT
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF334155.toInt()
            textSize = 9.5f
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText(stationName, A4_WIDTH_PT - 102f, 44f, titlePaint)
        canvas.drawText(title, A4_WIDTH_PT - 102f, 62f, subPaint.apply { textSize = 12f; color = COLOR_NAVY })
        canvas.drawText(subtitle, A4_WIDTH_PT - 102f, 78f, subPaint.apply { textSize = 9.5f; color = 0xFF475569.toInt() })

        canvas.drawText("هاتف: $stationPhone", 32f, 44f, metaPaint)
        canvas.drawText(stationAddress, 32f, 60f, metaPaint)
        canvas.drawText("صفحة $pageNumber", 32f, 76f, metaPaint)

        // Gold divider line
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GOLD
            strokeWidth = 2.2f
        }
        canvas.drawLine(32f, 94f, A4_WIDTH_PT - 32f, 94f, goldPaint)

        // Footer bar
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF64748B.toInt()
            textSize = 8.5f
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
        canvas.drawLine(32f, A4_HEIGHT_PT - 36f, A4_WIDTH_PT - 32f, A4_HEIGHT_PT - 36f, Paint().apply {
            color = COLOR_BORDER
            strokeWidth = 1f
        })
        canvas.drawText(
            "$stationName — $stationAddress | هاتف: $stationPhone",
            A4_WIDTH_PT / 2f,
            A4_HEIGHT_PT - 20f,
            footerPaint
        )

        return 112f
    }

    private fun drawRtlRow(
        canvas: Canvas,
        y: Float,
        rowHeight: Float,
        columns: List<String>,
        widths: List<Float>,
        bgColor: Int?,
        textColor: Int,
        typeface: Typeface,
        isBold: Boolean = false,
        fontSize: Float = 9.5f
    ) {
        val rightStart = A4_WIDTH_PT - 32f
        val leftEnd = 32f
        if (bgColor != null) {
            canvas.drawRect(leftEnd, y, rightStart, y + rowHeight, Paint().apply { color = bgColor })
        }
        val borderPaint = Paint().apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        canvas.drawRect(leftEnd, y, rightStart, y + rowHeight, borderPaint)

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = fontSize
            this.typeface = if (isBold) Typeface.create(typeface, Typeface.BOLD) else typeface
        }

        var cursorRight = rightStart
        columns.forEachIndexed { idx, cellText ->
            val colW = widths.getOrElse(idx) { 80f }
            val cellLeft = cursorRight - colW
            if (idx > 0) {
                canvas.drawLine(cursorRight, y, cursorRight, y + rowHeight, borderPaint)
            }
            val layout = StaticLayout.Builder
                .obtain(cellText, 0, cellText.length, tp, (colW - 8f).toInt().coerceAtLeast(24))
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setMaxLines(2)
                .build()
            canvas.save()
            canvas.translate(cellLeft + 4f, y + 6f)
            layout.draw(canvas)
            canvas.restore()
            cursorRight = cellLeft
        }
    }

    private data class PdfPageSpec(
        val widthPt: Int,
        val heightPt: Int,
        val draw: (Canvas) -> Unit
    )

    /**
     * Uses Android native PdfDocument on device; if native PdfDocument is uninitialized (e.g. JVM/Robolectric),
     * renders each page via Android Canvas onto a high-res Bitmap and writes a valid multi-page PDF 1.4 file.
     */
    private fun writePagesPdf(
        file: File,
        pages: List<PdfPageSpec>,
        bitmapScale: Float = 2f
    ) {
        val nativeSucceeded = runCatching {
            val pdf = PdfDocument()
            try {
                pages.forEachIndexed { idx, spec ->
                    val pageInfo = PdfDocument.PageInfo.Builder(spec.widthPt, spec.heightPt, idx + 1).create()
                    val page = pdf.startPage(pageInfo)
                    spec.draw(page.canvas)
                    pdf.finishPage(page)
                }
                FileOutputStream(file).use { pdf.writeTo(it) }
            } finally {
                pdf.close()
            }
            file.exists() && file.length() > 100L
        }.getOrDefault(false)

        if (!nativeSucceeded) {
            writeFallbackBitmapPdf(file, pages, bitmapScale)
        }
    }

    private fun writeFallbackBitmapPdf(
        file: File,
        pages: List<PdfPageSpec>,
        bitmapScale: Float
    ) {
        val jpegPages = pages.map { spec ->
            val bmpW = (spec.widthPt * bitmapScale).toInt().coerceAtLeast(100)
            val bmpH = (spec.heightPt * bitmapScale).toInt().coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val canvas = Canvas(bitmap)
            canvas.save()
            canvas.scale(bitmapScale, bitmapScale)
            spec.draw(canvas)
            canvas.restore()
            val jpegBytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.toByteArray()
            }
            bitmap.recycle()
            Triple(spec.widthPt, spec.heightPt, jpegBytes to (bmpW to bmpH))
        }

        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        fun writeAscii(s: String) {
            out.write(s.toByteArray(Charsets.US_ASCII))
        }

        fun beginObj(id: Int) {
            offsets.add(out.size())
            writeAscii("$id 0 obj\n")
        }

        writeAscii("%PDF-1.4\n")
        // 1: Catalog, 2: Pages
        beginObj(1)
        writeAscii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        val pageCount = jpegPages.size
        val pageObjIds = (0 until pageCount).map { 3 + it * 3 }
        beginObj(2)
        writeAscii("<< /Type /Pages /Kids [${pageObjIds.joinToString(" ") { "$it 0 R" }}] /Count $pageCount >>\nendobj\n")

        jpegPages.forEachIndexed { idx, (wPt, hPt, data) ->
            val (jpegBytes, dims) = data
            val (imgW, imgH) = dims
            val pageId = 3 + idx * 3
            val contentId = pageId + 1
            val imageId = pageId + 2

            beginObj(pageId)
            writeAscii(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $wPt $hPt] " +
                    "/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n"
            )

            val contentStream = "q\n$wPt 0 0 $hPt 0 0 cm\n/Im0 Do\nQ\n".toByteArray(Charsets.US_ASCII)
            beginObj(contentId)
            writeAscii("<< /Length ${contentStream.size} >>\nstream\n")
            out.write(contentStream)
            writeAscii("\nendstream\nendobj\n")

            beginObj(imageId)
            writeAscii(
                "<< /Type /XObject /Subtype /Image /Width $imgW /Height $imgH " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n"
            )
            out.write(jpegBytes)
            writeAscii("\nendstream\nendobj\n")
        }

        val xrefOffset = out.size()
        val totalObjs = offsets.size + 1
        writeAscii("xref\n0 $totalObjs\n")
        writeAscii("0000000000 65535 f \n")
        offsets.forEach { off ->
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        writeAscii("trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")

        FileOutputStream(file).use { it.write(out.toByteArray()) }
    }

    fun generateReceiptA4Pdf(
        context: Context,
        receipt: ReceiptEntity,
        stationAddress: String = "محطة العسل لتوليد وتوزيع الكهرباء",
        outputFile: File? = null
    ): File {
        val dir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
        val file = outputFile ?: File(dir, "Receipt_${receipt.receiptNumber}.pdf")
        val typeface = loadArabicTypeface(context)
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar"))

        val pageSpec = PdfPageSpec(A4_WIDTH_PT, A4_HEIGHT_PT) { canvas ->
            var y = drawA4PageDecor(
                context = context,
                canvas = canvas,
                typeface = typeface,
                title = "إيصال تحصيل نقدي / سداد فاتورة",
                subtitle = "رقم الإيصال: ${receipt.receiptNumber} | التاريخ: ${dateFormat.format(Date(receipt.issuedAt))}",
                stationName = receipt.stationNameSnapshot.ifEmpty { "محطة العسل 2027" },
                stationPhone = receipt.stationPhoneSnapshot.ifEmpty { "770002027" },
                stationAddress = stationAddress,
                pageNumber = 1
            )

            val widths = listOf(180f, 351f)
            drawRtlRow(canvas, y, 28f, listOf("البيان", "القيمة / التفاصيل"), widths, COLOR_NAVY, Color.WHITE, typeface, isBold = true)
            y += 28f

            val rows = listOf(
                "رقم الإيصال المتسلسل" to receipt.receiptNumber,
                "اسم المشترك" to receipt.subscriberNameSnapshot.ifEmpty { "مشترك #${receipt.subscriberId}" },
                "رقم المشترك" to receipt.subscriberCodeSnapshot.ifEmpty { "-" },
                "رقم الفاتورة" to receipt.invoiceNumberSnapshot.ifEmpty { "-" },
                "طريقة التحصيل" to StationReportGenerator.translatePaymentMethod(receipt.paymentMethod),
                "اسم المحصّل" to receipt.collectorNameSnapshot,
                "تاريخ ووقت التحصيل" to dateFormat.format(Date(receipt.issuedAt)),
                "المبلغ السابق على الفاتورة" to "${receipt.previousBalance}",
                "المبلغ المحصل" to "${receipt.amount}",
                "المتبقي بعد التحصيل" to "${receipt.balanceAfterReceipt}",
                "الرصيد الدائن بعد الإيصال" to "${receipt.creditAfterReceipt}"
            )

            rows.forEachIndexed { idx, (label, value) ->
                val highlight = label.contains("المحصل") || label.contains("المتبقي")
                val bg = when {
                    highlight -> COLOR_HIGHLIGHT_BG
                    idx % 2 == 1 -> COLOR_LIGHT_BG
                    else -> null
                }
                val color = when {
                    label.contains("الدائن") && receipt.creditAfterReceipt > 0 -> COLOR_GREEN
                    label.contains("المتبقي") && receipt.balanceAfterReceipt > 0 -> COLOR_RED
                    else -> COLOR_NAVY
                }
                drawRtlRow(canvas, y, 28f, listOf(label, value), widths, bg, color, typeface, isBold = highlight, fontSize = 10.5f)
                y += 28f
            }
        }

        writePagesPdf(file, listOf(pageSpec))
        return file
    }

    fun generateStatementA4Pdf(
        context: Context,
        statement: SubscriberAccountStatement,
        stationAddress: String = "محطة العسل لتوليد وتوزيع الكهرباء",
        stationPhone: String = "770002027",
        outputFile: File? = null
    ): File {
        val report = StationReportGenerator.generateReport(
            filter = com.example.domain.finance.ReportFilter(
                category = com.example.domain.finance.ReportCategory.SUBSCRIBER_STATEMENT,
                subscriberId = statement.subscriber.id
            ),
            subscribers = listOf(statement.subscriber),
            invoices = statement.invoices,
            collectionsWithReceipts = statement.collectionsWithReceipts,
            readings = statement.readings,
            arrears = emptyList(),
            creditTransactions = statement.creditTransactions,
            users = emptyList()
        )
        val dir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
        val file = outputFile ?: File(dir, "Statement_${statement.subscriber.subscriberCode}.pdf")
        return generateReportA4Pdf(
            context = context,
            report = report,
            stationAddress = stationAddress,
            stationPhone = stationPhone,
            outputFile = file
        )
    }

    fun generateReportA4Pdf(
        context: Context,
        report: GeneratedReport,
        stationAddress: String = "محطة العسل لتوليد وتوزيع الكهرباء",
        stationPhone: String = "770002027",
        outputFile: File? = null
    ): File {
        val dir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
        val file = outputFile ?: File(dir, "Alasal_Report_${System.currentTimeMillis()}.pdf")
        val typeface = loadArabicTypeface(context)
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar"))

        // Chunk table rows into pages cleanly so both native PdfDocument and fallback draw identically
        val tableWidth = 531f
        val colCount = report.columns.size.coerceAtLeast(1)
        val colWidth = tableWidth / colCount
        val colWidths = List(colCount) { colWidth }

        val pages = mutableListOf<PdfPageSpec>()
        val firstPageSummaryHeight = if (report.summaryTotals.isNotEmpty()) (report.summaryTotals.size + 1) * 24f + 12f else 0f
        val firstPageStartY = 112f + firstPageSummaryHeight + 26f
        val firstPageCapacity = ((A4_HEIGHT_PT - 48f - firstPageStartY) / 30f).toInt().coerceAtLeast(4)
        val nextPageCapacity = ((A4_HEIGHT_PT - 48f - (112f + 26f)) / 30f).toInt().coerceAtLeast(6)

        val chunks = mutableListOf<List<List<String>>>()
        if (report.tableRows.isEmpty()) {
            chunks.add(emptyList())
        } else {
            chunks.add(report.tableRows.take(firstPageCapacity))
            var offset = firstPageCapacity
            while (offset < report.tableRows.size) {
                chunks.add(report.tableRows.subList(offset, (offset + nextPageCapacity).coerceAtMost(report.tableRows.size)))
                offset += nextPageCapacity
            }
        }

        chunks.forEachIndexed { pageIndex, rowSlice ->
            val pageNum = pageIndex + 1
            pages.add(
                PdfPageSpec(A4_WIDTH_PT, A4_HEIGHT_PT) { canvas ->
                    var y = drawA4PageDecor(
                        context = context,
                        canvas = canvas,
                        typeface = typeface,
                        title = if (pageNum == 1) report.title else "${report.title} (تابع)",
                        subtitle = if (pageNum == 1) "${report.filterSummary} | ${dateFormat.format(Date())}" else report.filterSummary,
                        stationName = "محطة العسل 2027 (Alasal2027)",
                        stationPhone = stationPhone,
                        stationAddress = stationAddress,
                        pageNumber = pageNum
                    )

                    if (pageNum == 1 && report.summaryTotals.isNotEmpty()) {
                        val sumWidths = listOf(220f, 311f)
                        drawRtlRow(canvas, y, 24f, listOf("ملخص التقرير المالي", "القيمة الإجمالية"), sumWidths, COLOR_NAVY, Color.WHITE, typeface, isBold = true)
                        y += 24f
                        report.summaryTotals.forEach { (k, v) ->
                            val color = when {
                                k.contains("دائن") -> COLOR_GREEN
                                k.contains("متأخر") || k.contains("المتبقي") -> COLOR_RED
                                else -> COLOR_NAVY
                            }
                            drawRtlRow(canvas, y, 24f, listOf(k, v), sumWidths, COLOR_HIGHLIGHT_BG, color, typeface, isBold = true, fontSize = 10f)
                            y += 24f
                        }
                        y += 12f
                    }

                    drawRtlRow(canvas, y, 26f, report.columns, colWidths, COLOR_NAVY, Color.WHITE, typeface, isBold = true, fontSize = 9f)
                    y += 26f

                    rowSlice.forEachIndexed { idx, rowCells ->
                        val bg = if (idx % 2 == 1) COLOR_LIGHT_BG else null
                        drawRtlRow(canvas, y, 30f, rowCells, colWidths, bg, COLOR_NAVY, typeface, fontSize = 8.5f)
                        y += 30f
                    }
                }
            )
        }

        writePagesPdf(file, pages)
        return file
    }

    /**
     * Generates a crisp white PNG image of the collection receipt with logo, watermark,
     * and full RTL details ready for direct image sharing or WhatsApp.
     */
    fun generateReceiptPngImage(
        context: Context,
        receipt: ReceiptEntity,
        stationAddress: String = "محطة العسل لتوليد وتوزيع الكهرباء",
        outputFile: File? = null
    ): File {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = outputFile ?: File(dir, "Receipt_${receipt.receiptNumber}.png")
        val width = 820
        val height = 1040
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val typeface = loadArabicTypeface(context)
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar"))

        // Centered transparent watermark
        loadBitmapSafe(context, R.drawable.img_alasal_watermark)?.let { wm ->
            val wmSize = 360f
            val left = (width - wmSize) / 2f
            val top = (height - wmSize) / 2f
            canvas.drawBitmap(
                wm,
                null,
                RectF(left, top, left + wmSize, top + wmSize),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 85 }
            )
        }

        // Top Navy banner & Logo
        canvas.drawRect(0f, 0f, width.toFloat(), 155f, Paint().apply { color = COLOR_NAVY })
        canvas.drawRect(0f, 155f, width.toFloat(), 163f, Paint().apply { color = COLOR_GOLD })

        loadBitmapSafe(context, R.drawable.img_alasal_logo_transparent)?.let { logo ->
            canvas.drawBitmap(
                logo,
                null,
                RectF(width - 145f, 22f, width - 25f, 142f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GOLD
            textSize = 22f
            this.typeface = typeface
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(receipt.stationNameSnapshot.ifEmpty { "محطة العسل 2027" }, width - 165f, 70f, headerTitlePaint)
        canvas.drawText("إيصال تحصيل رسمي: ${receipt.receiptNumber}", width - 165f, 114f, headerSubPaint)

        val lines = listOf(
            "اسم المشترك" to receipt.subscriberNameSnapshot.ifEmpty { "مشترك #${receipt.subscriberId}" },
            "رقم المشترك" to receipt.subscriberCodeSnapshot.ifEmpty { "-" },
            "رقم الفاتورة" to receipt.invoiceNumberSnapshot.ifEmpty { "-" },
            "المبلغ السابق" to "${receipt.previousBalance}",
            "المبلغ المحصل" to "${receipt.amount}",
            "المتبقي بعد التحصيل" to "${receipt.balanceAfterReceipt}",
            "الرصيد الدائن بعد الإيصال" to "${receipt.creditAfterReceipt}",
            "طريقة التحصيل" to StationReportGenerator.translatePaymentMethod(receipt.paymentMethod),
            "اسم المحصل" to receipt.collectorNameSnapshot,
            "التاريخ والوقت" to dateFormat.format(Date(receipt.issuedAt))
        )

        var y = 200f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt()
            textSize = 23f
            this.typeface = typeface
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_NAVY
            textSize = 24f
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val divider = Paint().apply { color = COLOR_BORDER; strokeWidth = 1.5f }

        lines.forEachIndexed { idx, (label, value) ->
            if (idx % 2 == 0) {
                canvas.drawRect(36f, y - 34f, width - 36f, y + 26f, Paint().apply { color = 0xFFF8FAFC.toInt() })
            }
            if (label.contains("المحصل")) {
                canvas.drawRect(36f, y - 34f, width - 36f, y + 26f, Paint().apply { color = COLOR_HIGHLIGHT_BG })
            }
            canvas.drawText(label, width - 54f, y, labelPaint)
            valuePaint.color = when {
                label.contains("الدائن") && receipt.creditAfterReceipt > 0 -> COLOR_GREEN
                label.contains("المتبقي") && receipt.balanceAfterReceipt > 0 -> COLOR_RED
                else -> COLOR_NAVY
            }
            canvas.drawText(value, 54f, y, valuePaint)
            canvas.drawLine(36f, y + 26f, width - 36f, y + 26f, divider)
            y += 70f
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt()
            textSize = 19f
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "$stationAddress | هاتف: ${receipt.stationPhoneSnapshot.ifEmpty { "770002027" }}",
            width / 2f,
            height - 46f,
            footerPaint
        )

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return file
    }

    /**
     * Generates a high-contrast monochrome thermal receipt Bitmap calibrated for 58mm (384px)
     * or 80mm (576px) roll paper with zero horizontal clipping and bold ink density.
     */
    fun generateThermalReceiptBitmap(
        context: Context,
        receipt: ReceiptEntity,
        paperWidthMm: Int = 80,
        stationAddress: String = "محطة العسل لتوليد وتوزيع الكهرباء"
    ): Bitmap {
        val is58 = paperWidthMm == 58
        val pxWidth = if (is58) 384 else 576
        val margin = if (is58) 12f else 18f
        val contentWidth = (pxWidth - margin * 2).toInt()
        val fontSizeTitle = if (is58) 22f else 28f
        val fontSizeBody = if (is58) 17f else 21f
        val typeface = loadArabicTypeface(context)
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar"))

        val bodyText = buildString {
            appendLine("رقم الإيصال: ${receipt.receiptNumber}")
            appendLine("المشترك: ${receipt.subscriberNameSnapshot.ifEmpty { "مشترك #${receipt.subscriberId}" }}")
            appendLine("رقم المشترك: ${receipt.subscriberCodeSnapshot.ifEmpty { "-" }}")
            appendLine("رقم الفاتورة: ${receipt.invoiceNumberSnapshot.ifEmpty { "-" }}")
            appendLine("--------------------------------")
            appendLine("المبلغ السابق: ${receipt.previousBalance}")
            appendLine("المبلغ المحصل: ${receipt.amount}")
            appendLine("المتبقي بعد التحصيل: ${receipt.balanceAfterReceipt}")
            if (receipt.creditAfterReceipt > 0.0) {
                appendLine("الرصيد الدائن: ${receipt.creditAfterReceipt}")
            }
            appendLine("--------------------------------")
            appendLine("طريقة السداد: ${StationReportGenerator.translatePaymentMethod(receipt.paymentMethod)}")
            appendLine("المحصل: ${receipt.collectorNameSnapshot}")
            appendLine("التاريخ: ${dateFormat.format(Date(receipt.issuedAt))}")
            appendLine("--------------------------------")
            appendLine(stationAddress)
            append("هاتف: ${receipt.stationPhoneSnapshot.ifEmpty { "770002027" }}")
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = fontSizeTitle
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            isFakeBoldText = true // Boost thermal ink density
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = fontSizeBody
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            isFakeBoldText = true
        }

        val titleText = receipt.stationNameSnapshot.ifEmpty { "محطة العسل 2027" }
        val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        val bodyLayout = StaticLayout.Builder.obtain(bodyText, 0, bodyText.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .setLineSpacing(6f, 1.0f)
            .build()

        val logoSize = if (is58) 76f else 104f
        val totalHeight = (24 + logoSize + 14 + titleLayout.height + 16 + bodyLayout.height + 32).toInt()
        val bitmap = Bitmap.createBitmap(pxWidth, totalHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = 16f
        loadBitmapSafe(context, R.drawable.img_alasal_logo_transparent)?.let { logo ->
            // High contrast grayscale / dark ink boost for thermal paper
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val left = (pxWidth - logoSize) / 2f
            canvas.drawBitmap(logo, null, RectF(left, y, left + logoSize, y + logoSize), logoPaint)
        }
        y += logoSize + 12f

        canvas.save()
        canvas.translate(margin, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 10f

        canvas.drawLine(margin, y, pxWidth - margin, y, Paint().apply {
            color = Color.BLACK
            strokeWidth = 3f
        })
        y += 10f

        canvas.save()
        canvas.translate(margin, y)
        bodyLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    /**
     * Generates a custom-width PDF (164pt for 58mm, 226pt for 80mm) wrapping the crisp thermal bitmap
     * for seamless printing through Android PrintManager to Bluetooth/USB thermal print services.
     */
    fun generateThermalReceiptPdf(
        context: Context,
        receipt: ReceiptEntity,
        paperWidthMm: Int = 80,
        stationAddress: String = "محطة العسل لتوليد وتوزيع الكهرباء",
        outputFile: File? = null
    ): File {
        val dir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
        val file = outputFile ?: File(dir, "Thermal_${paperWidthMm}mm_${receipt.receiptNumber}.pdf")
        val thermalBitmap = generateThermalReceiptBitmap(context, receipt, paperWidthMm, stationAddress)
        val pageWidthPt = if (paperWidthMm == 58) 164 else 226
        val scale = pageWidthPt.toFloat() / thermalBitmap.width.toFloat()
        val pageHeightPt = (thermalBitmap.height * scale).toInt().coerceAtLeast(220)

        try {
            val spec = PdfPageSpec(pageWidthPt, pageHeightPt) { canvas ->
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(
                    thermalBitmap,
                    Rect(0, 0, thermalBitmap.width, thermalBitmap.height),
                    RectF(0f, 0f, pageWidthPt.toFloat(), pageHeightPt.toFloat()),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
            }
            writePagesPdf(file, listOf(spec), bitmapScale = 2.5f)
        } finally {
            thermalBitmap.recycle()
        }
        return file
    }

    fun printPdfFile(
        context: Context,
        pdfFile: File,
        jobName: String,
        thermalWidthMm: Int? = null
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                runCatching {
                    pdfFile.inputStream().use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure { err ->
                    callback.onWriteFailed(err.message)
                }
            }
        }

        val attrsBuilder = PrintAttributes.Builder()
            .setColorMode(
                if (thermalWidthMm != null) PrintAttributes.COLOR_MODE_MONOCHROME
                else PrintAttributes.COLOR_MODE_COLOR
            )
        if (thermalWidthMm == 58) {
            attrsBuilder.setMediaSize(PrintAttributes.MediaSize("THERMAL_58MM", "Thermal 58mm", 2283, 8000))
            attrsBuilder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        } else if (thermalWidthMm == 80) {
            attrsBuilder.setMediaSize(PrintAttributes.MediaSize("THERMAL_80MM", "Thermal 80mm", 3150, 8000))
            attrsBuilder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        } else {
            attrsBuilder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        }
        printManager.print(jobName, adapter, attrsBuilder.build())
    }

    fun getShareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = getShareUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun generateFieldCollectionA4Pdf(
        context: Context,
        items: List<FieldCollectionSheetItem>,
        areaFilterLabel: String = "جميع المناطق",
        debtFilterLabel: String = "عليه متأخرات فقط",
        stationAddress: String = "محطة العسل لتوليد وتوزيع الكهرباء",
        stationPhone: String = "770002027",
        outputFile: File? = null
    ): File {
        val dir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
        val file = outputFile ?: File(dir, "FieldCollectionSheet_${System.currentTimeMillis()}.pdf")
        val typeface = loadArabicTypeface(context)
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar"))

        // 7 RTL columns summing to 531f
        val columns = listOf(
            "الكود",
            "اسم المشترك",
            "المنطقة / العنوان",
            "الهاتف",
            "القراءة السابقة",
            "المبلغ المستحق",
            "المبلغ المحصل / التوقيع"
        )
        val colWidths = listOf(48f, 108f, 92f, 74f, 64f, 65f, 80f)
        val totalArrears = items.sumOf { it.currentArrears }

        val firstPageCapacity = 16
        val nextPageCapacity = 20
        val chunks = mutableListOf<List<FieldCollectionSheetItem>>()
        if (items.isEmpty()) {
            chunks.add(emptyList())
        } else {
            chunks.add(items.take(firstPageCapacity))
            var offset = firstPageCapacity
            while (offset < items.size) {
                chunks.add(items.subList(offset, (offset + nextPageCapacity).coerceAtMost(items.size)))
                offset += nextPageCapacity
            }
        }

        val pages = chunks.mapIndexed { pageIdx, slice ->
            val pageNum = pageIdx + 1
            PdfPageSpec(A4_WIDTH_PT, A4_HEIGHT_PT) { canvas ->
                var y = drawA4PageDecor(
                    context = context,
                    canvas = canvas,
                    typeface = typeface,
                    title = if (pageNum == 1) "كشف النزول الميداني للمحصلين" else "كشف النزول الميداني للمحصلين (تابع)",
                    subtitle = "المنطقة: $areaFilterLabel | الحالة: $debtFilterLabel | ${dateFormat.format(Date())}",
                    stationName = "محطة العسل 2027 (Alasal2027)",
                    stationPhone = stationPhone,
                    stationAddress = stationAddress,
                    pageNumber = pageNum
                )

                if (pageNum == 1) {
                    val sumWidths = listOf(177f, 177f, 177f)
                    drawRtlRow(
                        canvas,
                        y,
                        24f,
                        listOf("عدد المشتركين: ${items.size}", "إجمالي المستحقات: $totalArrears", "اسم المحصل: ...................."),
                        sumWidths,
                        COLOR_HIGHLIGHT_BG,
                        COLOR_NAVY,
                        typeface,
                        isBold = true,
                        fontSize = 9.5f
                    )
                    y += 32f
                }

                drawRtlRow(canvas, y, 28f, columns, colWidths, COLOR_NAVY, Color.WHITE, typeface, isBold = true, fontSize = 8.5f)
                y += 28f

                slice.forEachIndexed { rowIdx, item ->
                    val bg = if (rowIdx % 2 == 1) COLOR_LIGHT_BG else null
                    val cells = listOf(
                        item.subscriberCode,
                        item.fullName,
                        item.address,
                        item.phoneNumber,
                        "${item.previousMeterReading}",
                        "${item.currentArrears}",
                        "" // Intentionally left blank for manual field entry & signature
                    )
                    drawRtlRow(canvas, y, 30f, cells, colWidths, bg, COLOR_NAVY, typeface, fontSize = 8.5f)
                    y += 30f
                }
            }
        }

        writePagesPdf(file, pages)
        return file
    }

    /**
     * Generates an Excel-ready UTF-8 BOM CSV file for offline field collection sheets.
     */
    fun generateFieldCollectionCsv(
        context: Context,
        items: List<FieldCollectionSheetItem>,
        areaFilterLabel: String = "جميع المناطق",
        outputFile: File? = null
    ): File {
        val dir = File(context.cacheDir, "shared_exports").apply { mkdirs() }
        val file = outputFile ?: File(dir, "Alasal_FieldSheet_${System.currentTimeMillis()}.csv")

        fun esc(value: String): String {
            val cleaned = value.replace("\"", "\"\"")
            return "\"$cleaned\""
        }

        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel & Google Sheets open Arabic headers cleanly without mojibake
        sb.append('\uFEFF')
        sb.appendLine(esc("كشف التحصيل الميداني - محطة العسل 2027 - المنطقة: $areaFilterLabel"))
        sb.appendLine(
            listOf(
                "كود المشترك",
                "اسم المشترك",
                "العنوان / المنطقة",
                "رقم الهاتف",
                "القراءة السابقة",
                "المبلغ المستحق",
                "سعر الكيلوواط",
                "المبلغ المحصل / التوقيع"
            ).joinToString(",") { esc(it) }
        )
        items.forEach { item ->
            sb.appendLine(
                listOf(
                    item.subscriberCode,
                    item.fullName,
                    item.address,
                    item.phoneNumber,
                    item.previousMeterReading.toString(),
                    item.currentArrears.toString(),
                    item.customKwhPrice.toString(),
                    ""
                ).joinToString(",") { esc(it) }
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    /**
     * Launches direct WhatsApp chat to normalized subscriber phone with receipt details.
     * Returns null when dispatched or an explicit Arabic warning string if phone is missing/invalid.
     */
    fun shareReceiptViaWhatsApp(
        context: Context,
        receipt: ReceiptEntity,
        subscriberPhone: String?,
        defaultCountryCode: String = "967"
    ): String? {
        val norm = InvoiceShareAndPrintService.normalizePhoneForWhatsApp(subscriberPhone, defaultCountryCode)
        if (!norm.isValid) {
            return norm.warningMessage
        }
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar"))
        val text = buildString {
            appendLine("✅ *${receipt.stationNameSnapshot.ifEmpty { "محطة العسل 2027" }}*")
            appendLine("إيصال تحصيل رقم: *${receipt.receiptNumber}*")
            appendLine("المشترك: ${receipt.subscriberNameSnapshot} (${receipt.subscriberCodeSnapshot})")
            appendLine("رقم الفاتورة: ${receipt.invoiceNumberSnapshot}")
            appendLine("المبلغ السابق: ${receipt.previousBalance}")
            appendLine("المبلغ المحصل: *${receipt.amount}*")
            appendLine("المتبقي بعد التحصيل: ${receipt.balanceAfterReceipt}")
            if (receipt.creditAfterReceipt > 0.0) {
                appendLine("الرصيد الدائن: ${receipt.creditAfterReceipt}")
            }
            appendLine("المحصل: ${receipt.collectorNameSnapshot}")
            appendLine("طريقة السداد: ${StationReportGenerator.translatePaymentMethod(receipt.paymentMethod)}")
            append("التاريخ: ${dateFormat.format(Date(receipt.issuedAt))}")
        }
        val uri = InvoiceShareAndPrintService.buildWhatsAppChatUri(norm.normalizedDigits, text)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            null
        }.getOrElse {
            "تعذر فتح محادثة واتساب مباشرة. تأكد من تثبيت واتساب وصحة رقم المشترك."
        }
    }
}
