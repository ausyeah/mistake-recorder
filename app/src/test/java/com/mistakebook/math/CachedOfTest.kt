package com.mistakebook.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「能立刻显示的那部分」与「需要渲染的那部分」的分界。
 *
 * ## 背景
 *
 * 界面上的「公式出图」和「公式算完」本来是两件事，但 [MathRenderer.renderAll]
 * 要等**全部**条目处理完才返回。于是：
 *
 * > 10 条公式里 8 条早就命中了缓存，用户要陪这 8 条一起等剩下 2 条
 * > 走完 WebView 往返 + 长卷分配 + 抓图。
 *
 * [cachedOf] 把「纯内存查表」这部分单独摘出来，让调用方先把命中的画出来。
 *
 * ## 顺带修的另一个问题：分批位置
 *
 * 原来 [MathRenderer.renderAll] **先分批、再让每批各自过滤缓存**，
 * 于是「10 条里 8 条命中」也被摊成 2 批、跑 2 次完整往返。
 * 改成先滤后分：命中项不进 `pending`，批大小是真正要画的条数。
 *
 * ## 为什么能测
 *
 * 缓存抽成 `(String) -> RenderedMath?` 注入——`LruCache` 与 `Bitmap` 在
 * JVM 单测里都用不了，而**测试里复制一份实现就等于没有测试**。
 * 分批规则同理，抽成纯函数 [splitPending]。
 */
class CachedOfTest {

    /**
     * `RenderedMath` 持有 `Bitmap`，而 Bitmap 在 JVM 单测里是空壳，造不出来。
     *
     * 所以 [cachedOf] 的缓存查函数做成**泛型**：它只搬运引用、从不碰位图内容，
     * 测试于是能用任意类型当「已渲染结果」，只需覆盖三种取值——
     * 命中 / 未命中 / 命中但为 null。
     */
    private class FakeRendered(val tag: String)

    /** `(key, latex, displayMode)` —— 与 `renderAll` 同一形状。 */
    private fun req(key: String) = Triple(key, "\\alpha_$key", false)

    // ------------------------------------------------------------------
    // 一、只交出真正可用的
    // ------------------------------------------------------------------

    @Test
    fun `命中项原样返回`() {
        val m = mapOf("a" to FakeRendered("A"), "b" to FakeRendered("B"))
        val got = cachedOf({ m[it] }, listOf(req("a"), req("b")))
        assertEquals(setOf("a", "b"), got.keys)
        assertEquals("A", got["a"]?.tag)
        assertEquals("B", got["b"]?.tag)
    }

    @Test
    fun `未命中项不出现`() {
        val m = mapOf("a" to FakeRendered("A"))
        val got = cachedOf({ m[it] }, listOf(req("a"), req("z")))
        assertEquals(setOf("a"), got.keys)
        assertFalse("未命中的 z 不该出现", got.containsKey("z"))
    }

    /**
     * **缓存里存了 `null`（= 渲染失败）也当未命中。**
     *
     * 这是本条最容易写错的地方。`null` 属于「要重试」的结论，
     * 提前钉给界面会让本可重试成功的公式永远停在源码。
     */
    @Test
    fun `缓存里的失败结论不当成可用结果`() {
        val m: Map<String, FakeRendered?> = mapOf("ok" to FakeRendered("OK"), "failed" to null)
        val got = cachedOf({ m[it] }, listOf(req("ok"), req("failed")))
        assertEquals(setOf("ok"), got.keys)
        assertNull(got["failed"])
    }

    @Test
    fun `全部未命中时返回空而不是报错`() {
        assertTrue(cachedOf<Any>({ null }, listOf(req("a"), req("b"))).isEmpty())
    }

    @Test
    fun `空输入返回空`() {
        assertTrue(cachedOf<Any>({ null }, emptyList<Triple<String, String, Boolean>>()).isEmpty())
    }

    /** 顺序要跟传入一致——界面按 key 取值，顺序影响的是可预测性。 */
    @Test
    fun `保持传入顺序`() {
        val m = mapOf("c" to FakeRendered("C"), "a" to FakeRendered("A"), "b" to FakeRendered("B"))
        val got = cachedOf({ m[it] }, listOf(req("c"), req("a"), req("b")))
        assertEquals(listOf("c", "a", "b"), got.keys.toList())
    }

