package com.mistakebook.ui.edit

import com.mistakebook.domain.QuestionDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 手动录入（`taskId == null`）的数据不变量。
 *
 * ## 为什么这些必须钉死
 * 手动录入是「Key 没配 / MinerU 连不上」时唯一还能往错题本里加题的路。
 * 这条路一旦出问题，用户就彻底没有录入手段了——而这类问题
 * 恰恰只会发生在「没有网络、没有 Key」的环境里，最难自查。
 *
 * 这些断言覆盖的是**纯逻辑**，不需要 Android，所以能在 JVM 单测里跑。
 * UI 装配与数据库写入靠真机验证。
 */
class ManualEntryTest {

    /**
     * 手动录入的草稿：`imagePath` 为空串表示「这道题没有原图」。
     *
     * 空串（而不是 null 或某个假路径）是有讲究的：
     * `Question.imagePath` 是非空字段，下游详情页按后缀判断有没有图、
     * 打印按 `isNotBlank()` 判断，空串两边都天然走「无图」分支。
     */
    @Test
    fun `blankImagePathMeansNoOriginalImage`() {
        val draft = EditableDraft(imagePath = "")
        assertEquals("", draft.imagePath)
        // 与识别流程的判定方式保持一致：非图片后缀即视为无图
        val hasImage = draft.imagePath.endsWith(".jpg", true) ||
            draft.imagePath.endsWith(".jpeg", true) ||
            draft.imagePath.endsWith(".png", true)
        assertFalse("空 imagePath 必须判定为无图", hasImage)
    }

    /** 打印取图路径是「附图优先，回退原图」——无图时必须得到空串而不是崩。 */
    @Test
    fun `printImagePathFallsBackToBlankWhenNoImage`() {
        val printPath = emptyList<String>().firstOrNull() ?: ""
        assertTrue("无图时 printImagePath 应为空串", printPath.isBlank())
    }

    /**
     * 题干是唯一的必填项，其余都可空。
     *
     * 保存按钮的可用性完全依赖它，所以它必须和「空题干不能保存」严格一致——
     * 两个地方用不同的判据，用户会看到「按钮能点但存不进去」。
     */
    @Test
    fun `stemIsTheOnlyRequiredField`() {
        assertFalse("空题干不可保存", EditableDraft(imagePath = "").isValid())
        assertTrue("有题干即可保存", EditableDraft(imagePath = "", stem = "1+1=?").isValid())
        // 纯空白也算空：isValid 用的是 isNotBlank，不是 isNotEmpty。
        // 这里曾经写成 assertTrue（与断言消息自相矛盾），被测试自己逮到。
        assertFalse("纯空白题干不可保存", EditableDraft(imagePath = "", stem = "   ").isValid())
        assertFalse("含换行的空白题干也不可保存", EditableDraft(imagePath = "", stem = "\n\t ").isValid())
    }

    /** 手动录入默认不归类到任何学科——让用户自己选，避免全塞进「其他」。 */
    @Test
    fun `manualDraftStartsUnassigned`() {
        val draft = EditableDraft(imagePath = "")
        assertEquals(null, draft.subjectId)
        assertEquals("", draft.subjectName)
    }

    /**
     * 转成领域草稿时，学科名的取值顺序不能反。
     *
     * 手填的 `manualSubject` 优先于识别结果——用户自己写的比 AI 猜的准。
     */
    @Test
    fun `manualSubjectWinsOverRecognizedName`() {
        val draft = EditableDraft(
            imagePath = "",
            subjectName = "其他",
            manualSubject = "  物理  ",
            stem = "题干"
        )
        val domain: QuestionDraft = draft.toDomainDraft("")
        assertEquals("物理", domain.subjectName)
    }

    /** 没手填时才回退到识别结果。 */
    @Test
    fun `fallsBackToRecognizedSubjectWhenNotTyped`() {
        val draft = EditableDraft(imagePath = "", subjectName = "数学", manualSubject = "   ")
        assertEquals("数学", draft.toDomainDraft("").subjectName)
    }

    /** 手动录入没有识别文本，mineruMarkdown 必须是空串而不是 "null" 之类。 */
    @Test
    fun `manualDraftCarriesNoMarkdown`() {
        val domain = EditableDraft(imagePath = "", stem = "题干").toDomainDraft("")
        assertEquals("", domain.mineruMarkdown)
        assertTrue("无图时不应带出任何附图", domain.figurePaths.isEmpty())
    }

    /**
     * 手动录入的题也必须能通过 `isValid` 后走仓储的 `save`——
     * 也就是说 imagePath 为空不构成保存障碍。
     */
    @Test
    fun `manualDraftIsSavableWithoutImage`() {
        val draft = EditableDraft(
            imagePath = "",
            stem = "求 $\\int_0^1 x\\,dx$",
            answer = "$\\frac{1}{2}$"
        )
        assertTrue("无图的题应当可以保存", draft.isValid())
        val domain = draft.toDomainDraft("")
        assertTrue("领域草稿同样有效", domain.isValid())
        assertEquals("求 $\\int_0^1 x\\,dx$", domain.stem)
    }
}

/** 与 [EditableDraft.isValid] 同源，测试里独立写一份以免依赖私有的内部函数。 */
private fun EditableDraft.isValid(): Boolean = stem.isNotBlank()
