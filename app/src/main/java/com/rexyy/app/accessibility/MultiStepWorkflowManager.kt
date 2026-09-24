package com.rexyy.app.accessibility

import android.content.Context
import com.rexyy.app.launcher.AppDiscoveryManager
import com.rexyy.app.launcher.AppLaunchOutcome
import com.rexyy.app.router.CommandDiagnosticLogger
import com.rexyy.app.voice.VoiceCommand
import com.rexyy.app.voice.VoiceCommandExecutor
import com.rexyy.app.voice.VoiceCommandResult
import kotlinx.coroutines.delay

enum class WorkflowExecutionState {
    PLANNING,
    EXECUTING,
    WAITING_FOR_UI,
    VERIFIED,
    FAILED
}

data class WorkflowStep(
    val stepIndex: Int,
    val description: String,
    val targetPackage: String? = null,
    val command: VoiceCommand,
    val timeoutMs: Long = 3000L
)

data class StepExecutionResult(
    val stepIndex: Int,
    val requestedAction: String,
    val targetPackage: String?,
    val detectedUiNode: String?,
    val actionResult: String,
    val verificationResult: String, // "VERIFIED", "FAILED", "TIMED_OUT", "WAITING_FOR_UI"
    val latencyMs: Long,
    val failureReason: String? = null
)

data class WorkflowResult(
    val totalSteps: Int,
    val executedSteps: Int,
    val finalState: WorkflowExecutionState,
    val failedStepIndex: Int? = null,
    val stepResults: List<StepExecutionResult>,
    val summaryMessage: String
)

object MultiStepWorkflowManager {

    @Volatile
    private var currentState: WorkflowExecutionState = WorkflowExecutionState.PLANNING

    fun getCurrentState(): WorkflowExecutionState = currentState

    suspend fun planWorkflow(commands: List<VoiceCommand>, context: Context): List<WorkflowStep> {
        currentState = WorkflowExecutionState.PLANNING
        val discovery = AppDiscoveryManager(context)
        var currentTargetPackage: String? = null
        val steps = mutableListOf<WorkflowStep>()

        commands.forEachIndexed { index, cmd ->
            val stepNum = index + 1
            when (cmd) {
                is VoiceCommand.OpenApp -> {
                    val app = discovery.findApp(cmd.appName)
                    currentTargetPackage = app?.packageName ?: resolveCommonPackage(cmd.appName)
                    steps.add(
                        WorkflowStep(
                            stepIndex = stepNum,
                            description = "Open ${cmd.appName}",
                            targetPackage = currentTargetPackage,
                            command = cmd
                        )
                    )
                }
                is VoiceCommand.CloseApp -> {
                    currentTargetPackage = null
                    steps.add(
                        WorkflowStep(
                            stepIndex = stepNum,
                            description = "Exit to Home",
                            targetPackage = null,
                            command = cmd
                        )
                    )
                }
                is VoiceCommand.AccessibilityAction -> {
                    val desc = when (cmd.actionType) {
                        VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT -> "Type \"${cmd.argument}\""
                        VoiceCommand.AccessibilityAction.ActionType.REPLACE_TEXT -> "Replace text with \"${cmd.argument}\""
                        VoiceCommand.AccessibilityAction.ActionType.COPY -> "Copy text"
                        VoiceCommand.AccessibilityAction.ActionType.PASTE -> "Paste text"
                        VoiceCommand.AccessibilityAction.ActionType.SCROLL_DOWN -> "Scroll down"
                        VoiceCommand.AccessibilityAction.ActionType.SCROLL_UP -> "Scroll up"
                        VoiceCommand.AccessibilityAction.ActionType.GO_BACK -> "Go back"
                        VoiceCommand.AccessibilityAction.ActionType.GO_HOME -> "Go to home screen"
                        VoiceCommand.AccessibilityAction.ActionType.RECENTS -> "Open recent apps"
                        VoiceCommand.AccessibilityAction.ActionType.SUBMIT_SEARCH -> "Submit search"
                        VoiceCommand.AccessibilityAction.ActionType.CLICK_NODE -> "Click \"${cmd.argument}\""
                    }
                    steps.add(
                        WorkflowStep(
                            stepIndex = stepNum,
                            description = desc,
                            targetPackage = currentTargetPackage,
                            command = cmd
                        )
                    )
                }
                else -> {
                    steps.add(
                        WorkflowStep(
                            stepIndex = stepNum,
                            description = "Execute ${cmd::class.simpleName}",
                            targetPackage = currentTargetPackage,
                            command = cmd
                        )
                    )
                }
            }
        }
        return steps
    }

