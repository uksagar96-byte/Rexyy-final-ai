package com.rexyy.app.launcher

object AppMatcher {

    /**
     * Matches a searched application name against the device's installed apps.
     * Prevents unintended substitutions (e.g. YouTube vs YouTube Music).
     */
    fun matchApp(query: String, installedApps: List<AppInfo>): AppMatchResult {
        val cleanQuery = query.lowercase().trim()
            .replace("app", "")
            .trim()

        if (cleanQuery.isBlank()) return AppMatchResult.NotFound(query)

        // 0. High-priority deterministic package matching for major applications
        val deterministicMatch = when (cleanQuery) {
            "youtube", "yt", "you tube" -> {
                installedApps.firstOrNull { it.packageName == "com.google.android.youtube" }
                    ?: installedApps.firstOrNull { it.label.equals("YouTube", ignoreCase = true) }
            }
            "youtube music", "yt music", "ytmusic" -> {
                installedApps.firstOrNull { it.packageName == "com.google.android.apps.youtube.music" }
                    ?: installedApps.firstOrNull { it.label.contains("YouTube Music", ignoreCase = true) }
            }
            "whatsapp", "wa", "whats app" -> {
                installedApps.firstOrNull { it.packageName == "com.whatsapp" }
            }
            "instagram", "insta", "ig" -> {
                installedApps.firstOrNull { it.packageName == "com.instagram.android" }
            }
            "chrome", "google chrome" -> {
                installedApps.firstOrNull { it.packageName == "com.android.chrome" }
            }
            "spotify" -> {
                installedApps.firstOrNull { it.packageName == "com.spotify.music" }
            }
            "maps", "google maps" -> {
                installedApps.firstOrNull { it.packageName == "com.google.android.apps.maps" }
            }
            else -> null
        }
        if (deterministicMatch != null) {
            return AppMatchResult.Exact(deterministicMatch)
        }

        // 1. Exact alias match
        val exactAliasMatch = installedApps.firstOrNull { app ->
            app.aliases.any { it.equals(cleanQuery, ignoreCase = true) }
        }
        if (exactAliasMatch != null) {
            return AppMatchResult.Exact(exactAliasMatch)
        }

        // 2. Exact label match
        val exactLabelMatch = installedApps.firstOrNull {
            it.label.equals(cleanQuery, ignoreCase = true)
        }
        if (exactLabelMatch != null) {
            return AppMatchResult.Exact(exactLabelMatch)
        }

        // 3. Package name direct match (e.g. "com.instagram.android")
        val pkgMatch = installedApps.firstOrNull {
            it.packageName.equals(cleanQuery, ignoreCase = true) ||
                    it.packageName.endsWith(".$cleanQuery", ignoreCase = true)
        }
        if (pkgMatch != null) {
            return AppMatchResult.Exact(pkgMatch)
        }

        // 4. Prefix or substring matching with disambiguation
        val partialMatches = installedApps.filter { app ->
            app.label.lowercase().startsWith(cleanQuery) ||
                    app.aliases.any { it.startsWith(cleanQuery) || cleanQuery.startsWith(it) } ||
                    app.label.lowercase().contains(cleanQuery)
        }

        return when {
            partialMatches.size == 1 -> AppMatchResult.Exact(partialMatches.first())
            partialMatches.size > 1 -> {
                // If one of the matches starts exactly with cleanQuery or has an exact token match
                val bestMatch = partialMatches.firstOrNull {
                    it.label.lowercase() == cleanQuery || it.aliases.contains(cleanQuery)
                }
                if (bestMatch != null) {
                    AppMatchResult.Exact(bestMatch)
                } else {
                    AppMatchResult.Multiple(partialMatches.take(3), query)
                }
            }
            else -> AppMatchResult.NotFound(query)
        }
    }
}
