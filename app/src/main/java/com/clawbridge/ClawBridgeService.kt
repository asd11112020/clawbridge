package com.clawbridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Core accessibility service — the bridge between Android and OpenClaw.
 * 
 * Registered as an AccessibilityService so it can:
 * - Read the screen node tree from any app
 * - Perform gestures (tap, swipe) at pixel coordinates
 * - Set text in editable fields
 * - Trigger global actions (back, home, recents)
 */
class ClawBridgeService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClawBridgeService? = null
            private set

        /** Check if an instance exists AND is configured */
        fun isRunning(): Boolean = instance != null
    }

    @Volatile
    var screenWidth: Int = 1080
        private set

    @Volatile
    var screenHeight: Int = 1920
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Get real screen dimensions
        val dm = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager
        val display: Display? = wm?.defaultDisplay
        display?.getRealMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We mainly use the service for on-demand screen reading,
        // not for event-driven behavior. But we keep this to satisfy
        // the service contract.
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Is the accessibility service properly enabled in system settings? */
    fun isAccessibilityEnabled(): Boolean = instance != null

    /**
     * Tap at exact pixel coordinates using Accessibility GestureDescription.
     * Works on any screen, any app.
     */
    fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Swipe from (x1,y1) to (x2,y2) with configurable duration.
     */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Set text into the currently focused editable field.
     * Falls back to finding any editable node on screen.
     */
    fun setText(text: String) {
        val focused = findFocusedEditableNode()
        if (focused != null) {
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
        }
    }

    /**
     * Press a system key: back, home, recents.
     */
    fun pressKey(key: String): Boolean {
        return when (key.lowercase()) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "power_dialog" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "lock_screen" -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            else -> false
        }
    }

    /**
     * Find the currently focused editable node by searching the tree.
     */
    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        // First, check if any node has input focus
        val root = rootInActiveWindow ?: return null
        return findEditableRecursive(root)
    }

    private fun findEditableRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableRecursive(child)
            if (result != null) return result
            child.recycle()
        }
        // If we get here, no focused editable found — return any editable node
        if (node.isEditable) {
            return node
        }
        return null
    }

    /**
     * Launch an app by its package name.
     * Returns true if the intent was sent successfully.
     */
    fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Search installed apps with launcher activity by display name.
     * Returns the package name of the first match (case-insensitive contains).
     */
    fun findAppByDisplayName(name: String): String? {
        return try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = packageManager.queryIntentActivities(mainIntent, 0)
            for (app in apps) {
                val label = app.loadLabel(packageManager).toString()
                if (label.contains(name, ignoreCase = true)) {
                    return app.activityInfo.packageName
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the root node of the topmost visible app window (not system UI).
     * Falls back to [rootInActiveWindow] when [windows] is unavailable.
     *
     * This is the fix for the "/open" problem: after launching an app via
     * Intent, [rootInActiveWindow] still points to the old window, but
     * [windows] contains the newly opened app's window.
     */
    fun getScreenRoot(): AccessibilityNodeInfo? {
        // Prefer AccessibilityService.windows — it covers all visible windows
        val allWindows = try {
            windows
        } catch (_: Exception) {
            null
        }

        if (!allWindows.isNullOrEmpty()) {
            // Score windows: prefer active/visible app windows over system overlays
            var bestWindow: AccessibilityWindowInfo? = null
            var bestScore = -1

            for (win in allWindows) {
                if (!win.isVisible) continue
                val type = win.type
                when {
                    // Application windows are what we want
                    type == AccessibilityWindowInfo.TYPE_APPLICATION -> {
                        if (win.isActive && bestScore < 10) {
                            bestWindow = win
                            bestScore = 10
                        } else if (bestScore < 8) {
                            bestWindow = win
                            bestScore = 8
                        }
                    }
                    // Input method (keyboard) — low priority
                    type == AccessibilityWindowInfo.TYPE_INPUT_METHOD -> {
                        if (bestScore < 3) {
                            bestWindow = win
                            bestScore = 3
                        }
                    }
                    // Overlay / accessibility overlays
                    type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> {
                        if (bestScore < 2) {
                            bestWindow = win
                            bestScore = 2
                        }
                    }
                    // System windows (status bar, etc.) — last resort
                    type == AccessibilityWindowInfo.TYPE_SYSTEM -> {
                        if (bestScore < 1) {
                            bestWindow = win
                            bestScore = 1
                        }
                    }
                }
            }

            val chosen = bestWindow
            if (chosen != null) {
                val root = chosen.root
                // Don't recycle — caller owns the node
                return root
            }
        }

        // Fallback to the default active window
        return rootInActiveWindow
    }

    /** Get the current root as AccessibilityNodeInfo for reading */
    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow
}
