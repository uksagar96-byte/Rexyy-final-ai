package com.rexyy.app.task

import com.rexyy.app.voice.VoiceCommand

enum class TaskStepStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class TaskStep(
    val id: String,
    val description: String,
    val command: VoiceCommand,
    var status: TaskStepStatus = TaskStepStatus.QUEUED,
    var resultMessage: String? = null
)

data class TaskPlan(
    val id: String,
    val title: String,
    val steps: List<TaskStep>,
    val isFinished: Boolean = false,
    val isCancelled: Boolean = false,
    val currentStepIndex: Int = 0
)
