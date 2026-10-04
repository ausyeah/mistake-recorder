package com.mistakebook.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `<think>` 分隔解析的回归测试。
 *
 * ## 用户报告的实际现象（截图）
 *
 * 思考块里**直接显示了字面量 `<think>` 和 `</think>`**，而且出现了**好几对**：
 *
 * ```
 * <think> user is asking for my suggestions ... framework-level </think>
 * <think> report that acknowledges it lacks specific error data ... 错题 </think>
 * <think> 分类表，阅读错题日志）
 * ...
 * Good aspects:
 * - Clear structure
 * Potential issues/suggestions:
 * ```
 *
 * 而右上角的字数显示只有「3 字」，与展开后几千字完全对不上。
 *
 * ## 两个独立的原因
 *
 * 1. **标记泄漏**：部分模型在 `content` 流里**自带字面量标记**，
 *    而不是把它们放在 `reasoning_content` 字段。这些标记必须被剥掉，
 *    绝不能显示给用户。
 * 2. **多对标记**：`split` 只找第一对 `OPEN`/`CLOSE`，剩下的标记
 *    原样留在正文里；而当第一对之后又是新的一对时，正文和思考的边界就乱了。
 */
class ChatThinkingSplitTest {

    // ------------------------------------------------------- 基础

    @Test
    fun `无标记时整段是正文`() {
        val (thinking, answer) = ChatThinking.split("直接回答，没有思考")
        assertEquals("", thinking)
        assertEquals("直接回答，没有思考", answer)
    }

    @Test
    fun `一对标记`() {
        val (thinking, answer) = ChatThinking.split("<think>推理中</think>答案在这里")
        assertEquals("推理中", thinking)
        assertEquals("答案在这里", answer)
    }

    @Test
    fun `只思考没回答`() {
        val (thinking, answer) = ChatThinking.split("<think>只有推理</think>")
        assertEquals("只有推理", thinking)
        assertEquals("", answer)
    }

    /** 没有闭合标记时整段当思考——宁可多显示，不把思考混进正文。 */
    @Test
    fun `缺闭合标记时整段是思考`() {
        val (thinking, answer) = ChatThinking.split("<think>流式输出到一半被截断", streaming = true)
        assertTrue("应该整段是思考", thinking.contains("流式输出到一半"))
        assertEquals("", answer)
    }

    /**
     * 孤立的 `</think>`（前面没有 `<think>`）是**正文的一部分**，不能剥掉。
     *
     * 模型偶尔漏写开始标记，这时 `</think>` 出现在用户要读的正文中间。
     * 剥掉它会让用户看到的内容凭空少一块。
     */
    @Test
    fun `孤立的结束标记原样保留在正文里`() {
        val (_, answer) = ChatThinking.split("只有</think>没有开始")
        assertEquals("只有</think>没有开始", answer)
    }

    // ------------------------------------------------------- 本次修的核心

    /**
     * 截图里的真实形状：**标记嵌套在正文中间，而且有多对**。
     *
     * 期望：字面量标记**一个都不能出现在界面里**。
     */
    @Test
    fun `多对标记不泄漏到界面`() {
        val raw = "<think>第一段思考</think>中间正文<think>第二段思考</think>结尾正文"
        val (thinking, answer) = ChatThinking.split(raw)

        assertFalse("正文不该有开始标记", answer.contains("<think>"))
        assertFalse("正文不该有结束标记", answer.contains("</think>"))
        assertFalse("思考不该有标记", thinking.contains("<think>"))
        assertFalse("思考不该有结束标记", thinking.contains("</think>"))
    }

    /** 所有思考段应合并，全部思考都要能看到。 */
    @Test
    fun `多对标记的思考全部保留`() {
        val raw = "<think>第一段</think>正文<think>第二段</think>"
        val (thinking, _) = ChatThinking.split(raw)
        assertTrue("第一段", thinking.contains("第一段"))
        assertTrue("第二段", thinking.contains("第二段"))
    }

