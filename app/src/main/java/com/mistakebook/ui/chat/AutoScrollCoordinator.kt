package com.mistakebook.ui.chat

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/** Scroll ownership for a reverse-layout chat list. */
class AutoScrollCoordinator {
    var following by mutableStateOf(true)
        private set

    var showJumpButton by mutableStateOf(false)
        internal set

    fun pause() {
        following = false
        showJumpButton = true
    }

    fun resume() {
        following = true
        showJumpButton = false
    }

    suspend fun jumpToBottom(state: LazyListState) {
        if (state.layoutInfo.totalItemsCount > 0) {
            state.animateScrollToItem(0)
        }
        resume()
    }
}

/**
 * 反向列表（index 0 = 最新一条，贴底）的滚动跟随。
 *
 * ## 谁在滚动
 *
 * Compose 官方 `LazyListState.isScrollInProgress` 混合了手势、fling 与程序滚动，
 * 不能当作「用户正在拖」。这里用 `interactionSource` 的拖拽状态判定手指：
 * 按住就交出滚动权，松手后等惯性滑完，再按几何位置决定要不要收回。
 */
@Composable
fun rememberAutoScrollEffect(
    state: LazyListState,
    latestItemKey: Long?
): AutoScrollCoordinator {
    val coordinator = remember { AutoScrollCoordinator() }
    val isDragged by state.interactionSource.collectIsDraggedAsState()
    val tolerancePx = with(LocalDensity.current) { BOTTOM_TOLERANCE_DP.roundToPx() }

    LaunchedEffect(isDragged, tolerancePx) {
        if (isDragged) {
            coordinator.pause()
        } else {
            snapshotFlow { state.isScrollInProgress }.first { !it }
            if (state.isAtBottom(tolerancePx)) coordinator.resume()
        }
    }

    LaunchedEffect(state, tolerancePx) {
        snapshotFlow { state.isAtBottom(tolerancePx) }
            .distinctUntilChanged()
            .collect { atBottom ->
                coordinator.showJumpButton = !coordinator.following || !atBottom
            }
    }

    // 只在「最新一条的 key 变了」时滚。流式期间 key 不变：
    // 气泡长高由反向布局自己锚在底边，不需要反复重启滚动动画。
    LaunchedEffect(latestItemKey, coordinator.following) {
        if (coordinator.following && latestItemKey != null) {
            snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > 0 }
            state.animateScrollToItem(0)
        }
    }

    return coordinator
}

/**
 * 反向布局里 index 0 就是最新一条，它贴在视口底边才算「在底部」。
 *
 * ## 为什么容差必须对称
 *
 * `viewportEndOffset` 是否把 `contentPadding` 算进去，两种约定都可能出现在真机上：
 * 完全停在底部时 `gap = viewportEndOffset - (offset + size)` 可能是 0，
 * 也可能是**负值**（底部 8dp 内边距被算进视口）。再叠加像素取整误差，
 * 早先的 `gap in 0..TOLERANCE` 会把「已经到底」判成「没到底」——
 * 后果是 `following` 永不恢复、箭头常驻，看起来像列表卡死。
 *
 * 因此判据是 `|gap| <= tolerance`。
 */
internal fun isAtBottomOfViewport(
    itemOffset: Int,
    itemSize: Int,
    viewportEndOffset: Int,
    tolerancePx: Int
): Boolean {
    val gap = viewportEndOffset - (itemOffset + itemSize)
    return gap <= tolerancePx && gap >= -tolerancePx
}

private fun LazyListState.isAtBottom(tolerancePx: Int): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    val newest = info.visibleItemsInfo.firstOrNull { it.index == 0 } ?: return false
    return isAtBottomOfViewport(
        itemOffset = newest.offset,
        itemSize = newest.size,
        viewportEndOffset = info.viewportEndOffset,
        tolerancePx = tolerancePx
    )
}

/** 与底部内边距同量级即可；再大就会把「刚上滑一点」也算成底部。 */
private val BOTTOM_TOLERANCE_DP = 12.dp
