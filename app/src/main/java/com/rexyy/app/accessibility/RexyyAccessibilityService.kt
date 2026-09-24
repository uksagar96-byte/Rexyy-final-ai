package com.rexyy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

class RexyyAccessibilityService : AccessibilityService(), AccessibilityInteractionProvider {

    @Volatile
    private var currentForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank()) {
                currentForegroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance?.get() == this) {
            instance = null
        }
    }

    override fun isServiceEnabled(): Boolean = true

    override fun getForegroundPackage(): String? {
        testProvider?.let { return it.getForegroundPackage() }
        val rootPkg = rootInActiveWindow?.packageName?.toString()
        return if (!rootPkg.isNullOrBlank()) rootPkg else currentForegroundPackage
    }

    fun setForegroundPackageForTesting(pkg: String?) {
        currentForegroundPackage = pkg
    }

    fun isSafeNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isPassword) return false
        val text = (node.text?.toString() ?: "").lowercase()
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val viewId = (node.viewIdResourceName ?: "").lowercase()
        val sensitiveKeywords = listOf("password", "passwd", "pin", "otp", "cvv", "security code", "passcode")
        return sensitiveKeywords.none { text.contains(it) || desc.contains(it) || viewId.contains(it) }
    }

    override fun findNodeByText(text: String): AccessibilityNodeInfo? {
        testProvider?.let { return it.findNodeByText(text) }
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull { isSafeNode(it) }
    }

    override fun findVisibleText(text: String, ignoreCase: Boolean): AccessibilityNodeInfo? {
        testProvider?.let { return it.findVisibleText(text, ignoreCase) }
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val direct = nodes.firstOrNull { isSafeNode(it) }
        if (direct != null) return direct

        // Deep traversal if direct matching missed
        return findNodeBfs(root) { node ->
            isSafeNode(node) && node.text?.toString()?.contains(text, ignoreCase = ignoreCase) == true
        }
    }

    override fun findNodeByContentDescription(desc: String, ignoreCase: Boolean): AccessibilityNodeInfo? {
        testProvider?.let { return it.findNodeByContentDescription(desc, ignoreCase) }
        val root = rootInActiveWindow ?: return null
        return findNodeBfs(root) { node ->
            isSafeNode(node) && node.contentDescription?.toString()?.contains(desc, ignoreCase = ignoreCase) == true
        }
    }

    override fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        testProvider?.let { return it.findNodeByViewId(viewId) }
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes.firstOrNull { isSafeNode(it) }
    }

    override fun findClickableNodes(): List<AccessibilityNodeInfo> {
        testProvider?.let { return it.findClickableNodes() }
        val root = rootInActiveWindow ?: return emptyList()
        val list = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root) { node ->
            if (node.isClickable && isSafeNode(node)) {
                list.add(node)
            }
        }
        return list
    }

    override fun findEditableFields(): List<AccessibilityNodeInfo> {
        testProvider?.let { return it.findEditableFields() }
        val root = rootInActiveWindow ?: return emptyList()
        val list = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root) { node ->
            if (isSafeNode(node) && (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true)) {
                list.add(node)
            }
        }
        return list
    }

    override fun findFirstEditableField(): AccessibilityNodeInfo? {
        testProvider?.let { return it.findFirstEditableField() }
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && isSafeNode(focused) && (focused.isEditable || focused.className?.contains("EditText", ignoreCase = true) == true)) {
            return focused
        }
        return findNodeBfs(root) { node ->
            isSafeNode(node) && (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true)
        }
    }

    override fun findSearchField(): AccessibilityNodeInfo? {
        testProvider?.let { return it.findSearchField() }
        val root = rootInActiveWindow ?: return null
        // 1. Check known search view IDs
        val knownIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "com.google.android.youtube:id/search_edit_text",
            "com.instagram.android:id/action_bar_search_edit_text",
            "com.google.android.apps.maps:id/search_omnibox_edit_text"
        )
        for (id in knownIds) {
            val node = findNodeByViewId(id)
            if (node != null && isSafeNode(node)) return node
        }

        // 2. Check hint or text containing search
        val node = findNodeBfs(root) { n ->
            if (!isSafeNode(n)) return@findNodeBfs false
            val text = (n.text?.toString() ?: "").lowercase()
            val desc = (n.contentDescription?.toString() ?: "").lowercase()
            (n.isEditable || n.className?.contains("EditText", ignoreCase = true) == true) &&
                    (text.contains("search") || desc.contains("search") || text.contains("type url") || desc.contains("type url"))
        }
        if (node != null) return node

        // 3. Fallback to first editable field
        return findFirstEditableField()
    }

    override fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        testProvider?.let { return it.clickNode(node) }
        if (node == null) return false
        var target: AccessibilityNodeInfo? = node
        while (target != null) {
            if (target.isClickable) {
                return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            target = target.parent
        }
        return false
    }

    override fun inputText(node: AccessibilityNodeInfo?, text: String): Boolean {
        testProvider?.let { return it.inputText(node, text) }
        if (node == null || !isSafeNode(node)) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun replaceText(node: AccessibilityNodeInfo?, text: String): Boolean {
        testProvider?.let { return it.replaceText(node, text) }
        if (node == null || !isSafeNode(node)) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun copyText(node: AccessibilityNodeInfo?): Boolean {
        testProvider?.let { return it.copyText(node) }
        return node?.performAction(AccessibilityNodeInfo.ACTION_COPY) ?: false
    }

    override fun pasteText(node: AccessibilityNodeInfo?): Boolean {
        testProvider?.let { return it.pasteText(node) }
        return node?.performAction(AccessibilityNodeInfo.ACTION_PASTE) ?: false
    }

    override fun scrollForward(node: AccessibilityNodeInfo?): Boolean {
        testProvider?.let { return it.scrollForward(node) }
        val target = node ?: rootInActiveWindow ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    override fun scrollBackward(node: AccessibilityNodeInfo?): Boolean {
        testProvider?.let { return it.scrollBackward(node) }
        val target = node ?: rootInActiveWindow ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    override fun pressBack(): Boolean {
        testProvider?.let { return it.pressBack() }
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun pressHome(): Boolean {
        testProvider?.let { return it.pressHome() }
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun pressRecentApps(): Boolean {
        testProvider?.let { return it.pressRecentApps() }
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    override fun submitSearch(node: AccessibilityNodeInfo?): Boolean {
        testProvider?.let { return it.submitSearch(node) }
        val target = node ?: findSearchField()
        if (target != null) {
            // Check if there is an explicit search button or action
            val root = rootInActiveWindow
            if (root != null) {
                val searchBtn = findNodeBfs(root) { n ->
                    n.isClickable && (
                            n.contentDescription?.toString()?.contains("search", ignoreCase = true) == true ||
                                    n.text?.toString()?.contains("search", ignoreCase = true) == true ||
                                    n.viewIdResourceName?.contains("search_button", ignoreCase = true) == true
                            )
                }
                if (searchBtn != null) {
                    return clickNode(searchBtn)
                }
            }
            return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return false
    }

    override suspend fun waitForForegroundPackage(targetPackage: String, timeoutMs: Long): Boolean {
        testProvider?.let { return it.waitForForegroundPackage(targetPackage, timeoutMs) }
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val current = getForegroundPackage()
            if (current != null && current.contains(targetPackage, ignoreCase = true)) {
                return true
            }
            kotlinx.coroutines.delay(100)
        }
        return false
    }

    override suspend fun waitForUiCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        testProvider?.let { return it.waitForUiCondition(timeoutMs, condition) }
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) return true
            kotlinx.coroutines.delay(100)
        }
        return false
    }

    private fun findNodeBfs(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }
        return null
    }

    private fun collectNodes(node: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Unit) {
        action(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) collectNodes(child, action)
        }
    }

    companion object {
        private var instance: WeakReference<RexyyAccessibilityService>? = null
        private var testProvider: AccessibilityInteractionProvider? = null

        fun isServiceEnabled(): Boolean = testProvider?.isServiceEnabled() ?: (instance?.get() != null)

        fun isAccessibilityServiceConfigured(context: Context): Boolean {
            if (isServiceEnabled()) return true
            return try {
                val serviceName = "${context.packageName}/${RexyyAccessibilityService::class.java.canonicalName}"
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                val accessibilityEnabled = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                )
                accessibilityEnabled == 1 && enabledServices.contains(serviceName)
            } catch (_: Exception) {
                false
            }
        }

        fun getService(): RexyyAccessibilityService? = instance?.get()

        fun getInteractionProvider(): AccessibilityInteractionProvider? = testProvider ?: instance?.get()

        fun setTestProvider(provider: AccessibilityInteractionProvider?) {
            testProvider = provider
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
