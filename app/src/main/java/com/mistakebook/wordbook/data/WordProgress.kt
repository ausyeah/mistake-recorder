package com.mistakebook.wordbook.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单词学习进度实体。
 *
 * 记录每个单词在当前设备上的掌握度、错题本状态、连对次数等。
 */
@Entity(
    tableName = "word_progress",
    indices = [
        Index("level"),
        Index("isWrongBook"),
        Index("isMastered"),
        Index("everWrong"),
        Index("updatedAt")
    ]
)
data class WordProgress(
    @PrimaryKey
    val word: String,

    /** 掌握等级：0: 未学/生词, 1: 模糊/轻度, 2: 认识/熟练, 3: 掌握/精通 */
    val level: Int = 0,

    /** 连续答对次数（在错题本中连对 3 次即可毕业出库） */
    val reps: Int = 0,

    /** 答错遗忘次数 */
    val lapses: Int = 0,

    /** 累计答对总次数 */
    val correctCount: Int = 0,

    /** 累计答错总次数 */
    val wrongCount: Int = 0,

    /** 是否标记为熟词（☆）。标记为熟词后不再参与日常刷题练习。 */
    val isMastered: Boolean = false,

    /** 是否在错词本中。答错时自动进错题本，连对 3 次或手动移除后出库。 */
    val isWrongBook: Boolean = false,

    /** 是否曾经答错。用于曾错本防遗忘归档。 */
    val everWrong: Boolean = false,

    /** 上次作答时间戳（毫秒） */
    val lastAnsweredAt: Long = 0L,

    /** 下次到期复习时间戳（毫秒） */
    val nextReviewAt: Long = 0L,

    /** 记录更新时间戳（毫秒） */
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 每日打卡与作答流水记录。用于统计近 7 天学习量与正确率。
 */
@Entity(
    tableName = "word_study_log",
    indices = [
        Index("answeredAt"),
        Index("word")
    ]
)
data class WordStudyLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val word: String,
    val isCorrect: Boolean,
    val studyMode: String, // "quiz" or "card"
    val answeredAt: Long = System.currentTimeMillis()
)
