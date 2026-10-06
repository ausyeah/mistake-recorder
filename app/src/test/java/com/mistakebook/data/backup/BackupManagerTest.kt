package com.mistakebook.data.backup

import android.content.Context
import android.content.ContextWrapper
import com.mistakebook.data.AppFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManagerTest {

    private class TestContext(private val baseDir: File) : ContextWrapper(null) {
        override fun getFilesDir(): File = File(baseDir, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(baseDir, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = File(getFilesDir(), name)
    }

    @Test
    fun `export invokes reopenDatabase in finally block when export fails`() = runBlocking {
        val rootDir = Files.createTempDirectory("backup-test-fail").toFile()
        try {
            val context = TestContext(rootDir)
            val files = AppFiles(context)

            // Do NOT create files.databaseFile, so export will fail when trying to read databaseFile

            val callOrder = mutableListOf<String>()
            val backupManager = BackupManager(
                context = context,
                files = files,
                closeDatabase = { callOrder.add("close") },
                reopenDatabase = { callOrder.add("reopen") }
            )

            val result = backupManager.export()

            assertTrue("export should return failure when database file is missing", result.isFailure)
            assertEquals("closeDatabase then reopenDatabase must be invoked in order", listOf("close", "reopen"), callOrder)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `export succeeds and invokes closeDatabase and reopenDatabase in order`() = runBlocking {
        val rootDir = Files.createTempDirectory("backup-test-success").toFile()
        try {
            val context = TestContext(rootDir)
            val files = AppFiles(context)

            // Prepare database file and a sample attachment file
            files.databaseFile.parentFile?.mkdirs()
            files.databaseFile.writeText("sample db content")

            val cropDir = File(context.filesDir, "crops").apply { mkdirs() }
            File(cropDir, "test.jpg").writeText("sample image content")

            val callOrder = mutableListOf<String>()
            val backupManager = BackupManager(
                context = context,
                files = files,
                closeDatabase = { callOrder.add("close") },
                reopenDatabase = { callOrder.add("reopen") }
            )

            val result = backupManager.export()

            assertTrue("export should succeed", result.isSuccess)
            val exportPath = result.getOrThrow()
            assertTrue("exported zip file should exist", File(exportPath).exists())
            assertEquals("closeDatabase then reopenDatabase must be invoked in order", listOf("close", "reopen"), callOrder)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `restore invokes reopenDatabase and cleans up temp directory when restore fails`() = runBlocking {
        val rootDir = Files.createTempDirectory("restore-test-fail").toFile()
        try {
            val context = TestContext(rootDir)
            val files = AppFiles(context)

            val callOrder = mutableListOf<String>()
            val backupManager = BackupManager(
                context = context,
                files = files,
                closeDatabase = { callOrder.add("close") },
                reopenDatabase = { callOrder.add("reopen") }
            )

            // Invalid zip input stream (missing database file in zip)
            val badZipBytes = ByteArrayOutputStream().use { baos ->
                ZipOutputStream(baos).use { zip ->
                    zip.putNextEntry(ZipEntry("other.txt"))
                    zip.write("dummy".toByteArray())
                    zip.closeEntry()
                }
                baos.toByteArray()
            }

            val result = backupManager.restore(ByteArrayInputStream(badZipBytes))

            assertTrue("restore should return failure for invalid zip", result.isFailure)
            assertEquals("closeDatabase then reopenDatabase must be invoked in order", listOf("close", "reopen"), callOrder)

            // Verify temp restore directories in cacheDir are cleaned up
            val cacheFiles = context.cacheDir.listFiles()?.filter { it.name.startsWith("restore_") } ?: emptyList()
            assertTrue("temp restore directories should be deleted in finally block", cacheFiles.isEmpty())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `restore works with old backup lacking wordbook_local database`() = runBlocking {
        val rootDir = Files.createTempDirectory("restore-test-compat").toFile()
        try {
            val context = TestContext(rootDir)
            val files = AppFiles(context)
            
            // Create a backup zip lacking wordbook_local.db
            val zipBytes = ByteArrayOutputStream().use { baos ->
                ZipOutputStream(baos).use { zip ->
                    zip.putNextEntry(ZipEntry("mistake_book.db"))
                    zip.write("old db content".toByteArray())
                    zip.closeEntry()
                }
                baos.toByteArray()
            }
            
            val backupManager = BackupManager(context, files)
            val result = backupManager.restore(ByteArrayInputStream(zipBytes))
            
            // Should succeed without ZipEntry missing exception
            assertTrue("restore should succeed for old backup", result.isSuccess)
            assertEquals("old db content", files.databaseFile.readText())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `export and restore with wordbook_local database and its WAL SHM files`() = runBlocking {
        val rootDir = Files.createTempDirectory("backup-test-wordbook").toFile()
        try {
            val context = TestContext(rootDir)
            val files = AppFiles(context)

            files.databaseFile.parentFile?.mkdirs()
            files.databaseFile.writeText("mistake db")

            val wordbookDb = context.getDatabasePath("wordbook_local.db")
            wordbookDb.writeText("wordbook db")
            File(wordbookDb.path + "-wal").writeText("wordbook wal")
            File(wordbookDb.path + "-shm").writeText("wordbook shm")

            val backupManager = BackupManager(context, files)

            val exportPath = backupManager.export().getOrThrow()
            
            // Clear original files
            wordbookDb.delete()
            File(wordbookDb.path + "-wal").delete()
            File(wordbookDb.path + "-shm").delete()
            files.databaseFile.delete()

            val result = backupManager.restore(File(exportPath).inputStream())
            assertTrue(result.isSuccess)
            
            assertEquals("mistake db", files.databaseFile.readText())
            assertEquals("wordbook db", wordbookDb.readText())
            assertEquals("wordbook wal", File(wordbookDb.path + "-wal").readText())
            assertEquals("wordbook shm", File(wordbookDb.path + "-shm").readText())
        } finally {
            rootDir.deleteRecursively()
        }
    }
}
