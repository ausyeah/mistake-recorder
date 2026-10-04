package com.mistakebook.domain

/** 错因分类 */
enum class ErrorReason(val label: String) {
    CONCEPT("概念不清"),
    CALCULATION("计算失误"),
    READING("审题错误"),
    NO_IDEA("思路不会"),
    CARELESS("粗心遗漏"),
    OTHER("其他");

    companion object {
        fun fromNameOrDefault(value: String?): ErrorReason =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/**
 * 掌握状态：只剩「会不会了」两态。
 * 原来的 REVIEWING（复习中）与艾宾浩斯轮次过于鸡肋，标签一多反而干扰观感，
 * 详见 docs/DECISIONS.md。旧值仍可从库里读出（不迁移），映射到未掌握。
 */
enum class MasteryStatus(val label: String) {
    ACTIVE("未掌握"),
    MASTERED("已掌握");

    companion object {
        fun fromNameOrDefault(value: String?): MasteryStatus =
            entries.firstOrNull { it.name == value } ?: ACTIVE
    }
}

/** 识别任务状态 */
enum class TaskStatus {
    PENDING,
    UPLOADING,
    PARSING,
    LLM,
    DONE,
    FAILED
}

/**
 * 复习结果。界面上只暴露「会 / 不会」两态，由 MasteredSwitch 使用。
 * 三态枚举保留是为了兼容库里已有的 review_logs 记录（不迁移 schema）。
 */
enum class ReviewResult(val label: String) {
    CORRECT("会"),
    VAGUE("模糊"),
    WRONG("不会")
}
