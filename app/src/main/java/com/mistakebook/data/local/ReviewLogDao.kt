package com.mistakebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mistakebook.data.local.entities.ReviewLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {

    @Insert
    suspend fun insert(log: ReviewLog): Long

    @Query("SELECT * FROM review_logs WHERE questionId = :questionId ORDER BY reviewedAt DESC")
    fun observeOf(questionId: Long): Flow<List<ReviewLog>>

    @Query("SELECT COUNT(*) FROM review_logs WHERE questionId = :questionId")
    suspend fun countOf(questionId: Long): Int

    @Query("DELETE FROM review_logs WHERE questionId = :questionId")
    suspend fun clearOf(questionId: Long)
}
