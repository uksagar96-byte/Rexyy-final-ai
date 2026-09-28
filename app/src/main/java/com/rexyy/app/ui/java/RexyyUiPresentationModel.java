package com.rexyy.app.ui.java;

/**
 * Strongly-typed Java presentation model for REXXY UI status badges, cards, and labels.
 */
public class RexyyUiPresentationModel {

    private final String title;
    private final String subtitle;
    private final String statusBadgeText;
    private final long primaryColorHex;
    private final boolean isWorking;

    public RexyyUiPresentationModel(
            String title,
            String subtitle,
            String statusBadgeText,
            long primaryColorHex,
            boolean isWorking
    ) {
        this.title = title != null ? title : "";
        this.subtitle = subtitle != null ? subtitle : "";
        this.statusBadgeText = statusBadgeText != null ? statusBadgeText : "IDLE";
        this.primaryColorHex = primaryColorHex;
        this.isWorking = isWorking;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getStatusBadgeText() {
        return statusBadgeText;
    }

    public long getPrimaryColorHex() {
        return primaryColorHex;
    }

    public boolean isWorking() {
        return isWorking;
    }

    /**
     * Factory method creating a presentation model from assistant state and background metrics.
     */
    public static RexyyUiPresentationModel fromState(
            String stateName,
            String lastCommand,
            String feedback,
            boolean isServiceRunning
    ) {
        if ("LISTENING".equalsIgnoreCase(stateName)) {
            return new RexyyUiPresentationModel(
                    "Listening...",
                    "Speak your command now",
                    "LISTENING",
                    0xFF00E5FF, // Cyan
                    true
            );
        } else if ("PROCESSING".equalsIgnoreCase(stateName)) {
            return new RexyyUiPresentationModel(
                    "Processing",
                    lastCommand != null && !lastCommand.isEmpty() ? lastCommand : "Analyzing command...",
                    "WORKING",
                    0xFFB388FF, // Purple
                    true
            );
        } else if ("SPEAKING".equalsIgnoreCase(stateName)) {
            return new RexyyUiPresentationModel(
                    "Speaking",
                    feedback != null && !feedback.isEmpty() ? feedback : "Responding...",
                    "TRANSMITTING",
                    0xFF00E676, // Neon Green
                    true
            );
        } else if ("ERROR".equalsIgnoreCase(stateName)) {
            return new RexyyUiPresentationModel(
                    "Attention Required",
                    feedback != null && !feedback.isEmpty() ? feedback : "An issue occurred.",
                    "ERROR",
                    0xFFFF5252, // Red
                    false
            );
        } else {
            String sub = isServiceRunning ? "Background wake armed (Say \"Hello REXXY\")" : "Assistant idle. Tap microphone to speak.";
            return new RexyyUiPresentationModel(
                    "REXXY Ready",
                    sub,
                    isServiceRunning ? "ARMED" : "STANDBY",
                    0xFF00E5FF,
                    false
            );
        }
    }
}
