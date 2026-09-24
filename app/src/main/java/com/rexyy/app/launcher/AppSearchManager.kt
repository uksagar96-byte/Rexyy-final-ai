package com.rexyy.app.launcher

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.rexyy.app.accessibility.AccessibilityActionExecutor
import com.rexyy.app.accessibility.RexyyAccessibilityService
import kotlinx.coroutines.delay
import java.net.URLEncoder

enum class SearchVerificationStatus {
    APP_LAUNCHED,
    SEARCH_STARTED,
    SEARCH_VERIFIED,
    FAILED
}

data class AppSearchResult(
    val targetApp: String,
    val resolvedPackage: String?,
    val extractedQuery: String,
    val launchMethod: String,
    val searchMethod: String,
    val verificationStatus: SearchVerificationStatus,
    val replyText: String,
    val failureReason: String? = null
)

class AppSearchManager(private val context: Context) {

    private val repository = InstalledAppRepository(context)

    suspend fun executeSearch(rawTargetApp: String, rawQuery: String): AppSearchResult {
        val cleanQuery = rawQuery.trim().removeSurrounding("\"").removeSurrounding("'")
        val normApp = rawTargetApp.lowercase().trim()

        return when {
            normApp == "youtube music" || normApp == "yt music" || normApp == "ytmusic" -> executeYouTubeMusicSearch(cleanQuery)
            normApp == "youtube" || normApp == "yt" -> executeYouTubeSearch(cleanQuery)
            normApp == "chrome" || normApp == "google chrome" || normApp == "browser" -> executeChromeSearch(cleanQuery)
            normApp == "maps" || normApp == "google maps" -> executeMapsSearch(cleanQuery)
            normApp == "spotify" -> executeSpotifySearch(cleanQuery)
            normApp == "playstore" || normApp == "play store" || normApp == "google play" -> executePlayStoreSearch(cleanQuery)
            normApp == "instagram" || normApp == "insta" -> executeInstagramSearch(cleanQuery)
            else -> executeGenericAppSearch(rawTargetApp, cleanQuery)
        }
    }

    private suspend fun isPackageInstalled(packageName: String): Boolean {
        return try {
            val installed = repository.getInstalledApps()
            installed.any { it.packageName == packageName } ||
                    context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        }
    }

