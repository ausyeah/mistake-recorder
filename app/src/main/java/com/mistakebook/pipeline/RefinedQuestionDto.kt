package com.mistakebook.pipeline

import kotlinx.serialization.Serializable

// 大模型输出的单题结构，字段名与 PRD 5.1 完全一致。
@Serializable
data class RefinedQuestionDto(
    val subject: String = "",
    /** 简短标题，用于列表页展示；可为空（用户手填）。 */
    val title: String = "",
    val knowledge_points: List<String> = emptyList(),
    val stem: String = "",
    val options: List<RefinedOptionDto> = emptyList(),
    val answer: String = "",
    val analysis: String = "",
    val image_refs: List<String> = emptyList(),
    val error_reason_guess: String = "其他",
    val difficulty: Int = 3,
    val uncertain: List<String> = emptyList()
)

@Serializable
data class RefinedOptionDto(
    val label: String = "",
    val text: String = ""
)

// 多题输出时的外层结构。
@Serializable
data class RefinedItemsDto(
    val items: List<RefinedQuestionDto> = emptyList()
)
