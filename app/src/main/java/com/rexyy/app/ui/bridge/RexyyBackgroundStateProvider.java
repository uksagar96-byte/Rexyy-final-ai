package com.rexyy.app.ui.bridge;

/**
 * Interface providing background assistant service diagnostics and status to the UI.
 */
public interface RexyyBackgroundStateProvider {
    /**
     * Returns true if the background assistant foreground service is active.
     */
    boolean isServiceRunning();

    /**
     * Returns the name of the current wake word or listening state.
     */
    String getCurrentListeningState();

    /**
     * Returns the number of currently active SpeechRecognizer instances.
     */
    int getActiveRecognizersCount();

    /**
     * Returns the number of consecutive recovery attempts.
     */
    int getRecoveryAttempts();

    /**
     * Returns the latest diagnostic error description, if any.
     */
    String getLastErrorDescription();
}
