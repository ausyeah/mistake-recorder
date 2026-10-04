package com.mistakebook.ui.crop

import androidx.compose.ui.geometry.Offset
import com.mistakebook.data.prefs.CropSessionRecord
import com.mistakebook.data.prefs.SettingsStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 裁剪状态持久化。
 *
 * ## 为什么必须存
 * 用户的要求是「裁切后涂鸦、涂鸦后裁切，每次做完都保存状态，在这一步基础上继续编辑」。
 * 但裁剪页的 `rect` / `strokes` / `rotation` 原本是 `remember { mutableStateOf }`——
 * 离开页面就没了。表现是：涂完鸦返回编辑页，再点「重新裁剪」，遮罩全没了，
 * 用户得从头再涂一遍；裁剪框也回到默认位置。
 *
 * ## 存哪
 * 存在 [SettingsStore] 的 DataStore 里，**不是** Room：这是一份临时的、
 * 以源图片路径为键的编辑会话状态，不是业务数据。
 * 放进 Room 意味着为一个 UI 草稿建表 + 做迁移，代价与收益不成比例。
 *
 * ## 键
 * 用源图片的绝对路径做键。重裁剪会生成新文件，键自然变化，
 * 旧状态留在 DataStore 里无人访问，下次清缓存自然消失。
 */
@Serializable
data class CropSession(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val rotationQuarter: Int,
    /** 外层 = 第几笔，内层 = 该笔的点序列。双层才能完整还原「撤销」语义。 */
    val strokes: List<List<StrokePoint>> = emptyList()
)

@Serializable
data class StrokePoint(val x: Float, val y: Float)

class CropSessionStore(private val store: SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(imagePath: String): CropSession? {
        val record = store.getCropSession(imagePath) ?: return null
        return runCatching { json.decodeFromString(CropSession.serializer(), record.json) }
            .getOrNull()
            // 越界的数据会画出反直觉的裁剪框，直接回退到默认
            ?.takeIf { it.left in 0f..1f && it.top in 0f..1f && it.right in 0f..1f && it.bottom in 0f..1f && it.right > it.left && it.bottom > it.top }
    }

    suspend fun save(imagePath: String, session: CropSession) {
        val raw = runCatching { json.encodeToString(CropSession.serializer(), session) }
            .getOrNull() ?: return
        store.putCropSession(imagePath, CropSessionRecord(raw))
    }

    suspend fun clear(imagePath: String) = store.removeCropSession(imagePath)
}

/** 存的是「一批笔画」而不是「一笔」，才能完整还原撤销栈的语义。 */
internal fun List<MaskStroke>.toStrokePoints(): List<List<StrokePoint>> =
    map { stroke -> stroke.points.map { StrokePoint(it.x, it.y) } }

internal fun List<List<StrokePoint>>.toMaskStrokes(): List<MaskStroke> =
    map { points -> MaskStroke(points.map { Offset(it.x, it.y) }) }
