package com.mistakebook.data.prefs

/**
 * buildConfig 预填值的判定。
 *
 * 公开分发包用 `-PprefillKeys=false` 构建，此时 buildConfig 里注入的是
 * [PLACEHOLDER] 而不是真 Key。这个值必须在**所有使用点**被识别成「未配置」，
 * 否则用户会拿着占位符去请求接口，拿到 401 却不知道原因。
 */
object BuildConfigKeys {

    /** 与 `build.gradle.kts` 里的 `KEY_PLACEHOLDER` 保持一致。 */
    const val PLACEHOLDER = "REPLACE_IN_SETTINGS"

    /**
     * 是不是占位符。
     *
     * 除了精确匹配，也识别几种「明显不是 Key」的值：
     * - 空串 / 纯空白
     * - 常见的假 Key 写法（your-api-key、xxx、changeme）
     *
     * 宁可多判成「未配置」也不要误判成「已配置」——
     * 前者用户去设置页填一次就好，后者会拿着无效 Key 去请求然后一头雾水。
     */
    fun isPlaceholder(value: String?): Boolean {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return true
        if (raw.equals(PLACEHOLDER, ignoreCase = true)) return true
        val lower = raw.lowercase()
        return lower in FAKE_KEY_MARKERS
    }

    private val FAKE_KEY_MARKERS = setOf(
        "your-api-key",
        "your_api_key",
        "yourapikey",
        "changeme",
        "change_me",
        "todo",
        "xxx",
        "placeholder",
        "null",
        "undefined"
    )
}
