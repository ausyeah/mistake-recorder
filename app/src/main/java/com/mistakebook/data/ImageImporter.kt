package com.mistakebook.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把相册选中的图片导入 app 目录。
 *
 * 关键改动：不再做「裸字节拷贝」。
 * 裸拷贝会把 EXIF 方向标记原封不动带进新文件，而 BitmapFactory 不认 EXIF，
 * 导致「明明调正了却又歪回去」。这里统一走 [ImageNormalizer]：
 * 方向烘进像素 + OCR 对比度增强 + 统一转 JPEG。
 */
class ImageImporter(
    private val context: Context,
    private val files: AppFiles,
    private val enhance: () -> Boolean = { true }
) {

    suspend fun importUris(uris: List<Uri>): List<File> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri -> runCatching { importOne(uri) }.getOrNull() }
    }

    private fun importOne(uri: Uri): File? {
        val target = files.newImportFile("jpg")
        // 先落到临时文件：归一化要能按路径读两次（EXIF 一次、解码一次）
        val staging = files.newImportFile("src")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            staging.length() > 0
        }.getOrDefault(false)
        if (!copied) {
            staging.delete()
            return null
        }

        val ok = ImageNormalizer.normalize(staging, target, enhance())
        staging.delete()
        if (!ok) {
            // 归一化失败（极端损坏的图片）不该让整批导入作废，退回原始字节
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return if (target.length() > 0) target else null
        }
        return target
    }
}
