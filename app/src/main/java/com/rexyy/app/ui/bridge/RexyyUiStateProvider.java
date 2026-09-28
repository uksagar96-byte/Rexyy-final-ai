package com.rexyy.app.ui.bridge;

/**
 * Interface providing UI state data to the Java UI layer.
 */
public interface RexyyUiStateProvider {
    /**
     * Returns the current assistant state name.
     */
    String getAssistantStateName();

    /**
     * Returns the last spoken or recognized command text.
     */
    String getLastSpokenCommand();

    /**
     * Returns the last execution feedback or response text.
     */
    String getLastExecutionFeedback();

    /**
     * Returns whether REXXY activation cinematic has been completed.
     */
    boolean isRexyyActivated();

    /**
     * Registers a state listener.
     */
    void addStateListener(RexyyUiStateListener listener);

    /**
     * Unregisters a state listener.
     */
    void removeStateListener(RexyyUiStateListener listener);
}
