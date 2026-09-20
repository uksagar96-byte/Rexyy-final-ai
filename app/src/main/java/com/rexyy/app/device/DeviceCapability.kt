package com.rexyy.app.device

enum class CapabilityStatus {
    AVAILABLE,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    ROOT_REQUIRED,
    ACCESSIBILITY_REQUIRED
}

data class DeviceCapability(
    val id: String,
    val name: String,
    val description: String,
    val status: CapabilityStatus,
    val requiresRoot: Boolean = false,
    val requiresPermission: Boolean = false,
    val requiredPermissionName: String? = null,
    val requiresAccessibility: Boolean = false,
    val requiresConfirmation: Boolean = false
)
