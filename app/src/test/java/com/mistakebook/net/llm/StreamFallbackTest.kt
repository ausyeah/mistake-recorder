package com.mistakebook.net.llm

import com.mistakebook.net.ApiError
import com.mistakebook.net.ApiErrorKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 降级判定测试。
 *
 * 这个函数决定「要不要偷偷再发一次请求」，判错的代价都不小：
 * 该降级不降级 -> 用户看到「失败了」；不该降级还降级 -> 白烧配额、
 * 甚至把真实错误冲成一句笼统的失败。
 */
class StreamFallbackTest {

    private fun error(kind: ApiErrorKind) = ApiError(kind = kind)

    @Test
    fun `已经吐过内容绝不降级`() {
        // 最重要的一条：降级会重发，用户会看到同一段话出现两遍。
        listOf(
            ApiErrorKind.NETWORK, ApiErrorKind.TIMEOUT,
            ApiErrorKind.BAD_RESPONSE, ApiErrorKind.UNKNOWN
        ).forEach { kind ->
            assertFalse("$kind 不该降级", StreamFallback.shouldFallback(error(kind), deltasEmitted = 1))
        }
    }

    @Test
    fun `鉴权限流服务端错误不降级`() {
        // 这些重发一次还是同样的结果，只是白烧一次配额。
        listOf(
            ApiErrorKind.AUTH,
            ApiErrorKind.RATE_LIMIT,
            ApiErrorKind.SERVER,
            ApiErrorKind.NO_KEY
        ).forEach { kind ->
            assertFalse("$kind 不该降级", StreamFallback.shouldFallback(error(kind), deltasEmitted = 0))
        }
    }

    @Test
    fun `用户取消不降级`() {
        // 点了停止还偷偷重发，用户会觉得「停不下来」。
        assertFalse(StreamFallback.shouldFallback(error(ApiErrorKind.CANCELLED), deltasEmitted = 0))
    }

    @Test
    fun `连接类问题降级`() {
        listOf(
            ApiErrorKind.NETWORK,
            ApiErrorKind.TIMEOUT,
            ApiErrorKind.BAD_RESPONSE,
            ApiErrorKind.UNKNOWN
        ).forEach { kind ->
            assertTrue("$kind 该降级", StreamFallback.shouldFallback(error(kind), deltasEmitted = 0))
        }
    }
}
