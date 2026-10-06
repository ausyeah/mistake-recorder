package com.mistakebook.wordbook.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class WordAudioPlayer(context: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        try {
            // Use applicationContext to avoid Activity memory leaks
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("WordAudioPlayer", "Failed to initialize TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsInstance = tts ?: return
            
            // Try US English first, fallback to UK, then default
            val resultUS = ttsInstance.setLanguage(Locale.US)
            if (resultUS == TextToSpeech.LANG_MISSING_DATA || resultUS == TextToSpeech.LANG_NOT_SUPPORTED) {
                val resultUK = ttsInstance.setLanguage(Locale.UK)
                if (resultUK == TextToSpeech.LANG_MISSING_DATA || resultUK == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val resultDefault = ttsInstance.setLanguage(Locale.ENGLISH)
                    if (resultDefault != TextToSpeech.LANG_MISSING_DATA && resultDefault != TextToSpeech.LANG_NOT_SUPPORTED) {
                        _isReady.value = true
                    } else {
                        Log.w("WordAudioPlayer", "English language is not supported")
                    }
                } else {
                    _isReady.value = true
                }
            } else {
                _isReady.value = true
            }
        } else {
            Log.e("WordAudioPlayer", "TTS Initialization failed with status: $status")
        }
    }

    fun speak(text: String, pitch: Float = 1.0f, rate: Float = 0.95f) {
        if (!_isReady.value || text.isBlank()) return
        
        try {
            tts?.apply {
                setPitch(pitch)
                setSpeechRate(rate)
                // Use QUEUE_FLUSH to interrupt current speech and speak immediately
                val utteranceId = "WordAudio_${System.currentTimeMillis()}"
                speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
        } catch (e: Exception) {
            Log.e("WordAudioPlayer", "Error during TTS speak", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("WordAudioPlayer", "Error during TTS stop", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            _isReady.value = false
        } catch (e: Exception) {
            Log.e("WordAudioPlayer", "Error during TTS shutdown", e)
        }
    }
}
