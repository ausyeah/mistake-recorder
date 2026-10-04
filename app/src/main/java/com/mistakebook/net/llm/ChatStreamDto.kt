package com.mistakebook.net.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SSE 流式响应的一帧。
 *
 * 与非流式的 [ChatResponse] 分开：**流式给的是 `delta`（增量），
 * 非流式给的是 `message`（全量）**，字段名不同，共用一个类必然有一边读不到。
 */
@Serializable
data class ChatStreamChunk(
    val id: String = "",
    val choices: List<StreamChoice> = emptyList(),

    /** 只有显式要求 `stream_options.include_usage` 时才有。多数中转站不给。 */
    val usage: Usage? = null,

    /** 少数服务会带错误体在 200 响应里。 */
    val error: StreamError? = null
)

@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: StreamDelta? = null,

    /**
     * `stop` = 正常结束；`length` = **被 max_tokens 截断**。
     *
     * 截断必须单独识别：它和「模型胡乱输出」在下游表现完全一样
     * （都是半截内容），但处理方式不同。
     */
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamDelta(
    val role: String? = null,

    /** 正文。思考过程不在这里——见 [reasoningContent]。 */
    val content: String? = null,

    /**
     * **思考过程**（推理模型的适用字段）。
     *
     * 为什么三个字段都要读：各家的命名不一样——DeepSeek / Qwen 系用
     * `reasoning_content`，部分中转站用 `reasoning`，略带思考的接口用 `thinking`。
     * 只读其中一个的话，用户就会看到一个只有蓝点、没有内容的空气泡。
     *
     * 全部有默认值 → 未知的额外字段不会因为缺失而抛异常。
     */
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
    val thinking: String? = null
) {
    /** 取第一个非空的思考字段。 */
    fun reasoningOrNull(): String? =
        reasoningContent?.takeIf { it.isNotEmpty() }
            ?: reasoning?.takeIf { it.isNotEmpty() }
            ?: thinking?.takeIf { it.isNotEmpty() }
}

@Serializable
data class StreamError(
    val message: String = "",
    val type: String = ""
)
