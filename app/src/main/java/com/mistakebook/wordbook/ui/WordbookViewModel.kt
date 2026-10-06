package com.mistakebook.wordbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakebook.di.AppContainer
import com.mistakebook.wordbook.data.Word
import com.mistakebook.wordbook.data.WordProgress
import com.mistakebook.wordbook.data.WordStudyLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

class WordbookViewModel(private val container: AppContainer) : ViewModel() {

    private val vocabRepo = container.vocabRepository
    private val dao = container.wordbookDao

    private val _uiState = MutableStateFlow(WordbookUiState(isLoading = true))
    val uiState: StateFlow<WordbookUiState> = _uiState.asStateFlow()

    // 内存中的短期错题重现队列（答错后延迟若干题再次重现强化）
    private data class DeferItem(val word: Word, val reappearAtAnswerCount: Int)
    private val deferQueue = mutableListOf<DeferItem>()
    private var sessionAnswerCount = 0

    // 最近刷过的单词滑动窗口（冷却窗口，严格防止单词连着连续出现）
    private val recentWords = ArrayDeque<String>()
    private val COOL_DOWN_WINDOW = 8

    private fun recordRecentWord(wordStr: String) {
        recentWords.addLast(wordStr)
        while (recentWords.size > COOL_DOWN_WINDOW) {
            recentWords.removeFirst()
        }
    }

