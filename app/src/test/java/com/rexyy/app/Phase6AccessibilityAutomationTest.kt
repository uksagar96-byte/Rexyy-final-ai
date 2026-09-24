package com.rexyy.app

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import com.rexyy.app.accessibility.AccessibilityInteractionProvider
import com.rexyy.app.accessibility.MultiStepWorkflowManager
import com.rexyy.app.accessibility.RexyyAccessibilityService
import com.rexyy.app.accessibility.WorkflowExecutionState
import com.rexyy.app.router.CommandDiagnosticLogger
import com.rexyy.app.router.RexyyCommandRouter
import com.rexyy.app.voice.VoiceCommand
import com.rexyy.app.voice.VoiceCommandExecutor
import com.rexyy.app.voice.VoiceCommandResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase6AccessibilityAutomationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RexyyAccessibilityService.setTestProvider(null)
        CommandDiagnosticLogger.clear()
        com.rexyy.app.launcher.InstalledAppRepository.setTestApps(
            listOf(
                com.rexyy.app.launcher.AppInfo(
                    label = "Notes",
                    packageName = "com.google.android.keep",
                    activityName = "com.google.android.keep.activities.BrowseActivity",
                    aliases = listOf("notes", "keep")
                ),
                com.rexyy.app.launcher.AppInfo(
                    label = "Chrome",
                    packageName = "com.android.chrome",
                    activityName = "com.google.android.apps.chrome.Main",
                    aliases = listOf("chrome", "browser")
                ),
                com.rexyy.app.launcher.AppInfo(
                    label = "Instagram",
                    packageName = "com.instagram.android",
                    activityName = "com.instagram.mainactivity.MainActivity",
                    aliases = listOf("instagram", "insta")
                )
            )
        )
        com.rexyy.app.launcher.AppLauncher.testLaunchOverride = { true }
    }

    @After
    fun tearDown() {
        RexyyAccessibilityService.setTestProvider(null)
        CommandDiagnosticLogger.clear()
        com.rexyy.app.launcher.InstalledAppRepository.setTestApps(null)
        com.rexyy.app.launcher.AppLauncher.testLaunchOverride = null
    }

    @Test
    fun testNotesToChromeMultiStepPlanning() {
        val input = "Notes kholo, hello REXXY type karo, copy karo, Chrome kholo, paste karo aur search karo."
        val command = RexyyCommandRouter.route(input)

        assertTrue("Expected MultiStepTask but was $command", command is VoiceCommand.MultiStepTask)
        val task = command as VoiceCommand.MultiStepTask
        assertEquals(6, task.steps.size)

        // Step 1: Notes kholo
        assertTrue(task.steps[0] is VoiceCommand.OpenApp)
        assertEquals("Notes", (task.steps[0] as VoiceCommand.OpenApp).appName)

        // Step 2: hello REXXY type karo
        assertTrue(task.steps[1] is VoiceCommand.AccessibilityAction)
        val step2 = task.steps[1] as VoiceCommand.AccessibilityAction
        assertEquals(VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT, step2.actionType)
        assertEquals("hello REXXY", step2.argument)

        // Step 3: copy karo
        assertTrue(task.steps[2] is VoiceCommand.AccessibilityAction)
        val step3 = task.steps[2] as VoiceCommand.AccessibilityAction
        assertEquals(VoiceCommand.AccessibilityAction.ActionType.COPY, step3.actionType)

        // Step 4: Chrome kholo
        assertTrue(task.steps[3] is VoiceCommand.OpenApp)
        assertEquals("Chrome", (task.steps[3] as VoiceCommand.OpenApp).appName)

        // Step 5: paste karo
        assertTrue(task.steps[4] is VoiceCommand.AccessibilityAction)
        val step5 = task.steps[4] as VoiceCommand.AccessibilityAction
        assertEquals(VoiceCommand.AccessibilityAction.ActionType.PASTE, step5.actionType)

        // Step 6: search karo
        assertTrue(task.steps[5] is VoiceCommand.AccessibilityAction)
        val step6 = task.steps[5] as VoiceCommand.AccessibilityAction
        assertEquals(VoiceCommand.AccessibilityAction.ActionType.SUBMIT_SEARCH, step6.actionType)
    }

    @Test
    fun testInstagramScrollNavigationPlanning() {
        val input = "Instagram kholo, neeche scroll karo, phir upar scroll karo aur exit karo."
        val command = RexyyCommandRouter.route(input)

        assertTrue("Expected MultiStepTask but was $command", command is VoiceCommand.MultiStepTask)
        val task = command as VoiceCommand.MultiStepTask
        assertEquals(4, task.steps.size)

        // Step 1: Instagram kholo
        assertTrue(task.steps[0] is VoiceCommand.OpenApp)
        assertEquals("Instagram", (task.steps[0] as VoiceCommand.OpenApp).appName)

        // Step 2: neeche scroll karo
        assertTrue(task.steps[1] is VoiceCommand.AccessibilityAction)
        assertEquals(VoiceCommand.AccessibilityAction.ActionType.SCROLL_DOWN, (task.steps[1] as VoiceCommand.AccessibilityAction).actionType)

        // Step 3: phir upar scroll karo
        assertTrue(task.steps[2] is VoiceCommand.AccessibilityAction)
        assertEquals(VoiceCommand.AccessibilityAction.ActionType.SCROLL_UP, (task.steps[2] as VoiceCommand.AccessibilityAction).actionType)

        // Step 4: exit karo
        val step4 = task.steps[3]
        val isExitAction = (step4 is VoiceCommand.CloseApp) ||
                (step4 is VoiceCommand.AccessibilityAction && step4.actionType == VoiceCommand.AccessibilityAction.ActionType.GO_BACK)
        assertTrue("Expected CloseApp or GO_BACK, but was $step4", isExitAction)
    }

    @Test
    fun testWorkflowFailsWhenAccessibilityDisabled() = runBlocking {
        // No test provider configured -> isServiceEnabled returns false in Robolectric
        RexyyAccessibilityService.setTestProvider(null)

        val task = VoiceCommand.MultiStepTask(
            steps = listOf(
                VoiceCommand.OpenApp("Notes", "Notes kholo"),
                VoiceCommand.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT, "hello REXXY", "hello REXXY type karo")
            ),
            description = "Notes and type",
            rawInput = "Notes kholo aur hello REXXY type karo"
        )

        val result = MultiStepWorkflowManager.executeWorkflow(task, context)
        assertEquals(WorkflowExecutionState.FAILED, result.finalState)
        assertEquals(1, result.failedStepIndex)
        assertTrue(result.summaryMessage.contains("Accessibility service", ignoreCase = true))
        assertEquals(1, result.stepResults.size)
        assertEquals("FAILED", result.stepResults[0].verificationResult)
    }

    @Test
    fun testSuccessfulMultiStepWorkflowExecution() = runBlocking {
        val testProvider = MockAccessibilityProvider(
            enabled = true,
            foregroundPackage = "com.google.android.keep"
        )
        RexyyAccessibilityService.setTestProvider(testProvider)

        val task = VoiceCommand.MultiStepTask(
            steps = listOf(
                VoiceCommand.OpenApp("Notes", "Notes kholo"),
                VoiceCommand.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT, "hello REXXY", "hello REXXY type karo"),
                VoiceCommand.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.COPY, "", "copy karo")
            ),
            description = "Notes workflow",
            rawInput = "Notes kholo, hello REXXY type karo, copy karo"
        )

        val result = MultiStepWorkflowManager.executeWorkflow(task, context)
        assertEquals(WorkflowExecutionState.VERIFIED, result.finalState)
        assertEquals(3, result.executedSteps)
        assertEquals(3, result.stepResults.size)
        assertTrue(result.stepResults.all { it.verificationResult == "VERIFIED" })
    }

    @Test
    fun testStepFailureHaltsPipelineImmediately() = runBlocking {
        // Provider fails on copyText
        val testProvider = MockAccessibilityProvider(
            enabled = true,
            foregroundPackage = "com.google.android.keep",
            allowCopy = false
        )
        RexyyAccessibilityService.setTestProvider(testProvider)

        val task = VoiceCommand.MultiStepTask(
            steps = listOf(
                VoiceCommand.OpenApp("Notes", "Notes kholo"),
                VoiceCommand.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT, "hello REXXY", "hello REXXY type karo"),
                VoiceCommand.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.COPY, "", "copy karo"),
                VoiceCommand.OpenApp("Chrome", "Chrome kholo"),
                VoiceCommand.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.PASTE, "", "paste karo")
            ),
            description = "Notes to Chrome with failing copy",
            rawInput = "Notes kholo, hello REXXY type karo, copy karo, Chrome kholo, paste karo"
        )

        val result = MultiStepWorkflowManager.executeWorkflow(task, context)
        assertEquals(WorkflowExecutionState.FAILED, result.finalState)
        assertEquals(3, result.failedStepIndex)
        // Step 4 and 5 must NEVER have run!
        assertEquals(3, result.stepResults.size)
        assertEquals("FAILED", result.stepResults[2].verificationResult)
        assertEquals("COPY_FAILED", result.stepResults[2].actionResult)
    }

    @Test
    fun testPackageMismatchTimeoutHandling() = runBlocking {
        // Provider simulates package timeout (returns false for waitForForegroundPackage)
        val testProvider = MockAccessibilityProvider(
            enabled = true,
            foregroundPackage = "com.unexpected.launcher",
            allowForegroundWait = false
        )
        RexyyAccessibilityService.setTestProvider(testProvider)

        val task = VoiceCommand.MultiStepTask(
            steps = listOf(
                VoiceCommand.OpenApp("Chrome", "Chrome kholo"),
                VoiceCommand.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.PASTE, "", "paste karo")
            ),
            description = "Package timeout test",
            rawInput = "Chrome kholo aur paste karo"
        )

        val result = MultiStepWorkflowManager.executeWorkflow(task, context)
        assertEquals(WorkflowExecutionState.FAILED, result.finalState)
        assertEquals(1, result.failedStepIndex)
        assertEquals("TIMED_OUT", result.stepResults[0].verificationResult)
        assertTrue(result.stepResults[0].failureReason?.contains("timeout", ignoreCase = true) == true)
        // Subsequent step must never execute
        assertEquals(1, result.stepResults.size)
    }

    @Test
    fun testSecuritySensitiveNodesProtected() {
        val service = RexyyAccessibilityService()
        // Ensure isSafeNode returns false for null or null root
        assertFalse(service.isSafeNode(null))
    }

    @Test
    fun testDiagnosticsRecordedPerStep() = runBlocking {
        val testProvider = MockAccessibilityProvider(
            enabled = true,
            foregroundPackage = "com.instagram.android"
        )
        RexyyAccessibilityService.setTestProvider(testProvider)

        val task = VoiceCommand.MultiStepTask(
            steps = listOf(
                VoiceCommand.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.SCROLL_DOWN, "", "neeche scroll karo"),
                VoiceCommand.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.SCROLL_UP, "", "upar scroll karo")
            ),
            description = "Scroll test",
            rawInput = "neeche scroll karo aur upar scroll karo"
        )

        val result = VoiceCommandExecutor.execute(task, context)
        assertTrue(result is VoiceCommandResult.Handled)

        val traces = CommandDiagnosticLogger.traces.value
        assertTrue("Traces should have at least 2 entries for the 2 steps", traces.size >= 2)
        val stepTrace = traces.find { it.detectedIntent.startsWith("MultiStepTask_Step_") }
        assertNotNull(stepTrace)
        assertEquals("VERIFIED", stepTrace?.verificationResult)
    }

    /**
     * Test provider implementing full AccessibilityInteractionProvider interface
     */
    private class MockAccessibilityProvider(
        private val enabled: Boolean = true,
        private var foregroundPackage: String? = "com.google.android.keep",
        private val allowCopy: Boolean = true,
        private val allowForegroundWait: Boolean = true
    ) : AccessibilityInteractionProvider {

        private val dummyNode: AccessibilityNodeInfo = AccessibilityNodeInfo.obtain()

        override fun isServiceEnabled(): Boolean = enabled

        override fun getForegroundPackage(): String? = foregroundPackage

        override fun findNodeByText(text: String): AccessibilityNodeInfo? = dummyNode

        override fun findVisibleText(text: String, ignoreCase: Boolean): AccessibilityNodeInfo? = dummyNode

        override fun findNodeByContentDescription(desc: String, ignoreCase: Boolean): AccessibilityNodeInfo? = dummyNode

        override fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? = dummyNode

        override fun findClickableNodes(): List<AccessibilityNodeInfo> = listOf(dummyNode)

        override fun findEditableFields(): List<AccessibilityNodeInfo> = listOf(dummyNode)

        override fun findFirstEditableField(): AccessibilityNodeInfo? = dummyNode

        override fun findSearchField(): AccessibilityNodeInfo? = dummyNode

        override fun clickNode(node: AccessibilityNodeInfo?): Boolean = true

        override fun inputText(node: AccessibilityNodeInfo?, text: String): Boolean = true

        override fun replaceText(node: AccessibilityNodeInfo?, text: String): Boolean = true

        override fun copyText(node: AccessibilityNodeInfo?): Boolean = allowCopy

        override fun pasteText(node: AccessibilityNodeInfo?): Boolean = true

        override fun scrollForward(node: AccessibilityNodeInfo?): Boolean = true

        override fun scrollBackward(node: AccessibilityNodeInfo?): Boolean = true

        override fun pressBack(): Boolean = true

        override fun pressHome(): Boolean = true

        override fun pressRecentApps(): Boolean = true

        override fun submitSearch(node: AccessibilityNodeInfo?): Boolean = true

        override suspend fun waitForForegroundPackage(targetPackage: String, timeoutMs: Long): Boolean {
            if (!allowForegroundWait) return false
            foregroundPackage = targetPackage
            return true
        }

        override suspend fun waitForUiCondition(timeoutMs: Long, condition: () -> Boolean): Boolean = true
    }
}
