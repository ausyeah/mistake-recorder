package com.mistakebook.pipeline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LLM 输出的 JSON 里含 LaTeX 时，`\t` `\r` `\n` `\b` `\f` 与 JSON 转义序列冲突。
 *
 * ## 现象（真机 v0.0.3，DeepSeek 输出）
 * 题干原文：
 * ```
 * 求极限 $\lim_{x\to0}\left[\frac{\ln(1 + x)}{x}\right]^{\frac{1}{e^x-1}}$
 * ```
 * 实际存进去的：
 * ```
 * 求极限 $\lim_{xo 0}\left[\frac{\ln(1 + x)}{x}
 * ight]^{{\frac{1}{{e^{x} - 1}}}$
 * ```
 * `\lim` `\left` `\frac` `\ln` `\infty` 完好；`\to` 变 TAB+`o`；`\right` 变 CR+`ight`。
 *
 * ## 根因
 * 这些字母**恰好都是合法的 JSON 转义首字母**：t=TAB r=CR n=LF b=BS f=FF。
 * `\l` `\i` `\f` 不是合法转义，所以 `\lim` `\ln` `\infty` 原样保留——
 * 观察到的「有的坏有的不坏」完全对上。
 *
 * 模型本应写 `\\to`（JSON 里 `\\` -> `\`，得到正确的 `\to`），
 * 但它经常混用单双反斜杠。提示词约束不可靠，**必须在解析层兜住**。
 */
class LatexEscapeCollisionTest {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /**
     * **真机 v0.0.3 的真实输出形态**：模型**混用**单双反斜杠。
     *
     * `\\lim` `\\left` `\\frac` `\\ln` 双写（正确，解析后还原成 `\lim` 等），
     * `\to` `\right` 单写（错误，被当成合法 JSON 转义吃掉）。
     *
     * 这份文本本身是**合法 JSON**——`\t` `\r` 都是合法转义，
     * 所以解析不报错，而是静默产出损坏的公式。这正是它难查的原因。
     */
    private val badRaw = """{"stem":"求极限 $\\lim_{x\to0}\\left[\\frac{\\ln(1+x)}{x}\right]^{1}$"}"""

    /** 模型全部正确双写时的形态。 */
    private val goodRaw = """{"stem":"求极限 $\\lim_{x\\to0}\\left[\\frac{\\ln(1+x)}{x}\\right]^{1}$"}"""

    /**
     * 先确证根因：合法 JSON 里的单反斜杠 `\t` `\r` 会被解析成控制字符。
     *
     * 这条断言是整个修复的地基——如果解析器保留了字面 `\t`，
     * 那问题就不在解析层，修法完全不同。
     */
    @Test
    fun `validJsonSilentlyCorruptsLatexEscapes`() {
        val parsed = json.parseToJsonElement(badRaw).jsonObject["stem"]!!.jsonPrimitive.content
        assertTrue("`\\to` 被吃成了制表符（0x09）", parsed.contains('\t'))
        assertTrue("`\\to` 的反斜杠已被消费", !parsed.contains("\\to"))
        assertTrue("双写的 `\\\\lim` 应当完好", parsed.contains("\\lim"))
        assertTrue("双写的 `\\\\frac` 应当完好", parsed.contains("\\frac"))
        assertTrue("`\\right` 被吃成了回车", parsed.contains('\r'))
        assertTrue("`\\right` 剩下了 'ight'", parsed.contains("ight]"))
    }

    /**
     * 对照实验：若模型**全部**单写，JSON 直接解析失败（`\l` 不是合法转义），
     * 于是走降级兜底。说明「全单写」与「混用」是两种不同的失败形态，
     * 修法必须同时覆盖——只修混用那一种，全单写时会漏。
     */
    @Test
    fun `allSingleBackslashFailsToParseAtAll`() {
        val allSingle = """{"stem":"$\lim_{x\to0}\frac{a}{b}$"}"""
        val failed = runCatching { json.parseToJsonElement(allSingle) }.isFailure
        assertTrue("全单写应导致解析失败（Invalid escaped char 'l'）", failed)
    }

    /** 模型正确双写时，解析结果就是干净的 LaTeX。 */
    @Test
    fun `doubleBackslashParsesToCorrectLatex`() {
        val parsed = json.parseToJsonElement(goodRaw).jsonObject["stem"]!!.jsonPrimitive.content
        assertTrue("应还原出 \\to", parsed.contains("\\to"))
        assertTrue("应还原出 \\right", parsed.contains("\\right"))
        assertFalse("不应含 TAB", parsed.contains('\t'))
        assertFalse("不应含 CR", parsed.contains('\r'))
    }

    /**
     * **核心修复**：解析前保护 LaTeX 命令里的反斜杠。
     *
     * 只在 JSON 字符串字面量内部操作，且只对「后跟已知 LaTeX 命令名」的
     * 转义首字母生效——这样模型正确双写的 `\\to` 不会被二次处理，
     * 而错误单写的 `\to` 会被补成 `\\to`，JSON 解析后正好还原成 `\to`。
     */
    @Test
    fun `protectionFixesSingleBackslashLatex`() {
        val protectedJson = LatexEscapes.protectLatexEscapes(badRaw)
        val parsed = json.parseToJsonElement(protectedJson).jsonObject["stem"]!!.jsonPrimitive.content
        assertTrue("应还原出 \\to，实际: $parsed", parsed.contains("\\to"))
        assertTrue("应还原出 \\right", parsed.contains("\\right"))
        assertTrue("应还原出 \\lim", parsed.contains("\\lim"))
        assertTrue("应还原出 \\left", parsed.contains("\\left"))
        assertTrue("应还原出 \\frac", parsed.contains("\\frac"))
        assertFalse("不应含 TAB", parsed.contains('\t'))
        assertFalse("不应含 CR", parsed.contains('\r'))
    }

    /**
     * 修复不能破坏模型**已经写对**的情况。
     *
     * 这是必须守住的不变量：`\\to` 若被再补一层就变成 `\\\to`，
     * 解析后是 `\to` + 残留反斜杠，公式反而更糟。
     */
    @Test
    fun `protectionLeavesCorrectInputUntouched`() {
        val protectedJson = LatexEscapes.protectLatexEscapes(goodRaw)
        val parsed = json.parseToJsonElement(protectedJson).jsonObject["stem"]!!.jsonPrimitive.content
        assertTrue("双反斜杠不应被改动，实际: $parsed", parsed.contains("\\to"))
        assertFalse("不应出现三反斜杠残留", parsed.contains("\\\\to"))
        assertFalse("不应含 TAB", parsed.contains('\t'))
    }

    /** JSON 的合法转义必须保持原样，否则结构就坏了。 */
    @Test
    fun `realJsonEscapesSurviveProtection`() {
        val raw = """{"a":"x\ty","b":"p\nq","c":"say \"hi\"","d":"back\\slash"}"""
        val protectedRaw = LatexEscapes.protectLatexEscapes(raw)
        val obj = json.parseToJsonElement(protectedRaw).jsonObject
        assertEquals("x\ty", obj["a"]!!.jsonPrimitive.content)
        assertEquals("p\nq", obj["b"]!!.jsonPrimitive.content)
        assertEquals("say \"hi\"", obj["c"]!!.jsonPrimitive.content)
        assertEquals("back\\slash", obj["d"]!!.jsonPrimitive.content)
    }

    /** 字符串字面量之外（键名、结构符号）不能被改动。 */
    @Test
    fun `keysAndStructureAreUntouched`() {
        val raw = """{"count":1,"flag":true}"""
        assertEquals(raw, LatexEscapes.protectLatexEscapes(raw))
    }

    /** 常见命令名都要覆盖，否则漏一个就还在坏。 */
    @Test
    fun `coversCommonCollisionCommands`() {
        val commands = listOf(
            "to", "text", "theta", "times", "tan", "top", "therefore", "not",
            "neq", "nabla", "ne", "nu", "notin", "nmid",
            "begin", "boxed", "beta", "bar", "big", "brace",
            "right", "rho", "rangle", "rfloor", "rfloor",
            "frac", "forall", "flat"
        )
        for (cmd in commands) {
            val raw = """{"s":"$${'$'}\\$cmd x ${'$'}$"}"""
            val out = LatexEscapes.protectLatexEscapes(raw)
            assertTrue(
                "命令 \\$cmd 未被保护：$raw -> $out",
                out.contains("\\\\$cmd")
            )
        }
    }
}
