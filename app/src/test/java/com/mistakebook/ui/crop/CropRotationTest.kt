package com.mistakebook.ui.crop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 裁剪页旋转 / 遮罩坐标变换的单元测试。
 *
 * ## 为什么这些性质必须守住
 * 「涂鸦一旋转就错位」这个问题改了四轮，每轮都在新地方出错。
 * 根本原因不是某个公式写错，而是架构上让**旋转去改写笔迹数据**——
 * 每转一次都是一次出错机会，而且错了不报错，只是慢慢跑偏。
 *
 * 现在笔迹存在未旋转的原图坐标系里，旋转只是一个整数。
 * 下面这些性质一旦成立，「旋转破坏涂鸦」在数学上就不可能发生。
 */
class CropRotationTest {

    private val eps = 1e-5f

    private fun assertClose(expected: Float, actual: Float, message: String = "") {
        assertTrue(
            "$message 期望 $expected 实际 $actual",
            abs(expected - actual) < eps
        )
    }

    /** 未旋转时不改变任何坐标。 */
    @Test
    fun `zeroQuartersIsIdentity`() {
        val p = rotateNormalized(0.3f, 0.7f, 0)
        assertClose(0.3f, p.x)
        assertClose(0.7f, p.y)
    }

    /**
     * 逆时针 90°：`(x,y) -> (y, 1-x)`。
     *
     * 注意这个公式**与宽高比无关**——推导里宽高比被约掉了。
     * 直觉上容易以为「正方形才成立」，那是把归一化和像素搞混了。
     */
    @Test
    fun `oneQuarterIsCounterClockwise`() {
        val p = rotateNormalized(0.25f, 0.5f, 1)
        assertClose(0.5f, p.x)
        assertClose(0.75f, p.y)
    }

    /**
     * 与 Android `Matrix.postRotate(-90f)` 的真实像素映射对齐。
     *
     * 这是最重要的一条：显示用的位图是 `Bitmap.createBitmap(..., matrix)` 生成的，
     * 像素级映射是 `源 (px,py) -> 目标 (py, W-1-px)`。
     * 归一化公式必须和它一致，否则遮罩与图片会有亚像素级的系统性偏移，
     * 而且偏移量随图片尺寸变化——这就是「有的图对、有的图偏」的来源。
     */
    @Test
    fun `matchesAndroidPostRotatePixelMapping`() {
        val sizes = listOf(1600 to 1200, 1200 to 1600, 1024 to 1024, 900 to 1600)
        for ((w, h) in sizes) {
            val corners = listOf(
                0 to 0,
                (w - 1) to 0,
                0 to (h - 1),
                (w - 1) to (h - 1)
            )
            for ((px, py) in corners) {
                val destX = py
                val destY = w - 1 - px
                val r = rotateNormalized(px.toFloat() / (w - 1), py.toFloat() / (h - 1), 1)
                assertClose(
                    destX.toFloat(),
                    r.x * (h - 1),
                    "尺寸 ${w}x$h 目标 X"
                )
                assertClose(
                    destY.toFloat(),
                    r.y * (w - 1),
                    "尺寸 ${w}x$h 目标 Y"
                )
            }
        }
    }

    /** 连转四圈回到原点。这是「旋转不会累积误差」的根本保证。 */
    @Test
    fun `fourQuartersReturnToOrigin`() {
        for (x in listOf(0f, 0.13f, 0.5f, 0.87f, 1f)) {
            for (y in listOf(0f, 0.27f, 0.5f, 0.91f, 1f)) {
                var cx = x
                var cy = y
                repeat(4) {
                    val r = rotateNormalized(cx, cy, 1)
                    cx = r.x
                    cy = r.y
                }
                assertClose(x, cx, "四次旋转后 X")
                assertClose(y, cy, "四次旋转后 Y")
            }
        }
    }

