package com.rexyy.app.ui.bridge;

/**
 * Listener interface for receiving UI state change events from the REXXY core.
 * Implemented in Java to allow seamless Java UI component observation.
 */
public interface RexyyUiStateListener {
    /**
     * Called when the assistant state changes.
     *
     * @param stateName The human-readable name of the assistant state (e.g., "IDLE", "LISTENING", "PROCESSING").
     * @param lastCommand The last recognized or typed user command.
     * @param feedback The latest execution feedback or response text.
     */
    void onStateChanged(String stateName, String lastCommand, String feedback);

    /**
     * Called when the background service running status changes.
     *
     * @param isRunning True if the background assistant service is active.
     */
    void onServiceRunningChanged(boolean isRunning);
}
