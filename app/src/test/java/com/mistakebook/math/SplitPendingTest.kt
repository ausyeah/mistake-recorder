package com.mistakebook.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `splitPending` 纯函数逻辑测试。
 *
 * 测试场景覆盖：
 * 1. 空列表情况：返回空列表
 * 2. 正常分批：
 *    - 元素数量小于批次上限（单批次）
 *    - 元素数量等于批次上限（单批次）
 *    - 元素数量为批次上限的整倍数（多批次，且无末尾空批次）
 *    - 元素数量大于批次上限且有余数（多批次，末尾为小批次）
 * 3. 边界与异常参数：
 *    - batchLimit 零值或负值时自动矫正为 1
 * 4. 数据完整性与顺序：
 *    - 展开后所有元素与原列表完全一致且顺序保持
 */
class SplitPendingTest {

    @Test
    fun `空列表返回空列表`() {
        val result = splitPending(emptyList(), 6)
        assertTrue("空列表输入应该返回空列表", result.isEmpty())
    }

    @Test
    fun `元素数量小于批次上限时返回单批次`() {
        val keys = listOf("k1", "k2", "k3")
        val result = splitPending(keys, 5)
        assertEquals(1, result.size)
        assertEquals(listOf("k1", "k2", "k3"), result[0])
    }

    @Test
    fun `元素数量等于批次上限时不切分`() {
        val keys = listOf("k1", "k2", "k3", "k4", "k5")
        val result = splitPending(keys, 5)
        assertEquals(1, result.size)
        assertEquals(listOf("k1", "k2", "k3", "k4", "k5"), result[0])
    }

    @Test
    fun `元素数量为批次上限整倍数时按上限分成相等批次`() {
        val keys = listOf("k1", "k2", "k3", "k4", "k5", "k6")
        val result = splitPending(keys, 3)
        assertEquals(2, result.size)
        assertEquals(listOf("k1", "k2", "k3"), result[0])
        assertEquals(listOf("k4", "k5", "k6"), result[1])
    }

    @Test
    fun `元素数量大于批次上限且有余数时末尾为余数批次`() {
        val keys = (1..7).map { "k$it" }
        val result = splitPending(keys, 3)
        assertEquals(3, result.size)
        assertEquals(listOf("k1", "k2", "k3"), result[0])
        assertEquals(listOf("k4", "k5", "k6"), result[1])
        assertEquals(listOf("k7"), result[2])
    }

    @Test
    fun `batchLimit为0时自动矫正为1`() {
        val keys = listOf("a", "b", "c")
        val result = splitPending(keys, 0)
        assertEquals(3, result.size)
        assertEquals(listOf("a"), result[0])
        assertEquals(listOf("b"), result[1])
        assertEquals(listOf("c"), result[2])
    }

    @Test
    fun `batchLimit为负数时自动矫正为1`() {
        val keys = listOf("a", "b", "c")
        val result = splitPending(keys, -5)
        assertEquals(3, result.size)
        assertEquals(listOf("a"), result[0])
        assertEquals(listOf("b"), result[1])
        assertEquals(listOf("c"), result[2])
    }

    @Test
    fun `所有批次展开后元素与原列表完全一致且保持顺序`() {
        val keys = (1..25).map { "key_$it" }
        val result = splitPending(keys, 6)
        val flattened = result.flatten()
        assertEquals(keys, flattened)
    }
}
