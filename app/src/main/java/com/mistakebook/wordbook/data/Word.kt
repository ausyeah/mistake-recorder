package com.mistakebook.wordbook.data

import kotlinx.serialization.Serializable

/**
 * 单词条目（来自内置 4356 考研词库）。
 */
@Serializable
data class Word(
    val word: String,
    val meaning: String,
    val pos: String = "",
    val full: String = "",
    val variants: List<String> = emptyList()
)

/**
 * 单词书 JSON 顶层容器。
 */
@Serializable
data class VocabFile(
    val meta: VocabMeta? = null,
    val words: List<Word> = emptyList()
)

@Serializable
data class VocabMeta(
    val name: String? = null,
    val total: Int = 0
)

/**
 * 词根聚类索引文件结构。
 */
@Serializable
data class VocabIndexFile(
    val version: Int = 1,
    val clusters: Map<String, List<String>> = emptyMap(),
    val synGroups: List<List<String>> = emptyList(),
    val byPos: Map<String, List<String>> = emptyMap()
)
