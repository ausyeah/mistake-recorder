package com.mistakebook.pipeline

import com.mistakebook.net.ApiError
import com.mistakebook.net.ApiErrorKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRetryPolicyTest {

    @Test
    fun `authentication rate limit and cancellation are not retried`() {
        listOf(ApiErrorKind.AUTH, ApiErrorKind.RATE_LIMIT, ApiErrorKind.CANCELLED)
            .forEach { assertFalse("$it must not be retried", shouldRetryLlmCall(it)) }
    }

    @Test
    fun `transient and recoverable errors remain retryable`() {
        listOf(
            ApiErrorKind.NETWORK,
            ApiErrorKind.TIMEOUT,
            ApiErrorKind.SERVER,
            ApiErrorKind.BAD_RESPONSE,
            ApiErrorKind.UNKNOWN
        ).forEach { assertTrue("$it may be retried", shouldRetryLlmCall(it)) }
    }

    @Test
    fun `only HTTP 400 enables the next request variant`() {
        assertTrue(
            shouldTryNextLlmVariant(ApiError(ApiErrorKind.BAD_RESPONSE, detail = "HTTP 400"))
        )
        listOf(
            ApiError(ApiErrorKind.NETWORK),
            ApiError(ApiErrorKind.SERVER, detail = "HTTP 500"),
            ApiError(ApiErrorKind.BAD_RESPONSE, detail = "大模型未返回内容")
        ).forEach { assertFalse("$it must not switch request variants", shouldTryNextLlmVariant(it)) }
    }
}
