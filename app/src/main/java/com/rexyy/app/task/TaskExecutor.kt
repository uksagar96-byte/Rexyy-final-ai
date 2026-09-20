package com.rexyy.app.task

import android.content.Context
import com.rexyy.app.voice.VoiceCommandExecutor
import com.rexyy.app.voice.VoiceCommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class TaskExecutor(private val context: Context) {

    private val _activeTask = MutableStateFlow<TaskPlan?>(null)
    val activeTask: StateFlow<TaskPlan?> = _activeTask.asStateFlow()

    private var isCancelled = false

    fun cancelCurrentTask(): Boolean {
        if (_activeTask.value != null && !_activeTask.value!!.isFinished) {
            isCancelled = true
            _activeTask.value = _activeTask.value?.copy(isCancelled = true, isFinished = true)
            return true
        }
        return false
    }

    suspend fun executePlan(
        plan: TaskPlan,
        onStepUpdated: (TaskPlan) -> Unit
    ): VoiceCommandResult = withContext(Dispatchers.Main) {
        isCancelled = false
        var currentPlan = plan
        _activeTask.value = currentPlan
        onStepUpdated(currentPlan)

        for (i in currentPlan.steps.indices) {
            if (isCancelled) {
                currentPlan = currentPlan.copy(
                    isCancelled = true,
                    isFinished = true,
                    steps = currentPlan.steps.mapIndexed { idx, s ->
                        if (idx == i) s.copy(status = TaskStepStatus.CANCELLED) else s
                    }
                )
                _activeTask.value = currentPlan
                onStepUpdated(currentPlan)
                return@withContext VoiceCommandResult.Handled("Task was cancelled.")
            }

            // Mark step as running
            val updatedSteps = currentPlan.steps.toMutableList()
            updatedSteps[i] = updatedSteps[i].copy(status = TaskStepStatus.RUNNING)
            currentPlan = currentPlan.copy(steps = updatedSteps, currentStepIndex = i)
            _activeTask.value = currentPlan
            onStepUpdated(currentPlan)

            delay(600) // Visual pacing between actions

            try {
                val stepResult = VoiceCommandExecutor.execute(updatedSteps[i].command, context)
                when (stepResult) {
                    is VoiceCommandResult.Handled -> {
                        updatedSteps[i] = updatedSteps[i].copy(
                            status = TaskStepStatus.SUCCESS,
                            resultMessage = stepResult.replyText
                        )
                    }
                    is VoiceCommandResult.RequiresConfirmation -> {
                        updatedSteps[i] = updatedSteps[i].copy(
                            status = TaskStepStatus.SUCCESS,
                            resultMessage = "Waiting for confirmation"
                        )
                        currentPlan = currentPlan.copy(steps = updatedSteps)
                        _activeTask.value = currentPlan
                        onStepUpdated(currentPlan)
                        return@withContext stepResult
                    }
                    is VoiceCommandResult.Error -> {
                        updatedSteps[i] = updatedSteps[i].copy(
                            status = TaskStepStatus.FAILED,
                            resultMessage = stepResult.errorMessage
                        )
                        currentPlan = currentPlan.copy(steps = updatedSteps, isFinished = true)
                        _activeTask.value = currentPlan
                        onStepUpdated(currentPlan)
                        return@withContext VoiceCommandResult.Error("Task failed at step ${i + 1}: ${stepResult.errorMessage}")
                    }
                    is VoiceCommandResult.ForwardToAi -> {
                        updatedSteps[i] = updatedSteps[i].copy(status = TaskStepStatus.SUCCESS)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updatedSteps[i] = updatedSteps[i].copy(
                    status = TaskStepStatus.FAILED,
                    resultMessage = e.localizedMessage
                )
                currentPlan = currentPlan.copy(steps = updatedSteps, isFinished = true)
                _activeTask.value = currentPlan
                onStepUpdated(currentPlan)
                return@withContext VoiceCommandResult.Error("Step ${i + 1} encountered error: ${e.localizedMessage}")
            }

            currentPlan = currentPlan.copy(steps = updatedSteps)
            _activeTask.value = currentPlan
            onStepUpdated(currentPlan)
        }

        currentPlan = currentPlan.copy(isFinished = true)
        _activeTask.value = currentPlan
        onStepUpdated(currentPlan)

        VoiceCommandResult.Handled("Completed all steps for: ${plan.title}")
    }
}