    suspend fun executeWorkflow(task: VoiceCommand.MultiStepTask, context: Context): WorkflowResult {
        val totalStepsCount = task.steps.size

        // 1. Verify Accessibility is enabled
        val a11yConfigured = RexyyAccessibilityService.isAccessibilityServiceConfigured(context)
        if (!a11yConfigured) {
            currentState = WorkflowExecutionState.FAILED
            val failResult = StepExecutionResult(
                stepIndex = 1,
                requestedAction = "VERIFY_ACCESSIBILITY_ENABLED",
                targetPackage = null,
                detectedUiNode = null,
                actionResult = "SERVICE_DISABLED",
                verificationResult = "FAILED",
                latencyMs = 0L,
                failureReason = "Accessibility service is not enabled."
            )
            logStepDiagnostic(failResult, task.rawInput)
            return WorkflowResult(
                totalSteps = totalStepsCount,
                executedSteps = 0,
                finalState = WorkflowExecutionState.FAILED,
                failedStepIndex = 1,
                stepResults = listOf(failResult),
                summaryMessage = "Accessibility service enabled nahi hai. Automation ke liye Settings mein Accessibility permission allow karein."
            )
        }

        // 2. Plan steps
        val plannedSteps = planWorkflow(task.steps, context)
        currentState = WorkflowExecutionState.EXECUTING

        val stepResults = mutableListOf<StepExecutionResult>()
        val service = RexyyAccessibilityService.getInteractionProvider()

        for (step in plannedSteps) {
            val stepStart = System.currentTimeMillis()

            // 3. Package-Aware Verification
            if (step.targetPackage != null && service != null) {
                val currentPkg = service.getForegroundPackage()
                if (currentPkg == null || !currentPkg.contains(step.targetPackage, ignoreCase = true)) {
                    currentState = WorkflowExecutionState.WAITING_FOR_UI
                    val reached = service.waitForForegroundPackage(step.targetPackage, timeoutMs = 2500)
                    if (!reached) {
                        currentState = WorkflowExecutionState.FAILED
                        val elapsed = System.currentTimeMillis() - stepStart
                        val failStep = StepExecutionResult(
                            stepIndex = step.stepIndex,
                            requestedAction = step.description,
                            targetPackage = step.targetPackage,
                            detectedUiNode = null,
                            actionResult = "PACKAGE_MISMATCH",
                            verificationResult = "TIMED_OUT",
                            latencyMs = elapsed,
                            failureReason = "Expected app ${step.targetPackage} was not in foreground after timeout."
                        )
                        stepResults.add(failStep)
                        logStepDiagnostic(failStep, task.rawInput)
                        return WorkflowResult(
                            totalSteps = totalStepsCount,
                            executedSteps = stepResults.size,
                            finalState = WorkflowExecutionState.FAILED,
                            failedStepIndex = step.stepIndex,
                            stepResults = stepResults,
                            summaryMessage = "Step ${step.stepIndex} failed: ${failStep.failureReason}"
                        )
                    }
                }
                currentState = WorkflowExecutionState.EXECUTING
            }

            // 4. Execute Step Action
            val stepResult = executeSingleStep(step, service, context, stepStart)
            stepResults.add(stepResult)
            logStepDiagnostic(stepResult, task.rawInput)

            // 5. Verification Gate: If any step fails, stop immediately
            if (stepResult.verificationResult != "VERIFIED") {
                currentState = WorkflowExecutionState.FAILED
                return WorkflowResult(
                    totalSteps = totalStepsCount,
                    executedSteps = stepResults.size,
                    finalState = WorkflowExecutionState.FAILED,
                    failedStepIndex = step.stepIndex,
                    stepResults = stepResults,
                    summaryMessage = "Step ${step.stepIndex} (${step.description}) failed: ${stepResult.failureReason ?: stepResult.actionResult}"
                )
            }

            // Safe pause between steps for UI transitions
            delay(150)
        }

        // 6. Final verification
        currentState = WorkflowExecutionState.VERIFIED
        return WorkflowResult(
            totalSteps = totalStepsCount,
            executedSteps = stepResults.size,
            finalState = WorkflowExecutionState.VERIFIED,
            failedStepIndex = null,
            stepResults = stepResults,
            summaryMessage = "All $totalStepsCount steps executed and verified successfully."
        )
    }

