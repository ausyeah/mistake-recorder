package com.mistakebook.ui.importpdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mistakebook.R
import com.mistakebook.data.pdf.PdfImporter
import com.mistakebook.data.pdf.PdfTextProbe
import com.mistakebook.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PAGES = 60

/**
 * PDF 导入页：选择 PDF 后判断走文本链路还是逐页 MinerU 识别，可指定页码范围。
 * 解析期间显示全屏居中的加载遮罩。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(
    container: AppContainer,
    onStarted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var pageCount by remember { mutableStateOf(0) }
    var textMode by remember { mutableStateOf(false) }
    var rangeText by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val importer = remember(container) {
        PdfImporter(context, container.files, container.recognitionSubmitter)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            onBack()
            return@rememberLauncherForActivityResult
        }
        pdfUri = uri
        pdfName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
        working = true
        error = null
        scope.launch {
            // 复制、读取、解析全部放 IO 线程：大 PDF 在主线程做会直接 ANR
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val target = container.files.newImportFile("pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("无法读取所选文件")
                    val info = PdfTextProbe.probe(target)
                    target to info
                }
            }
            working = false
            val failure = result.exceptionOrNull()
            if (failure != null) {
                error = failure.message ?: "无法解析该 PDF"
                return@launch
            }
            val (target, info) = result.getOrThrow()
            if (info.pageCount <= 0) {
                error = "无法解析该 PDF"
                return@launch
            }
            pageCount = info.pageCount
            textMode = info.textMode
            container.pendingPdfFile = target
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.pdf_import_title)) }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                if (pdfUri == null) {
                    Text(
                        text = stringResource(R.string.pdf_import_pick),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                        Text(stringResource(R.string.pdf_import_pick))
                    }
                } else {
                    Text(pdfName, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (textMode) {
                            stringResource(R.string.pdf_import_text_mode, pageCount)
                        } else {
                            stringResource(R.string.pdf_import_image_mode, pageCount)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (pageCount > MAX_PAGES) {
                        Text(
                            text = stringResource(R.string.pdf_import_too_large, MAX_PAGES),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rangeText,
                        onValueChange = { rangeText = it },
                        label = { Text(stringResource(R.string.pdf_import_range)) },
                        placeholder = { Text(stringResource(R.string.pdf_import_range_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val file = container.pendingPdfFile ?: return@Button
                            val range = parseRange(rangeText, pageCount)
                            if (rangeText.isNotBlank() && range == null) {
                                error = context.getString(R.string.pdf_import_invalid_range)
                                return@Button
                            }
                            working = true
                            error = null
                            scope.launch {
                                val result = importer.importPdf(
                                    file,
                                    pdfName.substringBeforeLast('.'),
                                    range
                                )
                                working = false
                                when (result) {
                                    is PdfImporter.Result.Imported -> {
                                        container.lastImportTaskIds = result.taskIds
                                        onStarted()
                                    }

                                    is PdfImporter.Result.Failed -> error = result.message
                                }
                            }
                        },
                        enabled = !working
                    ) {
                        Text(stringResource(R.string.pdf_import_start))
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { error = null }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }

            if (working) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = stringResource(R.string.pdf_import_parsing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

}

// "1-5,8,11-12" -> [1,2,3,4,5,8,11,12]；无法解析返回 null。
internal fun parseRange(input: String, totalPages: Int): List<Int>? {
    val cleaned = input.trim()
    if (cleaned.isEmpty()) return null
    val result = mutableSetOf<Int>()
    cleaned.split(',').forEach { part ->
        val trimmed = part.trim()
        if (trimmed.isEmpty()) return@forEach
        if (trimmed.contains('-')) {
            val bounds = trimmed.split('-')
            if (bounds.size != 2) return null
            val start = bounds[0].trim().toIntOrNull() ?: return null
            val end = bounds[1].trim().toIntOrNull() ?: return null
            if (start > end) return null
            for (page in start..end) result += page
        } else {
            val page = trimmed.toIntOrNull() ?: return null
            result += page
        }
    }
    return result.filter { it in 1..totalPages }.sorted()
}
