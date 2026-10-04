package com.mistakebook.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakebook.data.local.entities.Question
import com.mistakebook.data.local.entities.Subject
import com.mistakebook.data.local.figurePaths
import com.mistakebook.data.local.knowledgePoints
import com.mistakebook.data.local.options
import com.mistakebook.di.AppContainer
import com.mistakebook.domain.ErrorReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuestionEditUiState(
    val loading: Boolean = true,
    val draft: EditableDraft? = null,
    val subjects: List<Subject> = emptyList(),
    val notebooks: List<com.mistakebook.data.local.entities.Notebook> = emptyList(),
    val saved: Boolean = false
) {
    val canSave: Boolean get() = draft?.stem?.isNotBlank() == true
}

/** 编辑已入库的题目（详情页进入）。 */
class QuestionEditViewModel(
    private val container: AppContainer,
    private val questionId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionEditUiState())
    val uiState: StateFlow<QuestionEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val question = container.questionRepository.findById(questionId)
            val subjects = container.subjectRepository.observeAll().first()
            val notebooks = container.notebookRepository.observeAll().first()
            if (question == null) {
                _uiState.value = QuestionEditUiState(
                    loading = false,
                    subjects = subjects,
                    notebooks = notebooks
                )
                return@launch
            }
            _uiState.value = QuestionEditUiState(
                loading = false,
                subjects = subjects,
                notebooks = notebooks,
                draft = EditableDraft(
                    imagePath = question.imagePath,
                    figurePaths = question.figurePaths,
                    title = question.title,
                    subjectName = subjects.firstOrNull { it.id == question.subjectId }?.name.orEmpty(),
                    subjectId = question.subjectId,
                    notebookId = question.notebookId,
                    stem = question.stem,
                    options = question.options,
                    answer = question.answer,
                    analysis = question.analysis,
                    knowledgePoints = question.knowledgePoints,
                    errorReason = question.errorReason,
                    difficulty = question.difficulty,
                    note = question.note
                )
            )
        }
    }

    fun update(transform: (EditableDraft) -> EditableDraft) {
        val draft = _uiState.value.draft ?: return
        _uiState.value = _uiState.value.copy(draft = transform(draft))
    }

    fun setStem(value: String) = update { it.copy(stem = value) }

    fun setAnswer(value: String) = update { it.copy(answer = value) }

    fun setAnalysis(value: String) = update { it.copy(analysis = value) }

    fun setNote(value: String) = update { it.copy(note = value) }

    fun setSubject(id: Long?) = update { draft ->
        val name = subjectsName(id)
        draft.copy(subjectId = id, subjectName = name)
    }

    fun setTitle(value: String) = update { it.copy(title = value) }

    fun setNotebook(value: Long?) = update { it.copy(notebookId = value) }

    fun setManualSubject(value: String) = update { it.copy(manualSubject = value) }

    /** 原图重新裁剪后替换。 */
    fun setImagePath(value: String) = update { it.copy(imagePath = value) }

    fun addFigure(path: String) = update { draft ->
        if (draft.figurePaths.contains(path)) draft
        else draft.copy(figurePaths = draft.figurePaths + path)
    }

    fun removeFigure(path: String) = update { it.copy(figurePaths = it.figurePaths - path) }

    private fun subjectsName(id: Long?): String =
        _uiState.value.subjects.firstOrNull { it.id == id }?.name.orEmpty()

    fun setReason(reason: ErrorReason) = update { it.copy(errorReason = reason) }

    fun setDifficulty(value: Int) = update { it.copy(difficulty = value.coerceIn(1, 5)) }

    fun addKnowledgePoint(point: String) {
        val clean = point.trim()
        if (clean.isEmpty()) return
        update { draft ->
            if (draft.knowledgePoints.contains(clean)) draft
            else draft.copy(knowledgePoints = draft.knowledgePoints + clean)
        }
    }

    fun removeKnowledgePoint(point: String) =
        update { it.copy(knowledgePoints = it.knowledgePoints - point) }

    fun addOption() = update {
        it.copy(options = it.options + com.mistakebook.domain.Option(text = ""))
    }

    fun updateOption(index: Int, text: String) = update { draft ->
        draft.copy(
            options = draft.options.mapIndexed { i, option ->
                if (i == index) option.copy(text = text) else option
            }
        )
    }

    fun removeOption(index: Int) = update { draft ->
        draft.copy(
            options = draft.options
                .filterIndexed { i, _ -> i != index }
                .mapIndexed { i, option ->
                    option.copy(label = com.mistakebook.domain.labelFor(i))
                }
        )
    }

    fun save(onSaved: () -> Unit) {
        val draft = _uiState.value.draft ?: return
        if (draft.stem.isBlank()) return
        viewModelScope.launch {
            val question = container.questionRepository.findById(questionId) ?: return@launch
            // 手填的学科优先：识别失败时是空串，用户填了就用它
            val manualName = draft.manualSubject.trim()
            val subjectId = if (manualName.isNotBlank()) {
                container.tagRepository.ensureSubject(manualName).id
            } else {
                draft.subjectId ?: question.subjectId
            }
            val updated = question.applyEditedQuestion(draft, subjectId)
            container.questionRepository.update(updated, draft.knowledgePoints)
            _uiState.value = _uiState.value.copy(saved = true)
            onSaved()
        }
    }
}
