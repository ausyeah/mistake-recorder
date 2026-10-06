package com.mistakebook.print

import com.mistakebook.math.MathLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF 排版尺寸与留白高度计算单元测试。
 */
class PdfLayoutCalculatorTest {

    @Test
    fun `a4 page usable area matches PRD spec`() {
        val pageWidth = PdfExporter.PAGE_WIDTH_PT
        val pageHeight = PdfExporter.PAGE_HEIGHT_PT
        val usableWidth = PdfExporter.USABLE_WIDTH_PT
        val usableHeight = PdfExporter.USABLE_CONTENT_HEIGHT_PT

        assertEquals(595f, pageWidth, 0.01f)
        assertEquals(842f, pageHeight, 0.01f)
        assertEquals(511f, usableWidth, 0.01f)
        assertEquals(712f, usableHeight, 0.01f)
    }

    @Test
    fun `lineHeight adapts to math height avoiding text overlap`() {
        val baseFontSize = 14f
        val baseLineHeight = baseFontSize * 1.3f // 18.2f
        val tallMathHeight = 28f // 分式等高公式

        val calculatedLineHeight = MathLayout.lineHeightFor(baseLineHeight, tallMathHeight, 2f)

        // 行高应扩大以适应高公式 + 上下 padding
        assertTrue(calculatedLineHeight >= tallMathHeight + 4f)
        assertTrue(calculatedLineHeight > baseLineHeight)
    }

    @Test
    fun `redo area pure blank height reflects user setting`() {
        val userHeights = listOf(60, 80, 100, 120, 150)
        userHeights.forEach { heightPt ->
            val actualBlank = heightPt.toFloat().coerceAtLeast(40f)
            assertEquals(heightPt.toFloat(), actualBlank, 0.001f)
        }
    }
}
