package com.mistakebook.wordbook.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakebook.di.AppContainer
import com.mistakebook.ui.common.containerViewModel
import com.mistakebook.ui.theme.PaperBad
import com.mistakebook.ui.theme.PaperBadBg
import com.mistakebook.ui.theme.PaperBorderLight
import com.mistakebook.ui.theme.PaperLv0
import com.mistakebook.ui.theme.PaperLv1
import com.mistakebook.ui.theme.PaperLv2
import com.mistakebook.ui.theme.PaperLv3
import com.mistakebook.ui.theme.PaperOk
import com.mistakebook.ui.theme.PaperOkBg
import com.mistakebook.ui.theme.PaperPrimary
import com.mistakebook.ui.theme.PaperPrimaryTint
import com.mistakebook.wordbook.data.Word
import com.mistakebook.wordbook.data.WordProgress

/**
 * 单词书主界面（纯本地化纸质书卷质感设计）。
 */
@Composable
fun WordbookScreen(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val viewModel: WordbookViewModel = containerViewModel(container) { WordbookViewModel(it) }
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部状态与打卡摘要
                WordbookHeaderSummary(
                    studiedToday = state.studiedTodayCount,
                    wrongCount = state.wrongBookCount,
                    masteredCount = state.masteredCount
                )

                // 核心功能标签栏
                WordbookTabBar(
                    selectedTab = state.currentTab,
                    onSelectTab = viewModel::setTab
                )

                // 各子视图切换
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (state.currentTab) {
                        WordbookTab.STUDY -> StudyView(
                            state = state,
                            viewModel = viewModel
                        )
                        WordbookTab.WRONG_BOOK -> WrongBookView(
                            state = state,
                            viewModel = viewModel
                        )
                        WordbookTab.LIBRARY -> LibraryView(
                            state = state,
                            viewModel = viewModel
                        )
                        WordbookTab.STATS -> StatsView(
                            state = state
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

// =========================================================================
// 顶部状态栏与 Tab 切换
// =========================================================================

@Composable
private fun WordbookHeaderSummary(
    studiedToday: Int,
    wrongCount: Int,
    masteredCount: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PaperOk)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "今日已学 $studiedToday 词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "待复习错词: $wrongCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (wrongCount > 0) PaperBad else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "已掌握: $masteredCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = PaperPrimary
                )
            }
        }
    }
}

