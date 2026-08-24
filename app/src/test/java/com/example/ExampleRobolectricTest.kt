package com.example

import com.example.model.MediaActionType
import com.example.model.TraversalStrategy
import com.example.service.AccessibilityNodeOptimizer
import com.example.service.CompanionStateManager
import com.example.service.OptimizationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testMockHierarchyCreation() {
        val mockRoot = CompanionStateManager.createMockDikshaHierarchy()
        assertNotNull(mockRoot)
        assertEquals("in.gov.diksha.app:id/main_content", mockRoot.viewIdResourceName)
        assertTrue(mockRoot.children.isNotEmpty())
    }

    @Test
    fun testOptimizerSimulatedMediaAction() {
        CompanionStateManager.simulateMediaAction(MediaActionType.PLAY_PAUSE)
        val state = CompanionStateManager.uiState.value
        assertTrue(state.lastDiagnostics.success)
        assertEquals(MediaActionType.PLAY_PAUSE, state.lastDiagnostics.matchedAction)
        assertTrue(state.lastDiagnostics.totalNodesVisited > 0)
    }

    @Test
    fun testPlayPauseToggle() {
        val initial = CompanionStateManager.uiState.value.isPlaying
        CompanionStateManager.togglePlayPause()
        assertEquals(!initial, CompanionStateManager.uiState.value.isPlaying)
    }

    @Test
    fun test10xTurboSpeed() {
        CompanionStateManager.setPlaybackSpeed("10.0x")
        val state = CompanionStateManager.uiState.value
        assertTrue(state.isTurbo10xActive)
        assertEquals("10.0x", state.currentPlaybackSpeed)
    }

    @Test
    fun testSpeedTransitions() {
        // 1x -> 5x
        CompanionStateManager.setPlaybackSpeed("5.0x")
        var state = CompanionStateManager.uiState.value
        assertEquals("5.0x", state.currentPlaybackSpeed)
        assertEquals(false, state.isTurbo10xActive)

        // 5x -> 2x
        CompanionStateManager.setPlaybackSpeed("2.0x")
        state = CompanionStateManager.uiState.value
        assertEquals("2.0x", state.currentPlaybackSpeed)

        // 2x -> 10x
        CompanionStateManager.setPlaybackSpeed("10.0x")
        state = CompanionStateManager.uiState.value
        assertEquals("10.0x", state.currentPlaybackSpeed)
        assertEquals(true, state.isTurbo10xActive)

        // 10x -> 1x
        CompanionStateManager.setPlaybackSpeed("1.0x")
        state = CompanionStateManager.uiState.value
        assertEquals("1.0x", state.currentPlaybackSpeed)
        assertEquals(false, state.isTurbo10xActive)
    }

    @Test
    fun testTargetAppToggle() {
        val dikshaPkg = "in.gov.diksha.app"
        CompanionStateManager.toggleTargetApp(dikshaPkg, false)
        var diksha = CompanionStateManager.uiState.value.targetApps.find { it.packageName == dikshaPkg }
        assertEquals(false, diksha?.isEnabled)

        CompanionStateManager.toggleTargetApp(dikshaPkg, true)
        diksha = CompanionStateManager.uiState.value.targetApps.find { it.packageName == dikshaPkg }
        assertEquals(true, diksha?.isEnabled)
    }

    @Test
    fun testOptimizationConfigUpdate() {
        val newConfig = OptimizationConfig(
            maxDepth = 20,
            maxNodesLimit = 300,
            strategy = TraversalStrategy.BFS
        )
        CompanionStateManager.updateOptimizationConfig(newConfig)
        assertEquals(20, CompanionStateManager.uiState.value.optimizationConfig.maxDepth)
        assertEquals(300, CompanionStateManager.uiState.value.optimizationConfig.maxNodesLimit)
    }

    @Test
    fun testAddAndRemoveCustomTargetApp() {
        val customPkg = "com.custom.learningapp"
        val customApp = com.example.model.TargetAppConfig(
            packageName = customPkg,
            displayName = "Custom Learning",
            description = "Test Custom App",
            isEnabled = true
        )
        CompanionStateManager.addCustomTargetApp(customApp)
        val added = CompanionStateManager.uiState.value.targetApps.find { it.packageName == customPkg }
        assertNotNull(added)
        assertEquals("Custom Learning", added?.displayName)

        CompanionStateManager.removeTargetApp(customPkg)
        val removed = CompanionStateManager.uiState.value.targetApps.find { it.packageName == customPkg }
        assertEquals(null, removed)
    }

    @Test
    fun testAllMediaActionSimulations() {
        // Test Fast Forward
        CompanionStateManager.simulateMediaAction(MediaActionType.FAST_FORWARD)
        var state = CompanionStateManager.uiState.value
        assertTrue(state.lastDiagnostics.success)
        assertEquals(MediaActionType.FAST_FORWARD, state.lastDiagnostics.matchedAction)

        // Test Rewind
        CompanionStateManager.simulateMediaAction(MediaActionType.REWIND)
        state = CompanionStateManager.uiState.value
        assertTrue(state.lastDiagnostics.success)
        assertEquals(MediaActionType.REWIND, state.lastDiagnostics.matchedAction)

        // Test Next
        CompanionStateManager.simulateMediaAction(MediaActionType.NEXT)
        state = CompanionStateManager.uiState.value
        assertTrue(state.lastDiagnostics.success)
        assertEquals(MediaActionType.NEXT, state.lastDiagnostics.matchedAction)

        // Test Captions
        CompanionStateManager.simulateMediaAction(MediaActionType.CAPTIONS)
        state = CompanionStateManager.uiState.value
        assertTrue(state.lastDiagnostics.success)
        assertEquals(MediaActionType.CAPTIONS, state.lastDiagnostics.matchedAction)

        // Test Speed Set (5.0x)
        CompanionStateManager.simulateMediaAction(MediaActionType.SPEED_SET, "5.0x")
        state = CompanionStateManager.uiState.value
        assertTrue(state.lastDiagnostics.success)
        assertEquals(MediaActionType.SPEED_SET, state.lastDiagnostics.matchedAction)
    }
}
