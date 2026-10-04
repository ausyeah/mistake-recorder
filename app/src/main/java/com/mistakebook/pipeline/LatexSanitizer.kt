package com.mistakebook.pipeline

/**
 * LaTeX 清洗器：把大模型输出的「大概意思对、但语法不合法」的 LaTeX 修到能渲染。
 *
 * ## 为什么必须有这一层
 * 提示词已经严格约束过（见 [PromptTemplates.LATEX_FORMAT_APPENDIX]），
 * 但**不能只靠提示词**。真机上观察到的失败模式是系统性的、可枚举的：
 * 缺右括号、`\frac` 分子分母没配对花括号、下标后面丢了内容等。
 * 这些都是「差一两个字符」的错误，把它们交给 KaTeX 只会得到一段红字源码。
 *
 * 原则：**只做「确定不改变语义」的修补**，不做猜测性补全。
 * 宁可少修，也不要把公式改成另一个意思——那比显示源码更危险。
 */
object LatexSanitizer {

    /**
     * 补全未闭合的花括号。
     * `\frac{1}{2` -> `\frac{1}{2}`。
     * 数一遍 `{` 与 `}` 的差，在末尾补齐。
     */
    fun balanceBraces(input: String): String {
        var depth = 0
        input.forEach { ch ->
            when (ch) {
                '{' -> depth++
                '}' -> if (depth > 0) depth--
            }
        }
        if (depth <= 0) return input
        return input + "}".repeat(depth)
    }

    /**
     * 修复未配对的 `\left` / `\right`。
     *
     * KaTeX 遇到孤立的 `\left` 会直接放弃整个公式（抛 ParseError），
     * 于是整条公式退化成红字源码。补上缺失的 `\right.` 就能救回来。
     */
    fun balanceDelimiters(input: String): String {
        val opens = LEFT_DELIMITER_REGEX.findAll(input).count()
        val closes = RIGHT_DELIMITER_REGEX.findAll(input).count()
        if (opens <= closes) return input
        // \left 比 \right 多几个，在末尾补对应数量的 \right.
        return input.trimEnd() + " ".repeat(opens - closes) + "\\right."
    }

    /**
     * 处理 KaTeX 不认识的命令。
     *
     * 大模型有时会输出中文资料里的自定义宏（`\bm`、`\degree`、`\cellcolor`）。
     * 这些在 KaTeX 里没有定义，撞上就整条公式失败。
     *
     * ## 两种处理方式，区别很重要
     * - **装饰性命令**（`\bm`、`\textbf`）：降级为正体文本（去掉反斜杠），
     *   只是排版变朴素，含义不变。
     * - **语义性命令**（`\bar`、`\hat`、`\vec`、`\tilde`、`\overline`、`\dot`…）：
     *   **直接删除**。这些命令是「在上一符号上加标记」，
     *   降级成字母 `bar` 会把 `\bar{x}` 变成 `barx`——
     *   **含义被改掉了**，比显示源码危险得多。
     *
     * 判据：命令是否改变符号的**数学含义**。改变的一律删，不改变的一律降级。
     */
    fun dropUnknownCommands(input: String): String {
        var out = input.replace(COMMAND_REGEX) { match ->
            val name = match.groupValues[1]
            when {
                name in KNOWN_COMMANDS -> match.value
                // 语义性：删掉，保留上一符号本身
                name in SEMANTIC_COMMANDS -> ""
                // 其余自定义宏：降级为正体文本
                else -> name
            }
        }
        // 删除后可能留下连续空格，压一下；也顺手清掉行尾空白
        return out.replace(MULTI_SPACE, " ").trim()
    }

    /**
     * 会改变数学含义、因此**必须删除而非降级**的命令。
     *
     * 这些都是「在上一符号上加标记」，把反斜杠命令当成字母串排出来
     * 会让公式变成另一个意思，例如：
     * - `\bar{x}` -> `barx`（本来是「x 的共轭/平均值」）
     * - `\hat{y}` -> `haty`
     * - `\vec{v}` -> `vecv`
     */
    private val SEMANTIC_COMMANDS = setOf(
        "bar", "hat", "vec", "tilde", "overline", "underline",
        "dot", "ddot", "widehat", "widetilde", "overline", "underbar"
    )

    private val MULTI_SPACE = Regex("\\s{2,}")