@Composable
private fun WordbookTabBar(
    selectedTab: WordbookTab,
    onSelectTab: (WordbookTab) -> Unit
) {
    val tabs = listOf(
        WordbookTab.STUDY to "学习刷题",
        WordbookTab.WRONG_BOOK to "错题本",
        WordbookTab.LIBRARY to "词库检索",
        WordbookTab.STATS to "学习统计"
    )

    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = PaperPrimary,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                color = PaperPrimary,
                height = 3.dp
            )
        }
    ) {
        tabs.forEach { (tab, title) ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

// =========================================================================
// 1. 刷题页面 (Study View)
// =========================================================================

@Composable
private fun StudyView(
    state: WordbookUiState,
    viewModel: WordbookViewModel
) {
    val currentWord = state.currentWord

    if (currentWord == null && !state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🎉 太棒了，当前词库学习已告一段落！",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "错词本与待复习列表已清空，可去词库检索更多生词",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::prepareNextQuestion) {
                    Text("抽取练习题")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 模式切换与熟词标记操作栏
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.studyMode == StudyMode.QUIZ,
                        onClick = { viewModel.setStudyMode(StudyMode.QUIZ) },
                        label = { Text("四选一模式") }
                    )
                    FilterChip(
                        selected = state.studyMode == StudyMode.CARD,
                        onClick = { viewModel.setStudyMode(StudyMode.CARD) },
                        label = { Text("卡片自测") }
                    )
                }

                // 熟词标记按钮
                val isMastered = state.currentProgress?.isMastered == true
                IconButton(onClick = viewModel::toggleMasterCurrentWord) {
                    Icon(
                        imageVector = if (isMastered) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "熟词标记",
                        tint = if (isMastered) PaperLv1 else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 单词主纸质卡片
        if (currentWord != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentWord.word,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        if (currentWord.pos.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = currentWord.pos,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 作答后或卡片揭晓后展示完整中文释义
                        AnimatedVisibility(
                            visible = state.isAnswered || state.isCardRevealed,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(Modifier.height(18.dp))
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth(),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = currentWord.full.ifBlank { currentWord.meaning },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }
                }
            }

            // 模式分支交互
            if (state.studyMode == StudyMode.QUIZ) {
                // 四选一单选列表
                items(state.options) { option ->
                    QuizOptionRow(
                        option = option,
                        isAnswered = state.isAnswered,
                        onSelect = {
                            val idx = state.options.indexOf(option)
                            if (idx >= 0) viewModel.selectOption(idx)
                        }
                    )
                }

                if (state.isAnswered) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = viewModel::prepareNextQuestion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PaperPrimary)
                        ) {
                            Text("下一题", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            } else {
                // 卡片模式：翻面揭晓与四档打分
                item {
                    if (!state.isCardRevealed) {
                        Button(
                            onClick = viewModel::revealCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PaperPrimary)
                        ) {
                            Text("点击揭晓完整释义", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // 四档掌握度打分按钮
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RateButton("忘记了", PaperBad, Modifier.weight(1f)) { viewModel.rateCard(0) }
                                RateButton("模糊", PaperLv1, Modifier.weight(1f)) { viewModel.rateCard(1) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RateButton("认识", PaperLv2, Modifier.weight(1f)) { viewModel.rateCard(2) }
                                RateButton("熟练", PaperLv3, Modifier.weight(1f)) { viewModel.rateCard(3) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RateButton(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuizOptionRow(
    option: QuizOption,
    isAnswered: Boolean,
    onSelect: () -> Unit
) {
    val bgColor = when {
        !isAnswered -> MaterialTheme.colorScheme.surface
        option.isCorrect -> PaperOkBg
        option.isSelected && !option.isCorrect -> PaperBadBg
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    }

    val borderColor = when {
        !isAnswered -> MaterialTheme.colorScheme.outline
        option.isCorrect -> PaperOk
        option.isSelected && !option.isCorrect -> PaperBad
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(enabled = !isAnswered, onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isAnswered && option.isCorrect -> PaperOk
                            isAnswered && option.isSelected -> PaperBad
                            else -> PaperPrimaryTint
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isAnswered && (option.isCorrect || option.isSelected)) Color.White else PaperPrimary
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (option.word.pos.isNotBlank()) {
                    Text(
                        text = option.word.pos,
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = option.word.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isAnswered) {
                if (option.isCorrect) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = PaperOk)
                } else if (option.isSelected) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = PaperBad)
                }
            }
        }
    }
}

// =========================================================================
// 2. 错题本体系 (WrongBook View)
// =========================================================================

@Composable
private fun WrongBookView(
    state: WordbookUiState,
    viewModel: WordbookViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 三级子标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.wrongSubTab == WrongSubTab.WRONG_BOOK,
                onClick = { viewModel.setWrongSubTab(WrongSubTab.WRONG_BOOK) },
                label = { Text("错词本 (${state.wrongBookCount})") }
            )
            FilterChip(
                selected = state.wrongSubTab == WrongSubTab.EVER_WRONG,
                onClick = { viewModel.setWrongSubTab(WrongSubTab.EVER_WRONG) },
                label = { Text("曾错毕业本") }
            )
            FilterChip(
                selected = state.wrongSubTab == WrongSubTab.MASTERED,
                onClick = { viewModel.setWrongSubTab(WrongSubTab.MASTERED) },
                label = { Text("熟词本 (${state.masteredCount})") }
            )
        }

        if (state.wrongBookList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (state.wrongSubTab) {
                        WrongSubTab.WRONG_BOOK -> "错词本干干净净，暂无待复习错词 🎉"
                        WrongSubTab.EVER_WRONG -> "暂无曾错毕业词汇"
                        WrongSubTab.MASTERED -> "暂无手动标记的熟词"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.wrongBookList, key = { it.first.word }) { (word, progress) ->
                    WordItemCard(
                        word = word,
                        progress = progress,
                        showRemove = state.wrongSubTab == WrongSubTab.WRONG_BOOK,
                        onRemove = { viewModel.removeFromWrongBook(word.word) }
                    )
                }
            }
        }
    }
}

// =========================================================================
// 3. 词库检索 (Library View)
// =========================================================================

@Composable
private fun LibraryView(
    state: WordbookUiState,
    viewModel: WordbookViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = state.libraryKeyword,
            onValueChange = viewModel::setLibraryKeyword,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索 4356 个考研单词或中文释义…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(Modifier.height(8.dp))

        // 等级筛选 Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                null to "全部",
                0 to "生词",
                1 to "模糊",
                2 to "认识",
                3 to "掌握"
            ).forEach { (lv, label) ->
                FilterChip(
                    selected = state.libraryFilterLevel == lv,
                    onClick = { viewModel.setLibraryFilterLevel(lv) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.libraryWords, key = { it.first.word }) { (word, progress) ->
                WordItemCard(
                    word = word,
                    progress = progress ?: WordProgress(word = word.word),
                    showRemove = false,
                    onRemove = {}
                )
            }
        }
    }
}

// =========================================================================
// 4. 统计看板 (Stats View)
// =========================================================================

@Composable
private fun StatsView(
    state: WordbookUiState
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "词库总览与进度",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    val progressRatio = (state.masteredCount.toFloat() / state.totalVocabCount.coerceAtLeast(1)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PaperPrimary,
                        trackColor = PaperPrimaryTint
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "已掌握 ${state.masteredCount} / ${state.totalVocabCount} 词 · ${(progressRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "今日已学",
                    value = "${state.studiedTodayCount}",
                    unit = "词",
                    color = PaperOk,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "待复习错词",
                    value = "${state.wrongBookCount}",
                    unit = "词",
                    color = if (state.wrongBookCount > 0) PaperBad else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "四级掌握度体系规则",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    RuleItem("Lv0 生词 / 未学", "尚未做过或刚刚答错的生词，优先安排练习", PaperLv0)
                    RuleItem("Lv1 模糊 / 重现", "曾答错已稍后重现作答，正在短期记忆阶段", PaperLv1)
                    RuleItem("Lv2 认识 / 熟练", "已连续答对，进入巩固队列", PaperLv2)
                    RuleItem("Lv3 掌握 / 毕业", "错题连对 3 次或手动标记熟词，达成完全掌握", PaperLv3)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RuleItem(title: String, desc: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WordItemCard(
    word: Word,
    progress: WordProgress,
    showRemove: Boolean,
    onRemove: () -> Unit
) {
    val levelColor = when (progress.level) {
        1 -> PaperLv1
        2 -> PaperLv2
        3 -> PaperLv3
        else -> PaperLv0
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 掌握度竖条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(levelColor)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                    if (progress.isMastered) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = PaperLv1, modifier = Modifier.size(14.dp))
                    }
                    if (progress.isWrongBook) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "连对 ${progress.reps}/3",
                            style = MaterialTheme.typography.labelSmall,
                            color = PaperBad
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = word.meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showRemove) {
                TextButton(onClick = onRemove) {
                    Text("移出", color = PaperBad, fontSize = 13.sp)
                }
            }
        }
    }
}