    @Test
    fun `重复 key 依赖 map 特性进行覆盖`() {
        val m = mapOf("a" to FakeRendered("A1"))
        val got = cachedOf({ m[it] }, listOf(req("a"), req("a")))
        assertEquals(setOf("a"), got.keys)
        assertEquals("A1", got["a"]?.tag)
    }

    @Test
    fun `支持空字符串与特殊字符 key`() {
        val m = mapOf("" to FakeRendered("EMPTY"), "a/b#c" to FakeRendered("SPECIAL"))
        val got = cachedOf({ m[it] }, listOf(req(""), req("a/b#c")))
        assertEquals(setOf("", "a/b#c"), got.keys)
        assertEquals("EMPTY", got[""]?.tag)
        assertEquals("SPECIAL", got["a/b#c"]?.tag)
    }

    @Test
    fun `精确对每个 item 触发一次 cache 查询`() {
        val calls = mutableListOf<String>()
        val cacheFunc: (String) -> FakeRendered? = { key ->
            calls.add(key)
            if (key == "hit") FakeRendered("HIT") else null
        }
        val items = listOf(req("hit"), req("miss1"), req("miss2"))
        val got = cachedOf(cacheFunc, items)

        assertEquals(listOf("hit", "miss1", "miss2"), calls)
        assertEquals(setOf("hit"), got.keys)
    }

    // ------------------------------------------------------------------
    // 二、分批必须在缓存过滤之后
    // ------------------------------------------------------------------

    /**
     * 核心：**先滤后分**。
     *
     * 8 条命中 + 2 条未命中（上限 6）——正确实现只要渲染 2 条，一批就够。
     * 先分后滤的话会变成 2 批（10 条 → 6+4），跑两趟完整往返。
     */
    @Test
    fun `分批只按未命中的条数算`() {
        val m = (1..8).associate { "h$it" to FakeRendered("H$it") }
        val all = (1..8).map { req("h$it") } + listOf(req("n1"), req("n2"))
        val cached = cachedOf({ m[it] }, all)
        val pending = all.map { it.first }.filter { cached[it] == null }

        assertEquals("只有 2 条要渲染", 2, pending.size)
        assertEquals("一批就够", 1, splitPending(pending, 6).size)
        assertEquals(listOf("n1", "n2"), splitPending(pending, 6).first())
    }

    /** 全未命中时批数才是按总量算的。 */
    @Test
    fun `全未命中时按总量分批`() {
        val keys = (1..10).map { "k$it" }
        assertEquals(2, splitPending(keys, 6).size)
        assertEquals(listOf(6, 4), splitPending(keys, 6).map { it.size })
    }

    /** 恰好等于上限时不切——多跑一趟没有收益。 */
    @Test
    fun `恰好等于上限不切分`() {
        val keys = (1..6).map { "k$it" }
        assertEquals(1, splitPending(keys, 6).size)
        assertEquals(6, splitPending(keys, 6).first().size)
    }

    @Test
    fun `零条不产生空批次`() {
        assertTrue(splitPending(emptyList(), 6).isEmpty())
    }

    /**
     * 批次不能为空——空批次会让 `renderPending` 白跑一趟
     * （还要抢一次互斥锁、进一次 WebView）。
     */
    @Test
    fun `不产生空批次`() {
        splitPending(listOf("a", "b", "c"), 6).forEach {
            assertTrue("出现了空批次", it.isNotEmpty())
        }
    }

    /**
     * 「先分后滤」会退化成多跑一趟。这条把旧行为的后果钉出来。
     *
     * 旧实现：`(命中8+未命中2).chunked(6)` = 2 批，每批各自滤缓存，
     * 第 2 批（4 条）若全部命中就白进一次 WebView。
     */
    @Test
    fun `先分后滤会多跑一批-这是被修掉的行为`() {
        val m = mapOf(
            "h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4,
            "h5" to 5, "h6" to 6, "h7" to 7, "h8" to 8
        )
        val all = (1..8).map { req("h$it") } + listOf(req("n1"), req("n2"))

        // 旧写法：先切
        val oldBatches = all.chunked(6)
        // 新写法：先滤
        val newBatches = splitPending(all.map { it.first }.filter { m[it] == null }, 6)

        assertEquals("旧写法要 2 批", 2, oldBatches.size)
        assertEquals("新写法只要 1 批", 1, newBatches.size)
    }
}