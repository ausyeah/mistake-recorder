package com.mistakebook.ui.edit

import com.mistakebook.domain.Option
import com.mistakebook.pipeline.TextAudit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 体检结果如何**落到草稿上**的回归测试。
 *
 * 引擎本身的判据在 [com.mistakebook.pipeline.TextAuditTest] 里；
 * 这里管的是另一类容易出错的地方：
 *
 * 1. **别改错字段。** 一条「题干」的问题绝不能被套用到答案上。
 * 2. **回退要能整体还原。** 撤销是拿一份草稿快照覆盖回去的，
 *    所以快照必须原封不动——这里验证改完之后再改回去，内容逐字符相同。
 * 3. **同一字段多条问题要一次修完**，而不是修一条就停。
 */
class AuditLogicTest {

    /** 真机损坏形态：TAB + 孤儿 `ight`。 */
    private val brokenStem: String

    init {
        val tab = '	'
        brokenStem = "求极限 \$\\lim_{x${tab}o0}\\left[\\frac{a}{x}ight]^{1}\$"
    }

    private fun draft(
        stem: String = "",
        answer: String = "",
        analysis: String = "",
        options: List<Option> = emptyList()
    ) = EditableDraft(imagePath = "", stem = stem, answer = answer, analysis = analysis, options = options)

    @Test
    fun `auditCoversAllFourFields`() {
        val d = draft(
            stem = brokenStem,
            answer = "答案 \$\\left( x ight)\$",
            analysis = "解析 \$\\frac{1} + 2\$",
            options = listOf(Option("A", "选项 \$\\left( y ight)\$"))
        )
        val issues = runAudit(d)
        val fields = issues.map { it.field }.toSet()
        assertTrue("题干应被报出", TextAudit.Field.STEM in fields)
        assertTrue("答案应被报出", TextAudit.Field.ANSWER in fields)
        assertTrue("解析应被报出（\\frac 缺分母）", TextAudit.Field.ANALYSIS in fields)
        assertTrue("选项应被报出", TextAudit.Field.OPTION in fields)
    }

    /** 选项问题必须带对下标，否则用户不知道该改第几个选项。 */
    @Test
    fun `optionIssuesCarryTheirIndex`() {
        val d = draft(options = listOf(Option("A", "正常 \$x\$"), Option("B", "坏的 \$\\left( y ight)\$")))
        val issues = runAudit(d).filter { it.field == TextAudit.Field.OPTION }
        assertTrue("应报出第 2 个选项有问题", issues.any { it.optionIndex == 1 })
        assertTrue("正常选项不该被报", issues.none { it.optionIndex == 0 })
    }

    @Test
    fun `applyFixesOnlyTouchesTheFieldsWithIssues`() {
        val d = draft(
            stem = brokenStem,
            answer = "干净答案 \$e^{-1}\$",
            analysis = "干净解析 \$\\frac{1}{2}\$"
        )
        val fixed = applyAuditFixes(d, runAudit(d))
        assertTrue("题干应被修好", fixed.stem.contains("\\to"))
        assertEquals("答案不该被动", d.answer, fixed.answer)
        assertEquals("解析不该被动", d.analysis, fixed.analysis)
    }

    /** 修完之后必须干净，否则说明修得不彻底。 */
    @Test
    fun `afterFixingTheFieldIsClean`() {
        val d = draft(stem = brokenStem)
        val fixed = applyAuditFixes(d, runAudit(d))
        val remaining = runAudit(fixed).filter { it.autoFixable }
        assertEquals("修完不该再有可自动修的问题，实际: ${remaining.map { it.summary }}", 0, remaining.size)
    }

    /**
     * 撤销 = 用快照覆盖回去，必须逐字符还原。
     *
     * 这是「还能回退」这个承诺的唯一证据：快照不能被就地改掉。
     */
    @Test
    fun `rollbackRestoresTheOriginalExactly`() {
        val original = draft(stem = brokenStem, answer = "答案 \$\\left( a ight)\$")
        val snapshot = original.copy()
        var current = original
        current = applyAuditFixes(current, runAudit(current))
        assertTrue("确实改了东西", current.stem != original.stem || current.answer != original.answer)
        // 撤销时将当前草稿整体替换回修复前快照。
        current = snapshot
        assertEquals("撤销必须还原整个草稿", original, current)
    }

    /**
     * 同一字段里**多处**损坏，报一条、修一次，全部修好。
     *
     * 刻意不按「一处损坏一条」来报：那是同一种病因的多次发作，
     * 拆成多条会让用户以为要分别处理。
     */
    @Test
    fun `multipleCorruptionsInOneFieldCollapseToOneIssue`() {
        val tab = '\t'
        val d = draft(
            analysis = "第一步 \$\\lim_{x${tab}o0}a\$；第二步 \$\\left( b ight)\$"
        )
        val issues = runAudit(d).filter { it.autoFixable }
        assertEquals("同字段多处损坏应合成一条问题", 1, issues.size)

        val fixed = applyAuditFixes(d, issues)
        assertTrue("第一个公式的 \\to 应修好", fixed.analysis.contains("\\to"))
        assertTrue("第二个公式的 \\right 应修好", fixed.analysis.contains("\\right"))
        assertTrue("中文正文应原样保留", fixed.analysis.contains("第一步"))
        assertTrue("中文正文应原样保留", fixed.analysis.contains("第二步"))
        assertEquals("修完应当干净", 0, runAudit(fixed).count { it.autoFixable })
    }

    /** 全部为不可自动修的问题时，草稿必须原样返回（不产生无谓的对象变更）。 */
    @Test
    fun `manualOnlyIssuesLeaveDraftUntouched`() {
        val d = draft(stem = "$\\left( x$")   // 少 \right，机器不该猜
        val same = applyAuditFixes(d, runAudit(d))
        assertTrue("不可自动修则对象原样返回", same === d)
    }

    /** 干净题目不该被改动，也避免无谓的重组。 */
    @Test
    fun `cleanDraftIsReturnedAsIs`() {
        val d = draft(stem = "求 \$\\lim_{x\\to0}\\frac{\\ln(1+x)}{x}\$")
        assertTrue("干净草稿原样返回", applyAuditFixes(d, runAudit(d)) === d)
        assertEquals("干净草稿零问题", 0, runAudit(d).size)
    }
}
