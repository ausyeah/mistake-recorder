package com.mistakebook.net.mineru

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `extract_progress` 解析的回归测试。
 *
 * ## 用户现象
 *
 * 「手机上一直在加载显示 mineru 解析中，有问题，不能用」。
 * 而 MinerU 控制台显示任务**已完成**（今日解析文件数 1/5000）。
 *
 * ## 两个叠加的原因
 *
 * 1. `extract_progress` 被当**标量**处理，实际是**对象**：
 *    ```json
 *    "extract_progress": { "extracted_pages": 1, "total_pages": 2, "start_time": "..." }
 *    ```
 *    `toDoubleOrNull()` 对 `{extracted_pages=1, ...}` 恒返回 null，
 *    于是整轮轮询**一次进度都没报出来**。
 * 2. 界面文案被 `CaptureTaskDao.moveToParsing` 用常量覆盖
 *    （那条 SQL 现在不写 stageText 了）。
 *
 * 两者叠加的效果：无论服务端在第几步、无论第几次轮询，
 * 界面永远是同一句话 + 无限转圈。与「真卡死」无法区分，排障完全失明。
 *
 * PRD 原文写的「extract_progress 0-100」是错的，实现照抄了。
 */
class MineruProgressTest {

    // ignoreUnknownKeys：官方响应里有本 DTO 没声明的字段，真实使用中必然遇到，
    // 不能因此崩。
    private val lenient = Json { ignoreUnknownKeys = true }

    private fun parseItem(payload: String): ExtractResultItem =
        lenient.decodeFromString(ExtractResultItem.serializer(), payload)

    // ------------------------------------------------------- 对象形态（真实形态）

    @Test
    fun `对象形态算出百分比`() {
        val item = parseItem(
            """{"state":"running","file_name":"a.jpg","extract_progress":{"extracted_pages":1,"total_pages":2}}"""
        )
        assertEquals(50, item.progressPercent())
    }

    @Test
    fun `跨行对象形态也能算`() {
        val item = parseItem(
            """
            {
              "state": "running",
              "file_name": "a.jpg",
              "extract_progress": {
                "extracted_pages": 3,
                "total_pages": 4,
                "start_time": "2025-01-20 11:43:20"
              }
            }
            """.trimIndent()
        )
        assertEquals(75, item.progressPercent())
    }

    @Test
    fun `带 start_time 的完整响应`() {
        val item = parseItem(
            """{"state":"running","extract_progress":{"extracted_pages":1,"total_pages":2,"start_time":"2025-01-20 11:43:20"}}"""
        )
        assertEquals(50, item.progressPercent())
    }

    // ------------------------------------------------------- 边界

    @Test
    fun `总页数为 0 不返回进度`() {
        val item = parseItem("""{"state":"running","extract_progress":{"extracted_pages":0,"total_pages":0}}""")
        assertNull("除零会崩，必须返回 null", item.progressPercent())
    }

    @Test
    fun `缺字段返回 null`() {
        val item = parseItem("""{"state":"running","extract_progress":{"start_time":"x"}}""")
        assertNull(item.progressPercent())
    }

    @Test
    fun `没有 extract_progress 字段`() {
        assertNull(parseItem("""{"state":"running"}""").progressPercent())
    }

    @Test
    fun `字段为 null`() {
        assertNull(parseItem("""{"state":"running","extract_progress":null}""").progressPercent())
    }

    /**
     * 服务端偶尔给字符串数字。
     *
     * 不能让这种「只是显示不出来」的情况把整个轮询循环崩掉——
     * 所以取值走的是 runCatching，而不是直接 `jsonPrimitive`（非 primitive 会抛）。
     */
    @Test
    fun `字符串数字也能解析`() {
        val item = parseItem("""{"state":"running","extract_progress":{"extracted_pages":"1","total_pages":"2"}}""")
        assertEquals(50, item.progressPercent())
    }

    @Test
    fun `字段类型不对时不崩`() {
        val item = parseItem(
            """{"state":"running","extract_progress":{"extracted_pages":{"a":1},"total_pages":[1,2]}}"""
        )
        assertNull("取不到就返回 null，不能抛", item.progressPercent())
    }

    @Test
    fun `标量形态不崩但返回 null`() {
        // 旧实现假设的形态。万一服务端改回标量，不该崩。
        val item = parseItem("""{"state":"running","extract_progress":42}""")
        assertNull(item.progressPercent())
    }

    @Test
    fun `字符串百分比形态不崩`() {
        val item = parseItem("""{"state":"running","extract_progress":"42%"}""")
        assertNull(item.progressPercent())
    }

    // ------------------------------------------------------- 状态字段

    /**
     * 官方枚举是 `done` / `waiting-file` / `pending` / `running` /
     * `failed` / `converting`——共 6 个，PRD 只写了 3 个。
     *
     * 客户端 `when` 只处理 `done` / `failed`，其余落 `else` 继续轮询，
     * 所以 4 个非终态都能正确等待。这条测试钉住「别把状态名改错」。
     */
    @Test
    fun `状态字段名是 state 不是 status`() {
        assertEquals("running", parseItem("""{"state":"running"}""").state)
        // 若服务端改用 status，state 会是默认值空串——
        // 那时客户端会永远落进 else 分支轮询到超时
        assertEquals("", parseItem("""{"status":"running"}""").state)
    }

    @Test
    fun `六种状态都能解析`() {
        listOf("done", "waiting-file", "pending", "running", "failed", "converting")
            .forEach { state ->
                assertEquals(state, parseItem("""{"state":"$state"}""").state)
            }
    }

    @Test
    fun `未知状态原样保留而不抛`() {
        // 应落进客户端 when 的 else 分支继续轮询，而不是被归为空串
        assertEquals("brand-new-state", parseItem("""{"state":"brand-new-state"}""").state)
    }
}