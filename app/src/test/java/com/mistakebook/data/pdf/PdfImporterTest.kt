package com.mistakebook.data.pdf

import android.content.Context
import android.content.ContextWrapper
import com.mistakebook.data.AppFiles
import com.mistakebook.data.local.CaptureTaskDao
import com.mistakebook.data.local.entities.CaptureTask
import com.mistakebook.data.repos.CaptureTaskRepository
import com.mistakebook.domain.TaskStatus
import com.mistakebook.pipeline.RecognitionEngine
import com.mistakebook.pipeline.RecognitionSubmitter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [PdfImporter] 单元测试。
 *
 * 测试范围：
 * - 损坏 / 非法 PDF 文件导入失败与提示文案
 * - 不存在的文件导入处理
 * - 纯文本 PDF 解析及按页提交给 [RecognitionSubmitter]
 * - [pageRange] 页码范围过滤逻辑
 * - 图片 PDF / Native [android.graphics.pdf.PdfRenderer] 在纯 JVM 环境下失败时的容错
 *
 * 未覆盖路径及原因：
 * - `renderPages` 中途渲染失败并回收 bitmap / file 的具体 Native 级行为：
 *   `PdfRenderer` 依赖 Android Native (`libpdfium.so`)，无法在纯 JVM 单测中运行。
 *   该路径需 `androidTest` / 真机环境验证。
 */
class PdfImporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var files: AppFiles
    private lateinit var fakeDao: FakeCaptureTaskDao
    private lateinit var submitter: RecognitionSubmitter
    private lateinit var pdfImporter: PdfImporter

    @Before
    fun setUp() {
        val filesDir = tempFolder.newFolder("files")
        val cacheDir = tempFolder.newFolder("cache")
        val fakeContext = FakeContext(filesDir, cacheDir)

        files = AppFiles(fakeContext)
        files.importDir.mkdirs()

        fakeDao = FakeCaptureTaskDao()
        val repo = CaptureTaskRepository(fakeDao)
        val dummyEngine = FakeRecognitionEngine()
        submitter = RecognitionSubmitter(repo, dummyEngine)
        pdfImporter = PdfImporter(fakeContext, files, submitter)
    }

    @Test
    fun `importPdf returns Failed when pdf is empty or corrupted`() = runBlocking {
        val corruptedPdfFile = tempFolder.newFile("corrupted.pdf")
        corruptedPdfFile.writeText("This is not a valid PDF file content.")

        val result = pdfImporter.importPdf(corruptedPdfFile, "Corrupted PDF Test")

        assertTrue(result is PdfImporter.Result.Failed)
        assertEquals("PDF 没有可识别的页面", (result as PdfImporter.Result.Failed).message)
    }

    @Test
    fun `importPdf returns Failed when non-existent pdf file processed`() = runBlocking {
        val nonExistentFile = File(tempFolder.root, "non_existent.pdf")

        val result = pdfImporter.importPdf(nonExistentFile, "Missing PDF Test")

        assertTrue(result is PdfImporter.Result.Failed)
    }

    @Test
    fun `importPdf succeeds for text PDF and submits text pages`() = runBlocking {
        val textPdfContent = createValidTextPdf("Question 1: Sample Text Content To Pass Threshold")

        val textPdfFile = tempFolder.newFile("text_sample.pdf")
        textPdfFile.writeBytes(textPdfContent)

        val result = pdfImporter.importPdf(textPdfFile, "Text PDF Test")

        assertTrue("Result should be Imported but was $result", result is PdfImporter.Result.Imported)
        val imported = result as PdfImporter.Result.Imported
        assertTrue(imported.textMode)
        assertEquals(1, imported.pageCount)
        assertEquals(1, imported.taskIds.size)
        assertEquals(1, fakeDao.storage.size)
    }

    @Test
    fun `importPdf filters out of range pages when pageRange specified`() = runBlocking {
        val twoPagePdfContent = createTwoPageTextPdf(
            "Page 1 Text Content Long Enough To Pass Min Char Threshold",
            "Page 2 Text Content Long Enough To Pass Min Char Threshold"
        )

        val pdfFile = tempFolder.newFile("two_page.pdf")
        pdfFile.writeBytes(twoPagePdfContent)

        // 请求第 1 页和第 99 页（99 页超出总页数被过滤，只保留第 1 页）
        val result = pdfImporter.importPdf(pdfFile, "Two Page PDF Test", pageRange = listOf(1, 99))

        assertTrue("Result should be Imported but was $result", result is PdfImporter.Result.Imported)
        val imported = result as PdfImporter.Result.Imported
        assertEquals(1, imported.pageCount)
    }

    @Test
    fun `importPdf with image PDF fails gracefully in JVM test environment when renderer cannot run`() = runBlocking {
        val imageOnlyPdfContent = """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [ 3 0 R ] /Count 1 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ] >> endobj
            trailer << /Root 1 0 R >>
            %%EOF
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val imagePdfFile = tempFolder.newFile("image_only.pdf")
        imagePdfFile.writeBytes(imageOnlyPdfContent)

        val result = pdfImporter.importPdf(imagePdfFile, "Image PDF Test")

        assertTrue(result is PdfImporter.Result.Failed)
        assertEquals("PDF 渲染失败", (result as PdfImporter.Result.Failed).message)
    }

    private fun createValidTextPdf(text: String): ByteArray {
        val streamText = "BT /F1 12 Tf ($text) Tj ET"
        val length = streamText.length
        return """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [ 3 0 R ] /Count 1 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ]
                /Resources << /Font << /F1 5 0 R >> >>
                /Contents 4 0 R >> endobj
            4 0 obj << /Length $length >>
            stream
            $streamText
            endstream
            endobj
            5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
            trailer << /Root 1 0 R >>
            %%EOF
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)
    }

    private fun createTwoPageTextPdf(text1: String, text2: String): ByteArray {
        val stream1 = "BT /F1 12 Tf ($text1) Tj ET"
        val stream2 = "BT /F1 12 Tf ($text2) Tj ET"
        return """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [ 3 0 R 4 0 R ] /Count 2 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ] /Resources << /Font << /F1 7 0 R >> >> /Contents 5 0 R >> endobj
            4 0 obj << /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ] /Resources << /Font << /F1 7 0 R >> >> /Contents 6 0 R >> endobj
            5 0 obj << /Length ${stream1.length} >>
            stream
            $stream1
            endstream
            endobj
            6 0 obj << /Length ${stream2.length} >>
            stream
            $stream2
            endstream
            endobj
            7 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
            trailer << /Root 1 0 R >>
            %%EOF
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)
    }

    private class FakeCaptureTaskDao : CaptureTaskDao {
        private var nextId = 1L
        val storage = mutableMapOf<Long, CaptureTask>()

        override fun observeById(id: Long): Flow<CaptureTask?> = flowOf(storage[id])
        override fun observeGroup(groupId: String): Flow<List<CaptureTask>> = flowOf(storage.values.filter { it.groupId == groupId })
        override suspend fun listGroup(groupId: String): List<CaptureTask> = storage.values.filter { it.groupId == groupId }
        override fun observeGroupOf(id: Long): Flow<List<CaptureTask>> = flowOf(emptyList())
        override suspend fun findById(id: Long): CaptureTask? = storage[id]
        override suspend fun insert(task: CaptureTask): Long {
            val id = nextId++
            storage[id] = task.copy(id = id)
            return id
        }
        override suspend fun update(task: CaptureTask) { storage[task.id] = task }
        override suspend fun updateStage(id: Long, stage: String, now: Long) {}
        override suspend fun updateStatus(id: Long, status: TaskStatus, message: String?, now: Long) {}
        override suspend fun updateFailure(id: Long, status: TaskStatus, message: String?, kind: String?, now: Long) {}
        override suspend fun moveToParsing(id: Long, now: Long) {}
        override suspend fun deleteById(id: Long) { storage.remove(id) }
        override suspend fun listPending(): List<CaptureTask> = emptyList()
        override suspend fun failUnfinished(message: String, now: Long) {}
        override suspend fun clearAll() { storage.clear() }
    }

    private class FakeRecognitionEngine : RecognitionEngine(
        taskRepository = CaptureTaskRepository(FakeCaptureTaskDao()),
        mineruClient = DummyMineruClient(),
        llmClient = DummyLlmClient(),
        settingsStore = DummySettingsStore()
    )

    private class DummyMineruClient : com.mistakebook.pipeline.MineruClient(
        api = DummyMineruApi(),
        files = AppFiles(FakeContext(File("/tmp"), File("/tmp")))
    )

    private class DummyLlmApi : com.mistakebook.net.llm.LlmApi {
        override suspend fun chatCompletions(url: String, authorization: String, request: com.mistakebook.net.llm.ChatRequest): retrofit2.Response<com.mistakebook.net.llm.ChatResponse> = TODO()
        override suspend fun listModels(url: String, authorization: String): retrofit2.Response<com.mistakebook.net.llm.ModelsResponse> = TODO()
    }

    private class DummyMineruApi : com.mistakebook.net.mineru.MineruApi {
        override suspend fun fileUrlsBatch(authorization: String, request: com.mistakebook.net.mineru.FileUrlsRequest): retrofit2.Response<com.mistakebook.net.mineru.MineruEnvelope<com.mistakebook.net.mineru.FileUrlsData>> = TODO()
        override suspend fun uploadFile(uploadUrl: String, body: okhttp3.RequestBody): retrofit2.Response<okhttp3.ResponseBody> = TODO()
        override suspend fun extractResults(authorization: String, batchId: String): retrofit2.Response<com.mistakebook.net.mineru.MineruEnvelope<com.mistakebook.net.mineru.ExtractResultsData>> = TODO()
        override suspend fun downloadZip(zipUrl: String): retrofit2.Response<okhttp3.ResponseBody> = TODO()
    }

    private class DummyLlmClient : com.mistakebook.pipeline.LlmClient(DummyLlmApi())

    private class DummySettingsStore : com.mistakebook.data.prefs.SettingsStore(FakeContext(File("/tmp"), File("/tmp")))

    private class FakeContext(
        private val filesDirFile: File,
        private val cacheDirFile: File
    ) : ContextWrapper(null) {
        override fun getFilesDir(): File = filesDirFile
        override fun getCacheDir(): File = cacheDirFile
        override fun getDataDir(): File = filesDirFile
        override fun getCodeCacheDir(): File = cacheDirFile
        override fun getDatabasePath(name: String): File = File(filesDirFile, name)
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences = DummySharedPreferences()
        override fun getApplicationContext(): Context = this
    }
}

private class DummySharedPreferences : android.content.SharedPreferences {
    override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    override fun getString(key: String?, defValue: String?): String? = defValue
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun contains(key: String?): Boolean = false
    override fun edit(): android.content.SharedPreferences.Editor = DummyEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class DummyEditor : android.content.SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor = this
        override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor = this
        override fun remove(key: String?): android.content.SharedPreferences.Editor = this
        override fun clear(): android.content.SharedPreferences.Editor = this
        override fun commit(): Boolean = true
        override fun apply() {}
    }
}
