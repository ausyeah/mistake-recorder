package com.mistakebook.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公式排版规则的回归测试。
 *
 * 这些规则原先散在 `RichText.kt` 与 `PdfExporter.kt` 两处、各写各的，
 * 表现为「App 里好好的，打印出来飞出纸面」。抽到
 * [MathLayout] 之后必须把行为钉死，否则又会漂回去。
 *
 * 用户报的现象与对应断言：
 * - 「会出现单排超级大」 → [uniformScaleMakesEveryFormulaSameSize]
 * - 「会被裁切」       → [neverExceedsWidthOrHeight]、[aspectRatioAlwaysPreserved]
 * - 「超出屏幕」       → [veryLongFormulaShrinksInsteadOfOverflowing]
 * - 「行间互相重合」   → [lineBoxGrowsToFitTallestFormula]
 */
class MathLayoutTest {

    private fun assertClose(expected: Float, actual: Float, msg: String, eps: Float = 0.01f) {
        assertTrue("$msg（期望 $expected，实际 $actual）", kotlin.math.abs(expected - actual) <= eps)
    }

    /** 模拟 KaTeX 以 64px 渲染的一条含分式的公式：宽 320、高 180。 */
    private val tallFormulaW = 320
    private val tallFormulaH = 180
    private val renderedFontPx = 64f
    private val bodyFontPx = 32f

    /**
     * 「同一段里有的公式大得离谱」。
     *
     * 根因是旧代码用**固定行高**去卡公式：`h > lineHeight` 就缩小。
     * 于是带分式的公式被压到行框内，而 `x\to0` 这种矮公式不动——
     * 两者最终显示出来一大一小。
     *
     * 正确的判据是**缩放倍数相同**：只要所有公式都用同一个
     * `targetFont / srcFont`，视觉上的字号就一致，公式之间的高矮差
     * 来自它们本身的内容（分式本来就高），而不是来自谁被多压了一次。
     */
    @Test
    fun `uniformScaleMakesEveryFormulaSameSize`() {
        val shortFitted = MathLayout.fit(200, 70, renderedFontPx, bodyFontPx)
        val tallFitted = MathLayout.fit(tallFormulaW, tallFormulaH, renderedFontPx, bodyFontPx)

        assertEquals("同一批公式的缩放倍数必须一致", shortFitted.scale, tallFitted.scale, 0.0001f)
        // 倍数就等于「正文 32px / 渲染 64px」
        assertClose(0.5f, shortFitted.scale, "缩放倍数")
    }

    /**
     * 归一化的真正保证：KaTeX 用不同字号渲染同一条公式，
     * 最终在版面上必须**完全一样大**。
     *
     * 批量渲染里超宽公式会被临时缩字号，它们回传的 `fs` 也就各不相同。
     * 若 Android 侧忘了按 `fs` 归一化，这些公式就会大出好几倍——
     * 这正是用户说的「单排超级大」。
     */
    @Test
    fun `differentRenderedFontSizesNormalizeToIdenticalLayout`() {
        // KaTeX 用**更小**字号渲染，位图本身也**更小**，二者成比例
        val from64 = MathLayout.fit(tallFormulaW, tallFormulaH, 64f, bodyFontPx)
        val from32 = MathLayout.fit(tallFormulaW / 2, tallFormulaH / 2, 32f, bodyFontPx)
        val from16 = MathLayout.fit(tallFormulaW / 4, tallFormulaH / 4, 16f, bodyFontPx)

        assertClose(from64.width, from32.width, "64px 与 32px 渲染应等宽")
        assertClose(from64.height, from32.height, "64px 与 32px 渲染应等高")
        assertClose(from64.width, from16.width, "64px 与 16px 渲染应等宽")
        assertClose(from64.height, from16.height, "64px 与 16px 渲染应等高")
    }

