package com.mistakebook.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ChatViewModel flush() 合成正文与思考的格式测试。
 */
class ChatViewModelFlushTest {

    // 模拟 ChatViewModel 的 joinThinkingAndAnswer 内部逻辑
    private fun joinThinkingAndAnswer(answer: String, thought: String): String = when {
        thought.isBlank() -> answer
        answer.isBlank() -> ChatThinking.tags(thought)
        else -> ChatThinking.tags(thought) + "\n\n" + answer
    }

    @Test
    fun `只有思考没有正文时_正确加上_think_标签`() {
        val result = joinThinkingAndAnswer("", "这是一段纯思考")
        assertEquals("\n<think>这是一段纯思考</think>", result)
    }

    @Test
    fun `只有正文没有思考时_直接返回正文`() {
        val result = joinThinkingAndAnswer("这是一段纯正文", "")
        assertEquals("这是一段纯正文", result)
    }

    @Test
    fun `既有思考又有正文时_标签与正文用双换行隔开`() {
        val result = joinThinkingAndAnswer("这是正文", "这是思考")
        assertEquals("\n<think>这是思考</think>\n\n这是正文", result)
    }
    
    @Test
    fun `思考本身自带_think_标签时_不重复套标签`() {
        // ChatThinking.tags 内部处理了重复套标签的情况
        val result = joinThinkingAndAnswer("正文", "\n<think>自带标签的思考</think>")
        assertEquals("\n<think>自带标签的思考</think>\n\n正文", result)
    }
}
