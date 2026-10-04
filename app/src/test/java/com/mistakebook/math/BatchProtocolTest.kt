package com.mistakebook.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `math.html` ↔ Kotlin 长卷协议的回归测试。
 *
 * ## 这组测试存在的理由
 *
 * 曾经把 JS 推的 `fs` 读成 `f`，fontPx 取到默认值 0。
 * 而 [MathLayout.fit] 的兜底把 0 当成「与目标字号相同」，
 * 于是缩放倍数变成 **1** —— 把设备像素当版面单位，公式按原生分辨率画：
 *
 * - 屏幕上：公式偏大（用户报「稍微有点大」）
 * - PDF 里：`w = bitmapW`（点），被 `USABLE_WIDTH` 截成**整页宽**
 *   （用户报「超级无敌大」）
 *
 * 这个 bug 能活这么久，是因为：
 * 1. JSON 键名是字符串，**编译期发现不了**
 * 2. 渲染**看起来是正常的**——公式确实画出来了，只是尺寸不对
 * 3. 没有任何一个测试覆盖这段解析
 *
 * 所以下面第一条测试的作用不是「验证逻辑」，而是**钉住字段名**。
 */
class BatchProtocolTest {

    /** 读仓库里的真实 math.html。 */
    private fun readHtml(): String {
        val candidates = listOf(
            "app/src/main/assets/katex/math.html",
            "src/main/assets/katex/math.html",
            "../../app/src/main/assets/katex/math.html"
        )
        for (path in candidates) {
            val f = java.io.File(path)
            if (f.exists()) return f.readText()
        }
        throw AssertionError("找不到 math.html，试过: $candidates")
    }

    /**
     * 一份**照着 math.html 的实际字段名**构造的真实形状返回。
     *
     * 故意用 `fs` 而不是 `f`：这正是当年写错的那个地方。
     */
    private val realisticJson = """
        {"w":2016,"h":1800,"items":[
          {"k":"i:x\\to0","x":0,"y":0,"w":640,"h":220,"fs":88,"err":0},
          {"k":"d:\\frac{a}{b}","x":0,"y":260,"w":900,"h":420,"fs":88,"err":0}
        ]}
    """.trimIndent()

    /**
     * **协议自检：Kotlin 定义的字段名必须真的出现在 math.html 里。**
     *
     * 这条比任何逻辑断言都重要：它把「两边各写各的字段名」这个隐患
     * 变成测试期就能发现的问题，而不用等用户看到公式变形。
     *
     * 直接读仓库里的真实文件（assets 不在单元测试 classpath 上）。
     */
    @Test
    fun `htmlUsesExactlyTheFieldNamesKotlinReads`() {
        val html = readHtml()
        val missing = listOf(
            BatchProtocol.Key.ITEM_KEY, BatchProtocol.Key.ITEM_X, BatchProtocol.Key.ITEM_Y,
            BatchProtocol.Key.ITEM_WIDTH, BatchProtocol.Key.ITEM_HEIGHT,
            BatchProtocol.Key.ITEM_FONT_PX, BatchProtocol.Key.ITEM_ERR,
            BatchProtocol.Key.SHEET_WIDTH, BatchProtocol.Key.SHEET_HEIGHT, BatchProtocol.Key.ITEMS
        ).filterNot { html.contains("$it:") }
        assertEquals("math.html 与 Kotlin 字段名不一致: $missing", 0, missing.size)
    }

    /** 单条路径的字段名同样要对齐（`render()` 与 `renderBatch()` 各有一套）。 */
    @Test
    fun `htmlSinglePathFieldNamesMatch`() {
        val html = readHtml()
        listOf(SingleProtocol.WIDTH, SingleProtocol.HEIGHT, SingleProtocol.FONT_PX, SingleProtocol.ERR)
            .forEach { key ->
                assertTrue("math.html 里找不到单条路径字段 $key:", html.contains("$key:"))
            }
    }

    /**
     * 反向自检：math.html 里出现的每个短字段名，Kotlin 侧也得认识。
     *
     * 只做单向检查是不够的——JS 多推一个字段而 Kotlin 不读，
     * 同样是协议漂移的前兆。
     */
    @Test
    fun `kotlinKnowsEveryFieldHtmlPushes`() {
        val html = readHtml()
        val pushed = Regex("""(\w+):""").findAll(html)
            .map { it.groupValues[1] }
            .filter { it.length == 1 }          // 只看单字符短键
            .toSet()
        val known = setOf(
            BatchProtocol.Key.SHEET_WIDTH, BatchProtocol.Key.SHEET_HEIGHT,
            BatchProtocol.Key.ITEM_KEY, BatchProtocol.Key.ITEM_X, BatchProtocol.Key.ITEM_Y,
            BatchProtocol.Key.ITEM_WIDTH, BatchProtocol.Key.ITEM_HEIGHT, BatchProtocol.Key.ITEM_ERR,
            SingleProtocol.ERR
        )
        val unknown = pushed - known
        assertEquals("math.html 推了 Kotlin 不认识的字段: $unknown", emptySet<String>(), unknown)
    }

    /**
     * 这条是本文件的核心：`fs` 必须被正确读出。
     *
     * 读成 `f` 时会取到 [BatchProtocol.FALLBACK_FONT_PX]（112）而不是 88，
     * 于是缩放倍数差 88/112 ≈ 0.79 倍——公式整体偏大，且**不报错**。
     */
    @Test
    fun `fontSizeIsParsedFromFsNotF`() {
        val layout = BatchProtocol.parse(realisticJson)
        assertTrue("解析不应失败", layout != null)
        layout!!.entries.forEach { entry ->
            assertEquals(
                "条目 ${entry.key} 的 fontPx 应为 88（字段名是 fs 不是 f）",
                88f, entry.fontPx, 0.001f
            )
        }
    }

