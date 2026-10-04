package com.mistakebook.net.llm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 思考过程字段的解析测试。
 *
 * ## 为什么这条必须钉住
 *
 * 早先的实现**只读 `content`**，把 `reasoning_content` 整个丢了。
 * 结果是用户看到一个只有蓝点、没有内容的空气泡，而模型确实已经思考了——
 * 而**任何测试都不会失败**，因为丢弃一个字段不报错。
 */
class ReasoningFieldTest {

    private fun delta(json: String): StreamDelta =
        Json.decodeFromString(StreamDelta.serializer(), json)

    // ------------------------------------------------------------ 三个别名

    @Test
    fun `读 DeepSeek 与 Qwen 系的 reasoning_content`() {
        val d = delta("""{"reasoning_content":"先求导再代入"}""")
        assertEquals("先求导再代入", d.reasoningOrNull())
    }

    @Test
    fun `读部分中转站用的 reasoning`() {
        val d = delta("""{"reasoning":"换个思路"}""")
        assertEquals("换个思路", d.reasoningOrNull())
    }

    @Test
    fun `读 thinking`() {
        val d = delta("""{"thinking":"第三种命名"}""")
        assertEquals("第三种命名", d.reasoningOrNull())
    }

    @Test
    fun `多个同时存在时取第一个非空`() {
        val d = delta("""{"reasoning_content":"a","reasoning":"b","thinking":"c"}""")
        assertEquals("a", d.reasoningOrNull())
    }

    @Test
    fun `第一个是空串时顺延到第二个`() {
        // 反向用例：空串不算「有值」，否则后面的真内容被吃掉
        val d = delta("""{"reasoning_content":"","reasoning":"真的在这"}""")
        assertEquals("真的在这", d.reasoningOrNull())
    }

    // ------------------------------------------------------------ 与正文共存

    @Test
    fun `思考与正文可以同时存在`() {
        val d = delta("""{"content":"答案是 0","reasoning_content":"极限为 0"}""")
        assertEquals("答案是 0", d.content)
        assertEquals("极限为 0", d.reasoningOrNull())
    }

    @Test
    fun `没有思考字段时返回 null`() {
        assertEquals(null, delta("""{"content":"直接答"}""").reasoningOrNull())
    }

    @Test
    fun `首帧只有 role 不丢思考`() {
        val d = delta("""{"role":"assistant","reasoning_content":""}""")
        assertEquals("assistant", d.role)
        assertEquals(null, d.reasoningOrNull())
    }

    @Test
    fun `未知字段不导致解析失败`() {
        val d = delta("""{"content":"x","reasoning_content":"想","foo":1,"bar":{"a":2}}""")
        assertEquals("想", d.reasoningOrNull())
    }

    // ------------------------------------------------------------ SSE 帧

    @Test
    fun `SSE 帧能取出思考`() {
        val parser = SseLineParser()
        val event = parser.accept(
            """data: {"choices":[{"index":0,"delta":{"reasoning_content":"推理中","content":""},"finish_reason":null}]}"""
        )
        val chunk = event as SseEvent.Chunk
        assertEquals("推理中", chunk.thinking)
        assertEquals("", chunk.text)
    }

    @Test
    fun `SSE 帧思考与正文分别取出`() {
        val parser = SseLineParser()
        val event = parser.accept(
            """data: {"choices":[{"index":0,"delta":{"reasoning_content":"想完了","content":"答案是 42"},"finish_reason":null}]}"""
        )
        val chunk = event as SseEvent.Chunk
        assertEquals("想完了", chunk.thinking)
        assertEquals("答案是 42", chunk.text)
    }

    @Test
    fun `普通帧的 thinking 为空串而非 null`() {
        val parser = SseLineParser()
        val event = parser.accept("""data: {"choices":[{"index":0,"delta":{"content":"x"}}]}""")
        assertEquals("", (event as SseEvent.Chunk).thinking)
    }

    // ------------------------------------------------------------ 非流式

    @Test
    fun `非流式的 ChoiceMessage 也读思考`() {
        val message = Json.decodeFromString(
            ChoiceMessage.serializer(),
            """{"role":"assistant","content":"答","reasoning_content":"想"}"""
        )
        assertEquals("想", message.reasoningContent)
        assertEquals("答", message.content)
    }

    @Test
    fun `非流式没有思考字段时是空串`() {
        val message = Json.decodeFromString(ChoiceMessage.serializer(), """{"content":"答"}""")
        assertEquals("", message.reasoningContent)
    }

    private val Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
}
