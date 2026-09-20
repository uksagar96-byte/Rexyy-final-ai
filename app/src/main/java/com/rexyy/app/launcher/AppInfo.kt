package com.rexyy.app.launcher

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String?,
    val aliases: List<String> = emptyList()
)

sealed class AppMatchResult {
    data class Exact(val app: AppInfo) : AppMatchResult()
    data class Multiple(val matches: List<AppInfo>, val query: String) : AppMatchResult()
    data class NotFound(val query: String) : AppMatchResult()
}
