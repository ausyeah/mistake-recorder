package com.mistakebook.net.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 逐行解析测试。
 *
 * 覆盖的都是**真实遇到过或极可能遇到**的形态：
 * 网关省掉冒号后的空格、CRLF 行尾、keep-alive 注释行、不发 [DONE]、
 * 末帧没有 content、200 里塞错误体。
 */
class SseLineParserTest {

    private fun chunk(text: String) =
        """{"choices":[{"index":0,"delta":{"content":"$text"},"finish_reason":null}]}"""

    /** 喂一帧，断言它是 Chunk 且文本相符。 */
    private fun assertChunk(parser: SseLineParser, line: String, expected: String): SseEvent.Chunk {
        val event = parser.accept(line)
        assertTrue("期望 Chunk，实际 $event", event is SseEvent.Chunk)
        return (event as SseEvent.Chunk).also { assertEquals(expected, it.text) }
    }

    @Test
    fun `标准 data 行解析出增量`() {
        val parser = SseLineParser()
        val chunk = assertChunk(parser, "data: ${chunk("你好")}", "你好")
        assertNull(chunk.finishReason)
    }

    @Test
    fun `冒号后没有空格也能解析`() {
        // 不少中转站（自研 nginx 插件）会省掉这个空格。
        assertChunk(SseLineParser(), "data:${chunk("hi")}", "hi")
    }

    @Test
    fun `CRLF 行尾也能解析`() {
        // Windows 侧网关会发 \r\n。不处理的话 JSON 尾部多个 \r 直接解析失败。
        assertChunk(SseLineParser(), "data: ${chunk("CRLF")}\r", "CRLF")
    }

    @Test
    fun `空行被忽略且不影响下一帧`() {
        // 真实 SSE 每个事件后都有空行，解析器必须能无视它
        val parser = SseLineParser()
        assertNull(parser.accept(""))
        assertChunk(parser, "data: ${chunk("A")}", "A")
        assertNull(parser.accept(""))
        assertNull(parser.accept("   "))
        assertChunk(parser, "data: ${chunk("B")}", "B")
    }

    @Test
    fun `DONE 哨兵被识别`() {
        val parser = SseLineParser()
        assertChunk(parser, "data: ${chunk("完")}", "完")
        assertEquals(SseEvent.Done, parser.accept("data: [DONE]"))
    }

    @Test
    fun `DONE 前后有空格也认`() {
        assertEquals(SseEvent.Done, SseLineParser().accept("data:   [DONE]  "))
    }

    @Test
    fun `DONE 之后的内容不再处理`() {
        val parser = SseLineParser()
        assertEquals(SseEvent.Done, parser.accept("data: [DONE]"))
    }

    @Test
    fun `keep-alive 注释行被忽略`() {
        val parser = SseLineParser()
        assertEquals(SseEvent.Ignore, parser.accept(": keep-alive"))
        assertEquals(SseEvent.Ignore, parser.accept("event: message"))
        assertEquals(SseEvent.Ignore, parser.accept("id: 42"))
        assertEquals(SseEvent.Ignore, parser.accept("retry: 3000"))
    }

    @Test
    fun `空 data 行被忽略`() {
        assertNull(SseLineParser().accept("data:"))
    }

    @Test
    fun `首帧只有 role 没有 content`() {
        // OpenAI 的第一帧固定是 {"delta":{"role":"assistant","content":""}}
        val parser = SseLineParser()
        val event = parser.accept(
            """data: {"choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}"""
        )
        assertEquals("", (event as SseEvent.Chunk).text)
    }

