package com.mistakebook.ui.settings

import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mistakebook.BuildConfig
import com.mistakebook.R
import com.mistakebook.data.prefs.LlmProfile
import com.mistakebook.di.AppContainer
import com.mistakebook.review.ReviewScheduler
import com.mistakebook.ui.common.containerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 设置页：Key 与大模型配置、识别参数、打印默认项、复习提醒、数据管理。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = containerViewModel(container) { SettingsViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAddProfile by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_restore_failed, "")
                )
                return@launch
            }
            val result = container.backupManager.restore(stream)
            result.onSuccess { count ->
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_restore_done, count)
                )
                delay(1_500)
                Process.killProcess(Process.myPid())
            }
            result.onFailure { error ->
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_restore_failed, error.message.orEmpty())
                )
            }
        }
    }

    // 测试结果**常驻显示**，不再用 snackbar。
    // snackbar 几秒就消失，用户往往还没看清「首字响应 xxx ms」就被划走了；
    // 而这类信息本来就需要对照着看（比如两次测试比速度）。
    // modelsError 仍走 snackbar：那类错误处理完就不用再看。
    LaunchedEffect(state.modelsError) {
        state.modelsError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeModels()
        }
    }
    LaunchedEffect(state.backupMessage) {
        state.backupMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeBackupMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsGroup(title = stringResource(R.string.settings_mineru_key)) {
                PasswordField(
                    label = stringResource(R.string.settings_mineru_key),
                    value = state.snapshot.mineruKey,
                    onValueChange = viewModel::setMineruKey
                )
                Spacer(Modifier.height(8.dp))
                TestButton(
                    text = stringResource(R.string.settings_test_mineru),
                    loading = state.testingMineru,
                    onClick = viewModel::testMineru,
                    // 之前漏了这两行：结果在 ViewModel 里算好了、也进了 state，
                    // 但没人渲染——点「测试 MinerU」按钮右边永远空空如也。
                    result = state.mineruTestResult,
                    resultOk = state.mineruTestOk
                )
            }

            // 公开分发的包不预填任何 Key（密钥在 DEX 里是明文，预填等于公开）。
            // 用户第一次进来看到两个空框会不知道要干嘛，这里明确说清楚。
            if (!state.snapshot.mineruConfigured) {
                FirstRunHint(R.string.settings_hint_mineru_key)
            }

            SettingsGroup(title = stringResource(R.string.settings_llm_profiles)) {
                state.snapshot.llmProfiles.forEach { profile ->
                    ProfileRow(
                        profile = profile,
                        selected = profile.id == state.snapshot.activeProfileId,
                        onSelect = { viewModel.selectProfile(profile.id) },
                        onDelete = { viewModel.deleteProfile(profile.id) },
                        onSave = viewModel::saveActiveProfile,
                        onDraftChanged = { viewModel.clearLlmTestResult(profile.id) },
                        onFetchModels = { draft ->
                            // 拉到列表后直接弹选择框，省掉一次多余点击
                            viewModel.fetchModels(draft) { showModels = true }
                        },
                        onTest = viewModel::testLlm,
                        // 按配置取：之前传的是全局字段，配了第二个模型之后
                        // 两边显示同一份结果、两边同时转圈
                        testing = state.isTestingLlm(profile.id),
                        result = state.llmOutcome(profile.id)?.message,
                        resultOk = state.llmOutcome(profile.id)?.ok ?: false,
                        fetchingModels = state.loadingModels,
                        pickedModel = state.pickedModel,
                        onPickedModelConsumed = viewModel::consumePickedModel
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { showAddProfile = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.settings_add_profile))
                }
                if (!state.snapshot.llmConfigured) {
                    Spacer(Modifier.height(8.dp))
                    FirstRunHint(R.string.settings_hint_llm_key)
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_mineru_params)) {
                // 模型版本、OCR 语言、强制 OCR 三项已固定为 vlm / ch / 开启，从设置里移除。
                // - vlm 对纯英文内容同样能自适应，切成 pipeline 只会让中文识别变差；
                // - language=ch 是「首选中文」而非「仅中文」，纯英文题面照样能识别；
                // - forceOcr 对拍照/作业照几乎总是对的，关掉反而会漏字。
                // 这三项暴露给用户只会让不确定的人随手改坏。
                SwitchRow(
                    label = stringResource(R.string.settings_attach_image),
                    checked = state.snapshot.attachOriginalImage,
                    onChange = viewModel::setAttachImage
                )
                SwitchRow(
                    label = stringResource(R.string.settings_enhance_photos),
                    subtitle = stringResource(R.string.settings_enhance_photos_desc),
                    checked = state.snapshot.enhancePhotos,
                    onChange = viewModel::setEnhancePhotos
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_print_defaults)) {
                SwitchRow(
                    label = stringResource(R.string.print_include_image),
                    checked = state.snapshot.printIncludeImage,
                    onChange = viewModel::setPrintIncludeImage
                )
                SwitchRow(
                    label = stringResource(R.string.print_show_answer),
                    checked = state.snapshot.printShowAnswer,
                    onChange = viewModel::setPrintShowAnswer
                )
                SwitchRow(
                    label = stringResource(R.string.print_blank_redo),
                    checked = state.snapshot.printBlankRedo,
                    onChange = viewModel::setPrintBlankRedo
                )
                if (state.snapshot.printBlankRedo) {
                    BlankHeightRow(
                        height = state.snapshot.printBlankHeightPt,
                        onChange = viewModel::setPrintBlankHeight
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_review_reminder)) {
                SwitchRow(
                    label = stringResource(R.string.settings_review_reminder),
                    checked = state.snapshot.reviewReminderEnabled,
                    onChange = viewModel::setReminderEnabled
                )
                if (state.snapshot.reviewReminderEnabled) {
                    val hour = state.snapshot.reminderHour
                    val minute = state.snapshot.reminderMinute
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%02d:%02d".format(hour, minute),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { showTimePicker = true }) {
                            Text(stringResource(R.string.settings_reminder_pick))
                        }
                    }
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_data_manage)) {
                // 占用空间：用户最想知道的其实是「我的错题占了多少、能不能导出」。
                // 原来只有三个竖排按钮，没有任何量化信息，出了问题也不知道该做什么。
                StorageInfoRow(container = container)
                Spacer(Modifier.height(10.dp))
                // 两个主操作并排，「清空回收站」单独一行且用错误色系——
                // 它是不可逆操作，不该和导出/恢复挤在一起
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val result = container.backupManager.export()
                                result.onSuccess { path ->
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.settings_backup_exported) + "：" + path
                                    )
                                }
                                result.onFailure { error ->
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.settings_backup_failed,
                                            error.message.orEmpty()
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_export_backup),
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = { showRestoreConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_restore_backup),
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::clearTrash,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_clear_trash))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        val hour = state.snapshot.reminderHour
        val minute = state.snapshot.reminderMinute
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.settings_reminder_pick)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setReminderTime(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                    scope.launch {
                        ReviewScheduler.schedule(
                            context,
                            pickerState.hour,
                            pickerState.minute
                        )
                        snackbarHostState.showSnackbar(
                            "已设为 %02d:%02d".format(pickerState.hour, pickerState.minute)
                        )
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showModels) {
                // 选完模型就关掉对话框，模型名由 ProfileRow 回填
                LaunchedEffect(state.pickedModel) {
                    if (state.pickedModel != null) showModels = false
                }
                val picked = state.pickedModel
                if (picked != null) {
                    showModels = false
                }
        AlertDialog(
            onDismissRequest = { showModels = false },
            title = { Text(stringResource(R.string.settings_models_title)) },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp)) {
                    if (state.models.isEmpty()) {
                        Text(
                                    text = stringResource(R.string.settings_models_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                                state.models.forEach { model ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = model,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.pickModel(model) }) {
                                            Text(stringResource(R.string.action_confirm))
                                        }
                                    }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 用当前选中的配置（而非已保存值）重新拉列表
                        val draft = state.snapshot.activeProfile
                        if (draft != null) {
                            viewModel.fetchModels(draft)
                        }
                        showModels = false
                    },
                    enabled = !state.loadingModels
                ) { Text(stringResource(R.string.settings_fetch_models)) }
            },
            dismissButton = {
                TextButton(onClick = { showModels = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showAddProfile) {
        AddProfileDialog(
            onDismiss = { showAddProfile = false },
            onConfirm = { profile ->
                viewModel.addProfile(profile)
                showAddProfile = false
            }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    restoreLauncher.launch(arrayOf("application/zip"))
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
