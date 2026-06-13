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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dotAccessibility = findViewById(R.id.dot_accessibility)
        textAccessibilityStatus = findViewById(R.id.text_accessibility_status)
        dotServer = findViewById(R.id.dot_server)
        textServerStatus = findViewById(R.id.text_server_status)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnServer = findViewById(R.id.btn_server)

        btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        btnServer.setOnClickListener { toggleServer() }
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
            textAccessibilityStatus.text = "ON"
            textAccessibilityStatus.setTextColor(getColor(R.color.accent_green))
        } else {
            dotAccessibility.setBackgroundResource(R.drawable.dot_red)
            textAccessibilityStatus.text = "OFF"
            textAccessibilityStatus.setTextColor(getColor(R.color.accent_red))
        }

        if (serverRunning) {
            dotServer.setBackgroundResource(R.drawable.dot_green)
            textServerStatus.text = "RUNNING :$serverPort"
            textServerStatus.setTextColor(getColor(R.color.accent_green))
            btnServer.text = "Stop Server"
            btnServer.setBackgroundResource(R.drawable.bg_btn_danger)
        } else {
            dotServer.setBackgroundResource(R.drawable.dot_gray)
            textServerStatus.text = "STOPPED"
            textServerStatus.setTextColor(getColor(R.color.text_hint))
            btnServer.text = "Start Server  ·  Port $serverPort"
            btnServer.setBackgroundResource(R.drawable.bg_btn_primary)
        }
    }

    private fun toggleServer() {
        if (server?.isRunning == true) {
            server?.stop()
            Toast.makeText(this, "Server stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_LONG).show()
                return
            }
            val service = ClawBridgeService.instance
            if (service == null) {
                Toast.makeText(this, "Accessibility service not connected", Toast.LENGTH_LONG).show()
                return
            }
            server = ClawBridgeServer(serverPort, service)
            server?.start()
            Toast.makeText(this, "Server started on localhost:$serverPort", Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Find ClawBridge in the list and enable it", Toast.LENGTH_LONG).show()
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
