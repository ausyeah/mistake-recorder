package com.mistakebook.ui.theme

import androidx.compose.ui.graphics.Color

// 品牌主色与中性色板（Material3 动态色关闭，保证跨设备一致）
val BrandBlue = Color(0xFF4F7DF3)
val BrandBlueDark = Color(0xFF2F5FD0)
val BrandBlueLight = Color(0xFFDDE6FD)
val Ink = Color(0xFF1B1F26)
val InkSecondary = Color(0xFF5A6472)
val SurfaceLight = Color(0xFFF6F7F9)
val SurfaceCard = Color(0xFFFFFFFF)
val Danger = Color(0xFFE5484D)
val WarningAmber = Color(0xFFF5A524)
val SuccessGreen = Color(0xFF2FA84F)
val DividerGray = Color(0xFFDDDDDD)


/**
 * 划开删除时露出的**深红**底色。
 *
 * 刻意比 [Danger] 深得多：原来用 Danger.copy(alpha = 0.12f)，
 * 实际呈现是很淡的粉，滑动时几乎看不出这里有操作，
 * 用户不知道「已经划开了、该点什么」。
 *
 * 深红 + 白色垃圾桶图标，滑动的瞬间就明确传达「这里是危险操作」——
 * 这本身就是防误触的一部分。
 */
val DeleteReveal = Color(0xFF8E1B16)