    /** 所有正文段都应保留，不能丢。 */
    @Test
    fun `多对标记的正文全部保留`() {
        val raw = "<think>思考</think>正文A<think>思考</think>正文B"
        val (_, answer) = ChatThinking.split(raw)
        assertTrue("正文A", answer.contains("正文A"))
        assertTrue("正文B", answer.contains("正文B"))
    }

    /** 截图里那种「`<think>分类表，阅读错题日志）` 后面没有闭合」的情况。 */
    @Test
    fun `嵌套的未闭合标记也能兜住`() {
        val raw = "<think>思考</think>Good aspects:\n<think>分类表，阅读错题日志）\n- Clear structure"
        val (thinking, answer) = ChatThinking.split(raw, streaming = false)

        assertFalse(answer.contains("<think>"))
        assertFalse(answer.contains("</think>"))
        // 未闭合的「思考」不该把后面的正常正文吞掉
        assertTrue("正文应保留正常内容", answer.contains("Clear structure"))
    }

    /** 只有标记、没有内容时不该产生空白段。 */
    @Test
    fun `空标记不产生空段`() {
        val (thinking, answer) = ChatThinking.split("<think></think>答案")
        assertEquals("", thinking)
        assertEquals("答案", answer)
    }

    @Test
    fun `连续标记`() {
        val (thinking, answer) = ChatThinking.split("<think>a</think><think>b</think>正文")
        assertFalse(answer.contains("think>"))
        assertTrue(answer.contains("正文"))
        assertTrue(thinking.contains("a") || thinking.contains("b"))
    }

    /**
     * 流式过程中同一个未闭合标记的**两种正确处理**。
     *
     * - 流式中：后面确实还是推理，归思考（用户要看到「正在想什么」）
     * - 已结束：模型忘了闭合，后面多半是答案。归思考的话用户会看到一个
     *   巨大的思考块却找不到答案——正是截图里的现象。
     */
    @Test
    fun `未闭合标记在流式与结束后处理不同`() {
        val raw = "<think>推理中，然后是正文"

        val (thinkingWhileStreaming, answerWhileStreaming) = ChatThinking.split(raw, streaming = true)
        assertTrue("流式中应视为思考", thinkingWhileStreaming.contains("推理中"))
        assertEquals("流式中不该提前当成答案", "", answerWhileStreaming)

        val (thinkingAfter, answerAfter) = ChatThinking.split(raw, streaming = false)
        assertTrue("结束后正文要能拿到内容", answerAfter.contains("正文"))
        assertFalse("结束后不该把答案吞进思考", thinkingAfter.contains("正文"))
    }

    // ------------------------------------------------------- 字数显示

    /**
     * 字数必须等于**清理后**的思考长度。
     *
     * 截图里「3 字」与几千字的实际内容对不上——
     * 界面把「含标记的原文」和「字数」算成了两个东西。
     * 这条测试钉住两者必须一致。
     */
    @Test
    fun `字数与清理后的思考一致`() {
        val raw = "<think>" + "思".repeat(500) + "</think>答案"
        val (thinking, _) = ChatThinking.split(raw)
        assertEquals("界面显示的字数必须等于这里的长度", 500, thinking.length)
    }

    @Test
    fun `多对标记的字数是全部思考之和`() {
        val raw = "<think>" + "甲".repeat(10) + "</think>正文<think>" + "乙".repeat(20) + "</think>"
        val (thinking, _) = ChatThinking.split(raw)
        assertEquals(30, thinking.length)
    }

    // ------------------------------------------------------- 往返

    /** `tags` 标记过的内容拆回来应还原。 */
    @Test
    fun `tags 拆回后还原`() {
        val original = "推理内容"
        val wrapped = ChatThinking.tags(original)
        assertEquals(original, ChatThinking.thinkingOf(wrapped))
    }

    @Test
    fun `已带标记的内容不被重复包裹`() {
        val wrapped = ChatThinking.tags("内容")
        assertEquals("应原样返回", wrapped, ChatThinking.tags(wrapped))
    }

    @Test
    fun `answerOf 剥掉标记`() {
        assertEquals("答案", ChatThinking.answerOf("<think>思考</think>答案"))
    }
}