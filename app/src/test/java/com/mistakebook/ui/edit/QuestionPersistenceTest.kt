package com.mistakebook.ui.edit

import com.mistakebook.data.local.entities.Question
import com.mistakebook.domain.ErrorReason
import com.mistakebook.domain.MasteryStatus
import com.mistakebook.domain.QuestionDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionPersistenceTest {

    @Test
    fun `editing persists original image and figure paths`() {
        val updated = question().applyEditedQuestion(
            EditableDraft(
                imagePath = "new-original.jpg",
                figurePaths = listOf("figure-1.jpg", "figure-2.jpg"),
                notebookId = 22,
                stem = "edited stem"
            ),
            subjectId = 33
        )

        assertEquals("new-original.jpg", updated.imagePath)
        assertEquals("[\"figure-1.jpg\",\"figure-2.jpg\"]", updated.figurePathsJson)
        assertEquals(22L, updated.notebookId)
        assertEquals(33L, updated.subjectId)
    }

    @Test
    fun `recognition overwrite replaces markdown and notebook`() {
        val updated = question().copy(note = "keep note", status = MasteryStatus.MASTERED)
            .applyRecognitionDraft(
                QuestionDraft(
                    imagePath = "recognized.jpg",
                    mineruMarkdown = "new recognition markdown",
                    notebookId = 44,
                    stem = "new stem"
                )
            )

        assertEquals("new recognition markdown", updated.mineruMarkdown)
        assertEquals(44L, updated.notebookId)
        assertEquals("keep note", updated.note)
        assertEquals(MasteryStatus.MASTERED, updated.status)
    }

    private fun question() = Question(
        id = 7,
        subjectId = 2,
        notebookId = 3,
        imagePath = "old.jpg",
        figurePathsJson = "[]",
        mineruMarkdown = "old markdown",
        stem = "old stem",
        optionsJson = "[]",
        answer = "old answer",
        analysis = "old analysis",
        knowledgePointsJson = "[]",
        errorReason = ErrorReason.OTHER,
        difficulty = 3,
        status = MasteryStatus.ACTIVE,
        createdAt = 1,
        updatedAt = 2
    )
}
