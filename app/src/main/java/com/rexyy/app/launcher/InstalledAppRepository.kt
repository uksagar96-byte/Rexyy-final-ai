package com.rexyy.app.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppRepository(private val context: Context) {

    private var cachedApps: List<AppInfo>? = null
    private var lastCacheTime: Long = 0L
    private val cacheTtlMs: Long = 60_000L // 1 minute TTL

    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedApps != null && (now - lastCacheTime) < cacheTtlMs) {
            return@withContext cachedApps!!
        }

        val apps = mutableListOf<AppInfo>()
        val pm = context.packageManager

        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedActivities = pm.queryIntentActivities(mainIntent, 0)

            for (resolveInfo in resolvedActivities) {
                val pkgName = resolveInfo.activityInfo?.packageName ?: continue
                val actName = resolveInfo.activityInfo?.name
                val label = try {
                    resolveInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkgName
                }

                if (label.isNotBlank()) {
                    val aliases = generateAliases(label, pkgName)
                    apps.add(AppInfo(label = label, packageName = pkgName, activityName = actName, aliases = aliases))
                }
            }
        } catch (_: Exception) {
            // Fallback for restricted query environments
            try {
                val installed = pm.getInstalledApplications(0)
                for (app in installed) {
                    val label = try {
                        pm.getApplicationLabel(app).toString()
                    } catch (_: Exception) {
                        app.packageName
                    }
                    if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                        val aliases = generateAliases(label, app.packageName)
                        apps.add(AppInfo(label = label, packageName = app.packageName, activityName = null, aliases = aliases))
                    }
                }
            } catch (_: Exception) {}
        }

        // Deduplicate by package name
        val distinctApps = apps.distinctBy { it.packageName }
        cachedApps = distinctApps
        lastCacheTime = now
        distinctApps
    }

    private fun generateAliases(label: String, packageName: String): List<String> {
        val aliases = mutableListOf<String>()
        val lowerLabel = label.lowercase().trim()
        val lowerPkg = packageName.lowercase()

        aliases.add(lowerLabel)
        // Split multi-word labels (e.g. "Google Chrome" -> "chrome", "google chrome")
        val words = lowerLabel.split(" ", "-", "_").filter { it.isNotBlank() }
        if (words.size > 1) {
            aliases.addAll(words)
        }

        // Well-known app aliases
        when {
            lowerPkg.contains("youtube") || lowerLabel.contains("youtube") -> {
                aliases.addAll(listOf("yt", "youtube", "you tube", "video"))
            }
            lowerPkg.contains("whatsapp") || lowerLabel.contains("whatsapp") -> {
                aliases.addAll(listOf("wa", "whatsapp", "whats app", "watsp", "watsapp"))
            }
            lowerPkg.contains("instagram") || lowerLabel.contains("instagram") -> {
                aliases.addAll(listOf("insta", "instagram", "ig", "reels"))
            }
            lowerPkg.contains("chrome") || lowerLabel.contains("chrome") -> {
                aliases.addAll(listOf("chrome", "google chrome", "browser", "internet"))
            }
            lowerPkg.contains("spotify") || lowerLabel.contains("spotify") -> {
                aliases.addAll(listOf("spotify", "songs", "music"))
            }
            lowerPkg.contains("camera") || lowerLabel.contains("camera") -> {
                aliases.addAll(listOf("camera", "cam", "photo", "kodak"))
            }
            lowerPkg.contains("settings") || lowerLabel.contains("settings") -> {
                aliases.addAll(listOf("settings", "setting", "phone settings"))
            }
            lowerPkg.contains("contacts") || lowerLabel.contains("contacts") -> {
                aliases.addAll(listOf("contacts", "contact", "phonebook"))
            }
            lowerPkg.contains("dialer") || lowerPkg.contains("phone") || lowerLabel.contains("phone") -> {
                aliases.addAll(listOf("phone", "dialer", "call", "caller"))
            }
            lowerPkg.contains("calculator") || lowerLabel.contains("calculator") -> {
                aliases.addAll(listOf("calculator", "calc", "hisab"))
            }
            lowerPkg.contains("clock") || lowerLabel.contains("clock") -> {
                aliases.addAll(listOf("clock", "alarm", "ghadi"))
            }
            lowerPkg.contains("calendar") || lowerLabel.contains("calendar") -> {
                aliases.addAll(listOf("calendar", "calender", "date"))
            }
            lowerPkg.contains("gallery") || lowerPkg.contains("photos") || lowerLabel.contains("photos") -> {
                aliases.addAll(listOf("gallery", "photos", "photo gallery", "images"))
            }
            lowerPkg.contains("maps") || lowerLabel.contains("maps") -> {
                aliases.addAll(listOf("maps", "google maps", "map", "naksha", "navigation"))
            }
            lowerPkg.contains("telegram") || lowerLabel.contains("telegram") -> {
                aliases.addAll(listOf("telegram", "tg"))
            }
            lowerPkg.contains("gmail") || lowerLabel.contains("gmail") -> {
                aliases.addAll(listOf("gmail", "email", "mail"))
            }
        }

        return aliases.distinct()
    }
}