    private suspend fun executeSingleStep(
        step: WorkflowStep,
        service: AccessibilityInteractionProvider?,
        context: Context,
        startTime: Long
    ): StepExecutionResult {
        return when (val cmd = step.command) {
            is VoiceCommand.OpenApp -> {
                val discovery = AppDiscoveryManager(context)
                when (val outcome = discovery.findAndLaunchApp(cmd.appName)) {
                    is AppLaunchOutcome.Success -> {
                        val reached = service?.waitForForegroundPackage(outcome.packageName, timeoutMs = 2500) ?: true
                        val elapsed = System.currentTimeMillis() - startTime
                        if (reached) {
                            StepExecutionResult(
                                stepIndex = step.stepIndex,
                                requestedAction = step.description,
                                targetPackage = outcome.packageName,
                                detectedUiNode = "AppRoot",
                                actionResult = "LAUNCHED",
                                verificationResult = "VERIFIED",
                                latencyMs = elapsed
                            )
                        } else {
                            StepExecutionResult(
                                stepIndex = step.stepIndex,
                                requestedAction = step.description,
                                targetPackage = outcome.packageName,
                                detectedUiNode = null,
                                actionResult = "LAUNCH_TIMEOUT",
                                verificationResult = "TIMED_OUT",
                                latencyMs = elapsed,
                                failureReason = "App launched but failed to reach foreground within timeout."
                            )
                        }
                    }
                    is AppLaunchOutcome.NotInstalled -> {
                        val elapsed = System.currentTimeMillis() - startTime
                        StepExecutionResult(
                            stepIndex = step.stepIndex,
                            requestedAction = step.description,
                            targetPackage = null,
                            detectedUiNode = null,
                            actionResult = "NOT_INSTALLED",
                            verificationResult = "FAILED",
                            latencyMs = elapsed,
                            failureReason = "${outcome.appName} is not installed."
                        )
                    }
                    is AppLaunchOutcome.MultipleMatches -> {
                        val elapsed = System.currentTimeMillis() - startTime
                        StepExecutionResult(
                            stepIndex = step.stepIndex,
                            requestedAction = step.description,
                            targetPackage = null,
                            detectedUiNode = null,
                            actionResult = "AMBIGUOUS_APP",
                            verificationResult = "FAILED",
                            latencyMs = elapsed,
                            failureReason = "Multiple apps match: ${outcome.candidates.joinToString(", ")}"
                        )
                    }
                    is AppLaunchOutcome.FailedToLaunch -> {
                        val elapsed = System.currentTimeMillis() - startTime
                        StepExecutionResult(
                            stepIndex = step.stepIndex,
                            requestedAction = step.description,
                            targetPackage = null,
                            detectedUiNode = null,
                            actionResult = "LAUNCH_ERROR",
                            verificationResult = "FAILED",
                            latencyMs = elapsed,
                            failureReason = "${outcome.appLabel} failed to launch."
                        )
                    }
                }
            }

            is VoiceCommand.AccessibilityAction -> {
                executeAccessibilityStep(step, cmd, service, startTime)
            }

            is VoiceCommand.CloseApp -> {
                val success = service?.pressHome() ?: false
                val elapsed = System.currentTimeMillis() - startTime
                StepExecutionResult(
                    stepIndex = step.stepIndex,
                    requestedAction = step.description,
                    targetPackage = null,
                    detectedUiNode = "HomeScreen",
                    actionResult = if (success) "EXITED" else "FAILED",
                    verificationResult = if (success) "VERIFIED" else "FAILED",
                    latencyMs = elapsed,
                    failureReason = if (!success) "Could not return to home screen." else null
                )
            }

            else -> {
                val res = VoiceCommandExecutor.execute(cmd, context)
                val elapsed = System.currentTimeMillis() - startTime
                when (res) {
                    is VoiceCommandResult.Handled -> {
                        StepExecutionResult(
                            stepIndex = step.stepIndex,
                            requestedAction = step.description,
                            targetPackage = step.targetPackage,
                            detectedUiNode = null,
                            actionResult = "HANDLED",
                            verificationResult = "VERIFIED",
                            latencyMs = elapsed
                        )
                    }
                    is VoiceCommandResult.Error -> {
                        StepExecutionResult(
                            stepIndex = step.stepIndex,
                            requestedAction = step.description,
                            targetPackage = step.targetPackage,
                            detectedUiNode = null,
                            actionResult = "ERROR",
                            verificationResult = "FAILED",
                            latencyMs = elapsed,
                            failureReason = res.errorMessage
                        )
                    }
                    else -> {
                        StepExecutionResult(
                            stepIndex = step.stepIndex,
                            requestedAction = step.description,
                            targetPackage = step.targetPackage,
                            detectedUiNode = null,
                            actionResult = "UNSUPPORTED",
                            verificationResult = "FAILED",
                            latencyMs = elapsed,
                            failureReason = "Command returned unexpected result type."
                        )
                    }
                }
            }
        }
    }

