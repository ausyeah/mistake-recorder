package com.mistakebook.pipeline

import com.mistakebook.net.mineru.ExtractResultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [matchItem] 的回归测试。
 *
 * ## 用户现象
 *
 * 「mineru 官方能看到用量确实涨了，但是这边就是一直 pending」。
 *
 * 用量在涨 = 服务端确实在解析**某个**文件。而客户端显示 `pending`。
 * 这两件事能同时成立，只有一种解释：**客户端在看错的那一项**。
 *
 * 而早先 `matchItem` 的最后一行是 `return items.firstOrNull()`——
 * 文件名一旦对不上（服务端改名、加后缀、编码差异），就静默拿到列表里的第一项。
 * 用户看到的是别人的状态，于是「卡住」这件事完全无法归因：
 * 是它真的在排队，还是我压根在看另一项？
 *
 * ## 测的是**生产代码本身**
 *
 * `matchItem` 特意从 `MineruClient` 的 private 方法提成顶层 `internal` 函数，
 * 就是为了这里能直接调用它。
 *
 * 之前这个测试在自己的文件里抄了一份实现——那测的是那份拷贝，
 * 真实代码改了它不会红。本项目已经吃过三次这种亏
 * （BatchProtocolTest / DocxPackageTest / ManualEntryTest）。
 */
class MineruMatchItemTest {

    private fun item(
        fileName: String,
        name: String = "",
        state: String = "pending",
        zip: String? = null
    ) = ExtractResultItem(
        fileName = fileName,
        name = name,
        state = state,
        fullZipUrl = zip
    )

    // ------------------------------------------------------- 明确匹配

    @Test
    fun `fileName 精确匹配`() {
        val items = listOf(item("a.jpg", state = "done"), item("b.jpg", state = "pending"))
        assertEquals("done", matchItem(items, "a.jpg")?.state)
    }

    @Test
    fun `name 字段精确匹配`() {
        val items = listOf(item("", name = "a.jpg", state = "running"))
        assertEquals("running", matchItem(items, "a.jpg")?.state)
    }

    @Test
    fun `忽略路径只比文件名`() {
        val items = listOf(item("images/a.jpg"))
        assertEquals("images/a.jpg", matchItem(items, "a.jpg")?.fileName)
    }

    // ------------------------------------------------------- 只有一项时接受

    @Test
    fun `只有一项时接受它`() {
        // 单文件批量接口本来就会返回单项，即便文件名对不上也只有它可选
        val items = listOf(item("server-renamed.jpg", state = "running"))
        assertEquals("running", matchItem(items, "mine.jpg")?.state)
    }

    @Test
    fun `空列表返回 null`() {
        assertNull(matchItem(emptyList(), "a.jpg"))
    }

    // ------------------------------------------------------- 多项且匹配不上：不猜

    /**
     * 核心回归。
     *
     * 早先这里会 `return items.firstOrNull()`，于是用户在 A 文件的等待界面
     * 看到 B 文件的状态。用量在涨（B 确实在解析）而 A 一直显示 B 的 pending，
     * 完全无法诊断。
     */
    @Test
    fun `多项且文件名对不上时返回 null 而不是乱猜`() {
        val items = listOf(
            item("first.jpg", state = "running"),
            item("second.jpg", state = "pending")
        )
        assertNull("不该猜第一项", matchItem(items, "totally-different.jpg"))
    }

    @Test
    fun `多项时正确的那一项仍能被找到`() {
        val items = listOf(item("first.jpg"), item("second.jpg", state = "done"))
        assertEquals("done", matchItem(items, "second.jpg")?.state)
    }

    // ------------------------------------------------------- 干扰项

    @Test
    fun `同名不同扩展名不误配`() {
        val items = listOf(item("photo.png", state = "done"), item("photo.jpg", state = "pending"))
        assertEquals("done", matchItem(items, "photo.png")?.state)
        assertEquals("pending", matchItem(items, "photo.jpg")?.state)
    }

    /**
 * 服务端偶尔对某些文件不返回 `file_name`（空串）。
 *
 * 此时若用空串去匹配，会把那一项当成「匹配成功」——
 * 而它未必是本次的文件。所以**查询名也为空时要跳过精确匹配**，
 * 只在列表只有一项时才接受。
 */
    @Test
    fun `查询名为空时不与空 fileName 的项误配`() {
        val items = listOf(item("", state = "done"), item("real.jpg", state = "pending"))
        assertNull("查询名与某项的 fileName 都是空串，属于误配", matchItem(items, ""))
    }
}
