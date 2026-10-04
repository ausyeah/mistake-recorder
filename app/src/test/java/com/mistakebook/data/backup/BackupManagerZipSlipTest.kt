package com.mistakebook.data.backup

import com.mistakebook.util.isWithinDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManagerZipSlipTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `isWithinDirectory permits valid nested paths`() {
        val targetDir = tempFolder.newFolder("restore_temp")
        val validFile1 = File(targetDir, "mistake_book.db")
        val validFile2 = File(targetDir, "files/sub/photo.jpg")

        assertTrue(isWithinDirectory(targetDir, validFile1))
        assertTrue(isWithinDirectory(targetDir, validFile2))
    }

    @Test
    fun `isWithinDirectory blocks path traversal outside target directory`() {
        val targetDir = tempFolder.newFolder("restore_temp")
        val traversalFile1 = File(targetDir, "../evil.txt")
        val traversalFile2 = File(targetDir, "files/../../evil.txt")

        assertFalse(isWithinDirectory(targetDir, traversalFile1))
        assertFalse(isWithinDirectory(targetDir, traversalFile2))
    }

    @Test
    fun `isWithinDirectory blocks directory prefix matching trick`() {
        val parent = tempFolder.newFolder("parent")
        val targetDir = File(parent, "restore_1")
        targetDir.mkdirs()

        // /parent/restore_10/file.txt starts with /parent/restore_1 if separator is missing
        val prefixOverlapFile = File(parent, "restore_10/file.txt")

        assertFalse(isWithinDirectory(targetDir, prefixOverlapFile))
    }

    @Test
    fun `zip extraction skips malicious path traversal entries`() {
        val targetDir = tempFolder.newFolder("restore_extract")
        val outsideDir = tempFolder.newFolder("outside")

        // Construct a zip in memory containing valid file and path traversal file
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            // Valid entry
            zip.putNextEntry(ZipEntry("mistake_book.db"))
            zip.write("valid db content".toByteArray())
            zip.closeEntry()

            // Valid nested entry
            zip.putNextEntry(ZipEntry("files/image.png"))
            zip.write("valid image content".toByteArray())
            zip.closeEntry()

            // Zip Slip traversal entry
            zip.putNextEntry(ZipEntry("../outside/pwned.txt"))
            zip.write("malicious content".toByteArray())
            zip.closeEntry()
        }

        val zipInput = ByteArrayInputStream(baos.toByteArray())

        // Extract with Zip Slip prevention guard as in BackupManager
        ZipInputStream(zipInput.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val target = File(targetDir, entry.name)
                    if (isWithinDirectory(targetDir, target)) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // Assertions
        assertTrue(File(targetDir, "mistake_book.db").exists())
        assertTrue(File(targetDir, "files/image.png").exists())
        assertFalse(File(outsideDir, "pwned.txt").exists())
        assertFalse(File(targetDir.parentFile, "outside/pwned.txt").exists())
    }
}
