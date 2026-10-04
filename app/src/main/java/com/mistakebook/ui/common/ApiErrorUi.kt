package com.mistakebook.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mistakebook.R
import com.mistakebook.net.ApiError
import com.mistakebook.net.ApiErrorKind

// 网络错误 -> 中文提示（PRD 4.3）
@StringRes
fun ApiErrorKind.messageRes(): Int = when (this) {
    ApiErrorKind.NO_KEY -> R.string.error_no_key
    ApiErrorKind.AUTH -> R.string.error_auth
    ApiErrorKind.RATE_LIMIT -> R.string.error_rate_limit
    ApiErrorKind.TIMEOUT -> R.string.error_timeout
    ApiErrorKind.NETWORK -> R.string.error_network
    ApiErrorKind.SERVER -> R.string.error_server
    ApiErrorKind.CANCELLED -> R.string.error_cancelled
    ApiErrorKind.BAD_RESPONSE -> R.string.error_bad_response
    ApiErrorKind.UNKNOWN -> R.string.error_bad_response
}

@Composable
fun ApiError.displayText(): String {
    val base = stringResource(kind.messageRes())
    // 服务端原始 msg 只在不是通用提示时补充，避免和中文提示重复
    if (kind == ApiErrorKind.SERVER && serverMessage.isNotBlank()) {
        return base + "：" + serverMessage
    }
    return base
}

/** 从任务表里的 errorKind 字符串还原错误类型。 */
fun errorKindOf(raw: String?): ApiErrorKind? =
    ApiErrorKind.entries.firstOrNull { it.name == raw }
