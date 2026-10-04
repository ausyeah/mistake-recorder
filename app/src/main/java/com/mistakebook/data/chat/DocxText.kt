package com.mistakebook.data.chat

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * DOCX 抽文本。**零依赖**（项目规则禁止引入 Apache POI 之类）。
 *
 * ## DOCX 是什么
 *
 * 一个 zip 包，正文在 `word/document.xml` 里。段落是 `<w:p>`，
 * 文字在 `<w:t>` 里，段内换行是 `<w:br/>`。
 *
 * ## 为什么不用 XML 解析器
 *
 * `XmlPullParser` 能用，但为了抽几百个字去搭一套 SAX 回调，
 * 还得处理各种命名空间——而我们只要「把标签换成换行、把实体还原」。
 * 正则在这里是安全的：我们**不关心 XML 结构是否合法**，
 * 只关心 `<w:t>` 之间的字。
 *
 * 代价是：如果文档里出现字面量 `<w:t>`（正常 Word 文档不会）会被误切。
 * 这个取舍写在这里而不是藏在代码里。
 */
object DocxText {

    /**
     * 段落开始。
     *
     * 必须是 [Regex] 而不是字符串：这里要匹配的是**字符类** `[ >]`，
     * 而 `String.indexOf` 是字面量匹配——早先写成 `"<w:p[ >]"` 配 `indexOf`，
     * 结果永远匹配不到，**所有文档都返回空串**，而测试当时也没覆盖到。
     */
    private val PARAGRAPH_BREAK = Regex("<w:p[ >]")

    /** 同上，`<w:t>` 或 `<w:t xml:space="preserve">` 都收。 */
    private val TEXT_OPEN = Regex("<w:t[ >]")

    private val TEXT_CLOSE = "</w:t>"

    /** 段内换行。真实 Word 也可能写成 `<w:br />`，两种都收。 */
    private val LINE_BREAK = Regex("<w:br\\s*/?>")

    /** 制表符。 */
    private val TAB = Regex("<w:tab\\s*/?>")

    /** 找不到时的占位值，保证「取最近」可以直接用 minOf。 */
    private const val NONE = Int.MAX_VALUE


    /**
     * @return 抽出的纯文本；不是 docx 或抽不出内容时返回空串。
     */
    fun extract(bytes: ByteArray): String {
        val xml = readDocumentXml(bytes) ?: return ""
        return xmlToText(xml)
    }

    /** 从 zip 里取出 `word/document.xml`。 */
    private fun readDocumentXml(bytes: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == DOCUMENT_PATH) return zip.readBytes().toString(Charsets.UTF_8)
                // 不需要的条目直接跳过，别读进内存
                zip.closeEntry()
                entry = zip.nextEntry
            }
            null
        }
    }.getOrNull()

    /** XML -> 纯文本。独立成函数是为了能脱离 zip 单测。 */
    fun xmlToText(xml: String): String {
        if (xml.isEmpty()) return ""
        val sb = StringBuilder()
        var i = 0
        while (i < xml.length) {
            // 四类标记一起找，取**最近**的那个。
            //
            // 早先只在「找不到 <w:t>」的 else 分支里处理 <w:br/> 和 <w:tab/>，
            // 而正文里后面永远还有 <w:t>——于是换行和制表**一个都没被扫到**，
            // 「甲<换行>乙<制表>丙」被抽成「甲乙丙」。
            val paragraphAt = PARAGRAPH_BREAK.find(xml, i)?.range?.first ?: NONE
            val textAt = TEXT_OPEN.find(xml, i)?.range?.first ?: NONE
            val brAt = LINE_BREAK.find(xml, i)?.range?.first ?: NONE
            val tabAt = TAB.find(xml, i)?.range?.first ?: NONE
            val next = minOf(paragraphAt, textAt, brAt, tabAt)
            if (next == NONE) break

            when (next) {
                paragraphAt -> {
                    if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                    i = paragraphAt + 1
                }

                brAt -> {
                    sb.append('\n')
                    i = brAt + LINE_BREAK.find(xml, brAt)!!.value.length
                }

                tabAt -> {
                    sb.append('\t')
                    i = tabAt + TAB.find(xml, tabAt)!!.value.length
                }

                else -> {
                    val tagEnd = xml.indexOf('>', textAt)
                    if (tagEnd < 0) break
                    val contentStart = tagEnd + 1
                    val contentEnd = xml.indexOf(TEXT_CLOSE, contentStart)
                    if (contentEnd < 0) {
                        // 损坏的文档：把剩下的收进来就收工，绝不空转
                        sb.append(unescape(xml.substring(contentStart)))
                        break
                    }
                    sb.append(unescape(xml.substring(contentStart, contentEnd)))
                    i = contentEnd + TEXT_CLOSE.length
                }
            }
        }
        return sb.toString().trim()
    }

    /** XML 实体还原。只处理文档里真会出现的五个。 */
    fun unescape(text: String): String {
        if ('&' !in text) return text
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private const val DOCUMENT_PATH = "word/document.xml"
}
