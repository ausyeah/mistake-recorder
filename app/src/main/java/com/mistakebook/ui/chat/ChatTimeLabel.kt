package com.mistakebook.ui.chat

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 会话列表的时间标签。**纯逻辑，可单测。**
 *
 * 单独抽出来是因为它有两个容易写错的地方：
 * 「昨天」要按**自然日**算而不是按 24 小时算；
 * 跨时区/夏令时下用毫秒差算会差一天。
 */
object ChatTimeLabel {

    enum class Kind {
        TODAY,
        YESTERDAY,
        BEFORE_YESTERDAY,

        /** 2~6 天前。 */
        DAYS_AGO,

        /** 更早，显示日期。 */
        DATE
    }

    /**
     * @param updatedAt 会话最后活动时间（epoch millis）。
     * @param now 当前时间，**由调用方传入**便于测试固定。
     */
    fun kindOf(
        updatedAt: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Kind {
        val days = daysBetween(updatedAt, now, zone)
        return when {
            // 未来时间（设备时间被改过）归到「今天」，不要显示「-1 天前」
            days <= 0 -> Kind.TODAY
            days == 1 -> Kind.YESTERDAY
            days == 2 -> Kind.BEFORE_YESTERDAY
            days < DAYS_AGO_LIMIT -> Kind.DAYS_AGO
            else -> Kind.DATE
        }
    }

    /**
     * 自然日之差。
     *
     * 用 [LocalDate] 相减而不是 `(now - then) / 86400000`：
     * 后者在夏令时切换那天会算出 0.958 天进而取整成 0，
     * 于是「昨天下午的对话」被标成「今天」。
     */
    fun daysBetween(updatedAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val then = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return (today.toEpochDay() - then.toEpochDay()).toInt()
    }

    /** 超过这个天数就显示具体日期，不再显示「N 天前」。 */
    const val DAYS_AGO_LIMIT = 7
}
