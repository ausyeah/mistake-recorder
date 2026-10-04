package com.mistakebook.data.repos

import com.mistakebook.data.local.SubjectDao
import com.mistakebook.data.local.TagDao
import com.mistakebook.data.local.entities.QuestionTagCrossRef
import com.mistakebook.data.local.entities.Subject
import kotlinx.coroutines.flow.Flow

/** 知识点标签与自建学科的写入入口。 */
class TagRepository(
    private val tagDao: TagDao,
    private val subjectDao: SubjectDao
) {

    fun observeAll(): Flow<List<com.mistakebook.data.local.entities.Tag>> = tagDao.observeAll()

    suspend fun ensureSubject(name: String): Subject {
        subjectDao.findByName(name.trim())?.let { return it }
        val id = subjectDao.insert(
            com.mistakebook.data.local.entities.Subject(
                name = name.trim(),
                sortOrder = subjectDao.count(),
                colorArgb = com.mistakebook.data.local.entities.Subject.DEFAULT_COLOR
            )
        )
        return subjectDao.findById(id)!!
    }

    /** 用新的知识点列表整体替换该题的标签关联。 */
    suspend fun replaceQuestionTags(questionId: Long, knowledgePoints: List<String>) {
        tagDao.clearQuestionTags(questionId)
        knowledgePoints.forEach { point ->
            val tagId = tagDao.ensureTag(point)
            tagDao.insertCrossRef(QuestionTagCrossRef(questionId, tagId))
        }
    }
}
