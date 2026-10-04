package com.mistakebook.data.prefs

/** 裁剪会话的持久化载荷。只有 json 一个字段，方便以后加版本号而不动调用方。 */
data class CropSessionRecord(val json: String)
