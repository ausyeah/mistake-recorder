package com.mistakebook.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mistakebook.domain.AttachmentKind
import com.mistakebook.domain.AttachmentStatus

/**
 * 一条消息上的一个附件。
 *
 * ## 纯单机：附件不上传
 *
 * - 图片：压缩后**以 base64 data URL** 走 `image_url`，随请求体发出去。
 * - PDF / TXT / MD / DOCX：**本地抽文本**写进 `textExcerpt`，拼进 prompt。
 *
 * 所以 [localPath] 指向的是应用私有目录里的副本——
 * 用户从相册或文件管理器选的原文件随时可能被删或被移动，
 * 必须在选中的那一刻拷进来。
 *
 * ## 为什么附件要有自己独立的 status
 *
 * 一条消息可以带多个附件，其中「图片压缩好了、PDF 抽文本失败」是完全正常的
 * （比如加密 PDF）。挂在 [com.mistakebook.domain.MessageStatus] 上会让
 * 整条消息跟着失败，用户白等。
 *
 * ## 字段说明（规格里这段被截断，按实际需要定成这样）
 *
 * - [sizeBytes]：文件字节数，用于「附件 3.2 MB」这类展示与超限判断
 * - [widthPx] / [heightPx]：只有图片有值；压缩时按长边限制算出，用于占位与展示
 * - [textExcerpt]：**抽出来的纯文本**，只截前若干字符
 *   （见 [com.mistakebook.data.chat.ChatAttachmentPreparer.MAX_EXCERPT_CHARS]），
 *   不塞进数据库全文——长文档全文入库既膨胀又超 prompt 预算
 * - [extractedChars]：实际抽了多少字符，用于「已提取 12000 字」这类反馈
 */
@Entity(
    tableName = "chat_attachments",
    indices = [
        Index("messageId"),
        Index("sessionId"),
        Index("status")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChatAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val sessionId: Long,
    val kind: AttachmentKind,
    /** 应用私有目录下的副本；图片另有生成的 data URL 缓存。 */
    val localPath: String,
    val fileName: String,
    val mimeType: String = "",
    val status: AttachmentStatus,
    val sizeBytes: Long = 0,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    /** 抽出来的纯文本**摘要**（已截断），不进数据库全文。截断长度见 [ChatAttachmentPreparer.MAX_EXCERPT_CHARS]。 */
    val textExcerpt: String = "",
    /** 实际抽取字符数，供 UI 反馈「已提取 N 字」。 */
    val extractedChars: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long
)
