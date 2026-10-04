package com.mistakebook.domain

import kotlinx.serialization.Serializable

/** 选择题选项 */
@Serializable
data class Option(
    val label: String = "",
    val text: String = ""
)

// 选项序号 -> 大写字母，超过 26 个退回数字。
fun labelFor(index: Int): String =
    if (index in 0..25) ('A' + index).toString() else (index + 1).toString()
