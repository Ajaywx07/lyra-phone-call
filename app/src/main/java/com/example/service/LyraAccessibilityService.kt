package com.example.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.preferences.LyraPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional Accessibility Service for Lyra.
 * Remains strictly inactive unless the user both enables the in-app Accessibility Automation toggle
 * and explicitly turns on Lyra in Android Settings -> Accessibility.
 * Helps read on-screen call/USSD dialog text and click confirmation prompts when opted in.
 */
class LyraAccessibilityService : AccessibilityService() {

    companion object {
        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _lastAutomationStatus = MutableStateFlow<String?>(null)
        val lastAutomationStatus: StateFlow<String?> = _lastAutomationStatus.asStateFlow()

        @Volatile
        private var activeInstance: LyraAccessibilityService? = null

        fun isAutomationPermittedAndConnected(): Boolean {
            return _isServiceConnected.value &&
                LyraPreferences.getCachedIncomingSnapshotOrNull()?.accessibilityAutomationEnabled == true
        }

        fun extractVisibleDialogText(): String? {
            if (!isAutomationPermittedAndConnected()) return null
            val root = activeInstance?.rootInActiveWindow ?: return null
            return runCatching {
                val builder = StringBuilder()
                collectText(root, builder, 0)
                builder.toString().trim().takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        fun tryClickButtonWithText(buttonLabel: String): Boolean {
            if (!isAutomationPermittedAndConnected()) return false
            val root = activeInstance?.rootInActiveWindow ?: return false
            return runCatching {
                val nodes = root.findAccessibilityNodeInfosByText(buttonLabel)
                val target = nodes?.firstOrNull { it.isClickable || it.parent?.isClickable == true }
                when {
                    target == null -> false
                    target.isClickable -> target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    target.parent?.isClickable == true -> target.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else -> false
                }
            }.getOrDefault(false)
        }

        private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
            if (node == null || depth > 8) return
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                if (out.isNotEmpty()) out.append(" ")
                out.append(text)
            }
            for (i in 0 until node.childCount) {
                collectText(node.getChild(i), out, depth + 1)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        _isServiceConnected.value = true
        _lastAutomationStatus.value = "Connected (gated by user in-app toggle)"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val isOptedIn = LyraPreferences.getCachedIncomingSnapshotOrNull()?.accessibilityAutomationEnabled == true
        if (!isOptedIn) return

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName == "com.android.phone" ||
            packageName == "com.android.server.telecom" ||
            packageName == this.packageName
        ) {
            val snippet = event.text?.joinToString(" ")?.trim().orEmpty()
            if (snippet.isNotBlank()) {
                _lastAutomationStatus.value = "Observed system/telecom dialog: ${snippet.take(80)}"
            }
        }
    }

    override fun onInterrupt() {
        _lastAutomationStatus.value = "Accessibility service interrupted"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) {
            activeInstance = null
        }
        _isServiceConnected.value = false
    }
}
