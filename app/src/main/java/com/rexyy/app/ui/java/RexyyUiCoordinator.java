package com.rexyy.app.ui.java;

import android.app.Activity;
import android.content.Context;
import com.rexyy.app.ui.bridge.RexyyCoreController;
import com.rexyy.app.ui.bridge.RexyyUiStateListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Java UI Coordinator managing the lifecycle observation, status bar styling,
 * overlay visibility transitions, and UI presentation bridging.
 */
public class RexyyUiCoordinator {

    private final RexyyCoreController coreController;
    private final List<RexyyUiStateListener> stateListeners = new ArrayList<>();
    private WeakReference<Activity> activeActivityRef;

    public RexyyUiCoordinator(RexyyCoreController coreController) {
        if (coreController == null) {
            throw new IllegalArgumentException("coreController cannot be null");
        }
        this.coreController = coreController;
    }

    /**
     * Called from Activity.onCreate().
     */
    public void onActivityCreate(Activity activity) {
        this.activeActivityRef = new WeakReference<>(activity);
        RexyyStatusBarHelper.applyDarkSystemBars(activity);

        // Auto-start background assistant service if Always Ready is enabled and permission is granted
        com.rexyy.app.data.local.SecureStorage storage = new com.rexyy.app.data.local.SecureStorage(activity);
        if (storage.isAlwaysReadyEnabled() &&
                coreController.getPermissionController().hasRecordAudioPermission(activity) &&
                !coreController.isAssistantRunning()) {
            com.rexyy.app.service.BackgroundAssistantManager.INSTANCE.startAlwaysReady(activity);
        }
    }

    /**
     * Called from Activity.onStart().
     */
    public void onActivityStart(Activity activity) {
        this.activeActivityRef = new WeakReference<>(activity);

        // While REXXY is in the foreground, hide system window overlay to avoid double pill
        coreController.dismissOverlay();

        // Ensure background assistant service is active if Always Ready is enabled
        com.rexyy.app.data.local.SecureStorage storage = new com.rexyy.app.data.local.SecureStorage(activity);
        if (storage.isAlwaysReadyEnabled() &&
                coreController.getPermissionController().hasRecordAudioPermission(activity) &&
                !coreController.isAssistantRunning()) {
            com.rexyy.app.service.BackgroundAssistantManager.INSTANCE.startAlwaysReady(activity);
        }
    }

    /**
     * Called from Activity.onResume().
     */
    public void onActivityResume(Activity activity) {
        this.activeActivityRef = new WeakReference<>(activity);
        com.rexyy.app.data.local.SecureStorage storage = new com.rexyy.app.data.local.SecureStorage(activity);
        if (storage.isAlwaysReadyEnabled() &&
                coreController.getPermissionController().hasRecordAudioPermission(activity) &&
                !coreController.isAssistantRunning()) {
            com.rexyy.app.service.BackgroundAssistantManager.INSTANCE.startAlwaysReady(activity);
        }
    }

    /**
     * Called from Activity.onStop().
     * Attaches system overlay if assistant is running and permission is granted.
     */
    public void onActivityStop(Context applicationContext) {
        if (coreController.isAssistantRunning() &&
                coreController.getPermissionController().hasOverlayPermission(applicationContext)) {
            coreController.requestOverlay(applicationContext);
        }
    }

    /**
     * Called from Activity.onDestroy().
     * Cleans up activity references to avoid memory leaks.
     */
    public void onActivityDestroy() {
        if (activeActivityRef != null) {
            activeActivityRef.clear();
            activeActivityRef = null;
        }
    }

    /**
     * Returns the current UI presentation model.
     */
    public RexyyUiPresentationModel getPresentationModel() {
        String state = coreController.getUiStateProvider().getAssistantStateName();
        String lastCmd = coreController.getUiStateProvider().getLastSpokenCommand();
        String feedback = coreController.getUiStateProvider().getLastExecutionFeedback();
        boolean running = coreController.isAssistantRunning();

        return RexyyUiPresentationModel.fromState(state, lastCmd, feedback, running);
    }

    public RexyyCoreController getCoreController() {
        return coreController;
    }
}
