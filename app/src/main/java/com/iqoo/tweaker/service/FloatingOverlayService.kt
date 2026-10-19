package com.iqoo.tweaker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.iqoo.tweaker.core.RmsController
import kotlinx.coroutines.*
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var autoKeep144: Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }

        overlayView = TextView(this).apply {
            text = "144Hz Monitor..."
            setTextColor(Color.parseColor("#00FF88"))
            setBackgroundColor(Color.parseColor("#CC121212"))
            textSize = 11f
            setPadding(24, 12, 24, 12)

            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        windowManager?.updateViewLayout(this, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Toque rápido reaplica o voto de 144Hz imediatamente
                        if (abs(event.rawX - touchX) < 10 && abs(event.rawY - touchY) < 10) {
                            scope.launch {
                                RmsController.acquire144ViaBinderDirect(144)
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (_: Throwable) {
        }

        startMonitoringLoop()
    }

    private fun startMonitoringLoop() {
        scope.launch {
            var tick = 0
            while (isActive) {
                // Utiliza consulta rápida por Binder na maioria dos ciclos (0.1ms) e completa a cada 3 ciclos
                val status = RmsController.queryStatus(applicationContext, fastMode = (tick % 3 != 0))

                // Watchdog automático em jogos: se a tela cair para 120Hz por reqFps, reaplica o voto
                if (autoKeep144 && !status.isLocked144) {
                    RmsController.acquire144ViaBinderDirect(144)
                }

                overlayView?.text = "${status.activeRate.replace("rate=", "")}Hz | ${status.reason}"
                overlayView?.setTextColor(
                    if (status.isLocked144) Color.parseColor("#00FF88") else Color.parseColor("#FFAA00")
                )

                tick++
                delay(2000)
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "iqoo_overlay_channel"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Monitor 144Hz", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Monitor 144Hz Nativo Ativo")
            .setContentText("Toque no overlay flutuante para forçar 144Hz em jogos")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(144, notification)
    }

    override fun onDestroy() {
        scope.cancel()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Throwable) {
            }
        }
        super.onDestroy()
    }
}
