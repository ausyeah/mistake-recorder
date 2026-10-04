package com.mistakebook.math

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批量协议解析测试。
 *
 * ## 这组测试为什么重写过
 *
 * 上一版在测试里**自己复制了一份 `unwrap()`**，注释还写着「与 MathRenderer 里的实现保持一致」，
 * 然后 `parseObject(unwrap(asJavascriptString))` ——**先在测试里解包，再喂给解析逻辑**。
 *
 * 结果是这个 bug 一路绿灯通过 CI：
 * 生产代码是 `BatchProtocol.parse(raw)` 直接吃**没解包**的字符串，恒返回 null，
 * 界面上所有公式都回退成 LaTeX 源码，从 v0.0.5 起。
 *
 * 根因是那句注释：「与实现保持一致」——**测试复制的不是行为，是实现**。
 * 实现改了它不会响，而它还绿着。
 *
 * 现在改成：**测试直接调生产代码，输入就用真机拿到的双层编码形态**。
 * 少一次调用、漏了就会红。
 */
class BatchLayoutParseTest {

    /** 真机日志里 `renderBatch` 返回值的**内部** JSON 形态。 */
    private val innerJson =
        """{"w":2016,"h":3152,"items":[""" +
            """{"k":"i:A, B","x":0,"y":42,"w":272,"h":172,"fs":112},""" +
            """{"k":"i:n","x":0,"y":305,"w":81,"h":172,"fs":112},""" +
            """{"k":"d:\\frac{1}{2}","x":12,"y":520,"w":640,"h":300,"fs":96}""" +
            """]}"""

    /**
     * **生产路径的入口**：`evaluateJavascript` 回调拿到的就是它。
     *
     * `math.html` 用 `JSON.stringify(...)` 返回一个 JS 字符串，
     * `evaluateJavascript` 会再 JSON 编码一层 —— 外面多一对引号。
     */
    /** 把内部 JSON 包成“evaluateJavascript 回调收到的字符串字面量”。 */
    private fun asJavascriptString(inner: String): String =
        Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(inner))

    private val asJavascriptString: String = asJavascriptString(innerJson)

    // ------------------------------------------------------------ 双层编码

    @Test
    fun `返回值确实多带一层 json 编码`() {
        assertTrue(
            "前提就不成立，说明这条测试在验证一个不存在的问题",
            asJavascriptString.startsWith("\"") && asJavascriptString.endsWith("\"")
        )
    }

    @Test
    fun `parse 直接吃双层编码的字符串也能解出来`() {
        // **这条是回归测试的核心。**
        // 以前这里写的是 `parseObject(unwrap(asJavascriptString))`，
        // 于是「生产代码忘了解包」这件事永远测不出来。
        val layout = BatchProtocol.parse(asJavascriptString)
        assertNotNull("双层编码的返回值必须能被解析——不能指望调用方记得解包", layout)
        assertEquals(2016, layout!!.sheetWidth)
        assertEquals(3152, layout.sheetHeight)
        assertEquals(3, layout.entries.size)
    }

    @Test
    fun `本来就没编码的字符串也能解`() {
        val layout = BatchProtocol.parse(innerJson)
        assertNotNull(layout)
        assertEquals(2016, layout!!.sheetWidth)
    }

    @Test
    fun `解包还原出内部 json`() {
        assertEquals(innerJson, BatchProtocol.unwrap(asJavascriptString))
    }

    @Test
    fun `解包对本来正常的输入是恒等的`() {
        assertEquals(innerJson, BatchProtocol.unwrap(innerJson))
    }

    @Test
    fun `解包对畸形输入原样返回好让上层报错`() {
        assertEquals("not json at all", BatchProtocol.unwrap("not json at all"))
        assertEquals("", BatchProtocol.unwrap("   "))
    }

    @Test
    fun `单条路径与批量路径走同一个解包`() {
        // 两条路径的载荷结构不同，但**外层编码方式一样**，
        // 所以解包必须是同一个函数，不能各写一份。
        val single = """{"w":2016,"h":1180,"fs":112,"err":0}"""
        val singleAsString = asJavascriptString(single)
        assertEquals(single, BatchProtocol.unwrap(singleAsString))
        assertNotNull(BatchProtocol.parse(innerJson))
    }

    // ------------------------------------------------------------ 布局语义

    @Test
    fun `全部条目都被解析出来`() {
        val obj = BatchProtocol.parse(asJavascriptString)!!
        assertEquals(3, obj.entries.size)
        assertEquals(listOf("i:A, B", "i:n", "d:\\frac{1}{2}"), obj.entries.map { it.key })
    }

    @Test
    fun `条目坐标单调且互不重叠`() {
        // 批量切片全靠这些坐标算错就产出看不懂的残缺图
        val entries = BatchProtocol.parse(asJavascriptString)!!.entries
        var previousBottom = -1
        entries.forEach { entry ->
            assertTrue("y 必须单调不减: ${entry.key}", entry.y >= previousBottom)
            assertTrue("高度必须为正: ${entry.key}", entry.height > 0)
            previousBottom = entry.y + entry.height
        }
    }

    @Test
    fun `条目都落在长卷范围内`() {
        val layout = BatchProtocol.parse(asJavascriptString)!!
        layout.entries.forEach { entry ->
            assertTrue("${entry.key} 越界右边", entry.x + entry.width <= layout.sheetWidth)
            assertTrue("${entry.key} 越界下边", entry.y + entry.height <= layout.sheetHeight)
            assertTrue("${entry.key} 负坐标", entry.x >= 0 && entry.y >= 0)
        }
    }

    @Test
    fun `字号取设备像素值`() {
        val layout = BatchProtocol.parse(asJavascriptString)!!
        assertEquals(112f, layout.entries[0].fontPx)
        assertEquals(96f, layout.entries[2].fontPx)
    }

    // ------------------------------------------------------------ 非法输入

    @Test
    fun `空输入返回 null`() {
        assertNull(BatchProtocol.parse(""))
        assertNull(BatchProtocol.parse("   "))
    }

    @Test
    fun `宽度或高度非正返回 null`() {
        // 宁可判失败回退源码，也不要拿一份错的坐标去切片——
        // 后者会产出「看起来像公式的残缺图」，比显示源码糟得多
        assertNull(BatchProtocol.parse("""{"w":0,"h":100,"items":[]}"""))
        assertNull(BatchProtocol.parse("""{"w":100,"h":-1,"items":[]}"""))
    }

    @Test
    fun `缺 items 返回 null`() {
        assertNull(BatchProtocol.parse("""{"w":100,"h":100}"""))
    }

    @Test
    fun `畸形条目被跳过而不是让整批失败`() {
        val bad = """{"w":100,"h":100,"items":[{"k":"i:a","x":0,"y":0,"w":10,"h":10,"fs":20},"garbage",""" +
            """{"k":"i:b","x":0,"y":20,"w":10,"h":10,"fs":20}]}"""
        val layout = BatchProtocol.parse(bad)
        assertNotNull("一条坏数据不该拖垮整批", layout)
        assertEquals(2, layout!!.entries.size)
    }

    @Test
    fun `双层编码的畸形输入也返回 null 而不是崩`() {
        val badString = asJavascriptString("{不是 json")
        assertNull(BatchProtocol.parse(badString))
    }
}
