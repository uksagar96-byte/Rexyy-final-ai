package com.rexyy.app.launcher

import android.content.Context

class AppDiscoveryManager(private val context: Context) {

    private val repository = InstalledAppRepository(context)

    suspend fun findAndLaunchApp(query: String): AppLaunchOutcome {
        val installed = repository.getInstalledApps()
        val matchResult = AppMatcher.matchApp(query, installed)

        return when (matchResult) {
            is AppMatchResult.Exact -> {
                val launched = AppLauncher.launchApp(context, matchResult.app)
                if (launched) {
                    AppLaunchOutcome.Success(matchResult.app.label, matchResult.app.packageName)
                } else {
                    AppLaunchOutcome.FailedToLaunch(matchResult.app.label)
                }
            }
            is AppMatchResult.Multiple -> {
                AppLaunchOutcome.MultipleMatches(matchResult.matches.map { it.label })
            }
            is AppMatchResult.NotFound -> {
                AppLaunchOutcome.NotInstalled(query)
            }
        }
    }

    suspend fun findApp(query: String): AppInfo? {
        val installed = repository.getInstalledApps()
        return when (val match = AppMatcher.matchApp(query, installed)) {
            is AppMatchResult.Exact -> match.app
            is AppMatchResult.Multiple -> match.matches.firstOrNull()
            is AppMatchResult.NotFound -> null
        }
    }

    suspend fun getInstalledAppList(): List<AppInfo> {
        return repository.getInstalledApps()
    }
}

sealed class AppLaunchOutcome {
    data class Success(val appLabel: String, val packageName: String = "") : AppLaunchOutcome()
    data class MultipleMatches(val candidates: List<String>) : AppLaunchOutcome()
    data class NotInstalled(val appName: String) : AppLaunchOutcome()
    data class FailedToLaunch(val appLabel: String) : AppLaunchOutcome()
}
