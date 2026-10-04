package com.mistakebook.ui.home

import com.mistakebook.data.local.entities.Question
import com.mistakebook.domain.ErrorReason
import com.mistakebook.domain.MasteryStatus
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSearchAndDateTest {

    @Test
    fun `due search matches all fields covered by normal search`() {
        val question = Question(
            subjectId = null,
            imagePath = "",
            mineruMarkdown = "",
            stem = "stem token",
            optionsJson = "[option token]",
            answer = "answer token",
            analysis = "analysis token",
            title = "title token",
            knowledgePointsJson = "[]",
            errorReason = ErrorReason.OTHER,
            difficulty = 3,
            status = MasteryStatus.ACTIVE,
            note = "note token",
            createdAt = 0,
            updatedAt = 0
        )

        listOf("stem token", "title token", "answer token", "analysis token", "option token", "note token")
            .forEach { assertTrue("missing search field: $it", matchesHomeSearch(question, it)) }
        assertTrue(matchesHomeSearch(question, "TITLE TOKEN"))
        assertTrue(matchesHomeSearch(question, "  "))
        assertFalse(matchesHomeSearch(question, "not present"))
    }

    @Test
    fun `next local midnight delay uses the active zone`() {
        val now = ZonedDateTime.of(
            2026, 1, 1, 23, 59, 30, 0, ZoneId.of("Asia/Shanghai")
        )
        assertEquals(30_000L, millisUntilNextLocalDay(now))
    }

    @Test
    fun `next local day delay accounts for daylight saving`() {
        val now = ZonedDateTime.of(
            2024, 3, 10, 0, 0, 0, 0, ZoneId.of("America/New_York")
        )
        assertEquals(Duration.ofHours(23).toMillis(), millisUntilNextLocalDay(now))
    }
}
