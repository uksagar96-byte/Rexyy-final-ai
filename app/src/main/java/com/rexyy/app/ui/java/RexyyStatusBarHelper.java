package com.rexyy.app.ui.java;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

/**
 * Java UI helper for configuring system bar styling, dark theme status bar contrast,
 * and edge-to-edge window configurations.
 */
public final class RexyyStatusBarHelper {

    private RexyyStatusBarHelper() {
        // Utility class
    }

    /**
     * Applies dark theme system bar colors to the given Activity.
     *
     * @param activity The target activity.
     */
    public static void applyDarkSystemBars(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;

        // Dark background color matching RexyyDarkBackground (0xFF0A0C10)
        int darkBg = Color.rgb(10, 12, 16);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                // Ensure icons are light/white on dark background
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            // Clear light flags to keep icons white/light
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(flags);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(darkBg);
        }
    }
}
