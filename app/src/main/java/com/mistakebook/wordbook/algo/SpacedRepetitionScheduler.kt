package com.mistakebook.wordbook.algo

/**
 * 0: 遗忘, 1: 模糊, 2: 认识, 3: 熟练
 */
data class ScheduleResult(
    val newLevel: Int,
    val newReps: Int,
    val newLapses: Int,
    val nextReviewAt: Long,
    val isMastered: Boolean,
    val intervalDays: Int
)

class SpacedRepetitionScheduler {
    
    // Rating constants
    companion object {
        const val RATING_FORGET = 0
        const val RATING_BLURRY = 1
        const val RATING_KNOW = 2
        const val RATING_MASTER = 3
        
        const val MINUTE_IN_MS = 60 * 1000L
        const val DAY_IN_MS = 24 * 60 * MINUTE_IN_MS
    }

    // Interval days for Rating 2 (KNOW) based on repetitions
    private val knowIntervals = listOf(1, 3, 7, 15, 30)

    fun schedule(
        rating: Int,
        currentLevel: Int,
        currentReps: Int,
        currentLapses: Int,
        nowMs: Long = System.currentTimeMillis()
    ): ScheduleResult {
        return when (rating) {
            RATING_FORGET -> {
                ScheduleResult(
                    newLevel = 0,
                    newReps = 0,
                    newLapses = currentLapses + 1,
                    nextReviewAt = nowMs + 10 * MINUTE_IN_MS,
                    isMastered = false,
                    intervalDays = 0
                )
            }
            RATING_BLURRY -> {
                ScheduleResult(
                    newLevel = Math.max(1, currentLevel),
                    newReps = 1,
                    newLapses = currentLapses,
                    nextReviewAt = nowMs + DAY_IN_MS,
                    isMastered = false,
                    intervalDays = 1
                )
            }
            RATING_KNOW -> {
                val newReps = currentReps + 1
                // intervalDays calculation based on newReps
                val intervalDays = if (newReps <= knowIntervals.size) {
                    knowIntervals[newReps - 1]
                } else {
                    knowIntervals.last()
                }
                
                ScheduleResult(
                    newLevel = Math.max(2, currentLevel + 1),
                    newReps = newReps,
                    newLapses = currentLapses,
                    nextReviewAt = nowMs + intervalDays * DAY_IN_MS,
                    isMastered = false,
                    intervalDays = intervalDays
                )
            }
            RATING_MASTER -> {
                ScheduleResult(
                    newLevel = 3,
                    newReps = currentReps + 1,
                    newLapses = currentLapses,
                    nextReviewAt = nowMs + 60 * DAY_IN_MS,
                    isMastered = true,
                    intervalDays = 60
                )
            }
            else -> throw IllegalArgumentException("Invalid rating: $rating")
        }
    }

    interface WordProgress {
        val nextReviewAt: Long
        val isMastered: Boolean
    }

    fun isDue(progress: WordProgress, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (progress.isMastered) return false
        return nowMs >= progress.nextReviewAt
    }

    fun calculateReviewUrgency(progress: WordProgress, nowMs: Long = System.currentTimeMillis()): Float {
        if (progress.isMastered) return 0f
        val timeOverdue = nowMs - progress.nextReviewAt
        return if (timeOverdue < 0) {
            0f
        } else {
            // Further overdue means higher urgency.
            // Using timeOverdue in hours or days to scale.
            1.0f + (timeOverdue.toFloat() / DAY_IN_MS.toFloat())
        }
    }
}
