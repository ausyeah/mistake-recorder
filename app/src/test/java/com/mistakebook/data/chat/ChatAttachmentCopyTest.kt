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

    @Test
    fun `invalid maxBytes throws IllegalArgumentException`() {
        val directory = Files.createTempDirectory("chat-attachment-copy").toFile()
        val target = File(directory, "invalid_max.bin")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                copyLimited(ByteArrayInputStream(byteArrayOf(1, 2)), target, maxBytes = 0)
            }
            assertThrows(IllegalArgumentException::class.java) {
                copyLimited(ByteArrayInputStream(byteArrayOf(1, 2)), target, maxBytes = -5)
            }
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `empty input stream throws IllegalArgumentException`() {
        val directory = Files.createTempDirectory("chat-attachment-copy").toFile()
        val target = File(directory, "empty.bin")
        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                copyLimited(ByteArrayInputStream(ByteArray(0)), target, maxBytes = 100)
            }
            assertTrue(error.message == "文件为空")
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `copying when total equals maxBytes succeeds`() {
        val directory = Files.createTempDirectory("chat-attachment-copy").toFile()
        val target = File(directory, "exact_max.bin")
        val bytes = byteArrayOf(10, 20, 30, 40, 50)
        try {
            val total = copyLimited(ByteArrayInputStream(bytes), target, maxBytes = 5)
            assertTrue(total == 5L)
            assertArrayEquals(bytes, target.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `multi chunk stream copying within limit succeeds`() {
        val directory = Files.createTempDirectory("chat-attachment-copy").toFile()
        val target = File(directory, "multichunk.bin")
        // Size > 8192 buffer size (e.g. 8192 * 2 + 123 = 16507 bytes)
        val bytes = ByteArray(16507) { (it % 256).toByte() }
        try {
            val total = copyLimited(ByteArrayInputStream(bytes), target, maxBytes = 20000)
            assertTrue(total == 16507L)
            assertArrayEquals(bytes, target.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `multi chunk stream copying exceeding limit fails and cleans target`() {
        val directory = Files.createTempDirectory("chat-attachment-copy").toFile()
        val target = File(directory, "multichunk_oversized.bin")
        val bytes = ByteArray(16507) { (it % 256).toByte() }
        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                copyLimited(ByteArrayInputStream(bytes), target, maxBytes = 10000)
            }
            assertTrue(error.message.orEmpty().startsWith("文件太大"))
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `target with nested or null parent directory and file overwrite handling`() {
        val directory = Files.createTempDirectory("chat-attachment-copy").toFile()
        val nestedTarget = File(directory, "nested/path/to/target.bin")
        val bytes = byteArrayOf(1, 2, 3)
        try {
            // Nested directory created automatically
            val total = copyLimited(ByteArrayInputStream(bytes), nestedTarget, maxBytes = 10)
            assertTrue(total == 3L)
            assertArrayEquals(bytes, nestedTarget.readBytes())

            // Overwrites existing target file cleanly
            val newBytes = byteArrayOf(4, 5)
            val newTotal = copyLimited(ByteArrayInputStream(newBytes), nestedTarget, maxBytes = 10)
            assertTrue(newTotal == 2L)
            assertArrayEquals(newBytes, nestedTarget.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }
}
