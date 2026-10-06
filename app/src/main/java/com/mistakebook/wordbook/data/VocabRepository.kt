package com.mistakebook.wordbook.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import kotlin.random.Random

/**
 * 单词词库仓储（管理内置 4356 词及词根干扰项索引）。
 */
class VocabRepository(private val context: Context? = null) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val mutex = Mutex()
    private var wordsList: List<Word> = emptyList()
    private var wordsMap: Map<String, Word> = emptyMap()

    // 索引缓存
    private var clusterOf: Map<String, List<String>> = emptyMap()
    private var synIdOf: Map<String, Int> = emptyMap()
    private var byPos: Map<String, List<String>> = emptyMap()

    private var isLoaded = false

    /**
     * 单元测试专用构造器：支持脱离 Android Context 独立验证同根干扰项与同义词过滤算法。
     */
    constructor(
        words: List<Word>,
        clusters: Map<String, List<String>> = emptyMap(),
        synGroups: List<List<String>> = emptyList(),
        byPos: Map<String, List<String>> = emptyMap()
    ) : this(null) {
        this.wordsList = words
        this.wordsMap = words.associateBy { it.word }
        val clusterMap = mutableMapOf<String, MutableList<String>>()
        clusters.values.forEach { group ->
            group.forEach { w ->
                clusterMap.getOrPut(w) { mutableListOf() }.addAll(group)
            }
        }
        this.clusterOf = clusterMap
        val synMap = mutableMapOf<String, Int>()
        synGroups.forEachIndexed { groupIndex, group ->
            group.forEach { w -> synMap[w] = groupIndex }
        }
        this.synIdOf = synMap
        this.byPos = byPos
        this.isLoaded = true
    }

    /**
     * 确保词库与索引已载入内存。
     */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext
        mutex.withLock {
            if (isLoaded) return@withLock
            loadVocabInternal()
            loadIndexInternal()
            isLoaded = true
        }
    }

    private fun loadVocabInternal() {
        val ctx = context ?: return
        runCatching {
            ctx.assets.open("vocab.json").use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    val content = reader.readText()
                    val parsed = json.decodeFromString<VocabFile>(content)
                    wordsList = parsed.words
                    wordsMap = wordsList.associateBy { it.word }
                }
            }
        }.onFailure {
            android.util.Log.e("VocabRepository", "加载 vocab.json 失败", it)
        }
    }

    private fun loadIndexInternal() {
        val ctx = context ?: return
        runCatching {
            ctx.assets.open("vocab-index.json").use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    val content = reader.readText()
                    val idx = json.decodeFromString<VocabIndexFile>(content)

                    val clusterMap = mutableMapOf<String, MutableList<String>>()
                    idx.clusters.values.forEach { group ->
                        group.forEach { w ->
                            clusterMap.getOrPut(w) { mutableListOf() }.addAll(group)
                        }
                    }
                    clusterOf = clusterMap

                    val synMap = mutableMapOf<String, Int>()
                    idx.synGroups.forEachIndexed { groupIndex, group ->
                        group.forEach { w -> synMap[w] = groupIndex }
                    }
                    synIdOf = synMap
                    byPos = idx.byPos
                }
            }
        }.onFailure {
            android.util.Log.w("VocabRepository", "加载 vocab-index.json 索引失败，降级为全库随机", it)
        }
    }

    suspend fun getAllWords(): List<Word> {
        ensureLoaded()
        return wordsList
    }

    suspend fun getWord(word: String): Word? {
        ensureLoaded()
        return wordsMap[word]
    }

    suspend fun search(query: String): List<Word> {
        ensureLoaded()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return wordsList
        return wordsList.filter { w ->
            w.word.lowercase().contains(q) || w.meaning.lowercase().contains(q)
        }
    }

    /**
     * 智能四选一干扰项抽取算法（继承原单词书四层分簇逻辑）：
     *
     * L1: 同词根簇 + 同词性（形近辨析价值最高）
     * L2: 同词根簇（跨词性）
     * L3: 同词性全库
     * L4: 全库随机兜底
     *
     * 严格排除同义组词和相同释义文本，避免出现双正确答案。
     */
    suspend fun pickDistractors(target: Word, count: Int = 3): List<Word> {
        ensureLoaded()
        if (wordsList.size <= 1) return emptyList()

        val picked = mutableListOf<Word>()
        val usedWords = mutableSetOf(target.word)
        val usedMeanings = mutableSetOf(target.meaning)
        val targetSynId = synIdOf[target.word]

        val targetPosSet = splitPos(target.pos)

        fun tryAdd(candidates: List<String>) {
            if (picked.size >= count) return
            val shuffled = candidates.shuffled(Random)
            for (wName in shuffled) {
                if (picked.size >= count) break
                val w = wordsMap[wName] ?: continue
                if (w.word in usedWords || w.meaning in usedMeanings) continue
                // 同义组排除
                val sId = synIdOf[w.word]
                if (targetSynId != null && sId != null && targetSynId == sId) continue

                picked.add(w)
                usedWords.add(w.word)
                usedMeanings.add(w.meaning)
            }
        }

        // L1 & L2: 同簇
        val clusterWords = clusterOf[target.word] ?: emptyList()
        if (clusterWords.isNotEmpty()) {
            val samePosSiblings = clusterWords.filter { sib ->
                val sibWord = wordsMap[sib]
                sibWord != null && overlaps(splitPos(sibWord.pos), targetPosSet)
            }
            tryAdd(samePosSiblings)
            tryAdd(clusterWords)
        }

        // L3: 同词性桶
        for (pos in targetPosSet) {
            val posWords = byPos[pos] ?: emptyList()
            tryAdd(posWords)
            if (picked.size >= count) break
        }

        // L4: 全库兜底
        if (picked.size < count) {
            val allWordNames = wordsList.map { it.word }
            tryAdd(allWordNames)
        }

        return picked.take(count)
    }

    private fun splitPos(pos: String): List<String> {
        return pos.split("/", "／", " ")
            .map { it.trim().removeSuffix(".") }
            .filter { it.isNotBlank() && it.all { ch -> ch.isLetter() } }
            .ifEmpty { listOf("x") }
    }

    private fun overlaps(a: List<String>, b: List<String>): Boolean {
        return a.any { it in b }
    }
}
