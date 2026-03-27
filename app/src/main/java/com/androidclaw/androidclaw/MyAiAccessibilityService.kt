package com.androidclaw.androidclaw

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.Display
import android.view.WindowManager


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

    fun performInputText(text: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream.bufferedWriter()
            os.write("input text '${text.replace(" ", "%s")}'\n")
            os.write("exit\n")
            os.flush()
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun performSystemAction(systemAction: String, value: String? = null): Boolean {
        return try {
            when (systemAction.lowercase()) {
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                "torch", "flashlight" -> toggleTorch(value)
                "wifi" -> toggleWifi(value)
                "bluetooth" -> toggleBluetooth(value)
                "lock", "lock_screen" -> lockScreen()
                "screenshot" -> takeScreenshot()
                "volume_up" -> changeVolume("up")
                "volume_down" -> changeVolume("down")
                "mute" -> changeVolume("mute")
                "brightness_up" -> changeBrightness("up")
                "brightness_down" -> changeBrightness("down")
                "airplane_mode" -> toggleAirplaneMode(value)
                "nfc" -> toggleNfc(value)
                "location" -> toggleLocation(value)
                "hotspot" -> toggleHotspot(value)
                "dnd", "do_not_disturb" -> toggleDnd(value)
                "night_mode" -> toggleNightMode(value)
                "rotate_screen" -> toggleRotation()
                "power_menu" -> showPowerMenu()
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun toggleTorch(value: String?): Boolean {
        return try {
            val newState = when (value?.lowercase()) {
                "on", "true", "1" -> "1"
                "off", "false", "0" -> "0"
                else -> null // toggle
            }
            val cmd = if (newState != null) {
                "settings put global flashlight_on $newState"
            } else {
                "input keyevent 223" // Toggle flashlight
            }
            execCommand(cmd)
        } catch (e: Exception) {
            // Fallback: open torch app
            openApp("com.android.camera")
            true
        }
    }

    private fun toggleWifi(value: String?): Boolean {
        return try {
            val cmd = when (value?.lowercase()) {
                "on", "enable", "true" -> "svc wifi enable"
                "off", "disable", "false" -> "svc wifi disable"
                else -> "svc wifi toggle"
            }
            execCommand(cmd)
        } catch (e: Exception) {
            openSettings("android.settings.WIFI_SETTINGS")
            true
        }
    }

    private fun toggleBluetooth(value: String?): Boolean {
        return try {
            val cmd = when (value?.lowercase()) {
                "on", "enable", "true" -> "svc bluetooth enable"
                "off", "disable", "false" -> "svc bluetooth disable"
                else -> "svc bluetooth toggle"
            }
            execCommand(cmd)
        } catch (e: Exception) {
            openSettings("android.settings.BLUETOOTH_SETTINGS")
            true
        }
    }

    private fun lockScreen(): Boolean {
        return try {
            execCommand("input keyevent 26") // KEYCODE_POWER
            true
        } catch (e: Exception) {
            execCommand("locksettings set-disabled true && input keyevent 26")
        }
    }

    private fun takeScreenshot(): Boolean {
        return try {
            execCommand("screencap -p /sdcard/screenshot.png && input keyevent 26")
            true
        } catch (e: Exception) {
            execCommand("input swipe 500 1500 500 500") // Fallback gesture
            true
        }
    }

    private fun changeVolume(direction: String): Boolean {
        return try {
            val keyevent = when (direction.lowercase()) {
                "up" -> "input keyevent 24" // VOLUME_UP
                "down" -> "input keyevent 25" // VOLUME_DOWN
                "mute" -> "input keyevent 164" // VOLUME_MUTE
                else -> return false
            }
            execCommand(keyevent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun changeBrightness(direction: String): Boolean {
        return try {
            val cmd = when (direction.lowercase()) {
                "up" -> "input swipe 500 100 500 500" // Brightness slider up
                "down" -> "input swipe 500 500 500 100" // Brightness slider down
                else -> return false
            }
            execCommand(cmd)
            true
        } catch (e: Exception) {
            openSettings("android.settings.DISPLAY_SETTINGS")
            true
        }
    }

    private fun toggleAirplaneMode(value: String?): Boolean {
        return try {
            val cmd = when (value?.lowercase()) {
                "on", "enable" -> "settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE"
                "off", "disable" -> "settings put global airplane_mode_on 0 && am broadcast -a android.intent.action.AIRPLANE_MODE"
                else -> return false
            }
            execCommand(cmd)
        } catch (e: Exception) {
            openSettings("android.settings.AIRPLANE_MODE_SETTINGS")
            true
        }
    }

    private fun toggleNfc(value: String?): Boolean {
        return try {
            val cmd = when (value?.lowercase()) {
                "on", "enable" -> "svc nfc enable"
                "off", "disable" -> "svc nfc disable"
                else -> return false
            }
            execCommand(cmd)
        } catch (e: Exception) {
            openSettings("android.settings.NFC_SETTINGS")
            true
        }
    }

    private fun toggleLocation(value: String?): Boolean {
        return try {
            val cmd = when (value?.lowercase()) {
                "on", "enable" -> "settings put secure location_mode 3"
                "off", "disable" -> "settings put secure location_mode 0"
                else -> return false
            }
            execCommand(cmd)
        } catch (e: Exception) {
            openSettings("android.settings.LOCATION_SOURCE_SETTINGS")
            true
        }
    }

    private fun toggleHotspot(value: String?): Boolean {
        return try {
            val cmd = when (value?.lowercase()) {
                "on", "enable" -> "svc wifi hotspot enable"
                "off", "disable" -> "svc wifi hotspot disable"
                else -> return false
            }
            execCommand(cmd)
        } catch (e: Exception) {
            openSettings("android.settings.TETHER_SETTINGS")
            true
        }
    }

    private fun toggleDnd(value: String?): Boolean {
        return try {
            val cmd = when (value?.lowercase()) {
                "on", "enable" -> "settings put global zen_mode 1"
                "off", "disable" -> "settings put global zen_mode 0"
                else -> "input keyevent 164" // Toggle DND
            }
            execCommand(cmd)
        } catch (e: Exception) {
            openSettings("android.settings.ZEN_MODE_PRIORITY_SETTINGS")
            true
        }
    }

    private fun toggleNightMode(value: String?): Boolean {
        return try {
            val cmd = when (value?.lowercase()) {
                "on", "enable" -> "settings put secure ui_night_mode 2"
                "off", "disable" -> "settings put secure ui_night_mode 1"
                else -> "settings put secure ui_night_mode 0" // Toggle
            }
            execCommand(cmd)
        } catch (e: Exception) {
            openSettings("android.settings.DISPLAY_SETTINGS")
            true
        }
    }

    private fun toggleRotation(): Boolean {
        return try {
            execCommand("input keyevent 18") // ROTATION
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showPowerMenu(): Boolean {
        return try {
            execCommand("input swipe 500 1500 500 100 && sleep 0.5 && input swipe 500 1500 500 100")
            true
        } catch (e: Exception) {
            execCommand("input keyevent 26")
            true
        }
    }

    private fun openSettings(settings: String) {
        try {
            val intent = android.content.Intent(settings)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            intent.setClassName("com.android.settings", "com.android.settings.Settings")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun openApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun execCommand(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream.bufferedWriter()
            os.write("$cmd\n")
            os.write("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}