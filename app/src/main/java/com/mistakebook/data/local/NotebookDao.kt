package com.mistakebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mistakebook.data.local.entities.Notebook
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {

    @Query("SELECT * FROM notebooks ORDER BY isDefault DESC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks ORDER BY isDefault DESC, sortOrder ASC, id ASC")
    suspend fun listAll(): List<Notebook>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun findById(id: Long): Notebook?

    @Query("SELECT * FROM notebooks WHERE isDefault = 1 LIMIT 1")
    suspend fun findDefault(): Notebook?

    @Query("SELECT * FROM notebooks WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Notebook?

    @Query("SELECT COUNT(*) FROM notebooks")
    suspend fun count(): Int

    @Insert
    suspend fun insert(notebook: Notebook): Long

    @Update
    suspend fun update(notebook: Notebook)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 把某个错题本下的题目转移到目标错题本（删本前先搬题，避免题目失去归属）。 */
    @Query("UPDATE questions SET notebookId = :targetId, updatedAt = :now WHERE notebookId = :fromId")
    suspend fun moveQuestions(fromId: Long, targetId: Long, now: Long)

    /** 历史题目（notebookId 为 NULL）归入默认错题本。 */
    @Query("UPDATE questions SET notebookId = :defaultId WHERE notebookId IS NULL")
    suspend fun assignOrphansToDefault(defaultId: Long)

    /** 单题改归属。 */
    @Query("UPDATE questions SET notebookId = :notebookId, updatedAt = :now WHERE id = :questionId")
    suspend fun reassignQuestion(questionId: Long, notebookId: Long?, now: Long)

    /** 错题本下的题目数（含软删的？否——只统计未删的，和列表口径一致）。 */
    @Query("SELECT COUNT(*) FROM questions WHERE notebookId = :notebookId AND deletedAt IS NULL")
    fun observeCount(notebookId: Long): Flow<Int>
}
