package com.mistakebook.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)

    /** epochDay -> yyyy-MM-dd */
    fun epochDay(day: Long?): String =
        if (day == null) "" else LocalDate.ofEpochDay(day).format(dateFormatter)

    /** 相对日期：今天 / 昨天 / N 天前 / yyyy-MM-dd */
    fun relativeDay(day: Long?, today: LocalDate = LocalDate.now()): String {
        if (day == null) return ""
        val target = LocalDate.ofEpochDay(day)
        val diff = today.toEpochDay() - target.toEpochDay()
        return when {
            diff <= 0L -> "今天"
            diff == 1L -> "昨天"
            diff < 7L -> "${diff} 天前"
            diff < 30L -> "${diff / 7} 周前"
            else -> target.format(dateFormatter)
        }
    }

    fun millisToDate(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(dateFormatter)

    /** 题干摘要：去掉换行，最多 [max] 字。 */
    fun stemDigest(stem: String, max: Int = 60): String =
        stem.replace('\n', ' ').trim().let {
            if (it.length <= max) it else it.take(max) + "…"
        }
}
