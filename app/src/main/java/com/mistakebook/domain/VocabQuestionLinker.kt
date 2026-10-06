package com.mistakebook.domain

import com.mistakebook.data.local.entities.Question
import com.mistakebook.wordbook.data.VocabRepository
import com.mistakebook.wordbook.data.Word
import com.mistakebook.wordbook.data.WordProgress
import com.mistakebook.wordbook.data.WordbookDao

data class QuestionHighlight(
    val title: String,
    val snippet: String,
    val question: Question
)

data class ExtractedWord(
    val word: Word,
    val progress: WordProgress?
)

class VocabQuestionLinker(
    private val vocabRepository: VocabRepository,
    private val wordbookDao: WordbookDao
) {

    /**
     * 功能 1：给定一个英文单词，在所有错题中检索，并返回带上下文摘录的结果。
     */
    fun findQuestionsByWord(word: String, questions: List<Question>): List<QuestionHighlight> {
        if (word.isBlank()) return emptyList()
        val regex = "\\b(?i)${Regex.escape(word)}\\b".toRegex()
        val results = mutableListOf<QuestionHighlight>()
        
        for (q in questions) {
            var targetText = ""
            var match = regex.find(q.stem)
            if (match != null) {
                targetText = q.stem
            } else {
                match = regex.find(q.analysis)
                if (match != null) {
                    targetText = q.analysis
                }
            }
            
            if (match != null) {
                val start = maxOf(0, match.range.first - 20)
                val end = minOf(targetText.length, match.range.last + 1 + 20)
                var snippet = targetText.substring(start, end).replace('\n', ' ')
                if (start > 0) snippet = "...$snippet"
                if (end < targetText.length) snippet = "$snippet..."
                
                val title = q.title.ifEmpty { 
                    val previewLength = minOf(q.stem.length, 10)
                    q.stem.substring(0, previewLength) + if (q.stem.length > 10) "..." else ""
                }.ifEmpty { "未命名错题" }
                
                results.add(QuestionHighlight(title, snippet, q))
            }
        }
        return results
    }

    /**
     * 功能 2：给定一段文本，提取所有英文生词，并返回考研核心词汇及掌握度。
     */
    suspend fun extractVocabFromText(text: String): List<ExtractedWord> {
        if (text.isBlank()) return emptyList()
        val regex = "[a-zA-Z]+".toRegex()
        val wordsInText = regex.findAll(text).map { it.value.lowercase() }.toSet()
        
        val allVocab = vocabRepository.getAllWords()
        
        val matchedWords = allVocab.filter { wordsInText.contains(it.word.lowercase()) }
        
        return matchedWords.map { word ->
            val progress = wordbookDao.getProgress(word.word)
            ExtractedWord(word, progress)
        }
    }
}
