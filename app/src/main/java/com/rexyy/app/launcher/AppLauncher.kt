package com.rexyy.app.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object AppLauncher {

    var testLaunchOverride: ((String) -> Boolean)? = null

    fun launchApp(context: Context, app: AppInfo): Boolean {
        testLaunchOverride?.let { return it(app.packageName) }
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(app.packageName) ?: run {
            if (app.activityName != null) {
                Intent(Intent.ACTION_MAIN).apply {
                    setClassName(app.packageName, app.activityName)
                }
            } else null
        }

        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
    }

    fun launchPackage(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openInPlayStoreOrWeb(context: Context, appName: String) {
        val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + URLEncoder.encode(appName, "UTF-8"))).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(searchIntent)
        } catch (_: Exception) {
            val webSearch = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(appName, "UTF-8"))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webSearch)
        }
    }
}
