package com.mistakebook.data.chat

import com.mistakebook.domain.ChatRole

/**
 * 组装请求用的**最小消息视图**。
 *
 * 刻意不直接用 Room 实体 [com.mistakebook.data.local.entities.ChatMessage]：
 * 那个类带着 status / errorMessage / createdAt 等一堆与「发给模型」无关的字段，
 * 而上下文组装是本项目里最容易出顺序依赖 bug 的地方（见
 * [ChatContextAssembler]），必须能脱离数据库单独测。
 */
data class OutgoingMessage(
    val id: Long,
    val role: ChatRole,

    /** 用户/模型说的话原文（Markdown + LaTeX）。 */
    val text: String,

    /** 附件抽出来的文本，追加在 [text] 之后发给模型。 */
    val attachmentText: String = "",

    /** 图片附件的 base64 data URL。 */
    val images: List<String> = emptyList()
) {
    /** 计入上下文预算的字符数。 */
    fun budgetCost(): Int = text.length + attachmentText.length
}

/**
 * 组装结果。
 *
 * @param omittedCount 被丢弃的早期对话轮数。**大于 0 时 UI 要显示「已省略 N 条早期对话」**，
 *   否则用户会以为 AI 突然失忆。
 * @param truncatedByMaxTokens 上一轮是否因为 `max_tokens` 截断。
 *   这与「模型胡乱输出」在下游完全一样，必须单独告诉用户。
 */
data class AssembledContext(
    val messages: List<OutgoingMessage>,
    val omittedCount: Int,
    val keptRounds: Int,
    val usedChars: Int
)
