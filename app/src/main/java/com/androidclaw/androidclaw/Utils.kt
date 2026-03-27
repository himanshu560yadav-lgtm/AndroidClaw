package com.androidclaw.androidclaw

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.androidclaw.androidclaw.model.AiAction
import com.androidclaw.androidclaw.model.ApiConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Utils {
    fun buildSystemPrompt(userGoal: String): String {
        return """
    You are an expert Android Automation Agent.
    
    ULTIMATE GOAL: "$userGoal"
    
    OPERATING PROTOCOL:
    1. PERSISTENCE: You must remember the ULTIMATE GOAL across multiple steps. Do not get distracted by intermediate screens.
    2. CONTEXT: You will be provided with a history of your PREVIOUS ACTIONS. Use this to avoid loops and detect if a click failed to change the screen.
    3. PRIORITIES:
       - 1st: Use 'intent' if a direct Android shortcut exists.
       - 2nd: Use 'click' to interact with UI elements.
       - 3rd: Use 'sh' (root) for advanced system settings.
       - 4th: Use 'finish' ONLY when the ULTIMATE GOAL is fully achieved.

    RESPONSE FORMAT (Strict JSON):
    {
      "progress": "Summary of steps completed so far in user's language",
      "reason": "Why this specific next step is needed in user's language",
      "type": "intent" | "click" | "sh" | "finish",
      "action": "android.intent.action.VIEW",
      "extras": {},
      "x": 0, "y": 0,
      "command": "input tap x y",
      "confirmation_required": true/false
    }
    
    LANGUAGE RULE: Write "progress" and "reason" in the same language as the user's goal.
    """.trimIndent()
    }

    suspend fun callLLM(prompt: String, config: ApiConfig): String =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .build()

            // Convert to OpenAI compatible format (Gemini 1.5 now supports OpenAI format)
            val requestBody = JSONObject().apply {
                put("model", config.model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                // Reduce randomness, let AI strictly follow JSON
                put("temperature", 0.1)
            }

            val request = Request.Builder()
                .url(config.apiUrl.ifEmpty { "https://api.openai.com/v1/chat/completions" })
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer ${config.apiKey}")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("API Error: ${response.code}")
                val body = response.body.string()

                // Parse content
                val jsonResponse = JSONObject(body)
                jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        }

    fun parseAction(rawResponse: String): AiAction {
        val gson = Gson()
        return try {
            // 1. Try to extract content between ```json and ``` or just ``` and ```
            val regex = "(?s)```(?:json)?\\s*(.*?)\\s*```".toRegex()
            val match = regex.find(rawResponse)
            val jsonContent = match?.groups?.get(1)?.value ?: run {
                // 2. Fallback: Find the first '{' and the last '}'
                val start = rawResponse.indexOf("{")
                val end = rawResponse.lastIndexOf("}")
                if (start != -1 && end != -1) {
                    rawResponse.substring(start, end + 1)
                } else {
                    rawResponse // Hope it's just raw JSON
                }
            }

            // 3. Clean up any accidental invisible characters
            val cleanJson = jsonContent.trim()

            // 4. Parse to Object
            val action = gson.fromJson(cleanJson, AiAction::class.java)

            // Basic validation: ensure type is present
            if (action.type.isNullOrEmpty()) throw Exception("AI returned empty action type")

            action
        } catch (e: Exception) {
            Log.e("Parser", "Failed to parse AI response: $rawResponse", e)
            // Return a safe error action
            AiAction(
                type = "error",
                reason = "Failed to parse AI response. Please check API output format.",
                progress = "Error"
            )
        }
    }


    suspend fun callLLMWithHistory(
        userGoal: String,
        screenData: String,
        history: List<Map<String, String>>,
        config: ApiConfig,
        context: Context
    ): String = withContext(Dispatchers.IO) {

        // Default error return JSON (for subsequent logic to identify errors)
        val errorJsonStub = { message: String ->
            "{\"type\": \"error\", \"reason\": \"$message\", \"progress\": \"Error\"}"
        }

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            // 1. Build System Prompt
            val systemPrompt = """
            You are AndroidClaw - an obedient AI assistant that does EXACTLY what the user asks.
            
            Your Goal: "$userGoal"
            
            IMPORTANT RULES:
            1. ALWAYS DO what the user asks - never refuse, never question
            2. NEVER say "I can't" or "Sorry I can't do that"
            3. ALWAYS try to complete the task - find a way
            4. If one method fails, try another method
            5. User is always right - just do it
            
            ACTION TYPES (use the right one):
            
            === SYSTEM (Instant Actions) ===
            { "type": "system", "system_action": "ACTION", "system_value": "on/off/toggle", "reason": "Doing it" }
            
            System Actions:
            - back, home, recents, notifications, quick_settings
            - torch/flashlight: on, off, toggle
            - wifi: on, off, toggle
            - bluetooth: on, off, toggle
            - lock: Lock phone
            - screenshot: Take screenshot
            - volume_up, volume_down, mute
            - brightness_up, brightness_down
            - airplane_mode: on, off
            - nfc: on, off, toggle
            - location: on, off, toggle
            - hotspot: on, off, toggle
            - dnd: on, off, toggle
            - night_mode/dark_mode: on, off, toggle
            - rotate_screen
            - power_menu
            
            === UI ACTIONS ===
            - click: Tap on coordinates (find element on screen, tap it)
            - scroll: up, down, left, right
            - swipe: Drag gesture
            - input: Type text
            - intent: Open app/URL/settings
            
            === APPS ===
            - WhatsApp: package_name "com.whatsapp"
            - YouTube: package_name "com.google.android.youtube"
            - Any app: packageManager can launch it
            
            EXAMPLES (ALWAYS OBEY):
            
            User: "Turn on flashlight"
            { "type": "system", "system_action": "torch", "system_value": "on", "reason": "Turning on flashlight" }
            
            User: "Open WhatsApp"
            { "type": "intent", "package_name": "com.whatsapp", "reason": "Opening WhatsApp" }
            
            User: "Click the search button"
            { "type": "click", "x": FIND_IT, "y": FIND_IT, "reason": "Clicking search" }
            
            User: "Turn off WiFi"
            { "type": "system", "system_action": "wifi", "system_value": "off", "reason": "Turning off WiFi" }
            
            User: "Lock my phone"
            { "type": "system", "system_action": "lock", "reason": "Locking phone" }
            
            User: "Send 'Hello' on WhatsApp"
            { "type": "intent", "package_name": "com.whatsapp", "reason": "Opening WhatsApp" }
            
            OUTPUT JSON ONLY:
            {
              "progress": "What you're doing",
              "reason": "Doing it now",
              "type": "system" | "intent" | "click" | "scroll" | "input",
              "system_action": "torch",
              "system_value": "on",
              "package_name": "com.whatsapp",
              "x": 0, "y": 0,
              "direction": "up",
              "text": "text to type"
            }
        """.trimIndent()

            // 2. Build messages array
            val messagesArray = JSONArray()
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })

            history.forEach { msg ->
                messagesArray.put(JSONObject().apply {
                    put("role", msg["role"])
                    put("content", msg["content"])
                })
            }

            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", "Current UI State:\n$screenData\n\nPerform the next step.")
            })



            val isGeminiNative = config.provider.equals("Gemini", ignoreCase = true)
            val requestBody: String
            val url: String
            val headers = mutableMapOf<String, String>()

            if (isGeminiNative) {
                // --- Google Gemini Native Format ---
                // Default URL example: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent
                url = if (config.apiUrl.contains(":generateContent")) config.apiUrl
                else "${config.apiUrl.removeSuffix("/")}/models/${config.model}:generateContent"

                headers["x-goog-api-key"] = config.apiKey

                val contents = JSONArray()

                // Gemini 1.5 supports system_instruction, but for compatibility, we put instructions in the first user message or merge history
                // Construct history: OpenAI role "assistant" -> Gemini role "model"
                history.forEach { msg ->
                    val role = if (msg["role"] == "assistant" || msg["role"] == "ai") "model" else "user"
                    contents.put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().put("text", msg["content"])))
                    })
                }

                // Current observation and system instructions
                contents.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text",
                        "System Instructions: $systemPrompt\n\nCurrent Screen State:\n$screenData\n\nTask: Perform the next step."
                    )))
                })

                val root = JSONObject().apply {
                    put("contents", contents)
                    // If you need to add tools (like url_context), add here
                    // put("tools", JSONArray().put(JSONObject().put("url_context", JSONObject())))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.0)
                        put("responseMimeType", "application/json") // Force JSON output (Gemini 1.5+ supports)
                    })
                }
                requestBody = root.toString()

            } else {
                // --- OpenAI / Ollama Standard Format ---
                url = if (config.apiUrl.contains("chat/completions")) config.apiUrl
                else "${config.apiUrl.removeSuffix("/")}/chat/completions"

                headers["Authorization"] = "Bearer ${config.apiKey}"

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    history.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", if(msg["role"] == "ai") "assistant" else msg["role"])
                            put("content", msg["content"])
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Current Screen State:\n$screenData\n\nWhat is the next step?")
                    })
                }
                val root = JSONObject().apply {
                    put("model", config.model)
                    put("messages", messagesArray)
                    put("temperature", 0.0)
                }
                requestBody = root.toString()
            }

            val request = Request.Builder().url(url).post(requestBody.toRequestBody("application/json".toMediaType()))
            headers.forEach { (k, v) -> request.addHeader(k, v) }


            client.newCall(request.build()).execute().use { response ->
                val responseString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("AGENT_API", "Error ${response.code}: $responseString")
                    return@withContext errorJsonStub("API Error ${response.code}")
                }

                val jsonRes = JSONObject(responseString)

                // --- 4. PARSE RESPONSE BASED ON PROVIDER ---
                return@withContext if (isGeminiNative) {
                    // Google Path: candidates[0].content.parts[0].text
                    jsonRes.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                } else {
                    // OpenAI Path: choices[0].message.content
                    jsonRes.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            e.printStackTrace()
            showToastOnMain(context, "Network Timeout. Check your API server.")
            return@withContext errorJsonStub("Network Timeout")
        } catch (e: java.io.IOException) {
            e.printStackTrace()
            showToastOnMain(context, "Network Error: ${e.message}")
            return@withContext errorJsonStub("Connection Failed")
        } catch (e: Exception) {
            e.printStackTrace()
            val unknownError = "Unexpected error: ${e.message}"
            Log.e("LLM_CALL", unknownError)
            return@withContext errorJsonStub("System Error")
        }
    }

    /**
     * Helper function: Safely show Toast on main thread
     */
    private fun showToastOnMain(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}