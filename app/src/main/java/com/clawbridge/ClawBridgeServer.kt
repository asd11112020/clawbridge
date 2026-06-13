package com.clawbridge

import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * Lightweight embedded HTTP server — no external dependencies.
 * Listens on localhost for commands from OpenClaw.
 */
class ClawBridgeServer(
    private val port: Int,
    private val service: ClawBridgeService
) {
    @Volatile
    var isRunning = false
        private set

    private var serverSocket: ServerSocket? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    try {
                        val client = serverSocket!!.accept()
                        Thread { handleClient(client) }.start()
                    } catch (e: Exception) {
                        if (isRunning) e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isRunning = false
            }
        }.apply {
            name = "ClawBridge-HTTP"
            isDaemon = true
        }.start()
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = socket.getOutputStream()

            // Request line
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            // Headers
            var contentLength = 0
            var line = input.readLine()
            while (line != null && line.isNotEmpty()) {
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                line = input.readLine()
            }

            // Body
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                input.read(buf, 0, contentLength)
                String(buf)
            } else ""

            // Route
            val response = route(method, path, body)
            val responseBytes = response.toByteArray(Charsets.UTF_8)

            // HTTP response
            output.write("HTTP/1.1 200 OK\r\n".toByteArray())
            output.write("Content-Type: application/json; charset=utf-8\r\n".toByteArray())
            output.write("Content-Length: ${responseBytes.size}\r\n".toByteArray())
            output.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
            output.write("Connection: close\r\n".toByteArray())
            output.write("\r\n".toByteArray())
            output.write(responseBytes)
            output.flush()
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun route(method: String, path: String, body: String): String {
        return try {
            when {
                method == "GET" && path == "/status" -> statusJson()
                method == "GET" && path == "/screen" -> getScreenJson()
                method == "POST" && path == "/tap" -> tap(JSONObject(body))
                method == "POST" && path == "/swipe" -> swipe(JSONObject(body))
                method == "POST" && path == "/text" -> setText(JSONObject(body))
                method == "POST" && path == "/key" -> pressKey(JSONObject(body))
                method == "POST" && path == "/auto_lock" -> setAutoLock(JSONObject(body))
                method == "POST" && path == "/find" -> findAndClick(JSONObject(body))
                method == "GET" && path == "/screenshot" -> takeScreenshot()
                method == "POST" && path == "/open" -> openApp(JSONObject(body))
                method == "OPTIONS" -> "{}" // CORS preflight
                else -> """{"ok":false,"error":"unknown_endpoint","path":"$path"}"""
            }
        } catch (e: Exception) {
            """{"ok":false,"error":"${e.message?.replace("\"", "\\\"")}"}"""
        }
    }

    private fun statusJson(): String {
        return JSONObject().apply {
            put("ok", true)
            put("service_running", true)
            put("accessibility_enabled", service.isAccessibilityEnabled())
        }.toString()
    }

    private fun getScreenJson(): String {
        val root = service.getScreenRoot()
        val result = NodeTreeDumper.dump(root, service.screenWidth, service.screenHeight).toString()
        root?.recycle()
        return result
    }

    private fun tap(params: JSONObject): String {
        val x = params.getDouble("x").toFloat()
        val y = params.getDouble("y").toFloat()
        service.tap(x, y)
        return """{"ok":true}"""
    }

    private fun swipe(params: JSONObject): String {
        val x1 = params.getDouble("x1").toFloat()
        val y1 = params.getDouble("y1").toFloat()
        val x2 = params.getDouble("x2").toFloat()
        val y2 = params.getDouble("y2").toFloat()
        val duration = params.optLong("duration", 300)
        service.swipe(x1, y1, x2, y2, duration)
        return """{"ok":true}"""
    }

    private fun setText(params: JSONObject): String {
        val text = params.getString("text")
        service.setText(text)
        return """{"ok":true}"""
    }

    private fun pressKey(params: JSONObject): String {
        val key = params.getString("key")
        service.pressKey(key)
        return """{"ok":true}"""
    }

    private fun setAutoLock(params: JSONObject): String {
        val enabled = params.optBoolean("enabled", true)
        if (enabled) {
            service.resetAutoLockTimer()
        } else {
            service.disableAutoLock()
        }
        return """{"ok":true,"auto_lock":$enabled}"""
    }

    private fun openApp(params: JSONObject): String {
        val packageName = params.optString("package", "")
        val appName = params.optString("name", "")

        var pkg = packageName
        if (pkg.isEmpty() && appName.isNotEmpty()) {
            pkg = service.findAppByDisplayName(appName) ?: ""
        }

        if (pkg.isEmpty()) {
            return """{"ok":false,"error":"no_package_or_name_provided"}"""
        }

        val ok = service.openApp(pkg)
        return if (ok) {
            """{"ok":true,"package":"$pkg"}"""
        } else {
            """{"ok":false,"error":"app_not_found_or_cannot_launch","package":"$pkg"}"""
        }
    }

    private fun takeScreenshot(): String {
        // Use AccessibilityService context to run screencap
        val pngBytes = service.takeScreenshotBytes()
        if (pngBytes == null) {
            return """{"ok":false,"error":"screenshot_failed"}""".trimIndent()
        }
        // Save to a well-known path that OpenClaw can read
        val file = java.io.File(service.filesDir, "screenshot.png")
        file.writeBytes(pngBytes)
        // Also copy to /storage/emulated/0/ for easy access
        try {
            val sharedFile = java.io.File("/storage/emulated/0/clawbridge_screenshot.png")
            sharedFile.writeBytes(pngBytes)
            return """{"ok":true,"path":"${sharedFile.absolutePath}","size":${pngBytes.size}}""".trimIndent()
        } catch (_: Exception) {
            return """{"ok":true,"path":"${file.absolutePath}","size":${pngBytes.size}}""".trimIndent()
        }
    }

    private fun findAndClick(params: JSONObject): String {
        val target = params.optString("text", "")
        val click = params.optBoolean("click", true)

        // Search the node tree for matching text
        val root = service.getScreenRoot()
        if (root == null || target.isEmpty()) {
            return """{"ok":false,"error":"no_root_or_empty_query"}"""
        }

        val matches = JSONObject()
        val items = org.json.JSONArray()
        findNodesByText(root, target, items)
        root.recycle()

        if (click && items.length() > 0) {
            // Click the first match
            val first = items.getJSONObject(0)
            val bounds = first.getJSONObject("bounds")
            val cx = (bounds.getInt("l") + bounds.getInt("r")) / 2f
            val cy = (bounds.getInt("t") + bounds.getInt("b")) / 2f
            service.tap(cx, cy)
            matches.put("clicked", true)
        }

        matches.put("ok", true)
        matches.put("matches", items)
        matches.put("count", items.length())
        return matches.toString()
    }

    private fun findNodesByText(node: AccessibilityNodeInfo, target: String, results: org.json.JSONArray) {
        val nodeText = (node.text?.toString() ?: "") + (node.contentDescription?.toString() ?: "")
        if (nodeText.contains(target, ignoreCase = true)) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val item = JSONObject()
            item.put("text", node.text?.toString() ?: "")
            item.put("desc", node.contentDescription?.toString() ?: "")
            item.put("class", node.className?.toString() ?: "")
            item.put("bounds", JSONObject().apply {
                put("l", rect.left)
                put("t", rect.top)
                put("r", rect.right)
                put("b", rect.bottom)
            })
            results.put(item)
            // Don't recurse into matched nodes to avoid duplicates
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByText(child, target, results)
            child.recycle()
        }
    }
}
