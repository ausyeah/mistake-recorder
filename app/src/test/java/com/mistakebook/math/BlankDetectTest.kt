package com.mistakebook.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这张位图到底画没画出东西」的判定。
 *
 * ## 背景
 *
 * 抓图之后原来调 [MathRenderer] 私有的 `alphaStats`，对整张位图**逐点** `getPixel`，
 * 只为了取 `visible == 0` 这一个布尔结论。
 *
 * 批量路径的长卷上限是 `2048 × 4096`，也就是**最多 838 万次 JNI 调用**，
 * 而且 `renderAll` 整体跑在主线程上。
 *
 * ## 为什么这里能测
 *
 * `Bitmap` 在 JVM 单测里是空壳，造不出来。所以判定逻辑把「取 alpha」做成注入的函数，
 * 测试用**离屏画布**驱动——它自己实现 alpha 的语义，
 * 而不是复制一份被测代码的逻辑（复制实现等于没有测试）。
 */
class BlankDetectTest {

    /**
     * 离屏画布：只存 alpha，语义与 `getPixel(...) ushr 24` 一致。
     *
     * @param ink 「墨水」的形状，画上去的地方 alpha=255，其余 0
     */
    private class Canvas(
        val width: Int,
        val height: Int,
        private val ink: (x: Int, y: Int) -> Boolean
    ) {
        fun alphaAt(x: Int, y: Int): Int = if (ink(x, y)) 255 else 0

        /** 采样点总数——用来验证「确实少读了」这件事。 */
        var reads = 0
            private set

        fun sampledAlphaAt(x: Int, y: Int): Int {
            reads++
            return alphaAt(x, y)
        }
    }

    private fun blank(w: Int, h: Int): Canvas = Canvas(w, h) { _, _ -> false }

    private fun check(canvas: Canvas): Boolean =
        isBlankBySampling(canvas.width, canvas.height, canvas::sampledAlphaAt)

