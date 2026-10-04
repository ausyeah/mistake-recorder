package com.mistakebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mistakebook.data.local.entities.QuestionTagCrossRef
import com.mistakebook.data.local.entities.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Tag?

    @Insert
    suspend fun insert(tag: Tag): Long

    @Insert
    suspend fun insertCrossRef(ref: QuestionTagCrossRef)

    @Query("DELETE FROM question_tags WHERE questionId = :questionId")
    suspend fun clearQuestionTags(questionId: Long)

    @Query("SELECT tagId FROM question_tags WHERE questionId = :questionId")
    suspend fun tagIdsOf(questionId: Long): List<Long>

    /** 取出或新建同名标签，返回 tag id。 */
    @Transaction
    suspend fun ensureTag(name: String): Long {
        findByName(name)?.let { return it.id }
        return insert(Tag(name = name.trim()))
    }
}
