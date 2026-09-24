package com.rexyy.app.launcher

object AppMatcher {

    /**
     * Matches a searched application name against the device's installed apps.
     * Enforces strict app identity: prevents unintended substitutions (e.g. YouTube vs YouTube Music).
     */
    fun matchApp(query: String, installedApps: List<AppInfo>): AppMatchResult {
        val cleanQuery = query.lowercase().trim()
            .replace(Regex("(?i)\\b(app|application)\\b"), "")
            .trim()

        if (cleanQuery.isBlank()) return AppMatchResult.NotFound(query)

        // 0. Strict deterministic resolution for core applications.
        // If a specific core app is requested, it MUST resolve to that exact app or NotFound.
        // It must NEVER fall through to partial matching or substitute another app.
        when (cleanQuery) {
            "youtube music", "yt music", "ytmusic" -> {
                val match = installedApps.firstOrNull { 
                    it.packageName == "com.google.android.apps.youtube.music" ||
                            it.label.contains("YouTube Music", ignoreCase = true) ||
                            it.label.contains("YT Music", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "youtube", "yt", "you tube" -> {
                val match = installedApps.firstOrNull {
                    (it.packageName == "com.google.android.youtube" || it.label.equals("YouTube", ignoreCase = true)) &&
                            !it.label.contains("Music", ignoreCase = true) &&
                            !it.label.contains("Kids", ignoreCase = true) &&
                            !it.label.contains("Studio", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "instagram", "insta", "ig" -> {
                val match = installedApps.firstOrNull {
                    it.packageName == "com.instagram.android" || it.label.equals("Instagram", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "whatsapp", "wa", "whats app" -> {
                val match = installedApps.firstOrNull {
                    it.packageName == "com.whatsapp" || it.packageName == "com.whatsapp.w4b" || it.label.equals("WhatsApp", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "chrome", "google chrome" -> {
                val match = installedApps.firstOrNull {
                    it.packageName == "com.android.chrome" || it.label.equals("Chrome", ignoreCase = true) || it.label.equals("Google Chrome", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "gmail", "email" -> {
                val match = installedApps.firstOrNull {
                    it.packageName == "com.google.android.gm" || it.label.equals("Gmail", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "spotify" -> {
                val match = installedApps.firstOrNull {
                    it.packageName == "com.spotify.music" || it.label.equals("Spotify", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "maps", "google maps" -> {
                val match = installedApps.firstOrNull {
                    it.packageName == "com.google.android.apps.maps" || it.label.equals("Maps", ignoreCase = true) || it.label.equals("Google Maps", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "camera" -> {
                val match = installedApps.firstOrNull {
                    it.packageName.contains("camera") || it.label.equals("Camera", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "contacts", "phonebook" -> {
                val match = installedApps.firstOrNull {
                    it.packageName.contains("contacts") || it.label.equals("Contacts", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "settings", "phone settings" -> {
                val match = installedApps.firstOrNull {
                    it.packageName == "com.android.settings" || it.label.equals("Settings", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
            "play store", "google play", "playstore" -> {
                val match = installedApps.firstOrNull {
                    it.packageName == "com.android.vending" || it.label.contains("Play Store", ignoreCase = true)
                }
                return if (match != null) AppMatchResult.Exact(match) else AppMatchResult.NotFound(query)
            }
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
