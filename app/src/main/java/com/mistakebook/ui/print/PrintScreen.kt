package com.mistakebook.ui.print

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mistakebook.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mistakebook.data.local.displayTitle
import com.mistakebook.data.local.entities.Question
import com.mistakebook.data.local.printImagePath
import com.mistakebook.domain.MasteryStatus
import com.mistakebook.di.AppContainer
import com.mistakebook.ui.common.EmptyState
import com.mistakebook.ui.common.Format
import com.mistakebook.ui.common.QuestionThumb
import com.mistakebook.ui.common.containerViewModel
import com.mistakebook.ui.settings.BlankHeightRow
import com.mistakebook.ui.common.ReorderDragHandle
import com.mistakebook.ui.common.moveItem
import com.mistakebook.ui.common.orderByIds
import com.mistakebook.print.ExportFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 打印页（PRD 7.7）：筛选 -> 勾选 -> 打印选项 -> 生成 PDF -> 分享。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: PrintViewModel = containerViewModel(container) { PrintViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    // 三条文案原本都写死了「PDF」，选了 HTML 也显示「生成 PDF」。
    val formatName = stringResource(state.format.shortLabelRes)
    val context = LocalContext.current
    val questionListState = rememberLazyListState()
    var arrangedQuestionIds by remember { mutableStateOf<List<Long>?>(null) }
    var draggedQuestionId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val displayQuestions = remember(state.questions, arrangedQuestionIds) {
        arrangedQuestionIds?.let { orderByIds(state.questions, it) { question -> question.id } }
            ?: state.questions
    }

    fun beginQuestionDrag(id: Long) {
        draggedQuestionId = id
        dragOffsetPx = 0f
        arrangedQuestionIds = displayQuestions.map { it.id }
    }

    fun updateQuestionDrag(delta: Float) {
        val draggedId = draggedQuestionId ?: return
        dragOffsetPx += delta
        val layout = questionListState.layoutInfo
        val draggedItem = layout.visibleItemsInfo.firstOrNull { it.key == draggedId } ?: return
        val center = draggedItem.offset + draggedItem.size / 2f + dragOffsetPx
        val target = layout.visibleItemsInfo.firstOrNull { item ->
            val top = item.offset.toFloat()
            val key = item.key as? Long
            key != null && center >= top && center < top + item.size
        } ?: return
        val targetId = target.key as? Long ?: return
        val order = arrangedQuestionIds ?: displayQuestions.map { it.id }
        val fromIndex = order.indexOf(draggedId)
        val toIndex = order.indexOf(targetId)
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            arrangedQuestionIds = moveItem(order, fromIndex, toIndex)
            dragOffsetPx = 0f
        }
    }

    fun finishQuestionDrag() {
        arrangedQuestionIds?.let(viewModel::setQuestionOrder)
        draggedQuestionId = null
        dragOffsetPx = 0f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.print_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Button(
                    onClick = viewModel::generate,
                    enabled = state.selected.isNotEmpty() && !state.generating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (state.generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(
                            R.string.print_selected_format,
                            state.selected.size,
                            formatName
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            PrintFilters(
                state = state,
                onSubjectChange = viewModel::setSubject,
                onStatusChange = viewModel::setStatus,
                onKeywordChange = viewModel::setKeyword,
                onSetAll = { checked ->
                    val ids = state.questions.map { it.id }
                    if (checked) viewModel.selectAll(ids) else viewModel.deselectAll(ids)
                },
                onInvert = { viewModel.invertSelection(state.questions.map { it.id }) }
            )

            PrintOptionsPanel(
                includeImage = state.includeImage,
                showAnswer = state.showAnswer,
                blankRedo = state.blankRedo,
                blankHeight = state.blankHeight,
                onIncludeImage = viewModel::setIncludeImage,
                onShowAnswer = viewModel::setShowAnswer,
                onBlankRedo = viewModel::setBlankRedo,
                onBlankHeight = viewModel::setBlankHeight
            )

            if (state.questions.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.print_empty),
                    subtitle = ""
                )
            } else {
                LazyColumn(
                    state = questionListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(displayQuestions, key = { _, item -> item.id }) { index, question ->
                        val subjectName = state.subjectNames[question.subjectId]
                        PrintRow(
                            question = question,
                            subjectName = subjectName,
                            index = index + 1,
                            checked = question.id in state.selected,
                            dragging = draggedQuestionId == question.id,
                            onToggle = { viewModel.toggleSelect(question.id) },
                            onReorderStart = { beginQuestionDrag(question.id) },
                            onReorder = ::updateQuestionDrag,
                            onReorderEnd = ::finishQuestionDrag
                        )
                    }
                }
            }
        }
    }

    if (state.result != null) {
        val output = state.result!!
        AlertDialog(
            onDismissRequest = viewModel::consumeResult,
            title = { Text(stringResource(R.string.print_done_title, formatName)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.print_path_format, output.displayPath),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.skipped.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.print_skipped_format,
                                state.skipped.joinToString("、")
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        // MIME 必须按格式取：写死 application/pdf 的话，分享 docx 时收件部应用会拒绝。
                            type = output.format.mimeType
                        putExtra(Intent.EXTRA_STREAM, output.shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }) { Text(stringResource(R.string.print_share)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(output.shareUri, output.format.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }) { Text(stringResource(R.string.print_open)) }
                    TextButton(onClick = viewModel::consumeResult) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        )
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            title = { Text(stringResource(R.string.print_failed, "")) },
            text = { Text(state.error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeError) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@Composable
private fun PrintRow(
    question: Question,
    subjectName: String?,
    /** 打印清单里的连续序号，和 PDF 卡片上的编号一致，方便对照漏题。 */
    index: Int,
    checked: Boolean,
    dragging: Boolean,
    onToggle: () -> Unit,
    onReorderStart: () -> Unit,
    onReorder: (Float) -> Unit,
    onReorderEnd: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            QuestionThumb(
                imagePath = question.printImagePath,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.print_item_index, index),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = subjectName ?: stringResource(R.string.print_unclassified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = question.displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
            ReorderDragHandle(
                dragging = dragging,
                onDragStart = onReorderStart,
                onDrag = onReorder,
                onDragEnd = onReorderEnd
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintFilters(
    state: PrintUiState,
    onSubjectChange: (Long?) -> Unit,
    onStatusChange: (MasteryStatus?) -> Unit,
    onKeywordChange: (String) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onInvert: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        SubjectDropdown(
            subjects = state.subjects,
            selectedId = state.subjectId,
            onSelect = onSubjectChange,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.allSelected,
                onCheckedChange = onSetAll,
                enabled = state.questions.isNotEmpty()
            )
            Text(stringResource(R.string.print_select_all), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onInvert, enabled = state.questions.isNotEmpty()) {
                Text(stringResource(R.string.print_select_invert))
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf<Pair<MasteryStatus?, Int>>(
                null to R.string.home_filter_all,
                MasteryStatus.ACTIVE to R.string.home_filter_active,
                MasteryStatus.MASTERED to R.string.home_filter_mastered
            ).forEach { (value, labelRes) ->
                FilterChip(
                    selected = state.status == value,
                    onClick = { onStatusChange(value) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
    }
}

@Composable
private fun PrintOptionsPanel(
    includeImage: Boolean,
    showAnswer: Boolean,
    blankRedo: Boolean,
        blankHeight: Int,
    onIncludeImage: (Boolean) -> Unit,
    onShowAnswer: (Boolean) -> Unit,
    onBlankRedo: (Boolean) -> Unit,
        onBlankHeight: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.print_options),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            OptionSwitch(
                label = stringResource(R.string.print_include_image),
                checked = includeImage,
                onChange = onIncludeImage
            )
            OptionSwitch(
                label = stringResource(R.string.print_show_answer),
                checked = showAnswer,
                onChange = onShowAnswer
            )
            OptionSwitch(
                label = stringResource(R.string.print_blank_redo),
                checked = blankRedo,
                onChange = onBlankRedo
            )
            if (blankRedo) {
                // 与设置页共用同一控件：直接显示并使用设置里存的值，不再是写死的三档
                BlankHeightRow(height = blankHeight, onChange = onBlankHeight)
            }
            Spacer(Modifier.height(10.dp))
            PdfViaBrowserHint()
        }
    }
}

/**
 * 小字提示：如何把 HTML 变成 PDF。
 *
 * ## 为什么不自己生成 PDF
 *
 * 已经取消了 App 内生成 PDF 的路径。它走 `WebView.createPrintDocumentAdapter`，
 * 且因为两个回调的构造器是 package-private，只能传 `null` ——
 * “写完没写完”无法直接知道，只能靠文件大小猜。实测结果就是
 * 「PDF 写出失败或超时」。
 *
 * 而浏览器的打印管线是成熟功能，页边距、缩放、页眉都能调，
 * 且 WebView 的渲染本身就是浏览器——它能把我们的 MathML 渲染得最好。
 *
 * 所以只导出 HTML，把「打印成 PDF」这一步交给用户手动做。
 */
@Composable
private fun PdfViaBrowserHint() {
    Text(
        text = stringResource(R.string.print_pdf_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectDropdown(
    subjects: List<com.mistakebook.data.local.entities.Subject>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = subjects.firstOrNull { it.id == selectedId }?.name
        ?: stringResource(R.string.home_subject_all)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_subject_all)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            subjects.forEach { subject ->
                DropdownMenuItem(
                    text = { Text(subject.name) },
                    onClick = {
                        onSelect(subject.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
