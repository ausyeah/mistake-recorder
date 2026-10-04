package com.mistakebook.data.pdf

// 本地自测入口：gradlew :app:compileDebugUnitTestKotlin 后直接用 java 运行，
// 绕开本机 non-ASCII 路径下 Gradle test worker 的 ClassNotFoundException。
fun main() {
    val pdf = """
        %PDF-1.4
        1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
        2 0 obj << /Type /Pages /Kids [ 3 0 R ] /Count 1 >> endobj
        3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ]
            /Resources << /Font << /F1 5 0 R >> >>
            /Contents 4 0 R >> endobj
        4 0 obj << /Length 200 >>
        stream
        BT /F1 18 Tf 72 760 Td (Hello) Tj ( MistakeBook) Tj ET
        endstream
        endobj
        5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
        trailer << /Root 1 0 R >>
    """.trimIndent().toByteArray(java.nio.charset.StandardCharsets.ISO_8859_1)

    val pages = PdfTextExtractor.extractAll(pdf)
    println("pages=" + PdfTextExtractor.extractAll(pdf).size); println("text=[" + PdfTextExtractor.extractAll(pdf).firstOrNull().orEmpty() + "]")
    println("text=[${pages.firstOrNull().orEmpty()}]")
    println("isTextPdf=${PdfTextExtractor.isTextPdf(pdf)}")
    check(pages.size == 1) { "页数应为 1" }
    check(pages[0].contains("Hello")) { "应包含 Hello" }
    check(pages[0].contains("MistakeBook")) { "应包含 MistakeBook" }
    println("PDF_TEXT_EXTRACTOR_OK")
}
