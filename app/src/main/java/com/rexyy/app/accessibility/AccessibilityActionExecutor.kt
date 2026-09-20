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
}
