package com.mistakebook.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mistakebook.domain.ChatRole
import com.mistakebook.domain.MessageStatus

/**
 * 一条对话消息。
 *
 * ## 为什么状态存在库里而不是只在内存里
 *
 * 流式回复期间，助手消息会**边收边更新同一条记录**（STREAMING -> DONE）。
 * 这样做的好处是：用户中途杀掉进程、或从会话页返回再进来，
 * 都能看到当时真实的内容和状态，而不是靠「最后一条是不是空的」去猜——
 * 那种写法在重进页面时必然出错。
 *
 * ## 关于 questionId 与 injected
 *
 * 题目上下文（题干 / 选项 / 答案 / 解析 / 学科…）是**注入的一条系统消息**，
 * 只在会话第一条时注入一次，[injected] 标 true。
 * 组装请求时靠它跳过重复注入，否则每轮都把题目重发一遍，
 * 既烧 token 又会让模型反复强调题目内容。
 */
@Entity(
    tableName = "chat_messages",
    indices = [
        Index("sessionId"),
        Index("createdAt"),
        // 外键列必须建索引：否则每次 questions 增删改都会全表扫 chat_messages。
        // Room 会在编译期警告这条，索引名与迁移 SQL 里的必须一致。
        Index("questionId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Question::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: ChatRole,

    /** Markdown + LaTeX 原文。渲染交给 RichText，这里不做任何格式化。 */
    val content: String,
    val status: MessageStatus,

    /** 失败原因。**只放给人看的简短中文**，不塞异常堆栈。 */
    val errorMessage: String? = null,

    /** 附件 id 列表，`List<Long>` 的 JSON 序列化。 */
    val attachmentIdsJson: String = "",

    /** 题目上下文注入的那条消息所关联的题目。 */
    val questionId: Long? = null,

    /** 组装请求时不重复发送。 */
    val injected: Boolean = false,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
