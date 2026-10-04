package com.mistakebook.data.chat

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentCopyTest {

    @Test
    fun `copying an oversized attachment removes the partial file`() {
        val directory = Files.createTempDirectory("chat-attachment-copy").toFile()
        val target = File(directory, "partial.bin")
        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                copyLimited(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), target, maxBytes = 3)
            }
            assertTrue(error.message.orEmpty().startsWith("文件太大"))
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `copying within the limit preserves all bytes`() {
        val directory = Files.createTempDirectory("chat-attachment-copy").toFile()
        val target = File(directory, "attachment.bin")
        val bytes = byteArrayOf(1, 2, 3)
        try {
            assertTrue(copyLimited(ByteArrayInputStream(bytes), target, maxBytes = 3) == 3L)
            assertArrayEquals(bytes, target.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }
}
