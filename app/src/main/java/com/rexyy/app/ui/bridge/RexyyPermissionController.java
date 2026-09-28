package com.rexyy.app.ui.bridge;

import android.app.Activity;
import android.content.Context;

/**
 * Controller interface handling Android permission queries and intent redirects.
 */
public interface RexyyPermissionController {
    /**
     * Checks if RECORD_AUDIO permission is granted.
     */
    boolean hasRecordAudioPermission(Context context);

    /**
     * Checks if SYSTEM_ALERT_WINDOW (display over other apps) permission is granted.
     */
    boolean hasOverlayPermission(Context context);

    /**
     * Checks if AccessibilityService is active.
     */
    boolean hasAccessibilityPermission(Context context);

    /**
     * Checks if Notification Listener Service access is granted.
     */
    boolean hasNotificationAccess(Context context);

    /**
     * Opens system settings to grant Overlay permission.
     */
    void openOverlaySettings(Context context);

    /**
     * Opens system settings to grant Accessibility permission.
     */
    void openAccessibilitySettings(Context context);

    /**
     * Opens system settings to grant Notification Access.
     */
    void openNotificationSettings(Context context);
}
