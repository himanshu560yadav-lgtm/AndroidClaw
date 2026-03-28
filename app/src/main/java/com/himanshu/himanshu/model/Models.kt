package com.himanshu.himanshu.model

import android.content.Intent
import com.google.gson.annotations.SerializedName

// AI returned action model
data class AiAction(
    /**
     * Action type: "intent", "click", "swipe", "scroll", "system", "sh", "finish", "error"
     */
    @SerializedName("type")
    val type: String = "error",

    /**
     * AI's summary of current task progress (helps maintain conversation state)
     */
    @SerializedName("progress")
    val progress: String? = null,

    /**
     * Reason for executing this action (displayed in chat bubble in user's language)
     */
    @SerializedName("reason")
    val reason: String? = null,

    val data: String? = null, // Used for Uri (e.g., https://youtube.com)
    /**
     * Intent Action string (e.g., "android.intent.action.VIEW")
     */
    @SerializedName("action")
    val action: String? = null,

    /**
     * Intent parameters key-value pairs
     * AI returned numbers are usually parsed as Double, boolean values as Boolean
     */
    @SerializedName("extras")
    val extras: Map<String, Any>? = null,

    /**
     * X coordinate for click operation
     */
    @SerializedName("x")
    val x: Int = 0,

    /**
     * Y coordinate for click operation
     */
    @SerializedName("y")
    val y: Int = 0,

    /**
     * Scroll direction: "up", "down", "left", "right"
     */
    @SerializedName("direction")
    val direction: String? = null,

    /**
     * System action (for system action): torch, wifi, bluetooth, lock, screenshot, volume, etc.
     */
    @SerializedName("system_action")
    val systemAction: String? = null,

    /**
     * System action value (on/off/toggle, up/down, etc.)
     */
    @SerializedName("system_value")
    val systemValue: String? = null,

    /**
     * Shell script content (e.g., "input tap 500 500" or system settings commands)
     */
    @SerializedName("command")
    val command: String? = null,

    /**
     * Optional: Target app's package name (for explicit launch)
     */
    @SerializedName("package_name")
    val packageName: String? = null,

    /**
     * Optional: Target Activity's class name
     */
    @SerializedName("class_name")
    val className: String? = null
) {
    companion object {
        const val TYPE_INTENT = "intent"
        const val TYPE_CLICK = "click"
        const val TYPE_SWIPE = "swipe"
        const val TYPE_SCROLL = "scroll"
        const val TYPE_SYSTEM = "system"
        const val TYPE_SH = "sh"
        const val TYPE_FINISH = "finish"
        const val TYPE_ERROR = "error"
    }

    /**
     * Helper method: Fill extras from Map into Intent
     * Handles the common AI issue of Double to Int conversion
     */
    fun fillIntentExtras(intent: Intent) {
        extras?.forEach { (key, value) ->
            when (value) {
                is Boolean -> intent.putExtra(key, value)
                is String -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Double -> {
                    // AI returned JSON numbers are usually parsed as Double, try to convert to Int
                    if (value == value.toInt().toDouble()) {
                        intent.putExtra(key, value.toInt())
                    } else {
                        intent.putExtra(key, value)
                    }
                }
                is Long -> intent.putExtra(key, value)
                else -> intent.putExtra(key, value.toString())
            }
        }
    }
}

// UI state model
data class AgentUiState(
    val isRunning: Boolean = false,
    val status: String = "waiting command...",
    val userInput: String = "",
    val aiProvider: String = "Gemini" // Gemini, OpenAI, Local
)

// API configuration
data class ApiConfig(
    val provider: String = "Gemini", // Gemini, OpenAI, Ollama
    val apiKey: String = "",
    val apiUrl: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent",
    val model: String = "gemini-3-flash-preview"
)

// Chat message
data class ChatMessage(
    val role: String, // "user", "ai", "system"
    val content: String,
    val action: AiAction? = null,
    val timestamp: Long = System.currentTimeMillis()
)
