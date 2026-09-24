package com.rexyy.app.accessibility

import kotlinx.coroutines.delay

object AccessibilityActionExecutor {

    suspend fun tryClickSearchOrInput(query: String): Boolean {
        val service = RexyyAccessibilityService.getInteractionProvider() ?: return false

        val searchField = service.findSearchField()
        if (searchField != null) {
            service.clickNode(searchField)
            delay(200)
            return service.inputText(searchField, query)
        }

        // Look for common search icons or search input fields
        val searchKeywords = listOf("Search", "Search YouTube", "Search or type URL", "Search contacts", "Search Google")
        for (keyword in searchKeywords) {
            val node = service.findNodeByText(keyword) ?: service.findNodeByContentDescription(keyword)
            if (node != null) {
                service.clickNode(node)
                delay(200)
                service.inputText(node, query)
                return true
            }
        }
        return false
    }

    fun scroll(down: Boolean): Boolean {
        val service = RexyyAccessibilityService.getInteractionProvider() ?: return false
        return if (down) service.scrollForward() else service.scrollBackward()
    }

    fun pressBack(): Boolean {
        return RexyyAccessibilityService.getInteractionProvider()?.pressBack() ?: false
    }

    fun pressHome(): Boolean {
        return RexyyAccessibilityService.getInteractionProvider()?.pressHome() ?: false
    }

    fun pressRecentApps(): Boolean {
        return RexyyAccessibilityService.getInteractionProvider()?.pressRecentApps() ?: false
    }

    fun clickByText(text: String): Boolean {
        val service = RexyyAccessibilityService.getInteractionProvider() ?: return false
        val node = service.findVisibleText(text)
        return service.clickNode(node)
    }

    fun clickByContentDescription(desc: String): Boolean {
        val service = RexyyAccessibilityService.getInteractionProvider() ?: return false
        val node = service.findNodeByContentDescription(desc)
        return service.clickNode(node)
    }

    fun inputText(text: String): Boolean {
        val service = RexyyAccessibilityService.getInteractionProvider() ?: return false
        val field = service.findFirstEditableField() ?: return false
        service.clickNode(field)
        return service.inputText(field, text)
    }

    fun replaceText(text: String): Boolean {
        val service = RexyyAccessibilityService.getInteractionProvider() ?: return false
        val field = service.findFirstEditableField() ?: return false
        service.clickNode(field)
        return service.replaceText(field, text)
    }

    fun copy(): Boolean {
        val service = RexyyAccessibilityService.getInteractionProvider() ?: return false
        val field = service.findFirstEditableField() ?: return false
        return service.copyText(field)
    }

    fun paste(): Boolean {
        val service = RexyyAccessibilityService.getInteractionProvider() ?: return false
        val field = service.findFirstEditableField() ?: return false
        service.clickNode(field)
        return service.pasteText(field)
    }

    fun submitSearch(): Boolean {
        val service = RexyyAccessibilityService.getInteractionProvider() ?: return false
        return service.submitSearch()
    }

    fun getForegroundPackage(): String? {
        return RexyyAccessibilityService.getInteractionProvider()?.getForegroundPackage()
    }
}
