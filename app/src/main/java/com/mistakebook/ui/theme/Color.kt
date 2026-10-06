package com.mistakebook.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================================
// 纸质化设计系统色彩（源自《单词书》设计规范）
// ==========================================================

// 浅色纸质模式（Warm Paper）
val PaperBgLight = Color(0xFFF6F4EE)         // 暖米灰纸张底色
val PaperCardLight = Color(0xFFFFFEFB)       // 暖白纸卡片
val PaperBorderLight = Color(0xFFE7E3D8)     // 纸张米灰线
val PaperTextPrimaryLight = Color(0xFF26242C)// 墨黑正文
val PaperTextSecondaryLight = Color(0xFF5C5862)
val PaperTextMutedLight = Color(0xFF918C7F)  // 纸墨浅灰辅助文字

// 经典书卷主品牌色
val PaperPrimary = Color(0xFF3B5BDB)         // 沉稳靛蓝
val PaperPrimaryDark = Color(0xFF2F4BC0)
val PaperPrimaryTint = Color(0xFFE9EDFB)     // 淡靛蓝底
val PaperPrimaryLine = Color(0xFFC9D3F4)

// 四级掌握度与反馈状态色彩
val PaperLv0 = Color(0xFFC7CCD9)             // Lv0 未学/生词
val PaperLv1 = Color(0xFFFFB84D)             // Lv1 模糊/重现 (暖金)
val PaperLv2 = Color(0xFF4DABF7)             // Lv2 认识 (晴空蓝)
val PaperLv3 = Color(0xFF51CF66)             // Lv3 精通/掌握 (竹林绿)

val PaperOk = Color(0xFF087F5B)              // 答对墨绿
val PaperOkBg = Color(0xFFF2FBF5)            // 答对暖绿底
val PaperBad = Color(0xFFC92A2A)             // 答错红
val PaperBadBg = Color(0xFFFFF4F2)           // 答错暖红底

// 深色墨砚纸质模式（Dark Ink Paper）
val PaperBgDark = Color(0xFF19181D)          // 深邃砚黑底色
val PaperCardDark = Color(0xFF24232C)        // 乌木深墨卡片
val PaperBorderDark = Color(0xFF383542)      // 暗调纸张边框线
val PaperTextPrimaryDark = Color(0xFFECE9E2) // 柔和月白文字（避免刺眼）
val PaperTextSecondaryDark = Color(0xFFB0ABA0)
val PaperTextMutedDark = Color(0xFF8E8A98)   // 暗调辅助文字
val PaperPrimaryDarkTheme = Color(0xFF748FFC)// 夜读亮靛蓝
val PaperPrimaryTintDark = Color(0xFF26335C)
val PaperOkDark = Color(0xFF51CF66)
val PaperOkBgDark = Color(0xFF193324)
val PaperBadDark = Color(0xFFFF6B6B)
val PaperBadBgDark = Color(0xFF3D1C1C)

// 兼容既有错题本模块的色值别名（映射到全新纸质化设计）
val BrandBlue = PaperPrimary
val BrandBlueDark = PaperPrimaryDark
val BrandBlueLight = PaperPrimaryTint
val Ink = PaperTextPrimaryLight
val InkSecondary = PaperTextSecondaryLight
val SurfaceLight = PaperBgLight
val SurfaceCard = PaperCardLight
val Danger = PaperBad
val WarningAmber = PaperLv1
val SuccessGreen = PaperOk
val DividerGray = PaperBorderLight
val DeleteReveal = Color(0xFFD32F2F)

