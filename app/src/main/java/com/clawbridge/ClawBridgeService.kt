package com.clawbridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    // Auto-lock timer — 30 seconds of no touch = lock screen
    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockIntervalMs = 30000L
    private val autoLockTask = Runnable {
        android.util.Log.d("ClawBridge", "auto-lock: no touch for 30s, locking screen")
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    /**
     * Reset the auto-lock timer. Call after every user-initiated action.
     * The screen will lock after [autoLockIntervalMs] of inactivity.
     */
    fun resetAutoLockTimer() {
        autoLockHandler.removeCallbacks(autoLockTask)
        autoLockHandler.postDelayed(autoLockTask, autoLockIntervalMs)
    }

    /**
     * Disable the auto-lock timer (e.g. when user explicitly wants the screen on).
     */
    fun disableAutoLock() {
        autoLockHandler.removeCallbacks(autoLockTask)
    }

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
        // Start the auto-lock timer on service start
        resetAutoLockTimer()
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
        resetAutoLockTimer()
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
        resetAutoLockTimer()
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
        resetAutoLockTimer()
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
        resetAutoLockTimer()
        return when (key.lowercase()) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "power_dialog" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "lock_screen" -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "screenshot", "sysrq" -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
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
        // Prefer AccessibilityService.windows — it covers all visible windows.
        // We score windows by type: active application windows rank highest.
        val allWindows = try {
            windows
        } catch (_: Exception) {
            null
        }

        if (!allWindows.isNullOrEmpty()) {
            var bestWindow: AccessibilityWindowInfo? = null
            var bestScore = -1

            for (win in allWindows) {
                val type = win.type
                val bounds = android.graphics.Rect()
                win.getBoundsInScreen(bounds)
                // Skip near-zero-size windows (likely decorations or hidden overlays)
                if (bounds.width() < 50 && bounds.height() < 50) continue

                when {
                    // Active application window — top priority
                    type == AccessibilityWindowInfo.TYPE_APPLICATION && win.isActive -> {
                        if (bestScore < 10) {
                            bestWindow = win
                            bestScore = 10
                        }
                    }
                    // Any other application window
                    type == AccessibilityWindowInfo.TYPE_APPLICATION -> {
                        if (bestScore < 8) {
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
                    // Accessibility overlay
                    type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> {
                        if (bestScore < 2) {
                            bestWindow = win
                            bestScore = 2
                        }
                    }
                }
            }

            if (bestWindow != null) {
                val root = bestWindow.root
                // Don't recycle — caller owns the returned node
                return root
            }
        }

        // Fallback to the default active window
        return rootInActiveWindow
    }

    /**
     * Take a screenshot by running /system/bin/screencap.
     * Runs inside the app process — the shell runs as the app's UID.
     * Returns raw PNG bytes, or null on failure.
     */
    fun takeScreenshotBytes(): ByteArray? {
        return try {
            val proc = Runtime.getRuntime().exec("/system/bin/screencap -p")
            val bytes = proc.inputStream.readBytes()
            proc.waitFor()
            if (proc.exitValue() != 0 || bytes.isEmpty()) {
                android.util.Log.w("ClawBridge", "screencap failed: exit=${proc.exitValue()} size=${bytes.size}")
                return null
            }
            bytes
        } catch (e: Exception) {
            android.util.Log.e("ClawBridge", "screencap error", e)
            null
        }
    }

    /** Get the current root as AccessibilityNodeInfo for reading */
    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow
}
