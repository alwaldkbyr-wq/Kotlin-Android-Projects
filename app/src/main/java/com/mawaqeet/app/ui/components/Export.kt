package com.mawaqeet.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Paint
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
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.mawaqeet.app.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * تصدير المعلومات والجداول ومشاركتها:
 * نص عبر التطبيقات، وملف CSV (يفتحه Excel)، وملف PDF بخط التطبيق، والطباعة.
 * المشاركة عبر نافذة النظام تتيح البلوتوث والواي فاي المباشر والبريد وغيرها.
 */
object Exporter {

    fun shareText(context: Context, text: String, title: String = "مشاركة") {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun dir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun safe(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    /** يكتب ملف CSV (UTF-8 مع BOM حتى يظهر العربي سليماً في Excel) */
    fun csv(context: Context, name: String, headers: List<String>, rows: List<List<String>>): File {
        val f = File(dir(context), safe(name) + ".csv")
        fun esc(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        f.outputStream().use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write((headers.joinToString(",") { esc(it) } + "\n").toByteArray())
            rows.forEach { r -> out.write((r.joinToString(",") { esc(it) } + "\n").toByteArray()) }
        }
        return f
    }

    /** يكتب جدولاً في ملف PDF (A4، من اليمين إلى اليسار، مع ترويسة وتذييل) */
    fun pdf(context: Context, name: String, title: String, subtitle: String, headers: List<String>, rows: List<List<String>>): File {
        val f = File(dir(context), safe(name) + ".pdf")
        val doc = PdfDocument()
        val pw = 595; val ph = 842; val margin = 36f
        val font: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.thmanyah_serif_text_regular) }.getOrNull() ?: Typeface.DEFAULT
        val bold: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.thmanyah_serif_text_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val titleFont: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.thmanyah_serif_display_black) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleFont; textSize = 17f; color = 0xFF1B2250.toInt(); textAlign = Paint.Align.RIGHT }
        val subP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 10f; color = 0xFF6E685C.toInt(); textAlign = Paint.Align.RIGHT }
        val headP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 9.5f; color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER }
        val cellP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 9f; color = 0xFF1A2633.toInt(); textAlign = Paint.Align.CENTER }
        val fill = Paint().apply { color = 0xFF3B4CCA.toInt() }
        val zebra = Paint().apply { color = 0xFFF3EFE6.toInt() }
        val line = Paint().apply { color = 0xFFD9CFB8.toInt(); strokeWidth = 0.6f }
        val cols = headers.size.coerceAtLeast(1)
        val colW = (pw - 2 * margin) / cols
        val rowH = 17f
        var pageNo = 0
        var i = 0
        do {
            pageNo++
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
            val c = page.canvas
            var y = margin + 14f
            c.drawText(title, pw - margin, y, titleP); y += 15f
            c.drawText(subtitle, pw - margin, y, subP); y += 12f
            c.drawRect(margin, y, pw - margin, y + rowH + 2, fill)
            // الأعمدة من اليمين إلى اليسار
            headers.forEachIndexed { k, h -> c.drawText(h, pw - margin - colW * k - colW / 2, y + rowH - 4.5f, headP) }
            y += rowH + 2
            var n = 0
            while (i < rows.size && y + rowH < ph - margin - 16) {
                if (n % 2 == 1) c.drawRect(margin, y, pw - margin, y + rowH, zebra)
                rows[i].forEachIndexed { k, v ->
                    if (k < cols) c.drawText(ellipsize(v, cellP, colW - 4), pw - margin - colW * k - colW / 2, y + rowH - 5f, cellP)
                }
                c.drawLine(margin, y + rowH, pw - margin, y + rowH, line)
                y += rowH; i++; n++
            }
            c.drawText("الساهور • صفحة $pageNo", pw / 2f, ph - margin + 8, subP.apply { textAlign = Paint.Align.CENTER })
            subP.textAlign = Paint.Align.RIGHT
            doc.finishPage(page)
        } while (i < rows.size)
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    private fun ellipsize(s: String, p: TextPaint, w: Float): String {
        if (p.measureText(s) <= w) return s
        var t = s
        while (t.length > 1 && p.measureText("$t…") > w) t = t.dropLast(1)
        return "$t…"
    }

    fun uri(context: Context, f: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)

    /** مشاركة ملف (بلوتوث، بريد، واي فاي مباشر، تطبيقات المراسلة…) */
    fun shareFile(context: Context, f: File, mime: String) {
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri(context, f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, "مشاركة الملف").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** فتح الملف في تطبيق مناسب */
    fun open(context: Context, f: File, mime: String) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, f), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }.onFailure { Toast.makeText(context, "لا يوجد تطبيق لفتح الملف", Toast.LENGTH_SHORT).show() }
    }

    /** طباعة ملف PDF عبر خدمة الطباعة في النظام (أو حفظه PDF من نافذة الطباعة) */
    fun print(context: Context, pdf: File, jobName: String) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        pm.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder(pdf.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                runCatching {
                    FileInputStream(pdf).use { input -> FileOutputStream(dest.fileDescriptor).use { input.copyTo(it) } }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure { cb.onWriteFailed(it.message) }
            }
        }, null)
    }
}