    /**
     * 字号缺失时必须落到**已知渲染字号**，绝不能变成 0。
     *
     * 0 会让 `MathLayout.fit` 算出 scale=1，
     * 也就是「把设备像素当版面单位」——那个 bug 的直接成因。
     */
    @Test
    fun `missingFontSizeFallsBackToAKnownRenderSizeNotZero`() {
        val json = """{"w":100,"h":100,"items":[{"k":"a","x":0,"y":0,"w":50,"h":50}]}"""
        val layout = BatchProtocol.parse(json)!!
        assertEquals(1, layout.entries.size)
        val fontPx = layout.entries[0].fontPx
        assertTrue("fontPx 必须为正，实际 $fontPx", fontPx > 0f)
        assertEquals(BatchProtocol.FALLBACK_FONT_PX, fontPx, 0.001f)
    }

    /** 字号为 0 或负数（JS 异常）也必须被兜底成正值。 */
    @Test
    fun `nonPositiveFontSizeIsReplaced`() {
        val json = """{"w":100,"h":100,"items":[
            {"k":"a","x":0,"y":0,"w":50,"h":50,"fs":0},
            {"k":"b","x":0,"y":60,"w":50,"h":50,"fs":-5}
        ]}"""
        val layout = BatchProtocol.parse(json)!!
        assertEquals(2, layout.entries.size)
        layout.entries.forEach {
            assertTrue("非法字号必须被兜底，实际 ${it.fontPx}", it.fontPx > 0f)
        }
    }

    /** 坐标、尺寸、错误码都要原样带出来。 */
    @Test
    fun `allFieldsAreParsed`() {
        val layout = BatchProtocol.parse(realisticJson)!!
        assertEquals(2016, layout.sheetWidth)
        assertEquals(1800, layout.sheetHeight)
        val second = layout.entries[1]
        assertEquals("d:\\frac{a}{b}", second.key)
        assertEquals(0, second.x)
        assertEquals(260, second.y)
        assertEquals(900, second.width)
        assertEquals(420, second.height)
        assertEquals(0, second.err)
    }

    /** err 标记要透传：1=源文本被转义破坏，2=KaTeX 解析失败，两者都要回退源码。 */
    @Test
    fun `errFlagsArePreserved`() {
        val json = """{"w":100,"h":300,"items":[
            {"k":"a","x":0,"y":0,"w":50,"h":50,"fs":88,"err":1},
            {"k":"b","x":0,"y":60,"w":50,"h":50,"fs":88,"err":2},
            {"k":"c","x":0,"y":120,"w":50,"h":50,"fs":88}
        ]}"""
        val layout = BatchProtocol.parse(json)!!
        assertEquals(listOf(1, 2, 0), layout.entries.map { it.err })
    }

    /** 宽高为 0 的条目无法切片，必须剔除而不是留在列表里。 */
    @Test
    fun `zeroSizedEntriesAreDropped`() {
        val json = """{"w":100,"h":300,"items":[
            {"k":"ok","x":0,"y":0,"w":50,"h":50,"fs":88},
            {"k":"zero","x":0,"y":60,"w":0,"h":50,"fs":88},
            {"k":"zeroH","x":0,"y":120,"w":50,"h":0,"fs":88}
        ]}"""
        val layout = BatchProtocol.parse(json)!!
        assertEquals(1, layout.entries.size)
        assertEquals("ok", layout.entries[0].key)
    }

    /** 坏输入必须返回 null，让上层回退源码，绝不能返回半个布局。 */
    @Test
    fun `malformedInputYieldsNull`() {
        assertEquals(null, BatchProtocol.parse(""))
        assertEquals(null, BatchProtocol.parse("   "))
        assertEquals(null, BatchProtocol.parse("not json"))
        assertEquals(null, BatchProtocol.parse("""{"w":0,"h":100,"items":[]}"""))
        assertEquals(null, BatchProtocol.parse("""{"w":100,"h":100}"""))
        assertEquals(null, BatchProtocol.parse("""{"w":100,"h":100,"items":"x"}"""))
    }

    /**
     * 端到端：协议解析出的 fontPx 喂给 [MathLayout] 之后，尺寸必须合理。
     *
     * 锁住的是「解析 + 缩放」串起来的行为——
     * 单测各自通过、串起来出问题的情况（本 bug 的形态）只有这样才抓得住。
     */
    @Test
    fun `parsedFontSizeProducesSaneLayout`() {
        val entry = BatchProtocol.parse(realisticJson)!!.entries[0]
        val targetPx = 38f          // 正文在 2.75x 屏上的实际像素高
        val fitted = MathLayout.fit(
            bitmapW = entry.width,
            bitmapH = entry.height,
            srcFontPx = entry.fontPx,
            targetFontPx = targetPx,
            maxWidthPx = 1400f,
            maxHeightPx = targetPx * MathLayout.INLINE_MATH_MAX_HEIGHT_RATIO
        )
        // 38/88 = 0.432；若 fontPx 误取兜底值 112，则是 0.339，明显偏小。
        // 实测 0.397——分式比行框高，触发了高度上限再缩一点，属正常。
        // 所以判据是「明显偏小」而不是精确值：fontPx 读错会落到 0.34 那一档。
        assertTrue(
            "缩放应落在 0.38~0.5（fontPx 读对才成立），实际 ${fitted.scale}",
            fitted.scale in 0.38f..0.5f
        )
        assertTrue("公式不应溢出可用宽度，实际 ${fitted.width}", fitted.width <= 1400f)
    }
}
