package com.mistakebook.pipeline

import com.mistakebook.util.isWithinDirectory
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MineruZipPathTest {

    @Test
    fun `zip entry must remain below extraction directory boundary`() {
        val parent = Files.createTempDirectory("mineru-zip-path-").toFile()
        try {
            val target = File(parent, "mineru")
            assertTrue(isWithinDirectory(target, File(target, "images/question.jpg")))
            assertFalse(isWithinDirectory(target, File(parent, "mineru-copy/question.jpg")))
            assertFalse(isWithinDirectory(target, File(target, "../outside/question.jpg")))
        } finally {
            parent.deleteRecursively()
        }
    }
}
