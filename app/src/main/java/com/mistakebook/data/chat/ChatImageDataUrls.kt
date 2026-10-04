package com.mistakebook.data.chat

import android.util.LruCache
import com.mistakebook.data.local.entities.ChatAttachment
import com.mistakebook.data.repos.ImageDataUrlProvider
import java.io.File
import java.util.Base64

/**
 * 附件图片 -> base64 data URL。
 *
 * ## 为什么要有缓存
 *
 * 每次组装请求都要把 JPEG 读成字节再 base64：一张长边 1280 的图约 300 KB，
 * base64 后 400 KB 字符。**不缓存的话每轮对话都重算一遍**，
 * 而组装上下文是每发一条消息就跑一次。
 *
 * 缓存键用 `路径 + 修改时间 + 长度`：附件文件是不可变的，但用户删了重传同名文件时
 * 只按路径当键会命中旧内容。
 *
 * ## 容量取 4
 *
 * 4 × 400 KB ≈ 1.6 MB。**再大没有意义**——上下文组装本身只带
 * [ChatContextAssembler.MAX_IMAGES_PER_REQUEST] 张图，缓存再深也用不到。
 */
class ChatImageDataUrls : ImageDataUrlProvider {

    private val cache = object : LruCache<String, String>(CACHE_ENTRIES) {}

    override suspend fun dataUrlOf(attachment: ChatAttachment): String? {
        val file = File(attachment.localPath)
        if (!file.exists() || file.length() == 0L) return null
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        cache.get(key)?.let { return it }

        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        // base64 不能有换行：SSE 走 JSON 字符串，换行必须转义，会白白撑大请求体。
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val mime = mimeOf(file.name)
        val url = "data:$mime;base64,$encoded"
        cache.put(key, url)
        return url
    }

    private fun mimeOf(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".heic") -> "image/heic"
            else -> "image/jpeg"
        }
    }

    private companion object {
        const val CACHE_ENTRIES = 4
    }
}
