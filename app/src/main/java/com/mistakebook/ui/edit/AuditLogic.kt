package com.mistakebook.ui.edit

import com.mistakebook.pipeline.TextAudit

/**
 * 对一整道草稿做体检，把四个字段的结果汇总成一份列表。
 *
 * **渲染失败的公式**（[mathFailures]）只跟题干/选项/答案/解析里**确实含有**它的
 * 那个字段关联，避免把「解析里公式挂了」错报成「题干有问题」。
 */
fun runAudit(draft: EditableDraft, mathFailures: Set<String> = emptySet()): List<TextAudit.Issue> {
    val issues = mutableListOf<TextAudit.Issue>()
    issues += TextAudit.audit(TextAudit.Field.STEM, draft.stem, renderedFailures = mathFailures)
    issues += TextAudit.audit(TextAudit.Field.ANSWER, draft.answer, renderedFailures = mathFailures)
    issues += TextAudit.audit(TextAudit.Field.ANALYSIS, draft.analysis, renderedFailures = mathFailures)
    draft.options.forEachIndexed { index, option ->
        issues += TextAudit.audit(
            TextAudit.Field.OPTION, option.text, optionIndex = index,
            renderedFailures = mathFailures
        )
    }
    return issues
}

/**
 * 把可自动修的条目套用到草稿上。
 *
 * 不可自动修的（`autoFixable == false`）一律跳过：
 * 那些是「猜着补结构」，改了可能把公式变成另一个意思。
 */
fun applyAuditFixes(
    draft: EditableDraft,
    fixes: List<TextAudit.Issue>
): EditableDraft {
    if (fixes.none { it.autoFixable }) return draft

    fun pick(field: TextAudit.Field, optionIndex: Int = -1): List<TextAudit.Issue> =
        fixes.filter {
            it.autoFixable && it.field == field &&
                (optionIndex < 0 || it.optionIndex == optionIndex)
        }

    val newStem = applyAll(draft.stem, pick(TextAudit.Field.STEM))
    val newAnswer = applyAll(draft.answer, pick(TextAudit.Field.ANSWER))
    val newAnalysis = applyAll(draft.analysis, pick(TextAudit.Field.ANALYSIS))
    if (newStem == draft.stem && newAnswer == draft.answer && newAnalysis == draft.analysis &&
        draft.options.none { option ->
            applyAll(option.text, pick(TextAudit.Field.OPTION, draft.options.indexOf(option))) != option.text
        }
    ) {
        return draft
    }

    val newOptions = draft.options.mapIndexed { index, option ->
        val fixed = applyAll(option.text, pick(TextAudit.Field.OPTION, index))
        if (fixed == option.text) option else option.copy(text = fixed)
    }

    return draft.copy(
        stem = newStem,
        answer = newAnswer,
        analysis = newAnalysis,
        options = newOptions
    )
}

private fun applyAll(text: String, issues: List<TextAudit.Issue>): String =
    if (issues.isEmpty()) text else TextAudit.applyFixes(text, issues)
