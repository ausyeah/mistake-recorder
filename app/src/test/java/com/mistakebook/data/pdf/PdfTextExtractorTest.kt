package com.mistakebook.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用手写的未压缩 PDF 校验抽取链路：对象表、字典解析、字面量字符串、TJ 数组、Tf 字体切换。
 */
class PdfTextExtractorTest {

    private val pdf = """
        %PDF-1.4
        1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
        2 0 obj << /Type /Pages /Kids [ 3 0 R ] /Count 1 >> endobj
        3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ]
            /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >>
            /Contents 4 0 R >> endobj
        4 0 obj << /Length 200 >>
        stream
        BT /F1 18 Tf 72 760 Td (Hello) Tj ( MistakeBook) Tj ET
        BT /F2 12 Tf 72 700 Td [(Second) -340 (Page)] TJ ET
        endstream
        endobj
        5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
        6 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
        trailer << /Root 1 0 R >>
        %%EOF
    """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.ISO_8859_1)

    @Test
    fun `extracts text from both text blocks`() {
        val pages = PdfTextExtractor.extractAll(pdf)
        assertEquals(1, pages.size)
        val text = pages[0]
        assertTrue(text.contains("Hello"))
        assertTrue(text.contains("MistakeBook"))
        assertTrue(text.contains("Second"))
        assertTrue(text.contains("Page"))
    }

    @Test
    fun `counts pages`() {
        assertEquals(1, PdfTextExtractor.pageCount(pdf))
    }

    @Test
    fun `recognises text pdf`() {
        assertTrue(PdfTextExtractor.isTextPdf(pdf))
    }
    @Test
    fun `toUnicode mapping is isolated per document`() {
        val mappedPdf = singlePagePdf(withToUnicode = true)
        val plainPdf = singlePagePdf(withToUnicode = false)

        assertTrue(PdfTextExtractor.extractAll(mappedPdf).single().contains("\u4E2D"))
        assertTrue(PdfTextExtractor.extractAll(plainPdf).single().contains("A"))
    }

    private fun singlePagePdf(withToUnicode: Boolean): ByteArray {
        val toUnicodeRef = if (withToUnicode) "/ToUnicode 7 0 R" else ""
        val cmapObject = if (withToUnicode) {
            """
                7 0 obj << /Length 32 >>
                stream
                1 beginbfchar
                <41> <4E2D>
                endbfchar
                endstream
                endobj
            """.trimIndent()
        } else {
            ""
        }
        return """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [ 3 0 R ] /Count 1 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >> endobj
            4 0 obj << /Length 30 >>
            stream
            BT /F1 12 Tf (A) Tj ET
            endstream
            endobj
            5 0 obj << /Type /Font $toUnicodeRef >> endobj
            $cmapObject
            trailer << /Root 1 0 R >>
            %%EOF
        """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.ISO_8859_1)
    }
}
