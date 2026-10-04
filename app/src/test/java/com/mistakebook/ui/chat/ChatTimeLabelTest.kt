package com.mistakebook.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ChatTimeLabelTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun millis(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    /** 固定「现在」= 2026-03-15 12:00，避免测试随真实时间漂移。 */
    private val now = millis(LocalDateTime.of(2026, 3, 15, 12, 0))

    @Test
    fun `今天`() {
        val at = millis(LocalDateTime.of(2026, 3, 15, 0, 1))
        assertEquals(ChatTimeLabel.Kind.TODAY, ChatTimeLabel.kindOf(at, now, zone))
    }

    @Test
    fun `今天但已经过了 23 小时也还是今天`() {
        // 反向用例：按 24 小时算会判成「昨天」。
        // 23:00 到次日 01:00 只隔 2 小时，但跨了自然日。
        val at = millis(LocalDateTime.of(2026, 3, 14, 23, 0))
        assertEquals(ChatTimeLabel.Kind.YESTERDAY, ChatTimeLabel.kindOf(at, now, zone))
    }

    @Test
    fun `昨天`() {
        val at = millis(LocalDateTime.of(2026, 3, 14, 23, 59))
        assertEquals(ChatTimeLabel.Kind.YESTERDAY, ChatTimeLabel.kindOf(at, now, zone))
    }

    @Test
    fun `前天`() {
        val at = millis(LocalDateTime.of(2026, 3, 13, 8, 0))
        assertEquals(ChatTimeLabel.Kind.BEFORE_YESTERDAY, ChatTimeLabel.kindOf(at, now, zone))
    }

    @Test
    fun `三天前到六天前显示 N 天前`() {
        (3..6).forEach { days ->
            val at = millis(LocalDateTime.of(2026, 3, 15 - days, 10, 0))
            assertEquals("相隔 $days 天", ChatTimeLabel.Kind.DAYS_AGO, ChatTimeLabel.kindOf(at, now, zone))
        }
    }

    @Test
    fun `七天前就显示日期`() {
        val at = millis(LocalDateTime.of(2026, 3, 8, 10, 0))
        assertEquals(ChatTimeLabel.Kind.DATE, ChatTimeLabel.kindOf(at, now, zone))
    }

    @Test
    fun `跨月也算对`() {
        val at = millis(LocalDateTime.of(2026, 2, 28, 10, 0))
        assertEquals(15, ChatTimeLabel.daysBetween(at, now, zone))
        assertEquals(ChatTimeLabel.Kind.DATE, ChatTimeLabel.kindOf(at, now, zone))
    }

    @Test
    fun `跨年也算对`() {
        val at = millis(LocalDateTime.of(2025, 12, 31, 10, 0))
        assertEquals(74, ChatTimeLabel.daysBetween(at, now, zone))
    }

    @Test
    fun `设备时间被改到未来归为今天`() {
        // 反向用例：显示「-1 天前」既难看又让人以为程序坏了。
        val future = millis(LocalDateTime.of(2026, 3, 16, 10, 0))
        assertEquals(ChatTimeLabel.Kind.TODAY, ChatTimeLabel.kindOf(future, now, zone))
    }

    @Test
    fun `同一时刻的天数差为零`() {
        assertEquals(0, ChatTimeLabel.daysBetween(now, now, zone))
    }

    @Test
    fun `夏令时切换日不会算成同一天`() {
        // 美东 2026-03-08 夏令时开始。当地 00:30 与前一日 23:30 只隔 1 小时。
        val dst = ZoneId.of("America/New_York")
        val nowDst = LocalDateTime.of(2026, 3, 9, 12, 0).atZone(dst).toInstant().toEpochMilli()
        val prevNight = LocalDateTime.of(2026, 3, 8, 23, 30).atZone(dst).toInstant().toEpochMilli()
        assertEquals(1, ChatTimeLabel.daysBetween(prevNight, nowDst, dst))
        assertEquals(ChatTimeLabel.Kind.YESTERDAY, ChatTimeLabel.kindOf(prevNight, nowDst, dst))
    }

    @Test
    fun `闰日 2 月 29 日能正确处理`() {
        val leap = millis(LocalDateTime.of(2024, 2, 29, 10, 0))
        val reference = LocalDateTime.of(2024, 3, 1, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(1, ChatTimeLabel.daysBetween(leap, reference, zone))
    }

    @Test
    fun `daysBetween 用的是本地日期而非 UTC`() {
        // 同一毫秒在 UTC 与东八区属于不同日期，判据必须是本地日期
        val date = LocalDate.of(2026, 3, 15)
        val at = date.atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(0, ChatTimeLabel.daysBetween(at, at, zone))
    }
}