    private suspend fun executeYouTubeSearch(query: String): AppSearchResult {
        val pkg = "com.google.android.youtube"
        val isInstalled = isPackageInstalled(pkg)
        if (!isInstalled) {
            return AppSearchResult(
                targetApp = "YouTube",
                resolvedPackage = null,
                extractedQuery = query,
                launchMethod = "NONE",
                searchMethod = "NONE",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "YouTube phone me installed nahi hai.",
                failureReason = "YouTube is not installed on device"
            )
        }

        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(pkg)
            putExtra(SearchManager.QUERY, query)
            putExtra("query", query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=$encoded")).apply {
            setPackage(pkg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(uriIntent)
            AppSearchResult(
                targetApp = "YouTube",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "DEEP_LINK",
                searchMethod = "DEEP_LINK_SEARCH",
                verificationStatus = SearchVerificationStatus.SEARCH_VERIFIED,
                replyText = "YouTube par \"$query\" search kiya ja raha hai..."
            )
        } catch (_: Exception) {
            try {
                context.startActivity(searchIntent)
                AppSearchResult(
                    targetApp = "YouTube",
                    resolvedPackage = pkg,
                    extractedQuery = query,
                    launchMethod = "SEARCH_INTENT",
                    searchMethod = "DEEP_LINK_SEARCH",
                    verificationStatus = SearchVerificationStatus.SEARCH_VERIFIED,
                    replyText = "YouTube par \"$query\" search kiya ja raha hai..."
                )
            } catch (e: Exception) {
                AppSearchResult(
                    targetApp = "YouTube",
                    resolvedPackage = pkg,
                    extractedQuery = query,
                    launchMethod = "INTENT",
                    searchMethod = "DEEP_LINK_SEARCH",
                    verificationStatus = SearchVerificationStatus.FAILED,
                    replyText = "YouTube me search perform nahi ho paya.",
                    failureReason = e.localizedMessage
                )
            }
        }
    }

    private suspend fun executeYouTubeMusicSearch(query: String): AppSearchResult {
        val pkg = "com.google.android.apps.youtube.music"
        val isInstalled = isPackageInstalled(pkg)
        if (!isInstalled) {
            return AppSearchResult(
                targetApp = "YouTube Music",
                resolvedPackage = null,
                extractedQuery = query,
                launchMethod = "NONE",
                searchMethod = "NONE",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "YouTube Music phone me installed nahi hai.",
                failureReason = "YouTube Music is not installed on device"
            )
        }

        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            setPackage(pkg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            AppSearchResult(
                targetApp = "YouTube Music",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "MEDIA_INTENT",
                searchMethod = "MEDIA_SEARCH_INTENT",
                verificationStatus = SearchVerificationStatus.SEARCH_VERIFIED,
                replyText = "YouTube Music par \"$query\" search kiya ja raha hai..."
            )
        } catch (e: Exception) {
            AppSearchResult(
                targetApp = "YouTube Music",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "MEDIA_INTENT",
                searchMethod = "MEDIA_SEARCH_INTENT",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "YouTube Music me search perform nahi ho paya.",
                failureReason = e.localizedMessage
            )
        }
    }

    private suspend fun executeChromeSearch(query: String): AppSearchResult {
        val pkg = "com.android.chrome"
        val isInstalled = isPackageInstalled(pkg)
        if (!isInstalled) {
            return AppSearchResult(
                targetApp = "Chrome",
                resolvedPackage = null,
                extractedQuery = query,
                launchMethod = "NONE",
                searchMethod = "NONE",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Chrome phone me installed nahi hai.",
                failureReason = "Chrome is not installed on device"
            )
        }

        val encoded = URLEncoder.encode(query, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
            setPackage(pkg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            AppSearchResult(
                targetApp = "Chrome",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "INTENT",
                searchMethod = "WEB_SEARCH_INTENT",
                verificationStatus = SearchVerificationStatus.SEARCH_VERIFIED,
                replyText = "Chrome par \"$query\" search kiya ja raha hai..."
            )
        } catch (e: Exception) {
            AppSearchResult(
                targetApp = "Chrome",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "INTENT",
                searchMethod = "WEB_SEARCH_INTENT",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Chrome me search perform nahi ho paya.",
                failureReason = e.localizedMessage
            )
        }
    }

    private suspend fun executeMapsSearch(query: String): AppSearchResult {
        val pkg = "com.google.android.apps.maps"
        val isInstalled = isPackageInstalled(pkg)
        if (!isInstalled) {
            return AppSearchResult(
                targetApp = "Maps",
                resolvedPackage = null,
                extractedQuery = query,
                launchMethod = "NONE",
                searchMethod = "NONE",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Google Maps phone me installed nahi hai.",
                failureReason = "Google Maps is not installed on device"
            )
        }

        val encoded = Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
            setPackage(pkg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            AppSearchResult(
                targetApp = "Maps",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "GEO_INTENT",
                searchMethod = "GEO_SEARCH_INTENT",
                verificationStatus = SearchVerificationStatus.SEARCH_VERIFIED,
                replyText = "Google Maps par \"$query\" search kiya ja raha hai..."
            )
        } catch (e: Exception) {
            AppSearchResult(
                targetApp = "Maps",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "GEO_INTENT",
                searchMethod = "GEO_SEARCH_INTENT",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Google Maps me search perform nahi ho paya.",
                failureReason = e.localizedMessage
            )
        }
    }

    private suspend fun executeSpotifySearch(query: String): AppSearchResult {
        val pkg = "com.spotify.music"
        val isInstalled = isPackageInstalled(pkg)
        if (!isInstalled) {
            return AppSearchResult(
                targetApp = "Spotify",
                resolvedPackage = null,
                extractedQuery = query,
                launchMethod = "NONE",
                searchMethod = "NONE",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Spotify phone me installed nahi hai.",
                failureReason = "Spotify is not installed on device"
            )
        }

        val encoded = Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).apply {
            setPackage(pkg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            AppSearchResult(
                targetApp = "Spotify",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "DEEP_LINK",
                searchMethod = "SPOTIFY_SEARCH_URI",
                verificationStatus = SearchVerificationStatus.SEARCH_VERIFIED,
                replyText = "Spotify par \"$query\" search kiya ja raha hai..."
            )
        } catch (e: Exception) {
            AppSearchResult(
                targetApp = "Spotify",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "DEEP_LINK",
                searchMethod = "SPOTIFY_SEARCH_URI",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Spotify me search perform nahi ho paya.",
                failureReason = e.localizedMessage
            )
        }
    }

    private suspend fun executePlayStoreSearch(query: String): AppSearchResult {
        val pkg = "com.android.vending"
        val isInstalled = isPackageInstalled(pkg)
        if (!isInstalled) {
            return AppSearchResult(
                targetApp = "Play Store",
                resolvedPackage = null,
                extractedQuery = query,
                launchMethod = "NONE",
                searchMethod = "NONE",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Play Store phone me installed nahi hai.",
                failureReason = "Play Store is not installed on device"
            )
        }

        val encoded = Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded")).apply {
            setPackage(pkg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            AppSearchResult(
                targetApp = "Play Store",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "MARKET_INTENT",
                searchMethod = "MARKET_SEARCH_URI",
                verificationStatus = SearchVerificationStatus.SEARCH_VERIFIED,
                replyText = "Play Store par \"$query\" search kiya ja raha hai..."
            )
        } catch (e: Exception) {
            AppSearchResult(
                targetApp = "Play Store",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "MARKET_INTENT",
                searchMethod = "MARKET_SEARCH_URI",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Play Store me search perform nahi ho paya.",
                failureReason = e.localizedMessage
            )
        }
    }

    private suspend fun executeInstagramSearch(query: String): AppSearchResult {
        val pkg = "com.instagram.android"
        val isInstalled = isPackageInstalled(pkg)
        if (!isInstalled) {
            return AppSearchResult(
                targetApp = "Instagram",
                resolvedPackage = null,
                extractedQuery = query,
                launchMethod = "NONE",
                searchMethod = "NONE",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Instagram phone me installed nahi hai.",
                failureReason = "Instagram is not installed on device"
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (launchIntent == null) {
            return AppSearchResult(
                targetApp = "Instagram",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "NONE",
                searchMethod = "NONE",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Instagram launch nahi ho paya.",
                failureReason = "Could not obtain launch intent for Instagram"
            )
        }

        return try {
            context.startActivity(launchIntent)

            if (RexyyAccessibilityService.isServiceEnabled()) {
                delay(600)
                val service = RexyyAccessibilityService.getService()
                if (service != null) {
                    val searchTab = service.findNodeByViewId("com.instagram.android:id/search_tab")
                        ?: service.findNodeByText("Search and explore")
                        ?: service.findNodeByText("Search")

                    if (searchTab != null) {
                        service.clickNode(searchTab)
                        delay(400)
                        val inputNode = service.findNodeByViewId("com.instagram.android:id/action_bar_search_edit_text")
                            ?: service.findNodeByText("Search")
                        if (inputNode != null) {
                            service.inputText(inputNode, query)
                            return AppSearchResult(
                                targetApp = "Instagram",
                                resolvedPackage = pkg,
                                extractedQuery = query,
                                launchMethod = "ACCESSIBILITY",
                                searchMethod = "ACCESSIBILITY_SEARCH",
                                verificationStatus = SearchVerificationStatus.SEARCH_VERIFIED,
                                replyText = "Instagram par \"$query\" search kiya ja raha hai..."
                            )
                        } else {
                            return AppSearchResult(
                                targetApp = "Instagram",
                                resolvedPackage = pkg,
                                extractedQuery = query,
                                launchMethod = "APP_LAUNCH",
                                searchMethod = "ACCESSIBILITY_SEARCH",
                                verificationStatus = SearchVerificationStatus.SEARCH_STARTED,
                                replyText = "Instagram open kar diya hai, search field navigate ho raha hai..."
                            )
                        }
                    }
                }

                AppSearchResult(
                    targetApp = "Instagram",
                    resolvedPackage = pkg,
                    extractedQuery = query,
                    launchMethod = "APP_LAUNCH",
                    searchMethod = "ACCESSIBILITY_SEARCH",
                    verificationStatus = SearchVerificationStatus.SEARCH_STARTED,
                    replyText = "Instagram open kiya gaya hai..."
                )
            } else {
                AppSearchResult(
                    targetApp = "Instagram",
                    resolvedPackage = pkg,
                    extractedQuery = query,
                    launchMethod = "APP_LAUNCH",
                    searchMethod = "ACCESSIBILITY_SEARCH",
                    verificationStatus = SearchVerificationStatus.APP_LAUNCHED,
                    replyText = "Instagram open kar diya hai. Automated typing ke liye Accessibility permission zaroori hai.",
                    failureReason = "Accessibility service disabled"
                )
            }
        } catch (e: Exception) {
            AppSearchResult(
                targetApp = "Instagram",
                resolvedPackage = pkg,
                extractedQuery = query,
                launchMethod = "APP_LAUNCH",
                searchMethod = "ACCESSIBILITY_SEARCH",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Instagram me search perform nahi ho paya.",
                failureReason = e.localizedMessage
            )
        }
    }

    private suspend fun executeGenericAppSearch(targetApp: String, query: String): AppSearchResult {
        val installedApps = repository.getInstalledApps()
        val match = AppMatcher.matchApp(targetApp, installedApps)

        return when (match) {
            is AppMatchResult.Exact -> {
                val app = match.app
                val launched = AppLauncher.launchApp(context, app)
                if (!launched) {
                    return AppSearchResult(
                        targetApp = app.label,
                        resolvedPackage = app.packageName,
                        extractedQuery = query,
                        launchMethod = "APP_LAUNCH",
                        searchMethod = "ACCESSIBILITY_SEARCH",
                        verificationStatus = SearchVerificationStatus.FAILED,
                        replyText = "${app.label} open nahi ho paya.",
                        failureReason = "Failed to launch ${app.packageName}"
                    )
                }

                if (RexyyAccessibilityService.isServiceEnabled()) {
                    delay(500)
                    val inputSuccess = AccessibilityActionExecutor.tryClickSearchOrInput(query)
                    if (inputSuccess) {
                        AppSearchResult(
                            targetApp = app.label,
                            resolvedPackage = app.packageName,
                            extractedQuery = query,
                            launchMethod = "ACCESSIBILITY",
                            searchMethod = "ACCESSIBILITY_SEARCH",
                            verificationStatus = SearchVerificationStatus.SEARCH_VERIFIED,
                            replyText = "${app.label} par \"$query\" search kiya ja raha hai..."
                        )
                    } else {
                        AppSearchResult(
                            targetApp = app.label,
                            resolvedPackage = app.packageName,
                            extractedQuery = query,
                            launchMethod = "APP_LAUNCH",
                            searchMethod = "ACCESSIBILITY_SEARCH",
                            verificationStatus = SearchVerificationStatus.SEARCH_STARTED,
                            replyText = "${app.label} khol diya hai, search input dhundha ja raha hai..."
                        )
                    }
                } else {
                    AppSearchResult(
                        targetApp = app.label,
                        resolvedPackage = app.packageName,
                        extractedQuery = query,
                        launchMethod = "APP_LAUNCH",
                        searchMethod = "ACCESSIBILITY_SEARCH",
                        verificationStatus = SearchVerificationStatus.APP_LAUNCHED,
                        replyText = "${app.label} khol diya hai. In-app search automation ke liye Accessibility permission enable karein.",
                        failureReason = "Accessibility service disabled"
                    )
                }
            }
            is AppMatchResult.Multiple -> {
                AppSearchResult(
                    targetApp = targetApp,
                    resolvedPackage = null,
                    extractedQuery = query,
                    launchMethod = "NONE",
                    searchMethod = "NONE",
                    verificationStatus = SearchVerificationStatus.FAILED,
                    replyText = "Multiple apps mile (${match.matches.joinToString { it.label }}). Kaunsa app use karna hai?",
                    failureReason = "Ambiguous target app: multiple matches"
                )
            }
            is AppMatchResult.NotFound -> {
                AppSearchResult(
                    targetApp = targetApp,
                    resolvedPackage = null,
                    extractedQuery = query,
                    launchMethod = "NONE",
                    searchMethod = "NONE",
                    verificationStatus = SearchVerificationStatus.FAILED,
                    replyText = "$targetApp phone me installed nahi hai.",
                    failureReason = "$targetApp is not installed on device"
                )
            }
        }
    }
}
