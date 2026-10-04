package com.mistakebook.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxTextTest {

    /** 造一个最小可用的 docx：只要有 `word/document.xml` 就行。 */
    private fun docx(documentXml: String, path: String = "word/document.xml"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // Word 真实包里有这些条目，顺序也不同——这里放几个无关条目验证「只挑对的」
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("<Types/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(path))
            zip.write(documentXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/styles.xml"))
            zip.write("<w:styles><w:t>样式里的字不该出现</w:t></w:styles>".toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------ zip 层面

    @Test
    fun `只取 word_document_xml_忽略其它条目`() {
        val text = DocxText.extract(docx("""<w:document><w:body><w:p><w:r><w:t>正文</w:t></w:r></w:p></w:body></w:document>"""))
        assertEquals("正文", text)
        assertTrue("不该抽到 styles 里的字", !text.contains("样式"))
    }

    @Test
    fun `不是 zip 时返回空串而不抛异常`() {
        assertEquals("", DocxText.extract(byteArrayOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `空字节数组返回空串`() {
        assertEquals("", DocxText.extract(ByteArray(0)))
    }

    @Test
    fun `缺少 document_xml 时返回空串`() {
        assertEquals("", DocxText.extract(docx("<w:t>孤儿</w:t>", path = "word/footnotes.xml")))
    }

    // ------------------------------------------------------------ 段落

    @Test
    fun `多个段落用换行分隔`() {
        val xml = """<w:document><w:body>
            <w:p><w:r><w:t>第一段</w:t></w:r></w:p>
            <w:p><w:r><w:t>第二段</w:t></w:r></w:p>
            <w:p><w:r><w:t>第三段</w:t></w:r></w:p>
        </w:body></w:document>"""
        assertEquals("第一段\n第二段\n第三段", DocxText.extract(docx(xml)))
    }

    @Test
    fun `一个段落里多个 run 拼在一起不换行`() {
        val xml = """<w:document><w:body><w:p>
            <w:r><w:t>设 </w:t></w:r><w:r><w:t>x = 1</w:t></w:r><w:r><w:t>，求解。</w:t></w:r>
        </w:p></w:body></w:document>"""
        assertEquals("设 x = 1，求解。", DocxText.extract(docx(xml)))
    }

    @Test
    fun `w_p 带属性时也能识别`() {
        // 真实 Word 会写 <w:p w:rsidR="...">，只匹配 "<w:p>" 会漏掉断段
        val xml = """<w:document><w:body>
            <w:p w:rsidR="00A1"><w:r><w:t>甲</w:t></w:r></w:p>
            <w:p w:rsidR="00A2"><w:r><w:t>乙</w:t></w:r></w:p>
        </w:body></w:document>"""
        assertEquals("甲\n乙", DocxText.extract(docx(xml)))
    }

    @Test
    fun `w_t 带属性时也能识别`() {
        val xml = """<w:document><w:body><w:p>
            <w:r><w:t xml:space="preserve"> 前后空格 </w:t></w:r>
        </w:p></w:body></w:document>"""
        assertEquals("前后空格", DocxText.extract(docx(xml)))
    }

    // ------------------------------------------------------------ 换行与制表

    @Test
    fun `段内换行与制表被保留`() {
        val xml = """<w:document><w:body><w:p>
            <w:r><w:t>甲</w:t><w:br/><w:t>乙</w:t><w:tab/><w:t>丙</w:t></w:r>
        </w:p></w:body></w:document>"""
        assertEquals("甲\n乙\t丙", DocxText.extract(docx(xml)))
    }

    // ------------------------------------------------------------ 实体

    @Test
    fun `XML 实体被还原`() {
        val xml = """<w:document><w:body><w:p><w:r><w:t>a &lt; b &amp;&amp; c &gt; d &quot;e&quot; &apos;f&apos;</w:t></w:r></w:p></w:body></w:document>"""
        assertEquals("""a < b && c > d "e" 'f'""", DocxText.extract(docx(xml)))
    }

    @Test
    fun `公式里的尖括号不会被当成标签`() {
        // 数学文档里 f(x) < g(y) 很常见，实体还原后不能再被当标签剥掉
        val xml = """<w:document><w:body><w:p><w:r><w:t>若 x &lt; 2 则 f(x) = 1 &lt; 2</w:t></w:r></w:p></w:body></w:document>"""
        assertEquals("若 x < 2 则 f(x) = 1 < 2", DocxText.extract(docx(xml)))
    }

    @Test
    fun `unescape 只还原这五个实体`() {
        assertEquals("&nbsp;", DocxText.unescape("&nbsp;"))
        assertEquals("没有实体的普通文本", DocxText.unescape("没有实体的普通文本"))
    }

    // ------------------------------------------------------------ 边界

    @Test
    fun `w_t 没有闭合标签时不吞掉已抽到的内容`() {
        // 损坏的文档：宁可少抽，也不能死循环
        val xml = "<w:document><w:p><w:t>已抽到"
        assertEquals("已抽到", DocxText.xmlToText(xml))
    }

    @Test
    fun `首尾空白被去掉`() {
        val xml = "<w:document><w:body><w:p><w:r><w:t>\n  正文  \n</w:t></w:r></w:p></w:body></w:document>"
        assertEquals("正文", DocxText.extract(docx(xml)))
    }

    @Test
    fun `空文档返回空串`() {
        assertEquals("", DocxText.extract(docx("<w:document><w:body></w:body></w:document>")))
    }

    @Test
    fun `表格里的文字也会被抽到`() {
        // 表格单元格里也是 w:p/w:t，不该被漏掉
        val xml = """<w:document><w:body><w:tbl>
            <w:tr><w:tc><w:p><w:r><w:t>甲</w:t></w:r></w:p></w:tc></w:tr>
            <w:tr><w:tc><w:p><w:r><w:t>乙</w:t></w:r></w:p></w:tc></w:tr>
        </w:tbl></w:body></w:document>"""
        assertEquals("甲\n乙", DocxText.extract(docx(xml)))
    }
}
