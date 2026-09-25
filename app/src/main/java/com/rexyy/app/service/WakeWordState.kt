package com.rexyy.app.service

/**
 * State machine states for REXXY wake-word standby, command listening,
 * and execution lifecycle.
 */
enum class WakeWordState {
    /**
     * Service is active in background, waiting silently for the wake word.
     */
    WAKE_STANDBY,

    /**
     * Wake word ("Hello Rex", "Rexyy", etc.) was detected.
     */
    WAKE_DETECTED,

    /**
     * Listening for full user voice command after wake word.
     */
    COMMAND_LISTENING,

    /**
     * Parsing command text and routing to appropriate handler.
     */
    PROCESSING,

    /**
     * Executing the resolved action (telecom, device, navigation, etc.).
     */
    EXECUTING,

    /**
     * Verifying action execution result.
     */
    VERIFYING,

    /**
     * Providing voice feedback and safely returning back to standby.
     */
    RETURNING_TO_STANDBY,

    /**
     * Error state; triggers bounded exponential backoff recovery.
     */
    ERROR
}

/**
 * Backward compatibility alias for any existing references.
 */
typealias AssistantBackgroundState = WakeWordState
