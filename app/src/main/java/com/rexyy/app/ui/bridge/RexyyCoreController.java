package com.rexyy.app.ui.bridge;

import android.content.Context;

/**
 * Authoritative Core Controller interface bridging the Java UI layer with the Kotlin Android Core.
 */
public interface RexyyCoreController {
    /**
     * Starts the background assistant foreground service if permissions are granted.
     *
     * @param context Android context.
     * @return True if start command was issued.
     */
    boolean startAssistant(Context context);

    /**
     * Stops the background assistant service.
     *
     * @param context Android context.
     */
    void stopAssistant(Context context);

    /**
     * Returns true if the background assistant is actively running.
     */
    boolean isAssistantRunning();

    /**
     * Executes a user command via the core router and executor.
     *
     * @param command Text command.
     * @param isVoice True if command was received via voice recognition.
     */
    void executeCommand(String command, boolean isVoice);

    /**
     * Marks REXXY as activated in local secure storage.
     */
    void activateRexyy();

    /**
     * Shows the floating Dynamic Pill overlay if permissions allow.
     *
     * @param context Android context.
     */
    void requestOverlay(Context context);

    /**
     * Dismisses the floating Dynamic Pill overlay window.
     */
    void dismissOverlay();

    /**
     * Returns the UI state provider.
     */
    RexyyUiStateProvider getUiStateProvider();

    /**
     * Returns the command controller.
     */
    RexyyCommandController getCommandController();

    /**
     * Returns the permission controller.
     */
    RexyyPermissionController getPermissionController();

    /**
     * Returns the background state provider.
     */
    RexyyBackgroundStateProvider getBackgroundStateProvider();
}
