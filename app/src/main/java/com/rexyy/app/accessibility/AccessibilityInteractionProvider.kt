package com.rexyy.app.accessibility

import android.view.accessibility.AccessibilityNodeInfo

interface AccessibilityInteractionProvider {
    fun isServiceEnabled(): Boolean
    fun getForegroundPackage(): String?
    fun findNodeByText(text: String): AccessibilityNodeInfo?
    fun findVisibleText(text: String, ignoreCase: Boolean = true): AccessibilityNodeInfo?
    fun findNodeByContentDescription(desc: String, ignoreCase: Boolean = true): AccessibilityNodeInfo?
    fun findNodeByViewId(viewId: String): AccessibilityNodeInfo?
    fun findClickableNodes(): List<AccessibilityNodeInfo>
    fun findEditableFields(): List<AccessibilityNodeInfo>
    fun findFirstEditableField(): AccessibilityNodeInfo?
    fun findSearchField(): AccessibilityNodeInfo?
    fun clickNode(node: AccessibilityNodeInfo?): Boolean
    fun inputText(node: AccessibilityNodeInfo?, text: String): Boolean
    fun replaceText(node: AccessibilityNodeInfo?, text: String): Boolean
    fun copyText(node: AccessibilityNodeInfo?): Boolean
    fun pasteText(node: AccessibilityNodeInfo?): Boolean
    fun scrollForward(node: AccessibilityNodeInfo? = null): Boolean
    fun scrollBackward(node: AccessibilityNodeInfo? = null): Boolean
    fun pressBack(): Boolean
    fun pressHome(): Boolean
    fun pressRecentApps(): Boolean
    fun submitSearch(node: AccessibilityNodeInfo? = null): Boolean
    suspend fun waitForForegroundPackage(targetPackage: String, timeoutMs: Long = 2500): Boolean
    suspend fun waitForUiCondition(timeoutMs: Long = 2500, condition: () -> Boolean): Boolean
}
