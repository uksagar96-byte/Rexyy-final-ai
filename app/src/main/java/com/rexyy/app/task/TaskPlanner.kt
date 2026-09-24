package com.rexyy.app.task

import com.rexyy.app.voice.VoiceCommand
import java.util.UUID

object TaskPlanner {

    /**
     * Checks if an input sentence is a multi-step command and breaks it into sequential TaskSteps.
     */
    fun planTask(input: String, parseSingleCommand: (String) -> VoiceCommand): TaskPlan? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // Multi-step conjunctions: "and", "aur", "aur phir", "then", "phir", commas, semicolons
        val splitRegex = Regex("(?i)(\\s+(and then|aur phir|and|aur|then|phir)\\s+|[,;]\\s*)")
        val parts = trimmed.split(splitRegex).map { it.trim() }.filter { it.isNotBlank() }

        if (parts.size < 2) {
            // Check specific compound patterns like "Open YouTube search cricket"
            val youtubeCompound = Regex("(?i)^(open youtube|youtube kholo)\\s+(search|dhundo)\\s+(.+)$")
            val match = youtubeCompound.find(trimmed)
            if (match != null) {
                val query = match.groupValues[3].trim()
                val step1 = TaskStep(
                    id = "step_1",
                    description = "Opening YouTube...",
                    command = VoiceCommand.OpenApp("YouTube", "Open YouTube")
                )
                val step2 = TaskStep(
                    id = "step_2",
                    description = "Searching for \"$query\"...",
                    command = VoiceCommand.GoogleSearch("YouTube $query", "Search $query")
                )
                return TaskPlan(
                    id = UUID.randomUUID().toString(),
                    title = "YouTube Search: $query",
                    steps = listOf(step1, step2)
                )
            }
            return null
        }

        // Limit to max 8 steps for safety
        val validSteps = mutableListOf<TaskStep>()
        for ((index, rawPart) in parts.take(8).withIndex()) {
            val part = rawPart.replace("^(?i)(and then|aur phir|and|aur|then|phir)\\s+".toRegex(), "").trim()
            if (part.isBlank()) continue
            val cmd = parseSingleCommand(part)
            if (cmd !is VoiceCommand.AiChat) {
                validSteps.add(
                    TaskStep(
                        id = "step_${index + 1}",
                        description = part,
                        command = cmd
                    )
                )
            } else {
                // If part is e.g. "search cricket", convert to GoogleSearch
                if (part.lowercase().startsWith("search ") || part.lowercase().contains("search karo")) {
                    val query = part.replace("(?i)(search|karo)".toRegex(), "").trim()
                    validSteps.add(
                        TaskStep(
                            id = "step_${index + 1}",
                            description = "Searching $query",
                            command = VoiceCommand.GoogleSearch(query, part)
                        )
                    )
                }
            }
        }

        if (validSteps.size >= 2) {
            return TaskPlan(
                id = UUID.randomUUID().toString(),
                title = trimmed,
                steps = validSteps
            )
        }

        return null
    }
}
