package com.mistakebook.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端复现真机上的渲染输入，**并把每一步的中间值打印出来**。
 *
 * ## 为什么单独写这个测试
 *
 * 前面几个测试都是「给定输入 → 断言输出」，只能证明单个函数对。
 * 但真机上 `\to` 修好了、`\right` 没修好——两者走同一条函数，
 * 说明中间某一环把 CR 处理掉了。光看代码推不出来，
 * 必须把整条链路跑一遍看每一步的产物。
 *
 * ## 真实数据
 *
 * v0.0.3 真机详情页，题干：
 * ```
 * 求极限 $\lim_{x\to0}\left[\frac{\ln(1 + x)}{x}\right]^{{\frac{1}{{e^{x} - 1}}}$
 * ```
 * `\to` 被吃成 TAB+`o`，`\right` 被吃成 CR+`ight`（CR 在输入框里显示为换行，
 * 所以当初看到的是 `ight]` 顶到下一行开头）。
 *
 * v0.0.4 修复后真机详情页显示：
 * - `lim_{x→0}` 箭头正常 —— `\to` 已修复
 * - `ight]` 仍是斜体变量，且末尾多出一个 `.`
 *
 * 末尾多出的 `.` 是关键线索：那是 `LatexSanitizer.balanceDelimiters`
 * 补的 `\right.`。它只有在**看不到任何 `\right`** 时才会补——
 * 说明修复那一刻 CR 仍不是 `\right`。
 */
class FullRenderPipelineTest {

    /**
     * 真机 v0.0.3 数据库 `questions.stem` 的**逐字原文**（控制字符可见化后抄录）：
     * ```
     * 求极限 $\lim_{x<TAB>o 0}\left[\frac{\ln(1 + x)}{x}ight]^{\frac{1}{e^x - 1}}.$
     * ```
     * 损坏点：`\to` -> TAB+`o `，`\right]` -> `ight]`（反斜杠与字母 r 一并消失）。
     *
     * 注意 `o` 后面还有一个空格、`e^x` 没加花括号——这些都是原件的样子，
     * 不要顺手「修正」，否则测的就不是真实数据了。
     */
    private val corruptedStem: String =
        "求极限 \$\\lim_{x\to 0}\\left[\\frac{\\ln(1 + x)}{x}ight]^{\\frac{1}{e^x - 1}}.\$"

    /** 与 `RichText.inlineMath` 同款：把 `$...$` 的内部内容切出来并 trim。 */
    private val inlineMath = Regex("\\\$\\\$([^$]+)\\\$\\\$|\\\$([^\\$]+)\\\$")

    private fun extractMath(text: String): List<String> =
        inlineMath.findAll(text)
            .map { (it.groupValues[1].ifEmpty { it.groupValues[2] }).trim() }
            .toList()

    /** 与 `MathRenderer.render` 完全一致的两步，顺序不能反。 */
    private fun renderPipeline(bare: String): String =
        LatexSanitizer.clean(LatexEscapes.repairForDisplay(bare, mathOnly = false))

    @Test
    fun `printEveryStageOfThePipeline`() {
        println("=== [0] 数据库里的原始题干 ===")
        println(corruptedStem.replace("\t", "<TAB>").replace("\r", "<CR>"))

        val mathList = extractMath(corruptedStem)
        println("=== [1] RichText 切出的公式（分隔符已剥离）===")
        mathList.forEach { println("  " + it.replace("\t", "<TAB>").replace("\r", "<CR>")) }

        require(mathList.isNotEmpty()) { "没切出公式，说明本测试的 fixture 与正则不符" }
        val bare = mathList.first()

        val repaired = LatexEscapes.repairForDisplay(bare, mathOnly = false)
        println("=== [2] repairForDisplay 之后 ===")
        println("  " + repaired.replace("\t", "<TAB>").replace("\r", "<CR>"))

        val out = renderPipeline(bare)
        println("=== [3] LatexSanitizer.clean 之后（即发给 KaTeX 的内容）===")
        println("  " + out.replace("\t", "<TAB>").replace("\r", "<CR>"))

        assertTrue(
            "发给 KaTeX 的内容仍含控制字符: " +
                out.map { if (it.code in 8..13) "<${it.code}>" else it.toString() }.joinToString(""),
            out.none { it.code in 8..13 }
        )
        assertTrue("应还原出 \\to", out.contains("\\to"))
        assertTrue("应还原出 \\right", out.contains("\\right]"))
    }

    /**
     * `\left` 与 `\right` 必须配平，否则 KaTeX 整条公式报错。
     *
     * ## 这个顺序错误在真机上出现过
     *
     * 先 `clean` 后修复时：`balanceDelimiters` 面对的还是 `ight]`，
     * 认为少了一个 `\right`，于是在末尾补 `\right.`；
     * 随后修复又把 `\right]` 补回来 —— 变成 1 个 `\left` 对 2 个 `\right`，
     * KaTeX 直接失败，公式退化成红色源码。
     *
     * 也就是说「修复生效了」和「能正常渲染」是两件事，缺一不可。
     */
    @Test
    fun `delimitersAreBalancedAfterRepair`() {
        val bare = extractMath(corruptedStem).first()
        val out = renderPipeline(bare)
        val opens = Regex("\\\\left").findAll(out).count()
        val closes = Regex("\\\\right").findAll(out).count()
        assertEquals("`\\left` 与 `\\right` 数量必须相等，实际 out=$out", opens, closes)
        assertEquals(
            "不应出现 balanceDelimiters 补出来的多余 `\\right.`",
            0,
            Regex("\\\\right\\.").findAll(out).count()
        )
    }

    /** 反向顺序（先清理后修复）必须产出不平衡的结果——用它证明上一条不是侥幸通过。 */
    @Test
    fun `wrongOrderProducesUnbalancedDelimiters`() {
        val bare = extractMath(corruptedStem).first()
        val wrongOrder = LatexEscapes.repairForDisplay(LatexSanitizer.clean(bare), mathOnly = false)
        val opens = Regex("\\\\left").findAll(wrongOrder).count()
        val closes = Regex("\\\\right").findAll(wrongOrder).count()
        assertTrue(
            "反序应当产生不平衡（opens=$opens closes=$closes），" +
                "若这里也平衡，说明 balanceDelimiters 行为变了，需要重新评估",
            opens != closes
        )
    }
}