    /** 任何情况下都不得超出给定上限——这是「不裁切、不溢出」的根本保证。 */
    @Test
    fun `neverExceedsWidthOrHeight`() {
        val cases = listOf(
            Triple(MathLayout.fit(tallFormulaW, tallFormulaH, renderedFontPx, bodyFontPx, maxWidthPx = 100f), 100f, 0f),
            Triple(MathLayout.fit(tallFormulaW, tallFormulaH, renderedFontPx, bodyFontPx, maxHeightPx = 20f), 0f, 20f),
            Triple(MathLayout.fit(2000, 400, renderedFontPx, bodyFontPx, maxWidthPx = 300f), 300f, 0f),
            Triple(MathLayout.fit(2000, 400, renderedFontPx, bodyFontPx, maxHeightPx = 30f), 0f, 30f),
            Triple(MathLayout.fit(2000, 400, renderedFontPx, bodyFontPx, maxWidthPx = 120f, maxHeightPx = 15f), 120f, 15f)
        )
        cases.forEach { (f, maxW, maxH) ->
            if (maxW > 0f) assertTrue("宽度超限: ${f.width} > $maxW", f.width <= maxW + 0.01f)
            if (maxH > 0f) assertTrue("高度超限: ${f.height} > $maxH", f.height <= maxH + 0.01f)
        }
    }

    /**
     * 长宽比必须恒定。
     *
     * 旧代码先按宽缩、再单独把高 `coerceIn` 到上限，高度被夹住而宽度不变，
     * 公式被**拉扁**——这是最难被察觉的一类排版 bug。
     */
    @Test
    fun `aspectRatioAlwaysPreserved`() {
        // 每个用例都要跟**自己那张图**的原始比例比，不能拿同一个基准去套
        val cases = listOf(
            MathLayout.fit(tallFormulaW, tallFormulaH, renderedFontPx, bodyFontPx, maxWidthPx = 50f) to
                (tallFormulaH.toFloat() / tallFormulaW),
            MathLayout.fit(tallFormulaW, tallFormulaH, renderedFontPx, bodyFontPx, maxHeightPx = 15f) to
                (tallFormulaH.toFloat() / tallFormulaW),
            MathLayout.fit(2000, 400, renderedFontPx, bodyFontPx, maxWidthPx = 120f) to
                (400f / 2000f),
            MathLayout.fit(2000, 400, renderedFontPx, bodyFontPx, maxHeightPx = 10f) to
                (400f / 2000f)
        )
        cases.forEach { (f, srcRatio) ->
            assertClose(srcRatio, f.height / f.width, "长宽比被破坏：${f.width}x${f.height}")
        }
    }

    /**
     * 超长公式必须**缩小**而不是溢出。
     *
     * 这是 PDF 的实际故障：一条 2000px 宽的公式被原样放到 A4 上，
     * 右边直接切掉。旧代码只判断「换行」，从不缩小。
     */
    @Test
    fun `veryLongFormulaShrinksInsteadOfOverflowing`() {
        val usable = 511f  // A4 减去左右页边距，与 PdfExporter.USABLE_WIDTH 一致
        val fitted = MathLayout.fit(2000, 400, renderedFontPx, bodyFontPx, maxWidthPx = usable)
        assertTrue("长公式必须缩到可打印宽度以内，实际 ${fitted.width}", fitted.width <= usable)
        assertClose(usable, fitted.width, "应该刚好缩到边界")
        assertTrue("缩小后仍要有可见高度", fitted.height > 0f)
    }

    /**
     * 行高必须**迁就**最高的公式，而不是把公式压扁。
     *
     * 用户报「行间互相重合」：固定行高下分母伸出行框，压到下一行文字。
     */
    @Test
    fun `lineBoxGrowsToFitTallestFormula`() {
        val base = bodyFontPx * 1.45f
        val tallest = MathLayout.naturalHeight(tallFormulaH, renderedFontPx, bodyFontPx)
        val padding = tallest * MathLayout.MATH_LINE_PADDING_RATIO
        val lineHeight = MathLayout.lineHeightFor(base, tallest, padding)

        assertTrue(
            "行高($lineHeight) 必须容得下最高公式($tallest)加留白",
            lineHeight >= tallest + padding * 2f
        )
        // 没有公式时退回基础行高，不能被拉到 0
        assertEquals(base, MathLayout.lineHeightFor(base, 0f, 0f), 0.001f)
    }

