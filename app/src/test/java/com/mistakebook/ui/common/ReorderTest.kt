package com.mistakebook.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {
    @Test
    fun moveItemReordersWithinListAndLeavesInvalidMovesAlone() {
        assertEquals(listOf(2, 3, 1), moveItem(listOf(1, 2, 3), 0, 2))
        assertEquals(listOf(1, 2, 3), moveItem(listOf(1, 2, 3), -1, 2))
    }

    @Test
    fun orderByIdsKeepsUnlistedItemsStableAtTheEnd() {
        val result = orderByIds(listOf(1L, 2L, 3L, 4L), listOf(3L, 1L)) { it }
        assertEquals(listOf(3L, 1L, 2L, 4L), result)
    }
}
