package com.mistakebook.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文本体检的回归测试。
 *
 * ## 为什么要这么多「不该报警」的用例
 *
 * 一个只会误报的功能等于没有：用户点两次发现全是噪音，就再也不点了。
 * 所以这里**同时**测「该报的报」和「不该报的绝不报」——
 * 后者占了将近一半的用例量。
 *
 * ## 源码里一律用 [math] 而不是直接写 `$`
 *
 * 之前直接写 `"$x$"`，Kotlin 会把它当字符串模板开头；更糟的是它在
 * PowerShell 里被拼接时**静默截断**，改了好几轮才发现是这里。
 * 这条约束就是为了让那类事故不再发生。
 */
class TextAuditTest {

    /** 造一个行内公式。`\$$b\$` = 字面 `$` + b + 字面 `$`。 */
    private fun math(body: String) = "\$$body\$"

    /** 造一个独立成行的公式。 */
    private fun dmath(body: String) = "\$\$$body\$\$"

    private fun auditStem(text: String, failures: Set<String> = emptySet()) =
        TextAudit.audit(TextAudit.Field.STEM, text, renderedFailures = failures)

    // ------------------------------------------------------------ 真实损坏

    /**
     * 真机数据：`\to` 变制表符、`\right` 只剩 `ight]`。
     * 抄自 v0.0.3 真机 `questions` 表，不是手写编的。
     */
    @Test
    fun `detectsRealDeviceCorruption`() {
        val text = "求极限 " + math("\\lim_{x\u0009o0}\\left[\\frac{\\ln(1 + x)}{x}ight]^{1}")
        val issues = auditStem(text)
        val fixable = issues.filter { it.autoFixable }
        assertEquals(
            "同一处损坏应合成一条问题，实际: ${issues.map { it.summary }}",
            1, fixable.size
        )
        assertTrue("应标明可一键还原，实际: ${fixable[0].summary}", fixable[0].summary.contains("可一键还原"))
    }

    @Test
    fun `fixingRealCorruptionClearsTheIssues`() {
        val text = "求极限 " + math("\\lim_{x\u0009o0}\\left[\\frac{\\ln(1 + x)}{x}ight]^{1}")
        val fixed = TextAudit.applyFixes(text, auditStem(text))
        assertTrue("应还原出 \\to，实际: $fixed", fixed.contains("\\to"))
        assertTrue("应还原出 \\right", fixed.contains("\\right]"))
        assertFalse("修完不该再有控制字符", fixed.any { it.code in 8..13 })
        assertEquals("修完应当零问题", 0, auditStem(fixed).size)
    }

    /**
     * **不能报出「由别的损坏导致」的问题。**
     *
     * 真机数据里 `\right` 被删成 `ight`，于是查括号时会看到
     * 「`\left` 比 `\right` 多了 1 个」——可那是**假问题**：
     * `\right` 本来就在，孤儿还原回去就配平了。
     *
     * 报它会让用户去改一个没坏的地方，而真正的损坏反而不显眼。
     * 这条断言守着「先修、再查」这个顺序。
     */
    @Test
    fun `doesNotReportDerivedDelimiterProblem`() {
        val text = math("\\left[ x ight]")
        val issues = auditStem(text)
        assertFalse(
            "孤儿 ight 造成的括号不配对属于假问题，不该报。实际: ${issues.map { it.summary }}",
            issues.any { it.summary.contains("\\left 比 \\right") }
        )
    }

    /** 同样的道理：孤儿修好之后，结构检查应该完全干净。 */
    @Test
    fun `structuralChecksRunOnRepairedText`() {
        val text = math("\\left( x + y ight]")
        val fixed = TextAudit.applyFixes(text, auditStem(text))
        assertEquals(
            "修好之后不该再有结构问题，实际: ${auditStem(fixed).map { it.summary }}",
            0, auditStem(fixed).size
        )
    }

    // ------------------------------------------------------------ 结构问题（只报告，不自动改）

