package com.mistakebook.print

import com.mistakebook.data.local.entities.Question
import com.mistakebook.domain.ErrorReason
import com.mistakebook.domain.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出数据模型与分词切分回归测试。
 */
class ExportModelTest {

    @Test
    fun `pdf format metadata matches spec`() {
        val pdf = ExportFormat.PDF
        assertEquals("pdf", pdf.extension)
        assertEquals("application/pdf", pdf.mimeType)
        val name = pdf.fileName("20261006_120000")
        assertEquals("错题本_20261006_120000", name)
    }

    @Test
    fun `tokenizeWithMath parses inline and display math accurately`() {
        val input = "已知函数 \$f(x) = x^2 + 1\$，求证：\n\$\$\\lim_{x \\to 0} f(x) = 1\$\$"
        val tokens = tokenizeWithMath(input)

        // 验证含有行内公式
        val inlineToken = tokens.filterIsInstance<RichToken.MathToken>().firstOrNull { !it.display }
        assertTrue("必须切出行内公式", inlineToken != null)
        assertEquals("f(x) = x^2 + 1", inlineToken?.latex)

        // 验证含有独立显示公式
        val displayToken = tokens.filterIsInstance<RichToken.MathToken>().firstOrNull { it.display }
        assertTrue("必须切出独立块级公式", displayToken != null)
        assertEquals("\\lim_{x \\to 0} f(x) = 1", displayToken?.latex)
    }

    @Test
    fun `cardBuilder respects blankHeightPt and pure redo mode`() {
        val question = Question(
            id = 1L,
            subjectId = 10L,
            imagePath = "test.jpg",
            mineruMarkdown = "",
            stem = "求导数 \$y = \\ln x\$",
            optionsJson = "[]",
            answer = "\$y' = \\frac{1}{x}\$",
            analysis = "直接套用求导公式",
            knowledgePointsJson = "[]",
            errorReason = ErrorReason.CALCULATION,
            difficulty = 3,
            status = MasteryStatus.ACTIVE,
            createdAt = 1L,
            updatedAt = 1L
        )
        val options = ExportOptions(
            includeImage = false,
            showAnswer = false,
            blankRedoMode = true,
            blankHeightPt = 120
        )
        val builder = ExportCardBuilder(mapOf(10L to "数学"))
        val card = builder.build(question, options, 1)

        assertEquals(1, card.number)
        assertEquals(1L, card.questionId)
        assertEquals("1.", card.header)
        assertEquals("数学", card.subjectName)
        assertEquals(120, card.blankHeightPt)
        // 答案与解析在 showAnswer = false 时不展示
        assertFalse(card.hasAnswer)
        assertFalse(card.hasAnalysis)
    }
}
