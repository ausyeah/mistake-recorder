package com.mistakebook.wordbook.algo

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpacedRepetitionSchedulerTest {

    private lateinit var scheduler: SpacedRepetitionScheduler
    
    @Before
    fun setUp() {
        scheduler = SpacedRepetitionScheduler()
    }

    @Test
    fun testForgetRating() {
        val now = 1000000L
        val result = scheduler.schedule(
            rating = SpacedRepetitionScheduler.RATING_FORGET,
            currentLevel = 2,
            currentReps = 3,
            currentLapses = 1,
            nowMs = now
        )
        assertEquals(0, result.newLevel)
        assertEquals(0, result.newReps)
        assertEquals(2, result.newLapses)
        assertEquals(now + 10 * 60 * 1000L, result.nextReviewAt)
        assertFalse(result.isMastered)
        assertEquals(0, result.intervalDays)
    }

    @Test
    fun testBlurryRating() {
        val now = 1000000L
        val result = scheduler.schedule(
            rating = SpacedRepetitionScheduler.RATING_BLURRY,
            currentLevel = 0,
            currentReps = 0,
            currentLapses = 1,
            nowMs = now
        )
        assertTrue(result.newLevel >= 1)
        assertEquals(1, result.newReps)
        assertEquals(1, result.newLapses)
        assertEquals(now + 24 * 60 * 60 * 1000L, result.nextReviewAt)
        assertFalse(result.isMastered)
        assertEquals(1, result.intervalDays)
    }

    @Test
    fun testKnowRatingProgression() {
        val now = 1000000L
        // Rep 1
        var result = scheduler.schedule(SpacedRepetitionScheduler.RATING_KNOW, 0, 0, 0, now)
        assertEquals(1, result.intervalDays)
        assertEquals(now + 1L * 24 * 60 * 60 * 1000L, result.nextReviewAt)
        
        // Rep 2
        result = scheduler.schedule(SpacedRepetitionScheduler.RATING_KNOW, 1, 1, 0, now)
        assertEquals(3, result.intervalDays)
        assertEquals(now + 3L * 24 * 60 * 60 * 1000L, result.nextReviewAt)
        
        // Rep 3
        result = scheduler.schedule(SpacedRepetitionScheduler.RATING_KNOW, 2, 2, 0, now)
        assertEquals(7, result.intervalDays)
        assertEquals(now + 7L * 24 * 60 * 60 * 1000L, result.nextReviewAt)
        
        // Rep 4
        result = scheduler.schedule(SpacedRepetitionScheduler.RATING_KNOW, 2, 3, 0, now)
        assertEquals(15, result.intervalDays)
        assertEquals(now + 15L * 24 * 60 * 60 * 1000L, result.nextReviewAt)
        
        // Rep 5
        result = scheduler.schedule(SpacedRepetitionScheduler.RATING_KNOW, 2, 4, 0, now)
        assertEquals(30, result.intervalDays)
        assertEquals(now + 30L * 24 * 60 * 60 * 1000L, result.nextReviewAt)
        
        // Rep 6
        result = scheduler.schedule(SpacedRepetitionScheduler.RATING_KNOW, 2, 5, 0, now)
        assertEquals(30, result.intervalDays)
        assertEquals(now + 30L * 24 * 60 * 60 * 1000L, result.nextReviewAt)
    }

    @Test
    fun testMasterRating() {
        val now = 1000000L
        val result = scheduler.schedule(
            rating = SpacedRepetitionScheduler.RATING_MASTER,
            currentLevel = 2,
            currentReps = 3,
            currentLapses = 0,
            nowMs = now
        )
        assertEquals(3, result.newLevel)
        assertEquals(4, result.newReps)
        assertEquals(0, result.newLapses)
        assertTrue(result.nextReviewAt >= now + 60L * 24 * 60 * 60 * 1000L)
        assertTrue(result.isMastered)
        assertEquals(60, result.intervalDays)
    }

    @Test
    fun testIsDue() {
        val now = 1000000L
        
        val progressNotDue = object : SpacedRepetitionScheduler.WordProgress {
            override val nextReviewAt: Long = now + 1000L
            override val isMastered: Boolean = false
        }
        assertFalse(scheduler.isDue(progressNotDue, now))

        val progressDue = object : SpacedRepetitionScheduler.WordProgress {
            override val nextReviewAt: Long = now - 1000L
            override val isMastered: Boolean = false
        }
        assertTrue(scheduler.isDue(progressDue, now))

        val progressMastered = object : SpacedRepetitionScheduler.WordProgress {
            override val nextReviewAt: Long = now - 1000L
            override val isMastered: Boolean = true
        }
        assertFalse(scheduler.isDue(progressMastered, now))
    }

    @Test
    fun testCalculateReviewUrgency() {
        val now = 1000000L
        
        val progressNotDue = object : SpacedRepetitionScheduler.WordProgress {
            override val nextReviewAt: Long = now + 1000L
            override val isMastered: Boolean = false
        }
        assertEquals(0f, scheduler.calculateReviewUrgency(progressNotDue, now))

        val progressDue = object : SpacedRepetitionScheduler.WordProgress {
            override val nextReviewAt: Long = now - SpacedRepetitionScheduler.DAY_IN_MS
            override val isMastered: Boolean = false
        }
        val urgency = scheduler.calculateReviewUrgency(progressDue, now)
        assertEquals(2.0f, urgency) // 1.0 + 1 day overdue

        val progressMastered = object : SpacedRepetitionScheduler.WordProgress {
            override val nextReviewAt: Long = now - 1000L
            override val isMastered: Boolean = true
        }
        assertEquals(0f, scheduler.calculateReviewUrgency(progressMastered, now))
    }
}
