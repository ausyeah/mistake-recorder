package com.mistakebook.print

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.mistakebook.data.AppFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 导出文件的产出与分享。
 *
 * 三种格式（PDF / HTML / DOCX）走同一条路：Q+ 通过 MediaStore 落到
 * `Download/错题本/`，Q 以下放 app 私有目录；两种情况下都用一个本地文件负责分享
 * （FileProvider 授权）。
 *
 * 扩展名与 MIME 一律取自 [ExportFormat]——**不在这两处硬编码**。
 */
class ExportPublisher(
    private val context: Context,
    private val files: AppFiles
) {

    data class Output(
        val file: File,
        val displayPath: String,
        val shareUri: Uri,
        val format: ExportFormat
    )

    suspend fun publish(exported: File, format: ExportFormat): Output = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        val name = "${format.fileName(stamp)}.${format.extension}"
        var displayPath = exported.absolutePath

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, format.mimeType)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/错题本"
                    )
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        exported.inputStream().use { input -> input.copyTo(output) }
                    }
                    displayPath = "Download/错题本/$name"
                }
            }
        }

        val shareTarget = File(files.shareCacheDir.apply { mkdirs() }, name)
        exported.copyTo(shareTarget, overwrite = true)
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareTarget
        )
        Output(file = shareTarget, displayPath = displayPath, shareUri = shareUri, format = format)
    }

    /** 新建一个待写入的导出文件。 */
    fun createTempFile(format: ExportFormat): File {
        files.exportDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        return File(files.exportDir, "${format.fileName(stamp)}.${format.extension}")
    }
}
