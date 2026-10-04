package com.mistakebook.data.repos

import com.mistakebook.data.local.SubjectDao
import com.mistakebook.data.local.entities.Subject
import kotlinx.coroutines.flow.Flow

class SubjectRepository(private val dao: SubjectDao) {

    fun observeAll(): Flow<List<Subject>> = dao.observeAll()

    /**
     * 播种预置学科（幂等：已存在的不动）。
     * AppContainer 启动时调一次，学科下拉框因此总有「数学 / 通信原理」可选。
     */
    suspend fun seedPresets() {
        Subject.PRESETS.forEachIndexed { index, (name, color) ->
            if (dao.findByName(name) != null) return@forEachIndexed
            dao.insert(Subject(name = name, sortOrder = index, colorArgb = color))
        }
    }

    suspend fun ensure(name: String): Subject {
        val existing = dao.findByName(name.trim())
        if (existing != null) return existing
        val id = dao.insert(Subject(name = name.trim(), sortOrder = dao.count()))
        return dao.findById(id)!!
    }

    suspend fun create(name: String, colorArgb: Int = Subject.DEFAULT_COLOR): Subject {
        val id = dao.insert(Subject(name = name.trim(), sortOrder = dao.count(), colorArgb = colorArgb))
        return dao.findById(id)!!
    }

    suspend fun update(subject: Subject) = dao.update(subject)
}
