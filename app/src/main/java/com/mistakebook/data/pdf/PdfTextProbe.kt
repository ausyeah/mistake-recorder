package com.mistakebook.data.pdf

import java.io.File

// 选完 PDF 后的快速探测：页数 + 是否走文本链路（都必须在 IO 线程调用）。
object PdfTextProbe {

    data class Info(val pageCount: Int, val textMode: Boolean)

    // 超过这个大小就不整体读进内存了，直接提示分段导入
    const val MAX_FILE_BYTES = 64L * 1024 * 1024

    fun probe(file: File): Info {
        if (file.length() > MAX_FILE_BYTES) {
            error("PDF 超过 64MB，请分段导入")
        }
        val bytes = file.readBytes()
        val pages = PdfTextExtractor.pageCount(bytes)
        val textMode = pages > 0 && PdfTextExtractor.isTextPdf(bytes)
        return Info(pages, textMode)
    }
}
