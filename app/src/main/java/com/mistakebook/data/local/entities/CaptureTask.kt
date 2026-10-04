package com.mistakebook.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mistakebook.domain.TaskStatus

/**
 * 识别任务：一次「拍照 / 相册图 / PDF 页」对应一条，状态全部持久化，UI 只观察数据库。
 *
 * groupId：导入分组 id，相册多选与 PDF 多页导入时同组任务共享该值，用于进度页展示
 * 「3/12」与批量结果浏览；单张拍照为 null。
 * sourceType：IMAGE（拍照 / 相册 / PDF 栅格页）或 PDF_TEXT（有文本层的 PDF 页）。
 * questionIdsJson：保存入库的题目 id 列表，一次识别可产出多道题。
 */
@Entity(
    tableName = "capture_tasks",
    indices = [Index("status"), Index("groupId")]
)
data class CaptureTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoPath: String,
    val status: TaskStatus,
    val stageText: String = "",
    val errorMessage: String? = null,
    val errorKind: String? = null,
    val batchId: String? = null,
    val markdown: String? = null,
    val refinedJson: String? = null,
    val questionId: Long? = null,
    val questionIdsJson: String? = null,
    val groupId: String? = null,
    val orderInGroup: Int = 0,
    val groupSize: Int = 1,
    val groupTitle: String = "",
    val sourceType: String = SOURCE_IMAGE,
    /**
     * 重新识别时要覆盖的题目 id。非空表示「这次识别完直接更新那道题，不新建」。
     * 加这个字段而不是复用 questionId：questionId 是「本次识别产出的题目」，
     * 语义相反，复用会让覆盖逻辑和新建逻辑混在一起。
     */
    val replacesQuestionId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val SOURCE_IMAGE = "IMAGE"
        const val SOURCE_PDF_TEXT = "PDF_TEXT"
    }
}