    /**
     * 括号不配对**不自动补**。
     *
     * 补 `\right.` 是在猜模型想闭合什么；而且若先补了它、随后又把被删的
     * `\right]` 还原回来，就变成 1 个 `\left` 配 2 个 `\right`，比原来更糟。
     */
    @Test
    fun `detectsUnbalancedLeftRightWithoutAutoFix`() {
        val text = math("\\left( x + y")
        val issues = auditStem(text)
        val issue = issues.firstOrNull { it.summary.contains("\\left") }
        assertNotNull("应报出 \\left/\\right 不配对，实际: ${issues.map { it.summary }}", issue)
        assertFalse("不确定的补法不自动改", issue!!.autoFixable)
        assertNull("不应给出 suggestion", issue.suggestion)
        assertEquals("不可自动修则文本原样不动", text, TextAudit.applyFixes(text, issues))
    }

    /** 花括号不配对同样只报告：补在哪是猜的。 */
    @Test
    fun `detectsUnbalancedBracesWithoutAutoFix`() {
        val text = math("\\frac{1}{2")
        val issues = auditStem(text)
        val issue = issues.firstOrNull { it.summary.contains("{") }
        assertNotNull("应报出花括号不配对", issue)
        assertFalse("不确定的补法不自动改", issue!!.autoFixable)
        assertEquals("不可自动修则文本原样不动", text, TextAudit.applyFixes(text, issues))
    }

    @Test
    fun `detectsOddDollarCount`() {
        val issues = auditStem("答案：\$x + y")
        assertTrue("奇数个 \$ 应报错", issues.any { it.summary.contains("奇数") })
    }

    @Test
    fun `detectsUnclosedEnvironment`() {
        val issues = auditStem(math("\\begin{matrix} a & b \\end{pmatrix}"))
        assertTrue(
            "环境名不匹配应报错",
            issues.any { it.summary.contains("不匹配") || it.summary.contains("\\begin 有") }
        )
    }

    @Test
    fun `detectsEmptyScripts`() {
        val issues = auditStem(math("F_ + 1"))
        assertTrue(
            "空下标应报可疑，实际: ${issues.map { it.summary }}",
            issues.any { it.summary.contains("下标是空的") }
        )
    }

    @Test
    fun `detectsFracMissingDenominator`() {
        val issues = auditStem(math("\\frac{1} + 2"))
        assertTrue(
            "\\frac 缺分母应报错",
            issues.any { it.summary.contains("分母缺失") }
        )
    }

    // ------------------------------------------------------------ KaTeX 权威判定

    /** 这一条不是我们猜的，是 KaTeX 真的渲染失败了。 */
    @Test
    fun `reportsKaTeXRenderFailures`() {
        val latex = "\\frac{1}{2^{2^2}}"
        val issues = auditStem("正文 " + math(latex) + " 结束", failures = setOf(latex))
        val issue = issues.firstOrNull { it.summary.contains("KaTeX") }
        assertNotNull("应报出渲染失败，实际: ${issues.map { it.summary }}", issue)
        assertFalse("KaTeX 语法错不自动改", issue!!.autoFixable)
    }

    /** 渲染失败的公式若不在本字段里，不该报上来。 */
    @Test
    fun `ignoresFailuresFromOtherFields`() {
        val issues = auditStem("干净的公式 " + math("x"), failures = setOf("\\frac{1}{2}"))
        assertFalse(
            "别的字段的失败不该算到这里，实际: ${issues.map { it.summary }}",
            issues.any { it.summary.contains("KaTeX") }
        )
    }

    /** 渲染判定基于**修好之后**的文本——那才是最终会被渲染的东西。 */
    @Test
    fun `renderFailureMatchedAgainstRepairedText`() {
        val repairedLatex = "\\left( x \\right)"
        val text = math("\\left( x ight]")   // 坏形态
        val issues = auditStem(text, failures = setOf(repairedLatex))
        // 修好之后公式变成 repairedLatex，而它在原始文本里不存在
        assertFalse(
            "修好之后才成立的公式不该按原始形态去匹配",
            issues.any { it.summary.contains("KaTeX") }
        )
    }

    // ------------------------------------------------------------ 误报（这一半更重要）

    /** 正常公式必须零误报。 */
    @Test
    fun `cleanFormulaProducesNoIssues`() {
        val clean = "设函数 " + math("z = f(x, y)") + "，求 " +
            math("\\frac{\\partial^2 z}{\\partial x \\partial y}") + " 的值。"
        val issues = auditStem(clean)
        assertEquals("干净文本不应有任何问题，实际: ${issues.map { it.summary }}", 0, issues.size)
    }

