package com.rexyy.app.device

enum class CapabilityStatus {
    AVAILABLE,          // Granted / Enabled
    PERMISSION_REQUIRED,// Not Granted / Disabled
    NEEDS_SETUP,        // Needs Setup (system setting / service)
    UNAVAILABLE,
    ROOT_REQUIRED,
    ACCESSIBILITY_REQUIRED
}

data class DeviceCapability(
    val id: String,
    val name: String,
    val category: String = "Core",
    val description: String,
    val rationale: String,
    val status: CapabilityStatus,
    val isGranted: Boolean,
    val statusLabel: String,
    val isRequiredForCore: Boolean = false,
    val requiresPermission: Boolean = false,
    val requiredPermissionName: String? = null,
    val requiresAccessibility: Boolean = false,
    val requiresNotificationAccess: Boolean = false,
    val requiresWriteSettings: Boolean = false,
    val requiresBatteryOptimization: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val requiresRoot: Boolean = false
)
