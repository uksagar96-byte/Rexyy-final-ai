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
                    AppLaunchOutcome.Success(matchResult.app.label)
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

    suspend fun getInstalledAppList(): List<AppInfo> {
        return repository.getInstalledApps()
    }
}

sealed class AppLaunchOutcome {
    data class Success(val appLabel: String) : AppLaunchOutcome()
    data class MultipleMatches(val candidates: List<String>) : AppLaunchOutcome()
    data class NotInstalled(val appName: String) : AppLaunchOutcome()
    data class FailedToLaunch(val appLabel: String) : AppLaunchOutcome()
}
