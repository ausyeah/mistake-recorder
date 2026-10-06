package com.mistakebook.wordbook.audio

import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class WordAudioPlayerTest {

    @Test
    fun testMethodSignatures() {
        // We use reflection to verify the public API signature 
        // to avoid instantiating Android Context without Robolectric.
        val methods = WordAudioPlayer::class.java.methods
        
        val speakMethod = methods.find { it.name == "speak" && it.parameterTypes.size == 3 }
        assert(speakMethod != null) { "speak(String, Float, Float) not found" }
        
        val stopMethod = methods.find { it.name == "stop" }
        assert(stopMethod != null) { "stop() not found" }
        
        val shutdownMethod = methods.find { it.name == "shutdown" }
        assert(shutdownMethod != null) { "shutdown() not found" }
        
        val isReadyMethod = methods.find { it.name == "isReady" || it.name == "getIsReady" }
        assert(isReadyMethod != null) { "isReady() or getIsReady() not found" }
        assert(isReadyMethod?.returnType?.name?.contains("StateFlow") == true) { "Return type should be StateFlow" }
    }
}
