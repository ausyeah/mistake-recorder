package com.mistakebook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mistakebook.R
import com.mistakebook.data.prefs.LlmProfile
import com.mistakebook.domain.ErrorReason

@Composable
internal fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ChoiceRow 已删：唯一的使用者是 MinerU 的模型版本/识别语言两项，
// 而这两项现已固定为 vlm / ch，不再可配。

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    /** 可选的补充说明，放在标签下方，用来解释这个开关到底影响什么。 */
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * 重做区高度：60~300pt，步进 10pt。
 * 设置页与打印页共用同一控件，保证两处显示与实际取值永远一致
 * （之前打印页是写死的 60/100/150 三个 chip，设置里调成别的值就「一个都不选中」）。
 */
@Composable
fun BlankHeightRow(height: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_blank_height_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.settings_blank_height_range, height),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = height.toFloat(),
            onValueChange = { value -> onChange((value / 10).toInt() * 10) },
            valueRange = 60f..300f,
            steps = 23
        )
    }
}

/**
 * 首启引导条。
 *
 * 公开分发的 APK 不预填任何 API Key（密钥在 DEX 里是明文，预填等于公开），
 * 用户第一次进设置页看到空输入框会不知道该做什么。这里明确告诉他要填什么、
 * 去哪里拿，避免「以为坏了」。
 */
@Composable
internal fun FirstRunHint(textRes: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 测试结果常驻条。
 *
 * 用 Surface 而不是 snackbar：snackbar 会消失，而「首字响应 428ms」这类信息
 * 用户经常要对照看（改了配置再测一次，比一比快慢）。
 */
@Composable
internal fun TestResultBanner(
    message: String,
    ok: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val container = if (ok) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = if (ok) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer
            )
        }
    }
}

/**
 * 测试按钮。
 *
 * 加载态与空闲态**等高**（都用 [BUTTON_HEIGHT]）：原先用固定高度的 Box 包住，
 * 但没给宽度约束，加载态的「测试中…」会把整行布局挤变形。
 */
