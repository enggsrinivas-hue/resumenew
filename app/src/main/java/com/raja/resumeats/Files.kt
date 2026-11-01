package com.raja.resumeats

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

object Importer {
    fun read(ctx: Context, uri: Uri): String {
        var name = ""
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: ""
        }
        val type = ctx.contentResolver.getType(uri) ?: ""
        val stream = ctx.contentResolver.openInputStream(uri) ?: error("Can't open file")
        val raw = stream.use { s ->
            when {
                type.contains("pdf") || name.endsWith(".pdf", true) -> {
                    PDFBoxResourceLoader.init(ctx)
                    PDDocument.load(s).use { PDFTextStripper().getText(it) }
                }
                type.contains("wordprocessingml") || name.endsWith(".docx", true) -> docx(s)
                else -> s.bufferedReader().readText()
            }
        }
        return raw.replace("\u00a0", " ").replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun docx(s: InputStream): String {
        ZipInputStream(s).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "word/document.xml") {
                    val xml = z.readBytes().toString(Charsets.UTF_8)
                    return xml.replace("</w:p>", "\n").replace("<w:tab/>", " ").replace("<w:br/>", "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                }
                e = z.nextEntry
            }
        }
        return ""
    }
}

object Exporter {
    fun pdf(text: String, out: OutputStream) {
        val pw = 595
        val ph = 842
        val m = 50f
        val body = Paint().apply { textSize = 10.5f; typeface = Typeface.SANS_SERIF; isAntiAlias = true }
        val bold = Paint(body).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val doc = PdfDocument()
        var n = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
        var y = m

        fun emit(s: String, p: Paint, x: Float) {
            val h = p.textSize * 1.45f
            if (y + h > ph - m) {
                doc.finishPage(page)
                n++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
                y = m
            }
            page.canvas.drawText(s, x, y + p.textSize, p)
            y += h
        }

        fun wrap(t: String, p: Paint, x: Float) {
            val maxW = pw - m - x
            var line = ""
            for (w in t.split(" ")) {
                val test = if (line.isEmpty()) w else "$line $w"
                if (p.measureText(test) > maxW && line.isNotEmpty()) { emit(line, p, x); line = w } else line = test
            }
            if (line.isNotEmpty()) emit(line, p, x)
        }

        for (l in Resume.parse(text)) {
            when (l.kind) {
                Kind.BLANK -> {}
                Kind.NAME -> { bold.textSize = 18f; wrap(l.text, bold, m); bold.textSize = 11.5f; y += 4f }
                Kind.HEADING -> { y += 8f; bold.textSize = 11.5f; emit(l.text, bold, m); y += 1f }
                Kind.BULLET -> wrap("•  " + l.text, body, m + 8f)
                Kind.TEXT -> wrap(l.text, body, m)
            }
        }
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun para(text: String, bold: Boolean = false, size: Int = 22, before: Int = 0, indent: Int = 0): String {
        val ind = if (indent > 0) "<w:ind w:left=\"$indent\"/>" else ""
        val b = if (bold) "<w:b/>" else ""
        return "<w:p><w:pPr><w:spacing w:before=\"$before\" w:after=\"40\"/>$ind</w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>$b<w:sz w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"
    }

    fun docx(text: String, out: OutputStream) {
        val sb = StringBuilder()
        for (l in Resume.parse(text)) {
            when (l.kind) {
                Kind.BLANK -> {}
                Kind.NAME -> sb.append(para(l.text, bold = true, size = 36))
                Kind.HEADING -> sb.append(para(l.text, bold = true, size = 24, before = 200))
                Kind.BULLET -> sb.append(para("• " + l.text, indent = 360))
                Kind.TEXT -> sb.append(para(l.text))
            }
        }
        val header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        val ns = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
        val document = header + "<w:document $ns><w:body>" + sb +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1000\" w:right=\"1000\" w:bottom=\"1000\" w:left=\"1000\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>"
        val types = header + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"
        val rels = header + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
        ZipOutputStream(out).use { z ->
            fun put(name: String, data: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(data.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put("[Content_Types].xml", types)
            put("_rels/.rels", rels)
            put("word/document.xml", document)
        }
    }
}
