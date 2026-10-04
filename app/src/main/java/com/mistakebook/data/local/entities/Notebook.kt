package com.mistakebook.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 错题本：用户自建的题目分类。
 *
 * ## 和「学科」的关系：正交，不是替代
 * 学科回答「这是什么题」（数学 / 通信原理），错题本回答「我把它归到哪」，
 * 所以两个字段并存、互不覆盖。用户想按学科筛就按学科筛，想按「高数竞赛」
 * 「期末冲刺」这种自己的规划筛就用错题本。
 *
 * ## 为什么 notebookId 可空
 * 历史题目（以及任何没指定错题本的题目）都归到「默认错题本」。
 * 数据库里存 null 而不是硬塞一个 id，是为了让「没有错题本」这个状态
 * 和「默认错题本」在数据上可区分——迁移过来的老题目不该被假装成
 * 用户主动归过类。
 */
@Entity(
    tableName = "notebooks",
    indices = [Index("sortOrder")]
)
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    /**
     * 默认错题本。同一时刻至多一个。
     * 标记出来是为了两件事：新建题目时默认落进去；
     * 删除错题本时把题目转移到它，而不是让题目失去归属。
     */
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val DEFAULT_NAME = "默认错题本"
    }
}
