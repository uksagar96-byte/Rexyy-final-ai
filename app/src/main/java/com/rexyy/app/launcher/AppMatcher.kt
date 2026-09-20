package com.rexyy.app.launcher

object AppMatcher {

    /**
     * Matches a searched application name against the device's installed apps.
     */
    fun matchApp(query: String, installedApps: List<AppInfo>): AppMatchResult {
        val cleanQuery = query.lowercase().trim()
            .replace("app", "")
            .trim()

        if (cleanQuery.isBlank()) return AppMatchResult.NotFound(query)

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

        // 4. Prefix or substring matching
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
