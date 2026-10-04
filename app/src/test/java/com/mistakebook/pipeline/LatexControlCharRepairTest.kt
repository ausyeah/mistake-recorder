package com.mistakebook.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **渲染期修复**：把数据库里已经损坏的 LaTeX 救回来。
 *
 * ## 为什么解析层的修复救不了已有数据
 *
 * [LatexEscapes.protectLatexEscapes] 作用在 JSON 解析**之前**，只能保护
 * 今后新识别出来的内容。而用户手机上那几道题的题干**早就以损坏形态存进了 Room**：
 * `\to` 存成了 TAB+`o`，`\right` 存成了 CR+`ight`。解析层修得再好，
 * 旧数据还是坏的。
 *
 * 所以渲染链路上必须再兜一层：[LatexEscapes.restoreLatexControlChars]。
 * 关键是**控制字符后面的字母还在**——`\to` 吃掉反斜杠后剩下 `o`，
 * 补回 `\` + `t` 就还原了。
 *
 * ## 边界：只在 `$...$` 里动
 *
 * 中文正文里出现制表符完全正常（「题干<TAB>答案」这种对齐写法）。
 * 只有 LaTeX 数学区里不可能有裸 TAB/CR/BS/FF——出现即说明反斜杠被吃了。
 * 限定在数学区内，就不会误伤正常排版。
 */
class LatexControlCharRepairTest {

    /**
     * 真机 v0.0.3 的实际受损题干，逐字节还原自数据库。
     *
     * 损坏点：`\to` -> TAB+`o `，`\right` -> CR+`ight`。
     * 注意 CR 会让阅读器以为换行，所以截图上 `ight]` 跑到了下一行开头。
     */
    private val corruptedStem: String =
        "求极限 \$\\lim_{x\to0}\\left[\\frac{\\ln(1 + x)}{x}\right]^{{\\frac{1}{{e^{x} - 1}}}\$"

    @Test
    fun `restoresTabAsLatexCommandInsideMath`() {
        assertTrue("fixture 必须真的含 TAB，否则这条测试是空跑", corruptedStem.contains('\t'))
        val fixed = LatexEscapes.restoreLatexControlChars(corruptedStem)
        assertTrue("TAB 应还原成 \\t（于是得到 \\to）。实际: $fixed", fixed.contains("\\to"))
        assertFalse("不应再有裸 TAB", fixed.contains('\t'))
    }

    @Test
    fun `restoresCarriageReturnAsLatexCommandInsideMath`() {
        assertTrue("fixture 必须真的含 CR", corruptedStem.contains('\r'))
        val fixed = LatexEscapes.restoreLatexControlChars(corruptedStem)
        assertTrue("CR 应还原成 \\r（于是得到 \\right）。实际: $fixed", fixed.contains("\\right"))
        assertFalse("不应再有裸 CR", fixed.contains('\r'))
    }

    /** 修完之后，这条公式应该与「模型本来想输出的」逐字符一致。 */
    @Test
    fun `matchesIntendedFormulaAfterRepair`() {
        val expected =
            "求极限 \$\\lim_{x\\to0}\\left[\\frac{\\ln(1 + x)}{x}\\right]^{{\\frac{1}{{e^{x} - 1}}}\$"
        assertEquals(expected, LatexEscapes.restoreLatexControlChars(corruptedStem))
    }

    /**
     * 四类控制字符都要能救回来，不能只处理 TAB 和 CR。
     *
     * 用显式 Unicode 码点构造「已损坏」形态：Kotlin 没有 `\f` 转义，
     * 而且写死控制字符在源码里根本看不见，这段代码本来就不会存在。
     */
    @Test
    fun `restoresAllControlCharKinds`() {
        val damaged = buildString {
            append("a\$")
            append('\u0009').append("o ")   // \to
            append('\u000D').append("ight ") // \right
            append('\u0008').append("egin ") // \begin
            append('\u0008').append("oxed ") // \boxed
            append('\u000C').append("rac b") // \frac
            append('$')
        }
        val fixed = LatexEscapes.restoreLatexControlChars(damaged)
        assertTrue("\\begin 应还原。实际: $fixed", fixed.contains("\\begin"))
        assertTrue("\\boxed 应还原", fixed.contains("\\boxed"))
        assertTrue("\\frac 应还原", fixed.contains("\\frac"))
        assertTrue("\\right 应还原", fixed.contains("\\right"))
        assertTrue("\\to 应还原", fixed.contains("\\to"))
        assertTrue("数学区内不应剩任何控制字符", fixed.none { it.code in 8..13 })
    }

    /**
     * **回归测试：分隔符已被剥掉时也必须能修。**
     *
     * 渲染链路的实际数据流：
     * ```
     * RichText  → InlineSpan.Math(rest.substring(2, end).trim())   ← $ 没了
     * MathRenderer.render(token.latex, ...)
     * ```
     * 这一层拿到的公式里一个 `$` 都没有。第一版修复只认「在 `$...$` 里才修」，
     * 于是渲染路径上**一次都不触发**——而且不报错、不崩，只是静默失效。
     *
     * 这条测试就是防止那个 bug 再回来：它只传公式内部内容，必须修得回来。
     */
    @Test
    fun `repairsBareLatexWithoutDelimiters`() {
        // 模拟 RichText 剥掉 $$ 之后交给 MathRenderer 的内容
        val bareLatex = "lim_{x" + '\u0009' + "o0}" + '\u000D' + "ight]"
        assertFalse("fixture 必须真的含 TAB", bareLatex.contains("\\to"))
        val fixed = LatexEscapes.restoreLatexControlChars(bareLatex, mathOnly = false)
        assertTrue("裸 LaTeX 也必须修回 \\to。实际: $fixed", fixed.contains("\\to"))
        assertTrue("裸 LaTeX 也必须修回 \\right", fixed.contains("\\right"))
    }

    /**
     * 「后接 ASCII 字母」这条判据是 mathOnly=false 敢放开的前提。
     *
     * 中文排版里制表符后面跟的是汉字，不该被当成 `\t`+字母 而改掉。
     * 没有这条判据，放开 mathOnly 就会把正常中文对齐写法弄坏。
     */
    @Test
    fun `controlCharFollowedByChineseIsNotRepaired`() {
        val text = "步骤一" + '\u0009' + "步骤二"
        assertEquals(text, LatexEscapes.restoreLatexControlChars(text, mathOnly = false))
    }

    /**
     * 正文里的制表符必须原样保留。
     *
     * 这是本修复最容易造成二次伤害的地方：如果不限数学区，
     * 「题干<TAB>答案」这种正常对齐写法会被改坏。
     */
    @Test
    fun `leavesControlCharsOutsideMathAlone`() {
        val text = "步骤一\t步骤二\r\n第一行\n第二行"
        assertEquals("数学区外的控制字符不应被改动", text, LatexEscapes.restoreLatexControlChars(text))
    }

    /**
 * **回归测试：`$$...$$` 显示公式。**
 *
 * `$$` 是**一个**分隔符，不是两个。若按单 `$` 处理，状态会开关两次，
 * 中间整段被判成「正文」——修复对显示公式彻底失效，
 * 而解析结果里显示公式非常常见。
 */
@Test
    fun `repairsDisplayMathDelimitedByDoubleDollar`() {
        val damaged = "解：" + '$' + '$' + "x" + '\u0009' + "o0" + '$' + '$'
        val fixed = LatexEscapes.restoreLatexControlChars(damaged)
        assertTrue("`$\$...\$\$` 内的 TAB 必须修复。实际: $fixed", fixed.contains("\\to"))
        assertFalse("不应残留 TAB", fixed.contains('\u0009'))
    }

    /** 中文正文里出现不成对的 `$`（比如「价格 5 元 $x」漏了右括号）时，
     * 不能因此把后面的排版字符改掉。
     *
     * 这条是 mathOnly=true 模式的已知边界：分隔符配对一旦失衡，
     * 判定就会失准。靠「后接 ASCII 字母」这条判据兜底，
     * 汉字开头的场景不会受害。
     */
    @Test
    fun `unbalancedDollarInChineseTextDoesNotCorrupt`() {
        val text = "价格是 " + '$' + "5 元" + '\t' + "单位"
        val fixed = LatexEscapes.restoreLatexControlChars(text)
        assertTrue("后接汉字的制表符必须保留。实际: $fixed", fixed.contains('\t'))
    }

    /**
     * 换行**不还原**：这是本函数唯一一个故意的缺口。
     *
     * `\neq` 被 JSON 吃掉后是 LF+`eq`，但公式里也可能有真实换行，
     * 两者在解析完成后已无法区分。猜错会把好好的多行公式改坏，
     * 所以宁可放过。
     */
    @Test
    fun `lineFeedIsNotRestoredBecauseItIsAmbiguous`() {
        val text = "a\$x\neq y\$"
        val fixed = LatexEscapes.restoreLatexControlChars(text)
        assertTrue(
            "换行必须保持原样（与真实换行无法区分，猜错会更糟）。实际: $fixed",
            fixed.contains('\n')
        )
        assertFalse("不应凭空造出 \\n", fixed.contains("\\neq"))
    }

    /**
     * 幂等：跑两次结果必须一样。
     *
     * 渲染链路可能被多次调用（列表页 + 详情页 + 打印），
     * 不幂等就会把 `\to` 变成 `\\to`——越修越坏。
     */
    @Test
    fun `isIdempotentAcrossRepeatedCalls`() {
        val once = LatexEscapes.restoreLatexControlChars(corruptedStem)
        val twice = LatexEscapes.restoreLatexControlChars(once)
        assertEquals("二次调用不应改变结果", once, twice)
        assertFalse("不得出现双反斜杠", twice.contains("\\\\to"))
    }

    /** 无控制字符时应原样返回（快路径，也确保不误改正常文本）。 */
    @Test
    fun `cleanTextIsReturnedUnchanged`() {
        val clean = "求极限 \$\\lim_{x\\to0\\frac{\\ln(1+x)}{x}\$\$"
        assertEquals(clean, LatexEscapes.restoreLatexControlChars(clean))
    }
}
