package com.mistakebook.data.chat

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAttachmentDecodeTest {

    @Test
    fun `valid utf8 text is preserved`() {
        val text = "错题文本"
        assertEquals(text, decodePlainText(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `gb18030 text is used when utf8 decoding fails`() {
        val text = "中文附件内容"
        val bytes = text.toByteArray(Charset.forName("GB18030"))
        assertEquals(text, decodePlainText(bytes))
    }
}