@Composable
internal fun TestButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    /**
     * 这个按钮自己的测试结果。
     *
     * **结果必须跟着按钮走。** 早先两个测试共用一对 `testResult` / `testOk`，
     * 而横幅统一渲染在大模型那一组的底部——点 MinerU 的按钮，
     * 结果却显示在几屏之外的另一个分组里，用户根本对不上是哪个按钮的结果。
     * 而且两个测试还会互相覆盖：后测的那个把先测的结果冲掉。
     *
     * **没有默认值，是故意的。** 之前写成 `result: String? = null`，
     * 于是 MinerU 那个调用点漏传了 `result` / `resultOk` 也能编译通过，
     * 结果 ViewModel 里算好、存进 state 的测试结果**永远没人渲染**——
     * 点「测试 MinerU」，按钮右边空空如也，没有任何报错。
     * 去掉默认值后，漏传会直接编译不过。
     */
    result: String?,
    resultOk: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.height(BUTTON_HEIGHT)) {
            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_testing),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Button(onClick = onClick) { Text(text) }
            }
        }
        // 结果在按钮右边，不另起一行——换行会让按钮下方留一大片空白
        result?.let { message ->
            TestResultBanner(
                message = message,
                ok = resultOk,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 按钮统一高度，避免加载态切换时行高跳动。 */
internal val BUTTON_HEIGHT = 40.dp

/**
 * 存储占用信息。
 *
 * 用户在数据管理这一块最想知道的其实是「我的错题占了多少磁盘」——
 * 照片是错题本里最大的开销，删了几百道题后空间没降会让人以为删失败。
 * 原来只有三个按钮、零信息，出了问题无从判断。
 *
 * 目录统计走 IO 线程，不阻塞滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageInfoRow(container: com.mistakebook.di.AppContainer) {
    var stats by remember { mutableStateOf<StorageStats?>(null) }

    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) { computeStorageStats(container) }
    }

    val current = stats
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_storage_title),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (current == null) {
                        stringResource(R.string.settings_storage_loading)
                    } else {
                        stringResource(
                            R.string.settings_storage_detail,
                            current.questionCount,
                            formatSize(current.imagesBytes),
                            formatSize(current.databaseBytes),
                            formatSize(current.totalBytes)
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal data class StorageStats(
    val questionCount: Int,
    /** 原图 + 题目附图 + MinerU 切图 */
    val imagesBytes: Long,
    /** 数据库（含 -wal / -shm） */
    val databaseBytes: Long,
    val totalBytes: Long
)

/**
 * 按实际目录规划统计，不猜。
 *
 * 分开统计「图片」和「数据库」是有意义的：图片占绝大部分且会随删除题目而下降，
 * 数据库则基本不变。用户删完题发现空间没降，多半是没意识到图片还在。
 */
private fun computeStorageStats(container: com.mistakebook.di.AppContainer): StorageStats {
    val files = container.files
    var images = 0L
    images += dirSize(files.cropDir)
    images += dirSize(files.importDir)
    images += dirSize(java.io.File(files.cropDir.parentFile, "questions"))
    images += dirSize(java.io.File(files.cropDir.parentFile, "mineru"))

    val db = files.databaseFile
    var database = if (db.exists()) db.length() else 0L
    // WAL 里可能有还没 checkpoint 的数据，不算进来会低估
    listOf("-wal", "-shm").forEach { suffix ->
        val extra = java.io.File(db.path + suffix)
        if (extra.exists()) database += extra.length()
    }

    return StorageStats(
        questionCount = countFiles(java.io.File(files.cropDir.parentFile, "questions")),
        imagesBytes = images,
        databaseBytes = database,
        totalBytes = images + database
    )
}

private fun dirSize(dir: java.io.File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var total = 0L
    val stack = ArrayDeque<java.io.File>()
    stack.addLast(dir)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        val children = current.listFiles() ?: continue
        children.forEach { child ->
            if (child.isDirectory) stack.addLast(child) else total += child.length()
        }
    }
    return total
}