    /** 真机第二题：分式、下标、偏导号全都正常，绝不能误报。 */
    @Test
    fun `realDeviceQuestionTwoProducesNoIssues`() {
        val text = "设函数 " + math("z = f[xy, yg(x)]") + " ，求 " +
            math("\\left.\\frac{\\partial^2 z}{\\partial x \\partial y}\\right|_{x=1,y=1}") + " ."
        val issues = auditStem(text)
        assertEquals("真机正常题目不应误报，实际: ${issues.map { it.summary }}", 0, issues.size)
    }

    /** 独立成行公式也要能正确切分（`$$` 算一个分隔符，不是两个）。 */
    @Test
    fun `displayMathIsSegmentedAsOneDelimiter`() {
        val issues = auditStem("解：" + dmath("\\left[ x \\right]"))
        assertEquals(
            "独立成行公式不应误报，实际: ${issues.map { it.summary }}",
            0, issues.size
        )
    }

    /** 中文正文里的花括号不是公式，不能报。 */
    @Test
    fun `bracesInPlainTextAreNotMathIssues`() {
        val text = "用 JSON 的 {\"a\": 1} 表示，不要 { 括号不配对 } 这样。"
        val issues = TextAudit.audit(TextAudit.Field.ANALYSIS, text)
        assertFalse(
            "正文里的花括号不该被当成公式问题，实际: ${issues.map { it.summary }}",
            issues.any { it.summary.contains("{ 比 }") }
        )
    }

    /** 已经正确的 `\right` 不能被误报成「删坏了」。 */
    @Test
    fun `correctRightIsNotReportedAsCorruption`() {
        val issues = auditStem(math("\\left( x \\right)"))
        assertFalse(
            "正确的 \\right 不该报损坏，实际: ${issues.map { it.summary }}",
            issues.any { it.autoFixable }
        )
    }

    /** 英文单词里的 ight（fight / right）不是 LaTeX 残留。 */
    @Test
    fun `englishWordsWithIgtAreNotCorruption`() {
        val issues = TextAudit.audit(TextAudit.Field.ANALYSIS, " fight for the right to fight back ")
        assertFalse(
            "英文单词不该误报，实际: ${issues.map { it.summary }}",
            issues.any { it.autoFixable }
        )
    }

    @Test
    fun `blankTextProducesNoIssues`() {
        assertEquals(0, auditStem("").size)
        assertEquals(0, auditStem("   \n  ").size)
    }

    // ------------------------------------------------------------ 套用修正

    @Test
    fun `applyFixesIsIdempotent`() {
        val text = math("\\lim_{x\u0009o0}\\left[\\frac{a}{x}ight]")
        val once = TextAudit.applyFixes(text, auditStem(text))
        val twice = TextAudit.applyFixes(once, auditStem(once))
        assertEquals("重复套用不应继续改动（防 \\right. 越挂越多）", once, twice)
    }

    /** 没问题的文本套一遍必须逐字符不变。 */
    @Test
    fun `applyFixesLeavesCleanTextUntouched`() {
        val clean = "答案：" + math("e^{-\\frac{1}{2}}")
        assertEquals(clean, TextAudit.applyFixes(clean, auditStem(clean)))
    }

    // ------------------------------------------------------------ 字段与可修性

    @Test
    fun `issuesCarryTheirFieldAndOptionIndex`() {
        val opt = TextAudit.audit(
            TextAudit.Field.OPTION, math("x") + " 与 " + math("y_"), optionIndex = 2
        )
        assertTrue("应带上字段与选项下标", opt.all {
            it.field == TextAudit.Field.OPTION && it.optionIndex == 2
        })
        assertTrue("应报出空下标", opt.any { it.summary.contains("下标") })
    }

    /** 不可自动修的项 suggestion 必须是 null，UI 才知道不给「一键修复」。 */
    @Test
    fun `manualOnlyIssuesHaveNullSuggestion`() {
        val issues = auditStem(math("\\frac{1} + 2"))
        val manual = issues.firstOrNull { it.summary.contains("分母缺失") }
        assertNotNull(manual)
        assertNull("需人工修的项不应有 suggestion", manual!!.suggestion)
    }
}
