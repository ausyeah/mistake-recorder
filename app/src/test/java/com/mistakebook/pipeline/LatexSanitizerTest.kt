package com.mistakebook.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LatexSanitizer] 回归测试。
 *
 * 每个 case 都对应一个**真机上观察到的坏输出**，不是凭空构造的边界值。
 * 改动清洗逻辑前先跑这个。
 *
 * 方法名用英文：中文 backtick 方法名在部分 JVM 环境下会导致初始化错误。
 */
class LatexSanitizerTest {

    @Test
    fun unclosedBraceIsClosed() {
        // 真机案例：模型输出 F\left(\frac{y}{x} 后就断了
        assertEquals("\\frac{1}{2}", LatexSanitizer.balanceBraces("\\frac{1}{2"))
        assertEquals(
            "F\\left(\\frac{y}{x}",
            LatexSanitizer.balanceBraces("F\\left(\\frac{y}{x}")
        )
    }

    @Test
    fun balancedBraceUnchanged() {
        val input = "\\frac{a+b}{c-d}"
        assertEquals(input, LatexSanitizer.balanceBraces(input))
    }

    @Test
    fun extraClosingBraceIsTolerated() {
        // 不抛异常：多一个 } 时保留原样交给 KaTeX 自己处理
        val input = "\\frac{1}{2}}"
        assertEquals(input, LatexSanitizer.balanceBraces(input))
    }

    @Test
    fun unpairedLeftGetsRightDot() {
        // 真机案例：$F\left(\frac{y}{x},\frac{z}{x}\right) = 0$ 整条渲染失败
        val input = "F\\left(\\frac{y}{x}"
        val cleaned = LatexSanitizer.balanceDelimiters(input)
        assertTrue(cleaned.contains("\\right."))
        assertTrue(LatexSanitizer.looksRenderable(cleaned))
    }

    @Test
    fun pairedLeftRightUnchanged() {
        val input = "\\left( x \\right)"
        assertEquals(input, LatexSanitizer.balanceDelimiters(input))
    }

    @Test
    fun moreRightsThanLeftsUnchanged() {
        val input = "x \\right)"
        assertEquals(input, LatexSanitizer.balanceDelimiters(input))
    }

    @Test
    fun decorativeUnknownCommandDegradesToText() {
        // 装饰性命令：降级为正体文本，只是排版变朴素，含义不变
        assertEquals("bm x", LatexSanitizer.dropUnknownCommands("\\bm x"))
        assertEquals("degree", LatexSanitizer.dropUnknownCommands("\\degree"))
    }

    @Test
    fun semanticCommandIsDeletedNotDegraded() {
        // 关键：\bar 降级会变成 "barx"，含义被改掉了，比显示源码危险得多
        // 只删命令本身，花括号保留（交给后续 balanceBraces / KaTeX 处理）
        assertEquals("{x}", LatexSanitizer.dropUnknownCommands("\\bar{x}"))
        assertEquals("{y}", LatexSanitizer.dropUnknownCommands("\\hat{y}"))
        assertEquals("v", LatexSanitizer.dropUnknownCommands("\\vec v"))
    }

    @Test
    fun semanticDeletionKeepsSurroundingFormula() {
        // 删掉语义命令不能破坏整条公式的其他部分
        val cleaned = LatexSanitizer.dropUnknownCommands("F\\bar{x} + \\frac{1}{2}")
        assertFalse(cleaned.contains("\\bar"))
        assertFalse(cleaned.contains("bar"))
        assertTrue(cleaned.contains("\\frac{1}{2}"))
        assertTrue(cleaned.contains("F"))
    }

    @Test
    fun standardCommandsFullyPreserved() {
        val input = "\\frac{\\sqrt{x}}{\\sum_{i=1}^{n} \\alpha_i}"
        assertEquals(input, LatexSanitizer.dropUnknownCommands(input))
    }

    @Test
    fun oddDollarCountIsStripped() {
        val dollar = "$"
        // 配对已断时全删，比留着当普通字符更干净
        assertEquals("x", LatexSanitizer.stripStrayDollar("x" + dollar))
        assertEquals("x", LatexSanitizer.stripStrayDollar(dollar + "x"))
    }

    @Test
    fun evenDollarCountUnchanged() {
        val input = "a" + "$" + "b" + "$" + "c"
        assertEquals(input, LatexSanitizer.stripStrayDollar(input))
    }

    @Test
    fun fullPipelineHandlesTypicalBrokenInput() {
        val broken = "F\\left(\\frac{y}{x},\\frac{z}{x}\\right) = 0"
        val cleaned = LatexSanitizer.clean(broken)
        assertTrue("should be renderable", LatexSanitizer.looksRenderable(cleaned))
    }

    @Test
    fun emptyInputDoesNotCrash() {
        assertEquals("", LatexSanitizer.clean(""))
        assertEquals("   ", LatexSanitizer.clean("   "))
        assertFalse(LatexSanitizer.looksRenderable(""))
    }

    @Test
    fun mixedCommandsHandledByTheirOwnRule() {
        // 装饰性 -> 降级保留字母；语义性 -> 直接删掉
        val cleaned = LatexSanitizer.dropUnknownCommands("\\foo \\bar{x}")
        assertTrue("装饰性命令降级保留文字", cleaned.contains("foo"))
        assertFalse("语义性命令必须删除", cleaned.contains("\\bar"))
        assertFalse("语义性命令不能降级成 bar", cleaned.contains("bar"))
        assertTrue("上一符号本身保留", cleaned.contains("x"))
    }
}
