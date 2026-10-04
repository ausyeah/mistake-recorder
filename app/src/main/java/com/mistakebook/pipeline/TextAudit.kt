package com.mistakebook.pipeline

/**
 * 文本体检：找出题面里**本地就能判定**的问题，并给出可一键套用的修正。
 *
 * ## 为什么分两类检查
 *
 * 渲染不出来的原因分两种，检测手段完全不同：
 *
 * | 类型 | 例子 | 本模块能否判定 |
 * |---|---|---|
 * | 语法/结构损坏 | `ight]`、`\left` 少配对、`F_`、`\frac 1 2` | **能**，纯文本分析 |
 * | 识别内容抄错 | 题干 `x^2-1` 抄成 `e^x-1` | **不能**，必须靠语义判断 |
 *
 * 第二类（真机实测遇到过：原图是 `1/(x^2-1)`，识别存成 `1/(e^x-1)`，
 * 渲染完全正常但答案是错的）交给 LLM 审校，本模块不碰。
 *
 * ## 边界：不做什么
 *
 * - **不静默改写。** 任何修正都以 [AuditIssue.suggestion] 的形式返回给用户看，
 *   由用户决定是否采纳。自动改掉等于替用户做数学判断，越权。
 * - **不假装能查语义。** 发现不了就说发现不了，不要用「已检查通过」误导。
 * - **不重复已有的修复。** 已在 [LatexEscapes] 里修好的（控制字符、孤儿 `ight`）
 *   这里只**报告**并说明「显示时已自动修复」，因为用户改的是**存进库的内容**，
 *   那里的损坏依然存在。
 */
object TextAudit {

    /** 出问题的字段。 */
    enum class Field(val label: String) {
        STEM("题干"),
        OPTION("选项"),
        ANSWER("答案"),
        ANALYSIS("解析")
    }

    enum class Kind(val label: String) {
        /** 一定会导致渲染失败或显示错乱。 */
        BROKEN("损坏"),

        /** 可能有问题，需要人判断。 */
        SUSPECT("可疑")
    }

    /**
     * 一个问题 + 可选修正。
     *
     * @param original 出问题的原始片段（用于高亮定位）
     * @param suggestion 修好之后的完整片段；`null` 表示**只能人工改**
     * @param note 补充说明，尤其是「显示时已被自动修复但库里还是坏的」这类
     */
    data class Issue(
        val field: Field,
        val optionIndex: Int,
        val kind: Kind,
        val summary: String,
        val original: String,
        val suggestion: String?,
        val note: String = ""
    ) {
        val autoFixable: Boolean get() = suggestion != null
    }

    /**
     * 体检一段文本。
     *
     * ## 关键设计：先修，再查
     *
     * 先跑一遍 [LatexEscapes.repairForDisplay] 把**确定能还原**的损坏修掉
     * （控制字符、被删掉的 `\right`），再在**干净文本**上查结构问题。
     *
     * 顺序反了会报出「派生误报」。真机数据就是活例子：
     * ```
     * $\lim_{x<TAB>o0}\left[\frac{...}{x}ight]^{1}$
     * ```
     * 直接查会报「`\left` 比 `\right` 多了 1 个」——可那是**假问题**：
     * `\right` 本来就在，是被删成了 `ight`；把 `ight` 还原回去，括号自然配平。
     * 报它会让用户去改一个没坏的地方。
     *
     * @param text 字段原文（`$` 分隔符保留，配对检查需要它）
     * @param renderedFailures 渲染失败的公式原文集合，来自 `RichText` 的 mathCache
     *   （失败的公式在 cache 里是 `null`，等于白捡一份权威判定）
     */
    fun audit(
        field: Field,
        text: String,
        optionIndex: Int = -1,
        renderedFailures: Set<String> = emptySet()
    ): List<Issue> {
        if (text.isBlank()) return emptyList()
        val issues = mutableListOf<Issue>()

        // 1) 确定能还原的损坏：合成一条，避免同一处损坏报两遍
        val repaired = LatexEscapes.repairForDisplay(text, mathOnly = true)
        if (repaired != text) {
            issues += corruptionIssue(field, text, repaired, optionIndex)
        }

        // 2) 剩下的结构问题，一律在**修好之后**的文本上查
        issues += checkDollarPairing(field, repaired, optionIndex)
        issues += checkBraces(field, repaired, optionIndex)
        issues += checkDelimiters(field, repaired, optionIndex)
        issues += checkEnvironments(field, repaired, optionIndex)
        issues += checkEmptyScripts(field, repaired, optionIndex)
        issues += checkFracArgs(field, repaired, optionIndex)

        // 3) KaTeX 的权威判定同样基于修好后的文本——那才是最终会被渲染的东西
        issues += checkRenderFailures(field, repaired, optionIndex, renderedFailures)

        return issues
    }

