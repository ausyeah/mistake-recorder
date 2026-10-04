package com.mistakebook.data.prefs

import kotlinx.serialization.Serializable

/**
 * 一套可保存、可随时切换的大模型接入配置（OpenAI 兼容）。
 *
 * apiKey 只写 EncryptedSharedPreferences，不进 DataStore、不进日志。
 */
@Serializable
data class LlmProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val note: String = ""
) {
    fun displayName(): String = name.ifBlank { model.ifBlank { "未命名配置" } }

    fun isConfigured(): Boolean =
        baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /**
     * 保存前规范化：只填了根域名（如 https://api.example.com）时自动补 /v1，
     * 已经带路径的（如 https://host/api）保持原样。
     */
    fun normalized(): LlmProfile {
        val raw = baseUrl.trim().trimEnd('/')
        val hasPath = raw.removePrefix("https://").removePrefix("http://").contains('/')
        return copy(baseUrl = if (hasPath) raw else "$raw/v1")
    }

    fun chatCompletionsUrl(): String = baseUrl.trimEnd('/') + "/chat/completions"

    fun modelsUrl(): String = baseUrl.trimEnd('/') + "/models"
}
