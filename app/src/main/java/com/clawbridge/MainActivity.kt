package com.clawbridge

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.app.Activity

class MainActivity : Activity() {

    private val serverPort = 9876
    private var server: ClawBridgeServer? = null

    private lateinit var dotAccessibility: View
    private lateinit var textAccessibilityStatus: TextView
    private lateinit var dotServer: View
    private lateinit var textServerStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnServer: Button
    private lateinit var imgLogo: BlinkingLogoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dotAccessibility = findViewById(R.id.dot_accessibility)
        textAccessibilityStatus = findViewById(R.id.text_accessibility_status)
        dotServer = findViewById(R.id.dot_server)
        textServerStatus = findViewById(R.id.text_server_status)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnServer = findViewById(R.id.btn_server)
        imgLogo = findViewById(R.id.img_logo)

        btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        btnServer.setOnClickListener { toggleServer() }
        imgLogo.setOnClickListener { it as BlinkingLogoView; it.blink() }
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

        if (accEnabled) {
            dotAccessibility.setBackgroundResource(R.drawable.dot_green)
            textAccessibilityStatus.text = "已开启"
            textAccessibilityStatus.setTextColor(getColor(R.color.accent_green))
        } else {
            dotAccessibility.setBackgroundResource(R.drawable.dot_red)
            textAccessibilityStatus.text = "未开启"
            textAccessibilityStatus.setTextColor(getColor(R.color.accent_red))
        }

        if (serverRunning) {
            dotServer.setBackgroundResource(R.drawable.dot_green)
            textServerStatus.text = "运行中 :$serverPort"
            textServerStatus.setTextColor(getColor(R.color.accent_green))
            btnServer.text = "停止服务"
            btnServer.setBackgroundResource(R.drawable.bg_btn_danger)
        } else {
            dotServer.setBackgroundResource(R.drawable.dot_gray)
            textServerStatus.text = "未启动"
            textServerStatus.setTextColor(getColor(R.color.text_hint))
            btnServer.text = "启动服务  ·  端口 $serverPort"
            btnServer.setBackgroundResource(R.drawable.bg_btn_primary)
        }
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
