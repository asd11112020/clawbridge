package com.clawbridge

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dumps the accessibility node tree into a flattened JSON array
 * of interactive elements — much more AI-friendly than raw tree.
 */
object NodeTreeDumper {

    data class ScreenInfo(
        val packageName: String,
        val className: String,
        val elements: List<ElementInfo>,
        val screenWidth: Int,
        val screenHeight: Int
    )

    data class ElementInfo(
        val id: Int,
        val className: String,
        val text: String,
        val contentDesc: String,
        val resourceId: String,
        val bounds: Rect,
        val clickable: Boolean,
        val longClickable: Boolean,
        val focusable: Boolean,
        val scrollable: Boolean,
        val checkable: Boolean,
        val checked: Boolean,
        val editable: Boolean,
        val isPassword: Boolean,
        val enabled: Boolean
    )

    private var nextId = 0

    fun dump(root: AccessibilityNodeInfo?, screenW: Int, screenH: Int): JSONObject {
        val json = JSONObject()
        nextId = 0

        if (root == null) {
            json.put("ok", true)
            json.put("package", "")
            json.put("activity", "")
            json.put("elements", JSONArray())
            json.put("screen_width", screenW)
            json.put("screen_height", screenH)
            return json
        }

        val elements = JSONArray()
        val allElements = mutableListOf<ElementInfo>()

        collectElements(root, allElements)

        for (el in allElements) {
            val item = JSONObject()
            item.put("id", el.id)
            item.put("class", el.className)
            if (el.text.isNotEmpty()) item.put("text", el.text)
            if (el.contentDesc.isNotEmpty()) item.put("desc", el.contentDesc)
            if (el.resourceId.isNotEmpty()) item.put("res_id", el.resourceId)
            item.put("bounds", JSONObject().apply {
                put("l", el.bounds.left)
                put("t", el.bounds.top)
                put("r", el.bounds.right)
                put("b", el.bounds.bottom)
            })
            // Only include boolean flags when true (reduces noise)
            val flags = JSONObject()
            if (el.clickable) flags.put("click", true)
            if (el.longClickable) flags.put("long_click", true)
            if (el.focusable) flags.put("focus", true)
            if (el.scrollable) flags.put("scroll", true)
            if (el.checkable) flags.put("checkable", true)
            if (el.checked) flags.put("checked", true)
            if (el.editable) flags.put("edit", true)
            if (el.isPassword) flags.put("pwd", true)
            if (!el.enabled) flags.put("disabled", true)
            if (flags.length() > 0) item.put("flags", flags)

            elements.put(item)
        }

        val pkgName = root.packageName?.toString() ?: ""

        json.put("ok", true)
        json.put("package", pkgName)
        json.put("elements", elements)
        json.put("screen_width", screenW)
        json.put("screen_height", screenH)
        json.put("total_elements", elements.length())

        return json
    }

    private fun collectElements(node: AccessibilityNodeInfo, out: MutableList<ElementInfo>) {
        // Only include elements that are interesting: have text, clickable, focusable, etc.
        val isInteresting = node.text?.isNotEmpty() == true
                || node.contentDescription?.isNotEmpty() == true
                || node.isClickable
                || node.isLongClickable
                || node.isFocusable
                || node.isScrollable
                || node.isCheckable
                || node.isEditable

        if (isInteresting) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            out.add(ElementInfo(
                id = nextId++,
                className = node.className?.toString() ?: "unknown",
                text = node.text?.toString() ?: "",
                contentDesc = node.contentDescription?.toString() ?: "",
                resourceId = node.viewIdResourceName ?: "",
                bounds = rect,
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                focusable = node.isFocusable,
                scrollable = node.isScrollable,
                checkable = node.isCheckable,
                checked = node.isChecked,
                editable = node.isEditable,
                isPassword = node.isPassword,
                enabled = node.isEnabled
            ))
        }

        // Recurse children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectElements(child, out)
            child.recycle()
        }
    }
}
