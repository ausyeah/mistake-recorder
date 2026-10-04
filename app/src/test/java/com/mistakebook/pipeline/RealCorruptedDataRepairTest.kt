package com.mistakebook.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真机数据库里的原始字符串**做回归测试。
 *
 * ## 数据来源
 *
 * v0.0.3 真机 `questions` 表，字段以控制字符可见化后原样抄录：
 * ```
 * #1 stem: 求极限 $\lim_{x<TAB>o 0}\left[\frac{\ln(1 + x)}{x}ight]^{\frac{1}{e^x - 1}}.$
 * #2 stem: ... 求 $\left.\frac{\partial^2 z}{\partial x \partial y}ight|_{x=1,y=1}$ .
 * #3 analysis: ... $f_{1}+y\left(x f_{11}+g(x)f_{12}ight)+g'(x)f_{2}+y g'(x)\left(x f_{21}+g(x)f_{22}ight)$
 * ```
 * 损坏由 v0.0.3 的 `normalizeBreaks` 造成：
 * - `\to` 的 `\t` 被换成制表符 → **可还原**（`o` 还在）
 * - `\right` 的 `\r` 被整段删除 → 只剩 `ight`，**只能靠上下文还原**
 *
 * 这两条形态不同，必须分别处理，只写一个必然漏。
 */
class RealCorruptedDataRepairTest {

    /** 真机 #1 题干，含 TAB（`\to` 残留）与孤儿 `ight]`（`\right]` 残留）。 */
    private val stem1 =
        "求极限 \$\\lim_{x\to0}\\left[\\frac{\\ln(1 + x)}{x}ight]^{\\frac{1}{e^x - 1}}.\$"

    /** 真机 #2 题干，`ight|` 前面紧跟 `}`。 */
    private val stem2 =
        "设函数 \$z = f[xy, yg(x)]\$ ，求 \$\\left.\\frac{\\partial^2 z}{\\partial x \\partial y}ight|_{x=1,y=1}\$ ."

    /** 真机 #3 解析，`ight)` 前面紧跟数字 `2`（`f_{12}\right)`）。 */
    private val analysis3 =
        "于是 \$\\frac{\\partial^2 z}{\\partial x\\partial y}=f_{1}+y\\left(x f_{11}+g(x)f_{12}ight)" +
            "+g'(x)f_{2}+y g'(x)\\left(x f_{21}+g(x)f_{22}ight)\$"

    /** 走渲染链路的真实入口：切公式 -> 清理 -> 修复。 */
    private fun repairBare(bare: String): String =
        LatexSanitizer.clean(LatexEscapes.repairForDisplay(bare, mathOnly = false))

    /** 走解析链路的真实入口：整段文本，数学区内才修。 */
    private fun repairFull(text: String): String =
        LatexEscapes.repairForDisplay(text, mathOnly = true)

    /**
     * 取出**含损坏的那一个** `$...$`。
     *
     * 真机题干里有多个数学区（`$z = f[xy, yg(x)]$`、`$\left.\frac{...}$`），
     * 取第一个会拿到无关的那段——曾经因此让测试断言了一个根本没被修的公式。
     */
    private fun damagedMath(text: String): String =
        Regex("\\\$\\$([^\\\$]+)\\\\$|\\\$([^\\$]+)\\\$")
            .findAll(text)
            .map { (it.groupValues[1].ifEmpty { it.groupValues[2] }).trim() }
            .first { it.contains("ight") || it.contains('\t') }

    /**
     * 数一数「孤立 `ight`」还剩几个。
     *
     * 不能直接 `contains("ight")` —— 正确的 `\right` 里本来就含 `ight`，
     * 那样断言永远失败。判据要和修复函数一致：`ight` 前面不是字母。
     */
    private fun orphanCount(s: String): Int =
        Regex("(?<![A-Za-z\\\\])ight").findAll(s).count()

