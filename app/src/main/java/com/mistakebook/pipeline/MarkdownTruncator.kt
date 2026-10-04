package com.mistakebook.pipeline

/**
 * 输入 Markdown 超长截断（PRD 4.2）：超过 12000 字符时，优先保留含公式 `$` 与
 * 图片 `![](` 的行，其余按原顺序拼接后截断。
 */
object MarkdownTruncator {

    const val MAX_CHARS = 12000

    data class Result(val text: String, val truncated: Boolean)

    fun truncate(markdown: String, maxChars: Int = MAX_CHARS): Result {
        if (markdown.length <= maxChars) return Result(markdown, false)
        val priority = StringBuilder()
        val rest = StringBuilder()
        markdown.lineSequence().forEach { line ->
            if (line.contains('$') || line.contains("![](")) {
                priority.append(line).append('\n')
            } else {
                rest.append(line).append('\n')
            }
        }
        val combined = priority.toString() + rest.toString()
        val headroom = maxChars / 2
        val text = if (combined.length <= maxChars) {
            combined
        } else {
            combined.take(headroom) + "\n……（内容过长已截断）……\n" + combined.takeLast(maxChars - headroom)
        }
        return Result(text.trimEnd(), true)
    }
}
