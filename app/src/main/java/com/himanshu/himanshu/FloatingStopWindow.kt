package com.himanshu.himanshu

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button

class FloatingStopWindow(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var stopButton: View? = null

    fun show(onStop: () -> Unit) {
        if (stopButton != null) return

        // 1. Create the button first
        val btn = Button(context)
        btn.text = "STOP"
        btn.setBackgroundColor(Color.RED)
        btn.setTextColor(Color.WHITE)
        btn.setOnClickListener { onStop() }
        stopButton = btn

        // 2. Explicitly create LayoutParams
        val params = WindowManager.LayoutParams()

        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT

        // Set level: must use TYPE_APPLICATION_OVERLAY
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        // Set flags: do not intercept focus
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        // Set format: transparent
        params.format = PixelFormat.TRANSLUCENT

        // Set position
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 20
        params.y = 200

        // 3. Add to window
        try {
            windowManager.addView(btn, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismiss() {
        stopButton?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stopButton = null
        }
    }
}