    @Test
    fun `fixtureActuallyContainsTheDamage`() {
        // 先证明样本本身是坏的，否则下面的断言可能全是空跑
        assertTrue("fixture 必须含 TAB", stem1.contains('\t'))
        assertTrue("fixture 必须含孤儿 ight", stem1.contains("ight]"))
        assertEquals(0, Regex("\\\\right").findAll(stem1).count())
    }

    @Test
    fun `stem1ArrowAndRightBracketAreBothRestored`() {
        val bare = damagedMath(stem1)
        val fixed = repairBare(bare)
        assertTrue("应还原出 \\to，实际: $fixed", fixed.contains("\\to"))
        assertTrue("应还原出 \\right，实际: $fixed", fixed.contains("\\right]"))
        assertEquals("不应再有孤立 ight，实际: $fixed", 0, orphanCount(fixed))
        assertFalse("不应再有裸 TAB", fixed.contains('\t'))
    }

    @Test
    fun `stem2VerticalBarDelimiterIsRestored`() {
        val bare = damagedMath(stem2)
        val fixed = repairBare(bare)
        assertTrue("应还原出 \\right|，实际: $fixed", fixed.contains("\\right|"))
        assertEquals("不应再有孤立 ight，实际: $fixed", 0, orphanCount(fixed))
    }

    /** `ight)` 前面是数字 `2`，不是字母——这条专门守住这个形态。 */
    @Test
    fun `analysis3OrphanAfterDigitIsRestored`() {
        val bare = damagedMath(analysis3)
        val fixed = repairBare(bare)
        assertTrue("f_{12}\\right) 应还原。实际: $fixed", fixed.contains("f_{12}\\right)"))
        assertTrue("f_{22}\\right) 应还原", fixed.contains("f_{22}\\right)"))
        assertEquals("不应再有孤立 ight，实际: $fixed", 0, orphanCount(fixed))
    }

    /**
     * **绝不能把已经正确的 `\right` 改成 `\\right`。**
     *
     * 孤儿判据靠「`ight` 前面不能是字母」排除已正确的情况。
     * 一旦这条失效，重复渲染会把公式越改越坏——而且很难发现。
     */
    @Test
    fun `doesNotDoubleUpAlreadyCorrectRight`() {
        val correct = "\\left( x \\right)"
        val fixed = repairBare(correct)
        assertEquals("已正确的 \\right 不能被改动", correct, fixed)
        assertFalse("不得出现双反斜杠", fixed.contains("\\\\right"))
    }

    /** 新识别出来的干净公式（本就不含损坏）必须逐字符不变。 */
    @Test
    fun `cleanFormulaIsUntouched`() {
        val clean = "\\lim_{x\\to0}\\left[\\frac{\\ln(1+x)}{x}\\right]^{\\frac{1}{e^x-1}}"
        assertEquals(clean, repairBare(clean))
    }

    /** 幂等：渲染一次、两次、打印一次，反复调用结果必须一致。 */
    @Test
    fun `isIdempotent`() {
        val bare = damagedMath(stem1)
        val once = repairBare(bare)
        assertEquals("二次修复不得改变结果", once, repairBare(once))
        assertEquals("第三次同样", once, repairBare(repairBare(once)))
    }

    /**
     * 整段文本路径（解析层用）：数学区外的 `ight` 不该被动。
     *
     * 中文正文里写「fight」「right」是可能的，不该被当成 LaTeX 命令。
     */
    @Test
    fun `doesNotTouchIgtOutsideMathRegion`() {
        val text = "这道题 fight 一下 right here，不含数学区"
        assertEquals(text, repairFull(text))
    }

    /** 数学区内的 `ight` 该修，且前面的 `\left` 会让配对检查通过。 */
    @Test
    fun `fullTextPathRestoresInsideMath`() {
        val text = "求极限 \$\\lim_{x\to0}\\left[\\frac{\\ln(1+x)}{x}ight]^{1}\$"
        val fixed = repairFull(text)
        assertTrue("数学区内应还原 \\right", fixed.contains("\\right]"))
        assertTrue("数学区内应还原 \\to", fixed.contains("\\to"))
    }
}
