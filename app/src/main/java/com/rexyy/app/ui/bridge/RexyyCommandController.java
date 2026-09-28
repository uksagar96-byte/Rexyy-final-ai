package com.rexyy.app.ui.bridge;

/**
 * Interface allowing UI components to dispatch user commands to the Kotlin core.
 */
public interface RexyyCommandController {
    /**
     * Dispatches a text or quick command for processing and execution.
     *
     * @param commandText The command string entered by the user.
     */
    void dispatchCommand(String commandText);

    /**
     * Toggles voice listening (start or cancel voice input).
     */
    void dispatchVoiceToggle();

    /**
     * Cancels any currently active autonomous multi-step workflow.
     */
    void cancelActiveTask();

    /**
     * Confirms a sensitive pending action that requires user consent.
     */
    void confirmPendingAction();

    /**
     * Cancels a sensitive pending action that requires user consent.
     */
    void cancelPendingAction();
}