    /**
     * 最关键的一条：**矮公式不受影响，高公式才缩小**。
     *
     * 这是用户给的明确取舍：「正常字母和汉字的大小一致是最佳的，
     * 分数或者明显需要多行的适当小一点就可以了」。
     *
     * 所以判据不是「一律不压」也不是「一律压到上限」，而是：
     * 自然高度在上限之内 → 原尺寸；超出 → 等比缩到上限。
     */
    @Test
    fun `onlyFormulasTallerThanTheCapShrink`() {
        val cap = MathLayout.inlineMathMaxHeightPx(bodyFontPx)

        // x -> 0：KaTeX 渲染成 200x70，正文 32px -> 自然高 35，未超上限
        val shortNatural = MathLayout.naturalHeight(70, renderedFontPx, bodyFontPx)
        assertTrue(
            "样例 $shortNatural 应低于上限 $cap，否则测不到「矮公式不受影响」",
            shortNatural <= cap
        )
        val shortFitted = MathLayout.fit(200, 70, renderedFontPx, bodyFontPx, maxHeightPx = cap)
        assertClose(shortNatural, shortFitted.height, "未超上限的公式必须保持原尺寸")

        // 带分式：自然高 90，超过上限，必须缩到上限
        val tallNatural = MathLayout.naturalHeight(tallFormulaH, renderedFontPx, bodyFontPx)
        assertTrue("样例 $tallNatural 应高于上限 $cap，否则测不到「高公式被压」", tallNatural > cap)
        val tallFitted = MathLayout.fit(
            tallFormulaW, tallFormulaH, renderedFontPx, bodyFontPx, maxHeightPx = cap
        )
        assertClose(cap, tallFitted.height, "超上限的公式应缩到上限")
        assertTrue(
            "缩小后字母必然小于正文，这是用户接受的取舍",
            tallFitted.scale < shortFitted.scale
        )
    }

    /**
     * 行高必须大于行内公式的高度上限，否则被压到上限的公式仍会压到下一行。
     *
     * 用户报的原话是「行间互相重合」。
     */
    @Test
    fun `lineBoxIsTallerThanInlineMathCap`() {
        val box = MathLayout.lineBoxPx(bodyFontPx)
        val cap = MathLayout.inlineMathMaxHeightPx(bodyFontPx)
        assertTrue(
            "行高($box) 必须大于公式上限($cap)，否则公式压行（用户报「行间互相重合」）",
            box > cap
        )
        // 但也不能大太多，否则段落会散
        assertTrue("行高不宜超过公式上限的 1.3 倍，实际 ${box / cap}", box / cap <= 1.3f)
    }

    /** 字母目标字号必须等于正文字号——这是用户点名要的效果。 */
    @Test
    fun `mathLettersMatchBodyTextSize`() {
        assertClose(bodyFontPx, MathLayout.letterTargetPx(bodyFontPx), "公式字母应与正文同大")
    }

    /** 独立行公式比行内公式更宽松，但它同样有上限。 */
    @Test
    fun `displayMathHasItsOwnLooserCap`() {
        val inlineCap = MathLayout.inlineMathMaxHeightPx(bodyFontPx)
        val displayCap = MathLayout.displayMathMaxHeightPx(bodyFontPx)
        assertTrue("独立行公式上限应比行内宽松", displayCap > inlineCap)
        val fitted = MathLayout.fit(
            2000, 1200, renderedFontPx, bodyFontPx, maxHeightPx = displayCap
        )
        assertClose(displayCap, fitted.height, "独立行公式也应被上限约束")
    }

    /** 异常输入不能产生 NaN / 负数——排版里出现 NaN 会直接让整段消失。 */
    @Test
    fun `degenerateInputStaysSane`() {
        val zero = MathLayout.fit(0, 0, 0f, 0f, 100f, 100f)
        assertEquals(0f, zero.width, 0f)
        assertEquals(0f, zero.height, 0f)

        val badFont = MathLayout.fit(100, 50, 0f, 32f, 1000f, 1000f)
        assertTrue("字号为 0 时不能产生 NaN", badFont.width.isFinite() && badFont.width > 0f)
        assertTrue(badFont.height.isFinite() && badFont.height > 0f)

        assertEquals(0f, MathLayout.naturalHeight(0, 64f, 32f), 0f)
    }
}

