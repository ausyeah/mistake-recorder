package com.mistakebook.domain

/**
 * 编辑页中的一道题草稿：识别流水线的产出，也是入库前的唯一载体。
 *
 * 一次识别可能产出多道题（批量导入 / PDF 多题），编辑页按草稿序号翻页。
 */
data class QuestionDraft(
    /** 原始照片（整页，可能含多题与噪声）。 */
    val imagePath: String,
    /** 题目附图：MinerU 从原图切出的图形部分，或用户手动裁的图。打印优先用。 */
    val figurePaths: List<String> = emptyList(),

    val mineruMarkdown: String,
    val subjectName: String = "",
    val subjectId: Long? = null,
    /** 所属错题本；null 表示存进默认错题本。 */
    val notebookId: Long? = null,
    val stem: String = "",
    val options: List<Option> = emptyList(),
    val answer: String = "",
    val analysis: String = "",
    val title: String = "",
    val knowledgePoints: List<String> = emptyList(),
    val errorReason: ErrorReason = ErrorReason.OTHER,
    val difficulty: Int = 3,
    val uncertain: List<String> = emptyList(),
    val note: String = ""
) {
    fun isValid(): Boolean = stem.isNotBlank()
}
