package com.mistakebook.net

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpFactoryMessageTest {

    @Test
    fun `non primitive top level message does not abort extraction`() {
        val body = """{"msg":{"detail":"nested"},"message":"usable message"}"""
        assertEquals("usable message", HttpFactory.extractServerMessage(body))
    }

    @Test
    fun `nested error message is extracted`() {
        val body = """{"error":{"message":"request rejected"}}"""
        assertEquals("request rejected", HttpFactory.extractServerMessage(body))
    }

    @Test
    fun `non primitive nested error message returns empty`() {
        val body = """{"error":{"message":["unexpected"]}}"""
        assertEquals("", HttpFactory.extractServerMessage(body))
    }
}
