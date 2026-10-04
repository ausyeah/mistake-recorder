package com.mistakebook.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mistakebook.domain.ReviewResult

/**
 * 一次复习结果记录：用于详情页时间线与统计。
 */
@Entity(
    tableName = "review_logs",
    indices = [Index("questionId")]
)
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val reviewedAt: Long,
    val stageIndex: Int,
    val result: ReviewResult
)
