package com.example

import com.example.service.AccessibilityNodeOptimizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val optimizer = AccessibilityNodeOptimizer()

    @Test
    fun testNormalizeSpeedLabel() {
        assertEquals(1.0f, optimizer.normalizeSpeedLabel("1x"))
        assertEquals(1.0f, optimizer.normalizeSpeedLabel("1.0x"))
        assertEquals(1.0f, optimizer.normalizeSpeedLabel("1.00x"))
        assertEquals(1.0f, optimizer.normalizeSpeedLabel("Normal"))
        assertEquals(1.0f, optimizer.normalizeSpeedLabel("normal"))
        assertEquals(1.0f, optimizer.normalizeSpeedLabel("standard"))

        assertEquals(1.25f, optimizer.normalizeSpeedLabel("1.25x"))
        assertEquals(1.5f, optimizer.normalizeSpeedLabel("1.5x"))
        assertEquals(1.75f, optimizer.normalizeSpeedLabel("1.75x"))
        assertEquals(2.0f, optimizer.normalizeSpeedLabel("2x"))
        assertEquals(2.0f, optimizer.normalizeSpeedLabel("2.0x"))
        assertEquals(2.5f, optimizer.normalizeSpeedLabel("2.5x"))
        assertEquals(3.0f, optimizer.normalizeSpeedLabel("3x"))
        assertEquals(4.0f, optimizer.normalizeSpeedLabel("4x"))
        assertEquals(5.0f, optimizer.normalizeSpeedLabel("5x"))
        assertEquals(5.0f, optimizer.normalizeSpeedLabel("5.0x"))
        assertEquals(7.5f, optimizer.normalizeSpeedLabel("7.5x"))
        assertEquals(10.0f, optimizer.normalizeSpeedLabel("10x"))
        assertEquals(10.0f, optimizer.normalizeSpeedLabel("10.0x"))
        assertEquals(10.0f, optimizer.normalizeSpeedLabel("10.00x"))
    }

    @Test
    fun testExtractSpeedFromOptionText() {
        assertEquals(1.0f, optimizer.extractSpeedFromOptionText("Normal"))
        assertEquals(1.0f, optimizer.extractSpeedFromOptionText("1.0x"))
        assertEquals(1.0f, optimizer.extractSpeedFromOptionText("1x"))
        assertEquals(1.25f, optimizer.extractSpeedFromOptionText("1.25x"))
        assertEquals(1.5f, optimizer.extractSpeedFromOptionText("1.5x"))
        assertEquals(2.0f, optimizer.extractSpeedFromOptionText("2.0x"))
        assertEquals(5.0f, optimizer.extractSpeedFromOptionText("Playback speed: 5.0x"))
        assertEquals(5.0f, optimizer.extractSpeedFromOptionText("Speed 5x"))
        assertEquals(10.0f, optimizer.extractSpeedFromOptionText("10.0x"))
        assertEquals(10.0f, optimizer.extractSpeedFromOptionText("10x (Turbo)"))
    }

    @Test
    fun testMatchesRequestedSpeed_PositiveCases() {
        // Test all preset speed options
        assertTrue(optimizer.matchesRequestedSpeed("1.0x", null, 1.0f))
        assertTrue(optimizer.matchesRequestedSpeed("Normal", "Normal playback speed", 1.0f))
        assertTrue(optimizer.matchesRequestedSpeed("1.25x", null, 1.25f))
        assertTrue(optimizer.matchesRequestedSpeed("1.5x", null, 1.5f))
        assertTrue(optimizer.matchesRequestedSpeed("1.75x", null, 1.75f))
        assertTrue(optimizer.matchesRequestedSpeed("2.0x", null, 2.0f))
        assertTrue(optimizer.matchesRequestedSpeed("2x", null, 2.0f))
        assertTrue(optimizer.matchesRequestedSpeed("2.5x", null, 2.5f))
        assertTrue(optimizer.matchesRequestedSpeed("3.0x", null, 3.0f))
        assertTrue(optimizer.matchesRequestedSpeed("4.0x", null, 4.0f))
        assertTrue(optimizer.matchesRequestedSpeed("5.0x", null, 5.0f))
        assertTrue(optimizer.matchesRequestedSpeed("5x", null, 5.0f))
        assertTrue(optimizer.matchesRequestedSpeed("7.5x", null, 7.5f))
        assertTrue(optimizer.matchesRequestedSpeed("10.0x", null, 10.0f))
        assertTrue(optimizer.matchesRequestedSpeed("10x", null, 10.0f))
    }

    @Test
    fun testMatchesRequestedSpeed_NegativeCases() {
        // Critical requirement: 10x must NEVER match 1x
        assertFalse(optimizer.matchesRequestedSpeed("10.0x", null, 1.0f))
        assertFalse(optimizer.matchesRequestedSpeed("10x", null, 1.0f))
        assertFalse(optimizer.matchesRequestedSpeed("1.0x", null, 10.0f))
        assertFalse(optimizer.matchesRequestedSpeed("1x", null, 10.0f))

        // 1.5x must NOT match 1x
        assertFalse(optimizer.matchesRequestedSpeed("1.5x", null, 1.0f))
        assertFalse(optimizer.matchesRequestedSpeed("1.0x", null, 1.5f))

        // 2.5x must NOT match 2x
        assertFalse(optimizer.matchesRequestedSpeed("2.5x", null, 2.0f))
        assertFalse(optimizer.matchesRequestedSpeed("2.0x", null, 2.5f))

        // 5.0x must NOT match 1.0x, 1.25x, 2.0x, 10.0x
        assertFalse(optimizer.matchesRequestedSpeed("1.0x", null, 5.0f))
        assertFalse(optimizer.matchesRequestedSpeed("1.25x", null, 5.0f))
        assertFalse(optimizer.matchesRequestedSpeed("2.0x", null, 5.0f))
        assertFalse(optimizer.matchesRequestedSpeed("10.0x", null, 5.0f))
    }

    @Test
    fun testWebBridgeScriptContainsTampermonkeyCore() {
        val script = com.example.web.DikshaWebBridge.INJECTION_SCRIPT
        assertTrue(script.contains("playbackRate"))
        assertTrue(script.contains("MutationObserver"))
        assertTrue(script.contains("__setEduVideoSpeed"))
        assertTrue(script.contains("__queryEduVideoStatus"))
        assertTrue(script.contains("EduBridge"))
    }
}
