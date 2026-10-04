package com.mistakebook.ui.edit

import com.mistakebook.data.local.entities.Question
import com.mistakebook.domain.Option
import com.mistakebook.domain.QuestionDraft
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun Question.applyEditedQuestion(draft: EditableDraft, subjectId: Long?): Question = copy(
    imagePath = draft.imagePath,
    figurePathsJson = Json.encodeToString(draft.figurePaths),
    subjectId = subjectId,
    notebookId = draft.notebookId,
    title = draft.title.trim(),
    stem = draft.stem.trim(),
    optionsJson = Json.encodeToString(ListSerializer(Option.serializer()), draft.options),
    answer = draft.answer.trim(),
    analysis = draft.analysis.trim(),
    errorReason = draft.errorReason,
    difficulty = draft.difficulty,
    note = draft.note.trim()
)

internal fun Question.applyRecognitionDraft(draft: QuestionDraft): Question = copy(
    imagePath = draft.imagePath,
    notebookId = draft.notebookId,
    mineruMarkdown = draft.mineruMarkdown,
    subjectId = draft.subjectId,
    stem = draft.stem,
    optionsJson = Json.encodeToString(ListSerializer(Option.serializer()), draft.options),
    answer = draft.answer,
    analysis = draft.analysis,
    title = draft.title,
    errorReason = draft.errorReason,
    difficulty = draft.difficulty
)
