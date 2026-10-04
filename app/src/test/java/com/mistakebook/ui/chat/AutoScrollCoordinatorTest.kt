package com.mistakebook.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoScrollCoordinatorTest {
    @Test
    fun dragPauseDisablesFollowingUntilResumed() {
        val coordinator = AutoScrollCoordinator()

        coordinator.pause()

        assertFalse(coordinator.following)
        assertTrue(coordinator.showJumpButton)

        coordinator.resume()

        assertTrue(coordinator.following)
        assertFalse(coordinator.showJumpButton)
    }

    // ---- 反向布局的「是否已在底部」几何 ----

    @Test
    fun flushWithViewportBottomIsAtBottom() {
        assertTrue(
            isAtBottomOfViewport(
                itemOffset = 900,
                itemSize = 100,
                viewportEndOffset = 1000,
                tolerancePx = 32
            )
        )
    }

    @Test
    fun bottomPaddingInsideToleranceIsAtBottom() {
        // 底部 8dp 内边距被算进视口时间隙是正的，高密度屏上可到 24px 上下
        assertTrue(
            isAtBottomOfViewport(
                itemOffset = 900,
                itemSize = 100,
                viewportEndOffset = 1024,
                tolerancePx = 32
            )
        )
    }

    @Test
    fun roundingOvershootInsideToleranceIsAtBottom() {
        // 取整误差可能让内容底边略微越过视口底边，此时间隙为负
        assertTrue(
            isAtBottomOfViewport(
                itemOffset = 901,
                itemSize = 100,
                viewportEndOffset = 1000,
                tolerancePx = 32
            )
        )
    }

    @Test
    fun scrolledUpBeyondToleranceIsNotAtBottom() {
        assertFalse(
            isAtBottomOfViewport(
                itemOffset = 500,
                itemSize = 100,
                viewportEndOffset = 1000,
                tolerancePx = 32
            )
        )
    }

    @Test
    fun overshootBeyondToleranceIsNotAtBottom() {
        assertFalse(
            isAtBottomOfViewport(
                itemOffset = 1000,
                itemSize = 100,
                viewportEndOffset = 1000,
                tolerancePx = 32
            )
        )
    }
}