private fun countFiles(dir: java.io.File?): Int {
    if (dir == null || !dir.exists()) return 0
    var total = 0
    val stack = ArrayDeque<java.io.File>()
    stack.addLast(dir)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        val children = current.listFiles() ?: continue
        children.forEach { child ->
            if (child.isDirectory) stack.addLast(child) else total++
        }
    }
    return total
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
internal fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "隐藏" else "显示")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileRow(
    profile: LlmProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onSave: (LlmProfile) -> Unit,
    /**
     * 拉模型列表。传的是**当前输入框内容**，不是已保存的配置——
     * 否则「填了 URL 和 Key 但没按保存」时必然失败。
     */
    onFetchModels: (LlmProfile) -> Unit,
    onTest: (LlmProfile) -> Unit,
    testing: Boolean = false,

    /**
     * 草稿里任何一项被改动时回调。
     *
     * 用来作废这个配置上一次测试的结论：改了 Key / URL 却还挂着
     * 「连接成功」，用户会以为新配置也通了。
     */
    onDraftChanged: () -> Unit = {},

    /**
     * 这个配置自己的测试结果。
     *
     * 早先所有结果共用一对字段、统一渲染在大模型分组的**最底部**——
     * 那个位置和「测试」按钮之间隔着输入框与「可用模型」按钮，
     * 用户分不清眼前这行字是自己点的哪个测试的结果。
     *
     * 同样**不设默认值**：有默认值时漏传不会报错，
     * 结果就是「测了但什么都没显示」。
     */
    result: String?,
    resultOk: Boolean,
    fetchingModels: Boolean = false,
    pickedModel: String? = null,
    onPickedModelConsumed: () -> Unit = {}
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var baseUrl by remember(profile.id) { mutableStateOf(profile.baseUrl) }
    var apiKey by remember(profile.id) { mutableStateOf(profile.apiKey) }
    var model by remember(profile.id) { mutableStateOf(profile.model) }

    // 当前输入拼成的草稿。保存 / 拉列表 / 测试都用它，保证三者看到的是同一份值
    fun draft(): LlmProfile = profile.copy(
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model
    )

    // 只有域名和 Key 齐了才能发请求；模型名**不**参与判断
    val canRequest = baseUrl.isNotBlank() && apiKey.isNotBlank()

    // 模型列表里选中某个模型后回填到输入框
    LaunchedEffect(pickedModel) {
        if (!pickedModel.isNullOrBlank()) {
            model = pickedModel
            // 回填也是改动：换了个模型，之前那次的测试结论不再作数
            onDraftChanged()
            onPickedModelConsumed()
        }
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name.ifBlank { model.ifBlank { "未命名配置" } },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (selected) {
                        Text(
                            text = stringResource(R.string.settings_active_profile),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDelete, enabled = profile.model.isNotBlank()) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.settings_delete_profile)
                    )
                }
            }
            if (selected) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onDraftChanged() },
                    label = { Text(stringResource(R.string.settings_profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; onDraftChanged() },
                    label = { Text(stringResource(R.string.settings_base_url)) },
                    placeholder = { Text("https://api.deepseek.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    label = stringResource(R.string.settings_api_key),
                    value = apiKey,
                    onValueChange = { apiKey = it; onDraftChanged() }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; onDraftChanged() },
                    label = { Text(stringResource(R.string.settings_model)) },
                    placeholder = { Text(stringResource(R.string.settings_model_hint)) },
                    singleLine = true,
                    // 尾部的下拉按钮：点了直接拉列表并弹选择框。
                    // 原来这个功能是单独一个「可用模型」按钮，且要求模型名非空才显示——
                    // 而拉列表的目的正是拿到模型名，等于死锁。
                    trailingIcon = {
                        IconButton(
                            onClick = { onFetchModels(draft()) },
                            enabled = canRequest && !fetchingModels
                        ) {
                            if (fetchingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.settings_fetch_models)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                // 两个按钮等宽平分。原先是三个按钮挤在一行，
                // 「可用模型」被挤成两行竖排（用户反馈：UI 有问题）。
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSave(draft()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                    OutlinedButton(
                        onClick = { onTest(draft()) },
                        enabled = canRequest && !testing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.settings_test_llm))
                        }
                    }
                }
                // 结果紧跟在按钮下方（这一组是竖排布局，按钮已占满宽度）
                result?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    TestResultBanner(message = message, ok = resultOk)
                }
            }
        }
    }
}

@Composable
internal fun AddProfileDialog(onDismiss: () -> Unit, onConfirm: (LlmProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_add_profile)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_profile_name)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.settings_base_url)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    label = stringResource(R.string.settings_api_key),
                    value = apiKey,
                    onValueChange = { apiKey = it }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.settings_model)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    LlmProfile(name = name, baseUrl = baseUrl, apiKey = apiKey, model = model)
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// 题目编辑页共用的学科下拉
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubjectDropdown(
    subjects: List<com.mistakebook.data.local.entities.Subject>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = subjects.firstOrNull { it.id == selectedId }?.name ?: "未分类"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.edit_subject)) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

// 编辑页知识点输入
@Composable
internal fun KnowledgePointEditor(
    points: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(stringResource(R.string.edit_knowledge_add_hint)) },
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    onAdd(input)
                    input = ""
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (points.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                points.forEach { point ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(point) },
                        label = { Text(point) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_delete),
                                modifier = Modifier.height(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

// 编辑页错因单选
@Composable
internal fun ErrorReasonChooser(
    selected: ErrorReason,
    onSelect: (ErrorReason) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ErrorReason.entries.forEach { reason ->
            FilterChip(
                selected = selected == reason,
                onClick = { onSelect(reason) },
                label = { Text(reason.label) }
            )
        }
    }
}

@Composable
internal fun AddOptionButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(stringResource(R.string.edit_add_option))
    }
}
