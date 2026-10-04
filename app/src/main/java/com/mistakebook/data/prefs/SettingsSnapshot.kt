package com.mistakebook.data.prefs

/**
 * 生效中的设置快照：SettingsStore 每次发射都会重新拼一份。
 *
 * 读取优先级：用户填写的值 > buildConfig 预填（仅 debug）> 内置常量。
 */
data class SettingsSnapshot(
    val mineruKey: String = "",
    val llmProfiles: List<LlmProfile> = emptyList(),
    val activeProfileId: String? = null,
    val attachOriginalImage: Boolean = true,
    val imageLongEdgePx: Int = 1280,
    val printIncludeImage: Boolean = false,
    val printShowAnswer: Boolean = false,
    val printBlankRedo: Boolean = true,
    val printBlankHeightPt: Int = 100,
    /** 导入/拍照后做灰度化 + 自适应对比度增强，显著提升 MinerU 识别率。 */
    val enhancePhotos: Boolean = true,
    val reviewReminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0
) {
    // ===== 以下三项已固定，不再暴露给用户 =====
    //
    // 原来是可配置项，实测下来「唯一正确」的组合就是这三个值：
    // - 模型版本固定 vlm：pipeline 模式对中文手写作业的表格/公式识别明显更差，
    //   而 vlm 对纯英文题面同样能自适应，不存在「切英文题要改设置」的问题。
    // - 语言固定 ch：MinerU 的 language 参数是「首选语言」而非「限定语言」，
    //   纯英文内容照常识别，不需要切 en。
    // - 强制 OCR 固定开启：输入几乎全是拍照/扫描的作业，照版识别才会逐字提取；
    //   关掉会直接漏掉整页文字。
    //
    // 保留为属性（而不是直接删掉）是为了让 [RecognitionEngine] 的调用点
    // 保持可读，值改主意时只改这一处。

    /** MinerU 模型版本。固定 vlm。 */
    val mineruModelVersion: String = MINERU_MODEL_VLM

    /** OCR 首选语言。固定 ch（对纯英文内容同样自适应）。 */
    val ocrLanguage: String = OCR_LANG_ZH

    /** 是否强制照版识别。固定开启。 */
    val forceOcr: Boolean = true

    val activeProfile: LlmProfile?
        get() = llmProfiles.firstOrNull { it.id == activeProfileId } ?: llmProfiles.firstOrNull()

    val mineruConfigured: Boolean get() = mineruKey.isNotBlank()

    val llmConfigured: Boolean get() = activeProfile?.isConfigured() == true

    companion object {
        const val MINERU_MODEL_VLM = "vlm"
        const val OCR_LANG_ZH = "ch"
    }
}
