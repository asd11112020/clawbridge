package com.clawbridge

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.Activity

/**
 * Minimal UI — shows service status, lets user enable accessibility,
 * and toggle the HTTP bridge server.
 */
class MainActivity : Activity() {

    private val serverPort = 9876
    private var server: ClawBridgeServer? = null

    private lateinit var statusText: TextView
    private lateinit var settingsBtn: Button
    private lateinit var serverBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build a simple layout programmatically (no XML layout needed)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
        }

        val titleText = TextView(this).apply {
            text = "🦞 ClawBridge"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(titleText)

        statusText = TextView(this).apply {
            text = "检查服务状态..."
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(statusText)

        settingsBtn = Button(this).apply {
            text = "打开无障碍设置"
            setOnClickListener { openAccessibilitySettings() }
        }
        layout.addView(settingsBtn)

        serverBtn = Button(this).apply {
            text = "启动服务 (端口 $serverPort)"
            setOnClickListener { toggleServer() }
        }
        layout.addView(serverBtn)

        val hintText = TextView(this).apply {
            text = """
                
使用方法：
1. 点击「打开无障碍设置」
2. 找到 ClawBridge 并开启
3. 回到这里点击「启动服务」
4. OpenClaw 会通过 localhost:$serverPort 通信

端点为 localhost（仅本机可访问）。
            """.trimIndent()
            textSize = 13f
            setPadding(0, 32, 0, 0)
        }
        layout.addView(hintText)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    private fun updateStatus() {
        val accEnabled = isAccessibilityServiceEnabled()
        val serverRunning = server?.isRunning == true

        statusText.text = buildString {
            append("无障碍: ${if (accEnabled) "✅ 已开启" else "❌ 未开启"}\n")
            append("服务器: ${if (serverRunning) "🟢 运行中 localhost:$serverPort" else "⚫ 未启动"}")
        }

        serverBtn.text = if (serverRunning) "停止服务" else "启动服务 (端口 $serverPort)"
    }

    private fun toggleServer() {
        if (server?.isRunning == true) {
            server?.stop()
            Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
        } else {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
                return
            }
            val service = ClawBridgeService.instance
            if (service == null) {
                Toast.makeText(this, "无障碍服务未连接，请重新开启", Toast.LENGTH_LONG).show()
                return
            }
            server = ClawBridgeServer(serverPort, service)
            server?.start()
            Toast.makeText(this, "服务已启动 localhost:$serverPort", Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "请在列表中找到 ClawBridge 并开启", Toast.LENGTH_LONG).show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }
}
