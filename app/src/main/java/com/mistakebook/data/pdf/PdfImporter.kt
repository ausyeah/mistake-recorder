package com.mistakebook.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.mistakebook.data.AppFiles
import com.mistakebook.pipeline.RecognitionSubmitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDF 导入：文本 PDF 直接抽文字交给大模型，图片 PDF 逐页栅格化后走 MinerU。
 */
class PdfImporter(
    private val context: Context,
    private val files: AppFiles,
    private val submitter: RecognitionSubmitter
) {

    sealed class Result {
        data class Imported(val taskIds: List<Long>, val textMode: Boolean, val pageCount: Int) :
            Result()

        data class Failed(val message: String) : Result()
    }

    suspend fun importPdf(
        pdfFile: File,
        title: String,
        pageRange: List<Int>? = null
    ): Result = withContext(Dispatchers.IO) {
        try {
            val bytes = pdfFile.readBytes()
            val totalPages = PdfTextExtractor.pageCount(bytes)
            if (totalPages <= 0) return@withContext Result.Failed("PDF 没有可识别的页面")
            val wanted = pageRange?.filter { it in 1..totalPages }?.ifEmpty { null }
                ?: (1..totalPages).toList()

            val textPages = if (PdfTextExtractor.isTextPdf(bytes)) {
                PdfTextExtractor.extractAll(bytes).mapIndexedNotNull { index, text ->
                    val pageNumber = index + 1
                    if (pageNumber in wanted && text.isNotBlank()) text else null
                }
            } else {
                emptyList()
            }

            if (textPages.isNotEmpty()) {
                val ids = submitter.submitTextPages(textPages, title, files.importDir)
                return@withContext Result.Imported(ids, true, textPages.size)
            }

            val rendered = renderPages(pdfFile, wanted)
            if (rendered.isEmpty()) return@withContext Result.Failed("PDF 渲染失败")
            val ids = submitter.submitImages(rendered, title)
            Result.Imported(ids, false, rendered.size)
        } catch (error: Exception) {
            Result.Failed(error.message ?: "PDF 解析失败")
        }
    }

    /** 逐页栅格化成 150 DPI 的 JPEG。 */
    private suspend fun renderPages(pdfFile: File, wanted: List<Int>): List<File> =
        withContext(Dispatchers.IO) {
            val output = mutableListOf<File>()
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                descriptor = ParcelFileDescriptor.open(
                    pdfFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                renderer = PdfRenderer(descriptor)
                wanted.forEach { pageNumber ->
                    val index = pageNumber - 1
                    if (index < 0 || index >= renderer.pageCount) return@forEach
                    val rendered = renderPageSafely(renderer, index) ?: return@forEach
                    output += rendered
                }
            } catch (error: Exception) {
                output.clear()
            } finally {
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
            }
            output
        }

    // 单页渲染带内存保护：长边封顶 + OOM 时降级到 RGB_565 再试
    private fun renderPageSafely(renderer: PdfRenderer, index: Int): File? {
        val first = try {
            renderOnePage(renderer, index, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            null
        }
        if (first != null) return first
        return try {
            renderOnePage(renderer, index, Bitmap.Config.RGB_565)
        } catch (oom: OutOfMemoryError) {
            null
        }
    }

    private fun renderOnePage(
        renderer: PdfRenderer,
        index: Int,
        config: Bitmap.Config
    ): File? {
        renderer.openPage(index).use { page ->
            val longEdge = maxOf(page.width, page.height).coerceAtLeast(1)
            val scale = minOf(MAX_RASTER_EDGE.toFloat() / longEdge, MAX_SCALE)
            val width = (page.width * scale).toInt().coerceIn(100, MAX_RASTER_EDGE * 2)
            val height = (page.height * scale).toInt().coerceIn(100, MAX_RASTER_EDGE * 2)
            val bitmap = Bitmap.createBitmap(width, height, config)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val target = files.newPdfPageFile()
            FileOutputStream(target).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            }
            bitmap.recycle()
            return target
        }
    }

    private companion object {
        const val MAX_RASTER_EDGE = 1600
        const val MAX_SCALE = 150f / 72f
    }
}