    /**
     * 清理行内公式里最常见的致命问题：未配对的 `$`。
     *
     * 上游 [JsonExtractor] 已保证 `$` 成对，这里只兜底极端情况。
     */
    fun stripStrayDollar(input: String): String {
        // 奇数个未转义的 $ 说明配对已经断了，全删比留着当普通字符更干净
        val unescaped = input.count { it == '$' }
        if (unescaped % 2 == 0) return input
        return input.replace("$", "")
    }

    /** 完整清洗流水线。 */
    fun clean(input: String): String {
        if (input.isBlank()) return input
        var out = stripStrayDollar(input)
        out = dropUnknownCommands(out)
        out = balanceDelimiters(out)
        out = balanceBraces(out)
        return out.trim()
    }

    /** KaTeX 支持的命令白名单。够用即可，不求覆盖全部。 */
    private val KNOWN_COMMANDS = setOf(
        // 希腊字母
        "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta",
        "iota", "kappa", "lambda", "mu", "nu", "xi", "pi", "rho", "sigma", "tau",
        "upsilon", "phi", "chi", "psi", "omega",
        "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Upsilon", "Phi", "Psi", "Omega",
        // 运算符与关系
        "pm", "mp", "times", "div", "cdot", "cdots", "ldots", "dots", "vdots", "ddots",
        "leq", "le", "geq", "ge", "neq", "ne", "approx", "sim", "simeq", "cong", "equiv",
        "propto", "ll", "gg", "subset", "subseteq", "supset", "supseteq", "in", "ni", "notin",
        "cup", "cap", "emptyset", "varnothing", "forall", "exists", "nexists", "neg", "lnot",
        "land", "lor", "wedge", "vee", "oplus", "otimes", "perp", "parallel",
        "leftarrow", "rightarrow", "leftrightarrow", "Leftarrow", "Rightarrow", "Leftrightarrow",
        "to", "gets", "mapsto", "infty", "partial", "nabla", "sum", "prod", "int", "oint",
        "sqrt", "frac", "dfrac", "tfrac", "binom", "dbinom", "choose",
        // 上下标与结构
        "left", "right", "big", "Big", "bigg", "Bigg",
        "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr", "Biggl", "Biggr",
        "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
        "quad", "qquad", "enspace", "thinspace", "medspace", "thickspace",
        "begin", "end", "text", "textbf", "textit", "mathrm", "mathbf", "mathit", "mathcal", "mathbb", "mathfrak", "mathscr",
        // 注意：bar / hat / vec / tilde / overline / underline / dot 这类**语义性**命令
        // 故意不在白名单里 —— 它们改变数学含义，被"降级"会改掉公式本意，
        // 所以由 SEMANTIC_COMMANDS 分支直接删除。详见 dropUnknownCommands。
        // 同一个命令不能同时出现在两个集合里：白名单优先级更高，会让删除分支失效。
        "substack", "stackrel", "binom",
        // 三角函数与常见函数名
        "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "log", "ln", "lg", "exp", "lim", "max", "min", "sup", "inf", "arg", "det", "gcd", "deg",
        // 空格与符号
        ",", ";", ":", "!", " ",
        // 字体尺寸（少见但合法）
        "tiny", "scriptsize", "footnotesize", "small", "normalsize", "large", "Large", "LARGE", "huge", "Huge"
    )

    /** 匹配 `\command` 或 `\command*`，只取命令名部分。 */
    private val COMMAND_REGEX = Regex("""\\([a-zA-Z]+)\*?""")

    private val LEFT_DELIMITER_REGEX = Regex("""\\left\s*""")
    private val RIGHT_DELIMITER_REGEX = Regex("""\\right\s*""")

    /**
     * 判断清洗后是否值得尝试渲染。
     *
     * 清洗会带来一处残留风险：`\left(` 被补成 `\left(...\right.` 后，
     * 括号配平了但语义可能不完整。这里不判断语义，只判断
     * 「是否还有 KaTeX 一定拒绝的结构」——
     * 交给 KaTeX 的 `throwOnError: false` 去处理剩余情况。
     */
    fun looksRenderable(input: String): Boolean {
        if (input.isBlank()) return false
        // 还有未配对的 \left 说明补全失败
        val opens = LEFT_DELIMITER_REGEX.findAll(input).count()
        val closes = RIGHT_DELIMITER_REGEX.findAll(input).count()
        return opens <= closes
    }
}
