package com.rexyy.app.ui.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object SetupGuide : Screen("setup_guide")
    object SetupWizard : Screen("setup_wizard")
    object MainAssistant : Screen("main_assistant")
    object Chat : Screen("chat_main")
    object Settings : Screen("settings")
    object PermissionCenter : Screen("permission_center")
    object ControlCenter : Screen("control_center")
    object DevConsole : Screen("dev_console")

    // Retained for backward compatibility
    object Setup : Screen("api_key_setup")
}