    /**
     * 全空白时的采样点数：`(ceil(w/step) × ceil(h/step))`，step = `max(w/48, 1)`。
     *
     * 算清楚是为了让「读取次数」这类断言可核对，而不是拍脑袋写个数。
     */
    private fun expectedReads(width: Int, height: Int): Int {
        val stepX = (width / BLANK_SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (height / BLANK_SAMPLE_GRID).coerceAtLeast(1)
        val cols = (width + stepX - 1) / stepX
        val rows = (height + stepY - 1) / stepY
        return cols * rows
    }

    // ------------------------------------------------------------------
    // 一、语义：全透明 = 空白
    // ------------------------------------------------------------------

    @Test
    fun `全透明判为空白`() {
        assertTrue("什么都没画出来就是空白", check(blank(200, 80)))
    }

    @Test
    fun `有可见像素就不算空白`() {
        val canvas = Canvas(200, 80) { x, y -> x in 90..110 && y in 35..45 }
        assertFalse("中间有内容就不该判空白", check(canvas))
    }

    /**
     * `alpha > 0` 就算可见，不是 `alpha == 255`。
     *
     * 全透明判定关心的是「有没有东西」，不是「有多黑」——
     * 抗锯齿边缘、半透明字形都应该是可见的。
     */
    @Test
    fun `只要alpha大于零就算可见`() {
        // 上限 48×48，第 49 次调用不再发生，所以取不到第 50 个点。
        // 正好落在网格的第一行第一列（step=1 时 (0,0) 被采到）。
        var seen = 0
        val blankResult = isBlankBySampling(100, 100) { _, _ ->
            seen++
            if (seen == 1) 1 else 0   // 极淡的一个像素
        }
        assertFalse("alpha=1 也是可见的，不能当空白", blankResult)
    }

    // ------------------------------------------------------------------
    // 二、这张测试真正要守的东西：读了多少点
    // ------------------------------------------------------------------

    /**
     * 核心断言：读取次数与**面积无关**，只与网格有关。
     *
     * 这条红了就说明有人把逐点全扫描放回来了——那正是这次要修的东西。
     */
    @Test
    fun `读取次数不随位图面积增长`() {
        val small = blank(64, 64).also { check(it) }.reads
        val big = blank(2048, 4096).also { check(it) }.reads

        // 64×64 时 step=1 → 逐点 4096 次；2048×4096 时 step=42/85 → 49×49 = 2401 次。
        // **大图反而更少**，这正是「与面积脱钩」的含义。
        assertEquals(64 * 64, small)
        assertEquals(expectedReads(2048, 4096), big)
        assertTrue(
            "大图的读取次数($big)不应超过小图(${small})——网格固定时两者同量级",
            big <= small
        )
        assertTrue("应远小于逐点全扫描的 838 万点，实际 $big", big < 10_000)
    }

    /** 全扫描的代价是 838 万点，这里要真的降下来。 */
    @Test
    fun `大图上的读取量比全扫描低三个数量级`() {
        val reads = blank(2048, 4096).also { check(it) }.reads
        val fullScan = 2048 * 4096
        assertTrue(
            "读取 $reads 点，全扫描要 $fullScan 点，应至少低 100 倍",
            fullScan / reads > 100
        )
    }

    /**
     * 内容在**早期就被发现**时，读取次数应当更少——
     * 找到就返回，不扫完整个网格。
     */
    @Test
    fun `左上角有内容时立刻返回不扫完`() {
        val canvas = Canvas(2048, 4096) { x, y -> x < 5 && y < 5 }
        assertFalse(check(canvas))
        assertTrue(
            "左上角就有内容，不该扫完 ${BLANK_SAMPLE_GRID * BLANK_SAMPLE_GRID} 点，" +
                "实际 ${canvas.reads}",
            canvas.reads < BLANK_SAMPLE_GRID * BLANK_SAMPLE_GRID
        )
    }

    // ------------------------------------------------------------------
    // 三、边界
    // ------------------------------------------------------------------

    /**
     * 小位图必须**逐点**看。
     *
     * 公式常常只有几十像素宽，此时若还按 `宽/48` 跳点，一步就会跨过整个图，
     * 一个都没采到 → 把有内容的公式判成空白。
     */
    @Test
    fun `小位图逐点检查不跳采`() {
        // 30×20 → stepX=0→1, stepY=0→1，所以是逐点。
        // 公式只占中间一列：一旦按 48 跳采，一步就会跨过整张图，什么都采不到。
        val canvas = Canvas(30, 20) { x, _ -> x == 15 }
        assertFalse("窄图上的内容不能被跳采漏掉", check(canvas))
        // 逐点会在第 16 个点（x=15）就返回，不必扫完全图
        assertEquals(16, canvas.reads)
        assertEquals(30 * 20, expectedReads(30, 20))
    }

    @Test
    fun `单像素图`() {
        assertTrue("1×1 全透明算空白", check(blank(1, 1)))
        assertFalse(
            "1×1 有内容不能算空白",
            check(Canvas(1, 1) { _, _ -> true })
        )
    }

    /**
     * 尺寸为 0 的位图（「什么都没分配出来」）按空白处理。
     *
     * 这类位图不该被当成有效结果返回，否则会把空白图写进缓存，
     * 之后每次命中缓存都是空白。
     */
    @Test
    fun `零尺寸按空白处理`() {
        assertTrue(isBlankBySampling(0, 100) { _, _ -> 255 })
        assertTrue(isBlankBySampling(100, 0) { _, _ -> 255 })
        assertTrue(isBlankBySampling(0, 0) { _, _ -> 255 })
    }

    @Test
    fun `负尺寸按空白处理`() {
        assertTrue("负宽度应按空白处理", isBlankBySampling(-10, 100) { _, _ -> 255 })
        assertTrue("负高度应按空白处理", isBlankBySampling(100, -10) { _, _ -> 255 })
        assertTrue("负宽度和高度应按空白处理", isBlankBySampling(-10, -10) { _, _ -> 255 })
    }

    @Test
    fun `网格采样坐标边界精确判定`() {
        // 测试在采样网格的起始点 (0,0) 处存在不透明像素
        val originCanvas = Canvas(200, 200) { x, y -> x == 0 && y == 0 }
        assertFalse("(0,0) 处有内容不应判为空白", check(originCanvas))

        // 200x200 时 stepX = 200/48 = 4, stepY = 200/48 = 4
        // 采样坐标包含 (0,0), (4,4), ...
        val sampledPointCanvas = Canvas(200, 200) { x, y -> x == 4 && y == 4 }
        assertFalse("采样点 (4,4) 处有内容不应判为空白", check(sampledPointCanvas))
    }

    // ------------------------------------------------------------------
    // 四、采样必然带来的取舍，必须钉住
    // ------------------------------------------------------------------

    /**
     * **本实现的已知局限**：细到落在采样间隙里的内容会被漏掉。
     *
     * 这条测试是**故意**记录这个取舍的——它断言当前行为，
     * 这样「网格被调密」或「有人换回全扫描」时会有东西变化可查。
     *
     * 真实公式不会细到 1px，但**如果真机上报「公式不显示」**，
     * 第一件事就是回来调 `BLANK_SAMPLE_GRID`，而不是重写判定。
     */
    @Test
    fun `采样会漏掉落在间隙里的极细内容-已知取舍`() {
        // 2048 宽时 stepX = 2048/48 = 42，一根 1px 的竖线只占某一列
        val hairline = Canvas(2048, 4096) { x, y -> x == 100 && y in 100..300 }
        val missed = check(hairline)
        // 当前实现会漏判。这是**已知且接受**的取舍，不是 bug。
        assertTrue(
            "1px 细线在 48×48 网格下会被漏判；若此断言开始失败，说明网格被调密了，" +
                "真实公式的漏判风险进一步下降",
            missed
        )
    }

    /**
     * 但常规尺寸的公式**不该**被漏判——网格密度必须够用。
     *
     * 这条是真正的安全线：它模拟一个正常公式的墨迹占比，
     * 要求判为「非空白」。
     */
    @Test
    fun `常规尺寸的公式不会被漏判`() {
        val cases = listOf(120 to 40, 300 to 90, 800 to 200, 1400 to 300)
        cases.forEach { (w, h) ->
            // 字形墨迹：横跨约 60% 宽度、30% 高度，且笔画有一定粗细
            val canvas = Canvas(w, h) { x, y ->
                val inGlyph = x in (w * 0.2).toInt()..(w * 0.8).toInt() &&
                    y in (h * 0.35).toInt()..(h * 0.65).toInt()
                // 笔画有 3px 粗细，不是 1px 细线
                inGlyph && (x / 3 + y / 3) % 2 == 0
            }
            assertFalse("${w}×$h 的常规公式被判成了空白", check(canvas))
        }
    }
}