package com.mistakebook.print

import org.junit.Assert.assertEquals
import org.junit.Test

class VocabPrintTest {

    @Test
    fun testPaginationCalculator() {
        // 空列表兜底逻辑 / Empty list fallback
        assertEquals(1, VocabPrintExporter.calculatePages(0, 20))
        
        // Exact division
        assertEquals(2, VocabPrintExporter.calculatePages(40, 20))
        
        // With remainder
        assertEquals(3, VocabPrintExporter.calculatePages(41, 20))
        
        // Less than one page
        assertEquals(1, VocabPrintExporter.calculatePages(15, 20))
    }

    @Test
    fun testRowHeightMeasurement() {
        val pageHeight = 842
        val marginTop = 80f
        val marginBottom = 50f
        
        // row height 40 -> (842 - 130) / 40 = 712 / 40 = 17.8 -> 17 rows
        assertEquals(17, VocabPrintExporter.measureRowsPerPage(pageHeight, marginTop, marginBottom, 40f))
        
        // row height 60 -> 712 / 60 = 11.86 -> 11 rows
        assertEquals(11, VocabPrintExporter.measureRowsPerPage(pageHeight, marginTop, marginBottom, 60f))
    }
}
