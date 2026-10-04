package com.mistakebook.pipeline

import kotlinx.serialization.Serializable

/**
 * 一次识别的完整产出，序列化后存进 capture_tasks.refinedJson。
 *
 * 一次输入可能整理出多道题（相册批量 / PDF 一页多题），编辑页按 [questions] 翻页。
 */
@Serializable
data class DraftBundle(
    val imagePath: String,
    val markdown: String,
    val truncatedInput: Boolean = false,
    val degraded: Boolean = false,
    val degradedReason: String = "",
    val questions: List<DraftQuestion> = emptyList()
)

@Serializable
data class DraftQuestion(
    val subject: String = "",
    /** 简短标题（列表页展示），可为空。 */
    val title: String = "",
    val stem: String = "",
    val options: List<RefinedOptionDto> = emptyList(),
    val answer: String = "",
    val analysis: String = "",
    val knowledgePoints: List<String> = emptyList(),
    val errorReason: String = "其他",
    val difficulty: Int = 3,
    val uncertain: List<String> = emptyList()
)

object DraftBundleCodec {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(bundle: DraftBundle): String = json.encodeToString(DraftBundle.serializer(), bundle)

    fun decode(raw: String?): DraftBundle? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(DraftBundle.serializer(), raw) }.getOrNull()
    }

    fun encodeQuestion(question: DraftQuestion): String =
        json.encodeToString(DraftQuestion.serializer(), question)

    fun decodeQuestion(raw: String?): DraftQuestion? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(DraftQuestion.serializer(), raw) }.getOrNull()
    }
}