    /**
     * 屏幕坐标与原图坐标的往返换算必须闭合。
     *
     * 屏幕 -> 显示 -> 原图 用的是 `MASK_ROTATION_PERIOD - q`。
     * 这条不成立的话，用户点一下、看到笔迹跟手，但存下来的位置在漂。
     */
    @Test
    fun `displayRoundTripIsStable`() {
        for (q in 0 until 4) {
            for (x in listOf(0.2f, 0.5f, 0.8f)) {
                for (y in listOf(0.1f, 0.6f, 0.9f)) {
                    val display = rotateNormalized(x, y, q)
                    val back = rotateNormalized(
                        display.x,
                        display.y,
                        MASK_ROTATION_PERIOD - q
                    )
                    assertClose(x, back.x, "q=$q 往返 X")
                    assertClose(y, back.y, "q=$q 往返 Y")
                }
            }
        }
    }

    /**
     * 圈数必须规整到 0..3：`q` 与 `q + 4` 必须完全等价。
     *
     * 这是「连转四圈回到原样」能成立的前提。`rotationQuarter` 在按钮里
     * 按 `+1 % 4` 自增，如果规整写错（比如负数不处理），
     * 恢复到 0 圈那一步就会算成 -1 或 5，遮罩跟着转飞。
     *
     * 负数**向前**绕而不是向后：`(-1 + 4) % 4 == 3`，即「-1 圈」等于
     * 「3 圈」。这一条最早写错过——当时断言 `-1` 圈等价于不旋转，
     * 本地跑 Java 等价验证时又恰好没覆盖负圈数，于是本地全绿、CI 才炸。
     */
    @Test
    fun `quartersAreNormalized`() {
        // 取点必须让四个象限两两不同，否则检查会退化成恒真：
        // (0.3, 0.7) 在 q=0 与 q=3 下 x 恰好都是 0.3。
        // (0.2, 0.6)：q=0->(0.2,0.6) q=1->(0.6,0.8) q=2->(0.8,0.4) q=3->(0.4,0.2)
        val zero = rotateNormalized(0.2f, 0.6f, 0)
        val three = rotateNormalized(0.2f, 0.6f, 3)
        for (q in listOf(0, 4, 8, -4, -8)) {
            val p = rotateNormalized(0.2f, 0.6f, q)
            assertClose(zero.x, p.x, "圈数 $q 应等价于 0 圈")
            assertClose(zero.y, p.y, "圈数 $q 应等价于 0 圈")
        }
        for (q in listOf(3, 7, -1, -5)) {
            val p = rotateNormalized(0.2f, 0.6f, q)
            assertClose(three.x, p.x, "圈数 $q 应等价于 3 圈")
            assertClose(three.y, p.y, "圈数 $q 应等价于 3 圈")
        }
        // 负数向前绕，不是向后：-1 圈 = 3 圈（真转了 270 度）
        val minusOne = rotateNormalized(0.2f, 0.6f, -1)
        assertTrue("负圈数应向前绕到 3 圈，不能退化成 0 圈", abs(minusOne.x - zero.x) > eps)
        assertTrue("负圈数应向前绕到 3 圈，不能退化成 0 圈", abs(minusOne.y - zero.y) > eps)
    }

    /**
     * 笔画旋转：只变换坐标，笔的**数量和顺序**必须保持不变。
     *
     * 笔迹是「按落笔顺序的一串笔画」，撤销是 `dropLast(1)`。
     * 如果旋转顺手重排了列表，撤销就会删掉用户随手画的另一笔。
     */
    @Test
    fun `strokeRotationPreservesOrderAndCount`() {
        val strokes = listOf(
            MaskStroke(listOf(androidx.compose.ui.geometry.Offset(0.1f, 0.2f))),
            MaskStroke(
                listOf(
                    androidx.compose.ui.geometry.Offset(0.3f, 0.4f),
                    androidx.compose.ui.geometry.Offset(0.5f, 0.6f)
                )
            )
        )
        val rotated = strokes.map { it.rotated(2) }
        assertEquals(strokes.size, rotated.size)
        rotated.forEachIndexed { i, s ->
            assertEquals(strokes[i].points.size, s.points.size)
        }
    }
}
