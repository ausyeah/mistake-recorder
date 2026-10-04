package com.mistakebook.data.repos

import com.mistakebook.data.local.NotebookDao
import com.mistakebook.data.local.entities.Notebook
import kotlinx.coroutines.flow.Flow

/**
 * 错题本读写。
 *
 * 三条不变量：
 * 1. 任何时候都至少存在一个默认错题本（[seedDefault] 幂等保证）；
 * 2. 题目永远有归属——删错题本前先搬到目标本，不会留下孤儿；
 * 3. 默认错题本不可删除，因为它同时是「删本时的接盘者」。
 */
class NotebookRepository(private val dao: NotebookDao) {

    fun observeAll(): Flow<List<Notebook>> = dao.observeAll()

    suspend fun listAll(): List<Notebook> = dao.listAll()

    suspend fun findById(id: Long): Notebook? = dao.findById(id)

    /**
     * 播种默认错题本，并把历史题目（notebookId 为空）归入其中。
     *
     * 幂等：已存在就只补孤儿归属。每次启动都调，这样即使某条路径漏了赋值，
     * 也不会有题目长期挂在「未归类」状态。
     */
    suspend fun seedDefault() {
        val now = System.currentTimeMillis()
        var default = dao.findDefault()
        if (default == null) {
            // 「默认错题本」这个名字被用户自己建过但没标记为默认时，认它当默认，
            // 免得凭空冒出两个同名本
            default = dao.findByName(Notebook.DEFAULT_NAME) ?: run {
                val id = dao.insert(
                    Notebook(
                        name = Notebook.DEFAULT_NAME,
                        sortOrder = 0,
                        isDefault = true,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                dao.findById(id)
            }
            if (default != null && !default.isDefault) {
                dao.update(default.copy(isDefault = true, updatedAt = now))
            }
        }
        default?.let { dao.assignOrphansToDefault(it.id) }
    }

    suspend fun defaultId(): Long? = dao.findDefault()?.id

    /** 新建错题本；重名自动加后缀，不报错也不覆盖。 */
    suspend fun create(name: String): Notebook {
        val now = System.currentTimeMillis()
        val clean = name.trim().ifBlank { "新错题本" }
        val unique = uniqueName(clean)
        val id = dao.insert(
            Notebook(
                name = unique,
                sortOrder = nextSortOrder(),
                isDefault = false,
                createdAt = now,
                updatedAt = now
            )
        )
        return dao.findById(id)!!
    }

    suspend fun rename(id: Long, name: String) {
        val notebook = dao.findById(id) ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean == notebook.name) return
        dao.update(notebook.copy(name = uniqueName(clean), updatedAt = System.currentTimeMillis()))
    }

    /**
     * 删除错题本。
     *
     * 题目**不跟着删**，而是搬到默认错题本。
     * 「删除一个分类」的合理预期是「这些题还在，只是不在这个分类下了」，
     * 静默连带删掉几十道错题是不可接受的。
     */
    suspend fun delete(id: Long) {
        val notebook = dao.findById(id) ?: return
        if (notebook.isDefault) return
        val now = System.currentTimeMillis()
        val fallback = dao.findDefault()?.id ?: return
        dao.moveQuestions(id, fallback, now)
        dao.deleteById(id)
    }

    suspend fun assignQuestion(questionId: Long, notebookId: Long?) {
        dao.reassignQuestion(questionId, notebookId, System.currentTimeMillis())
    }

    private suspend fun uniqueName(base: String): String {
        if (dao.findByName(base) == null) return base
        var index = 2
        while (dao.findByName("$base $index") != null) index++
        return "$base $index"
    }

    private suspend fun nextSortOrder(): Int =
        (dao.listAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
}
