package com.mistakebook.data.local

import com.mistakebook.data.local.entities.Question
import com.mistakebook.domain.Option
import com.mistakebook.domain.labelFor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

// 解析选项 JSON；label 缺失时按序号补 A/B/C。
val Question.options: List<Option>
    get() = runCatching {
        json.decodeFromString(ListSerializer(Option.serializer()), optionsJson)
    }.getOrDefault(emptyList()).mapIndexed { index, option ->
        if (option.label.isBlank()) option.copy(label = labelFor(index)) else option
    }

// 解析知识点 JSON。
val Question.knowledgePoints: List<String>
    get() = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), knowledgePointsJson)
    }.getOrDefault(emptyList())

/** 列表页展示用标题：优先自定义标题，没有则回退到题干首句。 */
val Question.displayTitle: String
    get() = title.trim().ifBlank {
        stem.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.replace(imageRefRegex, "")
            ?.replace(whitespaceRegex, " ")
            ?.trim()
            ?.take(40)
            .orEmpty()
            .ifBlank { "未命名题目" }
    }

private val imageRefRegex = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
private val whitespaceRegex = Regex("\\s+")

/** 题目附图路径列表（MinerU 切出的图形 / 用户手动裁的图）。 */
val Question.figurePaths: List<String>
    get() = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), figurePathsJson)
    }.getOrDefault(emptyList()).filter { it.isNotBlank() }

/** 打印用图：优先附图，没有附图才回退到原始照片。 */
val Question.printImagePath: String
    get() = figurePaths.firstOrNull() ?: imagePath