    private fun executeAccessibilityStep(
        step: WorkflowStep,
        cmd: VoiceCommand.AccessibilityAction,
        service: AccessibilityInteractionProvider?,
        startTime: Long
    ): StepExecutionResult {
        if (service == null) {
            val elapsed = System.currentTimeMillis() - startTime
            return StepExecutionResult(
                stepIndex = step.stepIndex,
                requestedAction = step.description,
                targetPackage = step.targetPackage,
                detectedUiNode = null,
                actionResult = "SERVICE_UNAVAILABLE",
                verificationResult = "FAILED",
                latencyMs = elapsed,
                failureReason = "Accessibility service is not available."
            )
        }

        return when (cmd.actionType) {
            VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT -> {
                val field = service.findFirstEditableField()
                val elapsed = System.currentTimeMillis() - startTime
                if (field == null) {
                    StepExecutionResult(
                        stepIndex = step.stepIndex,
                        requestedAction = step.description,
                        targetPackage = step.targetPackage,
                        detectedUiNode = null,
                        actionResult = "NO_FIELD",
                        verificationResult = "FAILED",
                        latencyMs = elapsed,
                        failureReason = "No editable text field found on screen."
                    )
                } else {
                    service.clickNode(field)
                    val typed = service.inputText(field, cmd.argument)
                    val uiNode = field.viewIdResourceName ?: field.className?.toString() ?: "EditableField"
                    StepExecutionResult(
                        stepIndex = step.stepIndex,
                        requestedAction = step.description,
                        targetPackage = step.targetPackage,
                        detectedUiNode = uiNode,
                        actionResult = if (typed) "TYPED" else "TYPE_FAILED",
                        verificationResult = if (typed) "VERIFIED" else "FAILED",
                        latencyMs = System.currentTimeMillis() - startTime,
                        failureReason = if (!typed) "Could not type into field." else null
                    )
                }
            }

            VoiceCommand.AccessibilityAction.ActionType.REPLACE_TEXT -> {
                val field = service.findFirstEditableField()
                val elapsed = System.currentTimeMillis() - startTime
                if (field == null) {
                    StepExecutionResult(
                        stepIndex = step.stepIndex,
                        requestedAction = step.description,
                        targetPackage = step.targetPackage,
                        detectedUiNode = null,
                        actionResult = "NO_FIELD",
                        verificationResult = "FAILED",
                        latencyMs = elapsed,
                        failureReason = "No editable text field found on screen."
                    )
                } else {
                    service.clickNode(field)
                    val replaced = service.replaceText(field, cmd.argument)
                    val uiNode = field.viewIdResourceName ?: field.className?.toString() ?: "EditableField"
                    StepExecutionResult(
                        stepIndex = step.stepIndex,
                        requestedAction = step.description,
                        targetPackage = step.targetPackage,
                        detectedUiNode = uiNode,
                        actionResult = if (replaced) "REPLACED" else "REPLACE_FAILED",
                        verificationResult = if (replaced) "VERIFIED" else "FAILED",
                        latencyMs = System.currentTimeMillis() - startTime,
                        failureReason = if (!replaced) "Could not replace text in field." else null
                    )
                }
            }

            VoiceCommand.AccessibilityAction.ActionType.COPY -> {
                val field = service.findFirstEditableField()
                val copied = service.copyText(field)
                val elapsed = System.currentTimeMillis() - startTime
                StepExecutionResult(
                    stepIndex = step.stepIndex,
                    requestedAction = step.description,
                    targetPackage = step.targetPackage,
                    detectedUiNode = field?.viewIdResourceName ?: "ActiveScreen",
                    actionResult = if (copied) "COPIED" else "COPY_FAILED",
                    verificationResult = if (copied) "VERIFIED" else "FAILED",
                    latencyMs = elapsed,
                    failureReason = if (!copied) "Could not perform copy action." else null
                )
            }

            VoiceCommand.AccessibilityAction.ActionType.PASTE -> {
                val field = service.findSearchField() ?: service.findFirstEditableField()
                val elapsed = System.currentTimeMillis() - startTime
                if (field == null) {
                    StepExecutionResult(
                        stepIndex = step.stepIndex,
                        requestedAction = step.description,
                        targetPackage = step.targetPackage,
                        detectedUiNode = null,
                        actionResult = "NO_FIELD",
                        verificationResult = "FAILED",
                        latencyMs = elapsed,
                        failureReason = "No editable field found to paste into."
                    )
                } else {
                    service.clickNode(field)
                    val pasted = service.pasteText(field)
                    val uiNode = field.viewIdResourceName ?: field.className?.toString() ?: "EditableField"
                    StepExecutionResult(
                        stepIndex = step.stepIndex,
                        requestedAction = step.description,
                        targetPackage = step.targetPackage,
                        detectedUiNode = uiNode,
                        actionResult = if (pasted) "PASTED" else "PASTE_FAILED",
                        verificationResult = if (pasted) "VERIFIED" else "FAILED",
                        latencyMs = System.currentTimeMillis() - startTime,
                        failureReason = if (!pasted) "Could not perform paste action." else null
                    )
                }
            }

            VoiceCommand.AccessibilityAction.ActionType.SCROLL_DOWN -> {
                val scrolled = service.scrollForward()
                val elapsed = System.currentTimeMillis() - startTime
                StepExecutionResult(
                    stepIndex = step.stepIndex,
                    requestedAction = step.description,
                    targetPackage = step.targetPackage,
                    detectedUiNode = "ScrollableContainer",
                    actionResult = if (scrolled) "SCROLLED_DOWN" else "SCROLL_FAILED",
                    verificationResult = if (scrolled) "VERIFIED" else "FAILED",
                    latencyMs = elapsed,
                    failureReason = if (!scrolled) "Could not scroll down on screen." else null
                )
            }

            VoiceCommand.AccessibilityAction.ActionType.SCROLL_UP -> {
                val scrolled = service.scrollBackward()
                val elapsed = System.currentTimeMillis() - startTime
                StepExecutionResult(
                    stepIndex = step.stepIndex,
                    requestedAction = step.description,
                    targetPackage = step.targetPackage,
                    detectedUiNode = "ScrollableContainer",
                    actionResult = if (scrolled) "SCROLLED_UP" else "SCROLL_FAILED",
                    verificationResult = if (scrolled) "VERIFIED" else "FAILED",
                    latencyMs = elapsed,
                    failureReason = if (!scrolled) "Could not scroll up on screen." else null
                )
            }

            VoiceCommand.AccessibilityAction.ActionType.GO_BACK -> {
                val success = service.pressBack()
                val elapsed = System.currentTimeMillis() - startTime
                StepExecutionResult(
                    stepIndex = step.stepIndex,
                    requestedAction = step.description,
                    targetPackage = step.targetPackage,
                    detectedUiNode = "SystemNavigation",
                    actionResult = if (success) "BACK_PRESSED" else "BACK_FAILED",
                    verificationResult = if (success) "VERIFIED" else "FAILED",
                    latencyMs = elapsed,
                    failureReason = if (!success) "Could not go back." else null
                )
            }

            VoiceCommand.AccessibilityAction.ActionType.GO_HOME -> {
                val success = service.pressHome()
                val elapsed = System.currentTimeMillis() - startTime
                StepExecutionResult(
                    stepIndex = step.stepIndex,
                    requestedAction = step.description,
                    targetPackage = step.targetPackage,
                    detectedUiNode = "SystemNavigation",
                    actionResult = if (success) "HOME_PRESSED" else "HOME_FAILED",
                    verificationResult = if (success) "VERIFIED" else "FAILED",
                    latencyMs = elapsed,
                    failureReason = if (!success) "Could not return to home screen." else null
                )
            }

            VoiceCommand.AccessibilityAction.ActionType.RECENTS -> {
                val success = service.pressRecentApps()
                val elapsed = System.currentTimeMillis() - startTime
                StepExecutionResult(
                    stepIndex = step.stepIndex,
                    requestedAction = step.description,
                    targetPackage = step.targetPackage,
                    detectedUiNode = "SystemNavigation",
                    actionResult = if (success) "RECENTS_OPENED" else "RECENTS_FAILED",
                    verificationResult = if (success) "VERIFIED" else "FAILED",
                    latencyMs = elapsed,
                    failureReason = if (!success) "Could not open recent apps." else null
                )
            }

            VoiceCommand.AccessibilityAction.ActionType.SUBMIT_SEARCH -> {
                val success = service.submitSearch()
                val elapsed = System.currentTimeMillis() - startTime
                StepExecutionResult(
                    stepIndex = step.stepIndex,
                    requestedAction = step.description,
                    targetPackage = step.targetPackage,
                    detectedUiNode = "SearchWidget",
                    actionResult = if (success) "SEARCH_SUBMITTED" else "SUBMIT_FAILED",
                    verificationResult = if (success) "VERIFIED" else "FAILED",
                    latencyMs = elapsed,
                    failureReason = if (!success) "Could not submit search action." else null
                )
            }

            VoiceCommand.AccessibilityAction.ActionType.CLICK_NODE -> {
                val node = service.findVisibleText(cmd.argument) ?: service.findNodeByContentDescription(cmd.argument)
                val elapsed = System.currentTimeMillis() - startTime
                if (node == null) {
                    StepExecutionResult(
                        stepIndex = step.stepIndex,
                        requestedAction = step.description,
                        targetPackage = step.targetPackage,
                        detectedUiNode = null,
                        actionResult = "NODE_NOT_FOUND",
                        verificationResult = "FAILED",
                        latencyMs = elapsed,
                        failureReason = "Node \"${cmd.argument}\" was not found on screen."
                    )
                } else {
                    val clicked = service.clickNode(node)
                    val uiNode = node.text?.toString() ?: node.contentDescription?.toString() ?: "ClickableNode"
                    StepExecutionResult(
                        stepIndex = step.stepIndex,
                        requestedAction = step.description,
                        targetPackage = step.targetPackage,
                        detectedUiNode = uiNode,
                        actionResult = if (clicked) "CLICKED" else "CLICK_FAILED",
                        verificationResult = if (clicked) "VERIFIED" else "FAILED",
                        latencyMs = System.currentTimeMillis() - startTime,
                        failureReason = if (!clicked) "Failed to click node \"${cmd.argument}\"." else null
                    )
                }
            }
        }
    }