    @Test
    fun `末帧 content 为 null 不崩`() {
        val parser = SseLineParser()
        val event = parser.accept(
            """data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
        )
        val chunk = event as SseEvent.Chunk
        assertEquals("", chunk.text)
        assertEquals("stop", chunk.finishReason)
    }

    @Test
    fun `delta 整个缺失也不崩`() {
        val parser = SseLineParser()
        val event = parser.accept("""data: {"choices":[{"index":0,"finish_reason":"stop"}]}""")
        assertEquals("", (event as SseEvent.Chunk).text)
    }

    @Test
    fun `finish_reason 为 length 能取到`() {
        // 被 max_tokens 截断——必须能识别，否则和正常结束无法区分。
        val parser = SseLineParser()
        val chunk = assertChunk(
            parser,
            """data: {"choices":[{"index":0,"delta":{"content":"半截"},"finish_reason":"length"}]}""",
            "半截"
        )
        assertEquals("length", chunk.finishReason)
    }

    @Test
    fun `末帧带 usage 能取到 token 数`() {
        val parser = SseLineParser()
        val event = parser.accept(
            """data: {"choices":[],"usage":{"prompt_tokens":1200,"completion_tokens":380,"total_tokens":1580}}"""
        )
        val chunk = event as SseEvent.Chunk
        assertEquals(1200, chunk.promptTokens)
        assertEquals(380, chunk.completionTokens)
    }

    @Test
    fun `200 响应里塞错误体被识别为坏帧`() {
        val parser = SseLineParser()
        val event = parser.accept("""data: {"error":{"message":"额度已用尽","type":"billing"}}""")
        assertTrue(event is SseEvent.Malformed)
        assertEquals("额度已用尽", (event as SseEvent.Malformed).raw)
    }

    @Test
    fun `error 对象字段为空时回退到 type`() {
        val parser = SseLineParser()
        val event = parser.accept("""data: {"error":{"message":"","type":"billing"}}""")
        assertEquals("billing", (event as SseEvent.Malformed).raw)
    }

    @Test
    fun `坏帧不影响后续好帧`() {
        // 网关偶尔会把某一行改坏，不能因此整条回复作废。
        val parser = SseLineParser()
        assertTrue(parser.accept("data: {不是 JSON") is SseEvent.Malformed)
        assertChunk(parser, "data: ${chunk("还能用")}", "还能用")
    }

    @Test
    fun `未知字段不会导致解析失败`() {
        // 中转站爱加自己的字段：id / system_fingerprint / object…
        val parser = SseLineParser()
        assertChunk(
            parser,
            """data: {"id":"chatcmpl-1","object":"chat.completion.chunk","system_fingerprint":"fp_x","created":123,"choices":[{"index":0,"delta":{"content":"OK"},"finish_reason":null}]}""",
            "OK"
        )
    }

    @Test
    fun `空 choices 不崩`() {
        val parser = SseLineParser()
        assertEquals("", (parser.accept("""data: {"choices":[]}""") as SseEvent.Chunk).text)
    }

    @Test
    fun `多候选取第一个`() {
        // n>1 时服务端会按 index 分别推帧，取第一个即可（对话只用一个候选）
        val parser = SseLineParser()
        val event = parser.accept(
            """data: {"choices":[{"index":0,"delta":{"content":"第一"}},{"index":1,"delta":{"content":"第二"}}]}"""
        )
        assertEquals("第一", (event as SseEvent.Chunk).text)
    }

    @Test
    fun `一次真实回复的完整序列`() {
        // 端到端形状：首帧 role、若干内容帧、末帧 finish_reason、[DONE]
        val parser = SseLineParser()
        val collected = mutableListOf<String>()
        var finish: String? = null
        var sawDone = false

        val lines = listOf(
            """data: {"choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}""",
            """data: ${chunk("设 ")}""",
            """data: ${chunk("x = 1")}""",
            """data: ${chunk("，则 x² = 1。")}""",
            """data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
            "data: [DONE]"
        )
        for (line in lines) {
            when (val event = parser.accept(line)) {
                null, SseEvent.Ignore -> Unit
                is SseEvent.Chunk -> {
                    collected += event.text
                    event.finishReason?.let { finish = it }
                }
                SseEvent.Done -> sawDone = true
                is SseEvent.Malformed -> throw AssertionError("不该出现坏帧: ${event.raw}")
            }
        }
        assertEquals("设 x = 1，则 x² = 1。", collected.joinToString(""))
        assertEquals("stop", finish)
        assertTrue(sawDone)
    }
}
