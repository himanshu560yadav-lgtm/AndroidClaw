package com.himanshu.himanshu

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo


class MyAiAccessibilityService : AccessibilityService() {
    companion object {
        var instance: MyAiAccessibilityService? = null
    }
    override fun onServiceConnected() { instance = this }

    fun dumpScreenInfo(): String {
        val root = rootInActiveWindow ?: return "Empty Screen"
        val sb = StringBuilder()
        parseNode(root, sb)
        return sb.toString()
    }

    private fun parseNode(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        node ?: return
        if (node.isClickable || !node.text.isNullOrEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            sb.append("{text:'${node.text}', bounds:[${rect.left},${rect.top},${rect.right},${rect.bottom}]}\n")
        }
        for (i in 0 until node.childCount) parseNode(node.getChild(i), sb)
    }

    fun performClick(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
        dispatchGesture(gesture, null, null)
    }

    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Int = 300) {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong())).build()
        dispatchGesture(gesture, null, null)
    }

    fun performScroll(direction: String) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display: Display = wm.defaultDisplay
        val metrics = android.util.DisplayMetrics()
        display.getRealMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        var startX = screenWidth / 2
        var startY = screenHeight / 2
        var endX = screenWidth / 2
        var endY = screenHeight / 2

        when (direction.lowercase()) {
            "up" -> {
                startY = (screenHeight * 0.7).toInt()
                endY = (screenHeight * 0.3).toInt()
            }
            "down" -> {
                startY = (screenHeight * 0.3).toInt()
                endY = (screenHeight * 0.7).toInt()
            }
            "left" -> {
                startY = screenHeight / 2
                endX = (screenWidth * 0.8).toInt()
                endY = screenHeight / 2
            }
            "right" -> {
                startY = screenHeight / 2
                endX = (screenWidth * 0.2).toInt()
                endY = screenHeight / 2
            }
            else -> return
        }

        performSwipe(startX, startY, endX, endY, 300)
    }

    fun performSystemAction(action: String, value: String? = null): Boolean {
        return when (action.lowercase()) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "power_menu" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "lock_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else false
            }
            "torch", "flashlight" -> toggleTorch(value)
            "wifi" -> toggleWifi(value)
            "dnd", "do_not_disturb" -> toggleDnd(value)
            else -> false
        }
    }

    private fun toggleTorch(value: String?): Boolean {
        return try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            val newState = when (value?.lowercase()) {
                "on", "true", "1" -> true
                "off", "false", "0" -> false
                else -> true
            }
            cameraManager.setTorchMode(cameraId, newState)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun toggleWifi(value: String?): Boolean {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val newState = when (value?.lowercase()) {
                "on", "true", "enable" -> true
                "off", "false", "disable" -> false
                else -> !wifiManager.isWifiEnabled
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                wifiManager.isWifiEnabled = newState
                true
            } else {
                // For Android 10+, we must use Settings Panels as direct toggle is restricted
                val intent = android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun toggleDnd(value: String?): Boolean {
        return try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return false
                }
                val newState = when (value?.lowercase()) {
                    "on", "true" -> NotificationManager.INTERRUPTION_FILTER_NONE
                    "off", "false" -> NotificationManager.INTERRUPTION_FILTER_ALL
                    else -> if (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL)
                        NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL
                }
                notificationManager.setInterruptionFilter(newState)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}