    init {
        viewModelScope.launch {
            vocabRepo.ensureLoaded()
            observeDatabaseStats()
            prepareNextQuestion()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun observeDatabaseStats() {
        val startOfDayMs = getStartOfDayMillis()
        viewModelScope.launch {
            combine(
                dao.observeStudiedTodayCount(startOfDayMs),
                dao.observeMasteredCount(),
                dao.observeWrongBookCount()
            ) { today, mastered, wrong ->
                Triple(today, mastered, wrong)
            }.collect { (today, mastered, wrong) ->
                _uiState.update {
                    it.copy(
                        studiedTodayCount = today,
                        masteredCount = mastered,
                        wrongBookCount = wrong
                    )
                }
            }
        }

        // 监听错词本数据变动
        viewModelScope.launch {
            dao.observeWrongBook().collect { wrongProgressList ->
                if (_uiState.value.wrongSubTab == WrongSubTab.WRONG_BOOK) {
                    refreshWrongSubTabList(WrongSubTab.WRONG_BOOK)
                }
            }
        }
    }

    fun setTab(tab: WordbookTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == WordbookTab.WRONG_BOOK) {
            refreshWrongSubTabList(_uiState.value.wrongSubTab)
        } else if (tab == WordbookTab.LIBRARY) {
            refreshLibraryList()
        }
    }

    fun setStudyMode(mode: StudyMode) {
        if (_uiState.value.studyMode != mode) {
            _uiState.update { it.copy(studyMode = mode) }
            prepareNextQuestion()
        }
    }

    fun setWrongSubTab(subTab: WrongSubTab) {
        _uiState.update { it.copy(wrongSubTab = subTab) }
        refreshWrongSubTabList(subTab)
    }

    // =========================================================================
    // 刷题流程调度
    // =========================================================================

    fun prepareNextQuestion() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val nextWord = pickNextWord()
            if (nextWord == null) {
                _uiState.update {
                    it.copy(
                        currentWord = null,
                        currentProgress = null,
                        currentSense = null,
                        options = emptyList(),
                        isAnswered = false,
                        selectedOptionIndex = null,
                        isCardRevealed = false,
                        isLoading = false
                    )
                }
                return@launch
            }

            if (_uiState.value.studyMode == StudyMode.SENSE) {
                val senseQ = vocabRepo.buildSenseQuestion(nextWord)
                if (senseQ != null) {
                    val progress = dao.getProgress(senseQ.word.word) ?: WordProgress(word = senseQ.word.word)
                    val labels = listOf("A", "B", "C", "D")
                    val options = senseQ.options.mapIndexed { index, candidate ->
                        QuizOption(
                            label = labels.getOrElse(index) { "?" },
                            word = candidate,
                            isCorrect = candidate.word == senseQ.word.word,
                            isSelected = false
                        )
                    }
                    _uiState.update {
                        it.copy(
                            currentWord = senseQ.word,
                            currentProgress = progress,
                            currentSense = senseQ.sense,
                            options = options,
                            selectedOptionIndex = null,
                            isAnswered = false,
                            isCardRevealed = false,
                            isLoading = false
                        )
                    }
                    return@launch
                }
            }

            val progress = dao.getProgress(nextWord.word) ?: WordProgress(word = nextWord.word)
            val distractors = vocabRepo.pickDistractors(nextWord, 3)

            // 构造四选一并打乱
            val labels = listOf("A", "B", "C", "D")
            val allCandidates = (listOf(nextWord) + distractors).shuffled(Random)
            val options = allCandidates.mapIndexed { index, candidate ->
                QuizOption(
                    label = labels.getOrElse(index) { "?" },
                    word = candidate,
                    isCorrect = candidate.word == nextWord.word,
                    isSelected = false
                )
            }

            _uiState.update {
                it.copy(
                    currentWord = nextWord,
                    currentProgress = progress,
                    currentSense = null,
                    options = options,
                    selectedOptionIndex = null,
                    isAnswered = false,
                    isCardRevealed = false,
                    isLoading = false
                )
            }
        }
    }

    private suspend fun pickNextWord(): Word? = withContext(Dispatchers.IO) {
        val isSenseMode = _uiState.value.studyMode == StudyMode.SENSE
        fun isEligibleSenseWord(w: Word): Boolean = !isSenseMode || vocabRepo.splitSenses(w.meaning).size >= 2

        // 1. 优先查看短期重现队列（已达到重现题数且不在冷却窗口内的词）
        val deferIndex = deferQueue.indexOfFirst {
            sessionAnswerCount >= it.reappearAtAnswerCount && it.word.word !in recentWords && isEligibleSenseWord(it.word)
        }
        if (deferIndex >= 0) {
            val item = deferQueue.removeAt(deferIndex)
            return@withContext item.word
        }

        // 2. 错词本温和复习机制：
        //    - 权重由原先过重的 70% 降低至 20%（平滑融入日常刷题节奏，不反客为主）；
        //    - 严格排除当前冷却窗口（recentWords）以及等待短期重现（deferQueue）中的词。
        val wrongList = dao.observeWrongBook().first()
        val allWords = vocabRepo.getAllWords()
        if (allWords.isEmpty()) return@withContext null

        val allProgress = dao.observeAllProgress().first().associateBy { it.word }
        val pendingDeferWords = deferQueue.map { it.word.word }.toSet()

        val eligibleWrong = wrongList.filter {
            it.word !in recentWords && it.word !in pendingDeferWords && run {
                if (!isSenseMode) true else {
                    val w = vocabRepo.getWord(it.word)
                    w != null && isEligibleSenseWord(w)
                }
            }
        }

        if (eligibleWrong.isNotEmpty() && Random.nextFloat() < 0.20f) {
            val candidate = eligibleWrong.random(Random)
            val word = vocabRepo.getWord(candidate.word)
            if (word != null) return@withContext word
        }

        // 3. 抽取未掌握的新词/生词（排除熟词、冷却窗口以及排队中的重现词）
        val unmastered = allWords.filter { w ->
            w.word !in recentWords && w.word !in pendingDeferWords && isEligibleSenseWord(w) && run {
                val p = allProgress[w.word]
                p == null || (!p.isMastered && p.level < 3)
            }
        }

        if (unmastered.isNotEmpty()) {
            return@withContext unmastered.random(Random)
        }

        // 4. 兜底回退：若未掌握词已抽完，放宽限制，但依然绝对排除上一道刚答过的词
        val lastWord = recentWords.lastOrNull()
        val fallbackWords = allWords.filter { it.word != lastWord && isEligibleSenseWord(it) }
        if (fallbackWords.isNotEmpty()) {
            return@withContext fallbackWords.random(Random)
        }

        if (isSenseMode) {
            vocabRepo.getMultiSenseWords().randomOrNull(Random)
        } else {
            allWords.randomOrNull(Random)
        }
    }

    /**
     * 四选一模式选择选项。
     */
    fun selectOption(index: Int) {
        val state = _uiState.value
        if (state.isAnswered || index !in state.options.indices) return
        val current = state.currentWord ?: return

        sessionAnswerCount++
        recordRecentWord(current.word)
        val selectedOption = state.options[index]
        val isCorrect = selectedOption.isCorrect

        val updatedOptions = state.options.mapIndexed { idx, opt ->
            opt.copy(isSelected = idx == index)
        }

        _uiState.update {
            it.copy(
                selectedOptionIndex = index,
                isAnswered = true,
                options = updatedOptions
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val oldProgress = dao.getProgress(current.word) ?: WordProgress(word = current.word)

            val newProgress = if (isCorrect) {
                val newReps = oldProgress.reps + 1
                // 错题在间隔复习中答对 1 次即可毕业移出错词本（保留曾错标记），避免反复惩罚
                val outOfWrongBook = oldProgress.isWrongBook && newReps >= 1
                oldProgress.copy(
                    level = maxOf(oldProgress.level, 2),
                    reps = newReps,
                    correctCount = oldProgress.correctCount + 1,
                    isWrongBook = if (outOfWrongBook) false else oldProgress.isWrongBook,
                    lastAnsweredAt = now,
                    updatedAt = now
                )
            } else {
                // 答错惩罚：进入错题本，归零连对，并安排 8 题后重现强化
                deferQueue.add(DeferItem(current, sessionAnswerCount + 8))
                oldProgress.copy(
                    level = 0,
                    reps = 0,
                    lapses = oldProgress.lapses + 1,
                    wrongCount = oldProgress.wrongCount + 1,
                    isWrongBook = true,
                    everWrong = true,
                    lastAnsweredAt = now,
                    updatedAt = now
                )
            }

            dao.upsertProgress(newProgress)
            dao.insertStudyLog(
                WordStudyLog(
                    word = current.word,
                    isCorrect = isCorrect,
                    studyMode = if (state.studyMode == StudyMode.SENSE) "sense" else "quiz",
                    answeredAt = now
                )
            )

            _uiState.update { it.copy(currentProgress = newProgress) }
        }

        // 选对自动进入下一题：380ms 极短延迟（视觉保留绿标确认感，节奏紧凑跟手）
        if (isCorrect) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(380L)
                val s = _uiState.value
                if (s.isAnswered && s.currentWord?.word == current.word && (s.studyMode == StudyMode.QUIZ || s.studyMode == StudyMode.SENSE)) {
                    prepareNextQuestion()
                }
            }
        }
    }

    /**
     * 卡片模式翻面。
     */
    fun revealCard() {
        _uiState.update { it.copy(isCardRevealed = true) }
    }

    /**
     * 卡片模式评分打分：
     * 0: 遗忘 (Bad)
     * 1: 模糊 (Fuzzy)
     * 2: 认识 (Good)
     * 3: 熟练 (Master)
     */
    fun rateCard(rating: Int) {
        val current = _uiState.value.currentWord ?: return
        sessionAnswerCount++
        recordRecentWord(current.word)

        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val old = dao.getProgress(current.word) ?: WordProgress(word = current.word)

            val updated = when (rating) {
                0 -> {
                    deferQueue.add(DeferItem(current, sessionAnswerCount + 8))
                    old.copy(
                        level = 0,
                        reps = 0,
                        lapses = old.lapses + 1,
                        wrongCount = old.wrongCount + 1,
                        isWrongBook = true,
                        everWrong = true,
                        lastAnsweredAt = now,
                        updatedAt = now
                    )
                }
                1 -> {
                    old.copy(
                        level = 1,
                        reps = 0,
                        correctCount = old.correctCount + 1,
                        lastAnsweredAt = now,
                        updatedAt = now
                    )
                }
                2 -> {
                    val newReps = old.reps + 1
                    val outOfWrongBook = old.isWrongBook && newReps >= 1
                    old.copy(
                        level = 2,
                        reps = newReps,
                        correctCount = old.correctCount + 1,
                        isWrongBook = if (outOfWrongBook) false else old.isWrongBook,
                        lastAnsweredAt = now,
                        updatedAt = now
                    )
                }
                else -> {
                    old.copy(
                        level = 3,
                        reps = old.reps + 1,
                        correctCount = old.correctCount + 1,
                        isMastered = true,
                        isWrongBook = false,
                        lastAnsweredAt = now,
                        updatedAt = now
                    )
                }
            }

            dao.upsertProgress(updated)
            dao.insertStudyLog(
                WordStudyLog(
                    word = current.word,
                    isCorrect = rating >= 2,
                    studyMode = "card",
                    answeredAt = now
                )
            )

            prepareNextQuestion()
        }
    }

    /**
     * 一键标记/取消熟词（☆）。
     */
    fun toggleMasterCurrentWord() {
        val current = _uiState.value.currentWord ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val old = dao.getProgress(current.word) ?: WordProgress(word = current.word)
            val nextMastered = !old.isMastered
            val updated = old.copy(
                isMastered = nextMastered,
                level = if (nextMastered) 3 else old.level,
                isWrongBook = if (nextMastered) false else old.isWrongBook,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertProgress(updated)
            _uiState.update {
                it.copy(
                    currentProgress = updated,
                    snackbarMessage = if (nextMastered) "已标记为熟词，后续不再安排练习" else "已取消熟词"
                )
            }
        }
    }

    // =========================================================================
    // 错词本子列表加载
    // =========================================================================

    private fun refreshWrongSubTabList(subTab: WrongSubTab) {
        viewModelScope.launch(Dispatchers.IO) {
            val progressList = when (subTab) {
                WrongSubTab.WRONG_BOOK -> dao.observeWrongBook().first()
                WrongSubTab.EVER_WRONG -> dao.observeEverWrong().first()
                WrongSubTab.MASTERED -> dao.observeMastered().first()
            }

            val pairedList = progressList.mapNotNull { p ->
                vocabRepo.getWord(p.word)?.let { w -> w to p }
            }

            _uiState.update { it.copy(wrongBookList = pairedList) }
        }
    }

    fun removeFromWrongBook(wordStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = dao.getProgress(wordStr) ?: return@launch
            val updated = p.copy(isWrongBook = false, updatedAt = System.currentTimeMillis())
            dao.upsertProgress(updated)
            refreshWrongSubTabList(_uiState.value.wrongSubTab)
            _uiState.update { it.copy(snackbarMessage = "已移出错题本") }
        }
    }

    // =========================================================================
    // 词库检索与筛选
    // =========================================================================

    fun setLibraryKeyword(keyword: String) {
        _uiState.update { it.copy(libraryKeyword = keyword) }
        refreshLibraryList()
    }

    fun setLibraryFilterLevel(level: Int?) {
        _uiState.update { it.copy(libraryFilterLevel = level) }
        refreshLibraryList()
    }

    private fun refreshLibraryList() {
        viewModelScope.launch(Dispatchers.IO) {
            val kw = _uiState.value.libraryKeyword
            val filterLv = _uiState.value.libraryFilterLevel
            val matchedWords = vocabRepo.search(kw)
            val allProgress = dao.observeAllProgress().first().associateBy { it.word }

            val filtered = matchedWords.mapNotNull { w ->
                val p = allProgress[w.word]
                val currentLevel = p?.level ?: 0
                if (filterLv == null || currentLevel == filterLv) {
                    w to p
                } else null
            }

            _uiState.update { it.copy(libraryWords = filtered.take(150)) }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun getStartOfDayMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