    /**
     * 把确定能还原的损坏描述成一条问题。
     *
     * 合并成一条而不是按成因拆成两条：用户看到的是「这段有损坏，可一键修复」，
     * 而不是「这里有制表符」「那里有 ight」——后者会让人以为有两处要分别处理。
     */
    private fun corruptionIssue(
        field: Field,
        text: String,
        repaired: String,
        optionIndex: Int
    ): Issue {
        val reasons = mutableListOf<String>()
        if (text.any { it.code in 8..13 }) {
            reasons += "反斜杠被当成 JSON 转义吃掉（\\t 变制表符、\\r 变回车）"
        }
        if (Regex("(?<![A-Za-z\\\\])ight").containsMatchIn(text)) {
            reasons += "\\right 被整段删掉，只剩 ight"
        }
        return Issue(
            field = field,
            optionIndex = optionIndex,
            kind = Kind.BROKEN,
            summary = "公式有损坏，可一键还原",
            original = text,
            suggestion = repaired,
            note = reasons.joinToString("；") +
                "。屏幕上显示时已自动救回，但库里存的是坏的，导出或换端渲染会露馅"
        )
    }

    /**
     * 把 [audit] 里**可自动修**的结果套用到文本上。
     *
     * ## 为什么只有两条规则可自动修
     *
     * 「能确定还原」和「猜着补」必须分开：
     *
     * - **能确定还原**：被 JSON 转义吃掉反斜杠的 `\t` `\r`（命令名还在，
     *   补回去必然是原意）、被整段删掉的 `\right`（上下文无歧义）。
     * - **猜着补**：少一个 `}`、少一个 `\right`。
     *
     * 早先把两类都做成自动修，结果是顺序依赖的：
     * 先补 `\right.`、随后又把被删的 `\right]` 还原回来，
     * 变成 1 个 `\left` 配 2 个 `\right`，比原来更糟——
     * 而且会**反复追加**，`\right.` 后面挂了七八个。
     *
     * 所以现在只有前两类带 [Issue.suggestion]，其余只报告。
     *
     * ## 为什么一遍就够、不会重复套用
     *
     * 可自动修的两条规则都作用在**整段文本**上，且修复后就不再命中
     * （`repairForDisplay` 之后再调一次是恒等的）。
     * 这两条之间也互不影响（一个改控制字符，一个改字母 `ight`）。
     * 于是「每条最多应用一次」就是正确的，不需要重扫循环。
     */
    fun applyFixes(text: String, issues: List<Issue>): String {
        var out = text
        for (issue in issues) {
            val suggestion = issue.suggestion ?: continue
            if (issue.original == text && out == issue.original) {
                out = suggestion
            } else if (out.contains(issue.original)) {
                out = out.replace(issue.original, suggestion)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- 各项检查

    /** `$` 必须成对。数奇偶即可，`$$` 显示公式算两次。 */
    private fun checkDollarPairing(field: Field, text: String, optionIndex: Int): List<Issue> {
        val count = text.count { it == '$' }
        if (count % 2 == 0) return emptyList()
        return listOf(
            Issue(
                field, optionIndex, Kind.BROKEN,
                "\$ 数量为奇数（$count 个），公式定界符没配对",
                original = text.takeLast(40),
                suggestion = null,
                note = "检查是否漏了收尾的 \$，或公式里混入了裸 \$"
            )
        )
    }

    /**
     * 花括号必须配平。
     *
     * 特意**只统计数学区内**的括号：正文里出现 `{`（比如讲 JSON）不该报警，
     * 那是误报，误报多了用户就不信这个功能了。
     *
     * **不自动修**：少补一个 `}` 能让括号配平，但补在哪儿是猜的——
     * 模型可能是漏了分母的右括号，补错了会让公式变成另一个意思。
     */
    private fun checkBraces(field: Field, text: String, optionIndex: Int): List<Issue> {
        val issues = mutableListOf<Issue>()
        forEachMathSegment(text) { segment, _ ->
            var depth = 0
            for (ch in segment) {
                if (ch == '{') depth++
                if (ch == '}') depth--
            }
            if (depth != 0) {
                issues += Issue(
                    field, optionIndex, Kind.BROKEN,
                    if (depth > 0) "公式里 { 比 } 多了 $depth 个" else "公式里 } 比 { 多了",
                    original = segment.take(60),
                    suggestion = null,
                    note = if (depth > 0) {
                        "通常是 \\frac 少写了一半。补在哪需要人来判断，不自动改"
                    } else {
                        "多出来的 } 会让后面整条公式失败"
                    }
                )
            }
        }
        return issues
    }

    /**
     * `\left` 与 `\right` 必须成对，否则 KaTeX 直接放弃整条公式。
     *
     * **不自动修**：补 `\right.` 能让括号配平，但那是「猜模型本来想闭合什么」。
     * 早先试过自动补，结果是——若先补了 `\right.`、随后又把被删掉的 `\right]`
     * 还原回来，就变成 1 个 `\left` 配 2 个 `\right`，反而更糟。
     * 顺序依赖的自动修复是不可靠的，不如交给人。
     */
    private fun checkDelimiters(field: Field, text: String, optionIndex: Int): List<Issue> {
        val issues = mutableListOf<Issue>()
        forEachMathSegment(text) { segment, _ ->
            val opens = Regex("\\\\left\\s*").findAll(segment).count()
            val closes = Regex("\\\\right\\s*").findAll(segment).count()
            if (opens > closes) {
                issues += Issue(
                    field, optionIndex, Kind.BROKEN,
                    "\\left 比 \\right 多了 ${opens - closes} 个，括号伸缩没配对",
                    original = segment.take(60),
                    suggestion = null,
                    note = "在末尾补 \\right. 可以救回来，但那是在猜模型想闭合什么，不自动改"
                )
            } else if (closes > opens) {
                issues += Issue(
                    field, optionIndex, Kind.BROKEN,
                    "\\right 比 \\left 多了 ${closes - opens} 个",
                    original = segment.take(60),
                    suggestion = null,
                    note = "多出来的 \\right 建议手动删掉——机器不知道该删哪一个"
                )
            }
        }
        return issues
    }

    /** `\begin{...}` / `\end{...}` 必须配对且环境名一致。 */
    private fun checkEnvironments(field: Field, text: String, optionIndex: Int): List<Issue> {
        val issues = mutableListOf<Issue>()
        val begins = Regex("\\\\begin\\{([^}]*)\\}").findAll(text).map { it.groupValues[1] }.toList()
        val ends = Regex("\\\\end\\{([^}]*)\\}").findAll(text).map { it.groupValues[1] }.toList()
        if (begins.size != ends.size) {
            issues += Issue(
                field, optionIndex, Kind.BROKEN,
                "\\begin 有 ${begins.size} 个而 \\end 有 ${ends.size} 个",
                original = text.take(60), suggestion = null,
                note = "环境没闭合，整条公式会退化成源码"
            )
        } else {
            begins.forEachIndexed { i, name ->
                if (ends.getOrNull(i) != name) {
                    issues += Issue(
                        field, optionIndex, Kind.BROKEN,
                        "\\begin{$name} 与 \\end{${ends.getOrNull(i) ?: "?"}} 不匹配",
                        original = text.take(60), suggestion = null,
                        note = "环境名必须完全一致"
                    )
                }
            }
        }
        return issues
    }

    /**
     * 空上标/下标：`F_`、`x^{}`、`a_{}`。
     *
     * KaTeX 能渲染出东西，但位置会错（标到错误的地方），属于「静默错误」——
     * 比直接报错更难发现，所以单独列出来。
     */
    private val EMPTY_SCRIPT_PATTERNS = listOf(
        Regex("[A-Za-z0-9)]_\\s*\\{?\\s*\\}?\\s*(?![A-Za-z0-9{])") to "下标是空的",
        Regex("[A-Za-z0-9)]\\^\\s*\\{?\\s*\\}?\\s*(?![A-Za-z0-9{])") to "上标是空的"
    )

    private fun checkEmptyScripts(field: Field, text: String, optionIndex: Int): List<Issue> {
        return EMPTY_SCRIPT_PATTERNS.mapNotNull { (re, what) ->
            re.find(text)?.let { hit ->
                Issue(
                    field = field,
                    optionIndex = optionIndex,
                    kind = Kind.SUSPECT,
                    summary = "公式里出现$what（如 `${hit.value.trim()}`）",
                    original = hit.value,
                    suggestion = null,
                    note = "标号会跑到奇怪的位置。补上下标内容，或删掉这个空的 _ / ^"
                )
            }
        }
    }

    /**
     * `\frac` 的参数问题。
     *
     * `\frac 1 2` 在 LaTeX 里是合法的（单字符参数），但在**从图片识别**的场景下
     * 几乎总是识别漏了花括号，补上更符合原意——不过这属于推测，所以只标可疑、
     * 不给自动修正。
     */
    private fun checkFracArgs(field: Field, text: String, optionIndex: Int): List<Issue> {
        val issues = mutableListOf<Issue>()
        val bare = Regex("\\\\frac\\s+[^\\s{}]+\\s+[^\\s{}]+")
        bare.find(text)?.let {
            issues += Issue(
                field, optionIndex, Kind.SUSPECT,
                "\\frac 的参数没加花括号（\\frac 1 2 这种写法）",
                original = it.value, suggestion = null,
                note = "能渲染，但从扫描件识别过来时通常是漏了花括号，建议补上"
            )
        }
        Regex("\\\\frac\\s*\\{[^}]*\\}(?=\\s*[^\\s{}])").find(text)?.let {
            issues += Issue(
                field, optionIndex, Kind.BROKEN,
                "\\frac 只给了分子，分母缺失",
                original = it.value, suggestion = null,
                note = "补上分母，例如 ${it.value}{分母}"
            )
        }
        return issues
    }

    /**
     * KaTeX 渲染失败。
     *
     * 这一条用的是**权威判定**——不是我们猜的，是 KaTeX 真的报错了。
     * `RichText` 里渲染失败的公式在 mathCache 中是 `null`，直接把原文传进来即可。
     */
    private fun checkRenderFailures(
        field: Field,
        text: String,
        optionIndex: Int,
        renderedFailures: Set<String>
    ): List<Issue> {
        if (renderedFailures.isEmpty()) return emptyList()
        return renderedFailures.filter { text.contains(it) }.map { latex ->
            Issue(
                field, optionIndex, Kind.BROKEN,
                "这条公式 KaTeX 渲染失败",
                original = latex,
                suggestion = null,
                note = "KaTeX 报的是语法错，只能人工改。常见于 \\left 没配对、" +
                    "自定义宏、或者上标叠上标"
            )
        }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 遍历所有 `$...$` / `$$...$$` 片段。
     *
     * 与 `RichText.inlineMath` 同款切法，避免「检查器认为有公式、渲染器认为没有」
     * 这种两边不一致的问题。
     */
    private val MATH_SEGMENT = Regex("\\\$\\\$([^\\$]+)\\\$\\\$|\\\$([^\\$]+)\\\$")

    private inline fun forEachMathSegment(text: String, action: (String, Boolean) -> Unit) {
        MATH_SEGMENT.findAll(text).forEach { m ->
            val display = m.groupValues[1].isNotEmpty()
            val body = if (display) m.groupValues[1] else m.groupValues[2]
            action(body.trim(), display)
        }
    }
}
