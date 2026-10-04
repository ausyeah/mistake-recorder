package com.mistakebook.ui.common

import androidx.compose.ui.graphics.Color

// 预置学科的展示色，未列出的学科用主题蓝。
object SubjectPalette {

    private val colors = mapOf(
        "数学" to Color(0xFF4F7DF3),
        "物理" to Color(0xFF7A5AF8),
        "化学" to Color(0xFF12A594),
        "生物" to Color(0xFF3FA34D),
        "语文" to Color(0xFFD9534F),
        "英语" to Color(0xFFE08A2E),
        "历史" to Color(0xFF8D6E63),
        "地理" to Color(0xFF2E9BC6),
        "政治" to Color(0xFFC2185B),
        "其他" to Color(0xFF6B7A90)
    )

    fun colorOf(name: String?): Color = colors[name] ?: Color(0xFF4F7DF3)

    fun argbOf(name: String?): Int {
        val color = colorOf(name)
        return (255 shl 24) or
            ((color.red * 255).toInt() shl 16) or
            ((color.green * 255).toInt() shl 8) or
            (color.blue * 255).toInt()
    }
}
