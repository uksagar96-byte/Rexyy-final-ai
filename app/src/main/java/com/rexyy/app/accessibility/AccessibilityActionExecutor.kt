package com.rexyy.app.accessibility

import kotlinx.coroutines.delay

object AccessibilityActionExecutor {

    suspend fun tryClickSearchOrInput(query: String): Boolean {
        val service = RexyyAccessibilityService.getService() ?: return false

        // Look for common search icons or search input fields
        val searchKeywords = listOf("Search", "Search YouTube", "Search or type URL", "Search contacts", "Search Google")
        for (keyword in searchKeywords) {
            val node = service.findNodeByText(keyword)
            if (node != null) {
                service.clickNode(node)
                delay(300)
                service.inputText(node, query)
                return true
            }
        }
        return false
    }

    fun scroll(down: Boolean): Boolean {
        val service = RexyyAccessibilityService.getService() ?: return false
        return if (down) service.scrollForward() else service.scrollBackward()
    }

    fun pressBack(): Boolean {
        return RexyyAccessibilityService.getService()?.pressBack() ?: false
    }

    fun pressHome(): Boolean {
        return RexyyAccessibilityService.getService()?.pressHome() ?: false
    }

    fun pressRecentApps(): Boolean {
        return RexyyAccessibilityService.getService()?.pressRecentApps() ?: false
    }

    fun clickByText(text: String): Boolean {
        val service = RexyyAccessibilityService.getService() ?: return false
        val node = service.findNodeByText(text)
        return service.clickNode(node)
    }

    fun inputText(text: String): Boolean {
        val service = RexyyAccessibilityService.getService() ?: return false
        val root = service.rootInActiveWindow ?: return false
        val focused = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
        return if (focused != null) {
            service.inputText(focused, text)
        } else false
    }

    fun copy(): Boolean {
        val service = RexyyAccessibilityService.getService() ?: return false
        val root = service.rootInActiveWindow ?: return false
        val focused = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
        return service.copy(focused)
    }

    fun paste(): Boolean {
        val service = RexyyAccessibilityService.getService() ?: return false
        val root = service.rootInActiveWindow ?: return false
        val focused = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
        return service.paste(focused)
    }
}