    private fun resolveCommonPackage(appName: String): String {
        return when (appName.lowercase()) {
            "chrome" -> "com.android.chrome"
            "youtube" -> "com.google.android.youtube"
            "youtube music", "yt music" -> "com.google.android.apps.youtube.music"
            "instagram" -> "com.instagram.android"
            "whatsapp" -> "com.whatsapp"
            "spotify" -> "com.spotify.music"
            "maps", "google maps" -> "com.google.android.apps.maps"
            "notes", "keep notes", "google keep" -> "com.google.android.keep"
            "settings" -> "com.android.settings"
            else -> appName.lowercase().replace(" ", ".")
        }
    }

    private fun logStepDiagnostic(stepResult: StepExecutionResult, rawCommand: String) {
        CommandDiagnosticLogger.log(
            rawCommand = rawCommand,
            detectedIntent = "MultiStepTask_Step_${stepResult.stepIndex}",
            extractedEntities = mapOf(
                "stepNumber" to stepResult.stepIndex,
                "requestedAction" to stepResult.requestedAction,
                "targetPackage" to stepResult.targetPackage,
                "detectedUiNode" to stepResult.detectedUiNode
            ),
            targetApp = stepResult.targetPackage,
            searchQuery = null,
            launchMethod = null,
            searchMethod = null,
            executionResult = stepResult.actionResult,
            verificationResult = stepResult.verificationResult,
            failureReason = stepResult.failureReason,
            latencyMs = stepResult.latencyMs
        )
    }
}
