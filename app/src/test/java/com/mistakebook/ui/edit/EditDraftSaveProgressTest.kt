package com.mistakebook.ui.edit

import com.mistakebook.data.repos.serializeQuestionIdsByIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditDraftSaveProgressTest {

    @Test
    fun `persisted ids restore their draft order`() {
        assertEquals(
            mapOf(0 to 41L, 1 to 73L),
            parseSavedQuestionIds("41,73,99", draftCount = 2)
        )
    }

    @Test
    fun `sparse persisted ids preserve their original draft indexes`() {
        assertEquals(mapOf(2 to 99L), parseSavedQuestionIds(",,99", draftCount = 3))
    }

    @Test
    fun `sparse ids serialize and restore with stable indexes`() {
        val encoded = serializeQuestionIdsByIndex(mapOf(2 to 99L))
        assertEquals(",,99", encoded)
        assertEquals(mapOf(2 to 99L), parseSavedQuestionIds(encoded.orEmpty(), draftCount = 3))
    }

    @Test
    fun `saving a later draft continues with the first unsaved index`() {
        assertEquals(0, nextUnsavedDraftIndex(3, currentIndex = 2, savedIndices = setOf(2)))
    }

    @Test
    fun `already saved drafts are skipped`() {
        assertEquals(1, nextUnsavedDraftIndex(3, currentIndex = 0, savedIndices = setOf(0, 2)))
    }

    @Test
    fun `all saved drafts have no next index`() {
        assertNull(nextUnsavedDraftIndex(3, currentIndex = 1, savedIndices = setOf(0, 1, 2)))
    }
}
