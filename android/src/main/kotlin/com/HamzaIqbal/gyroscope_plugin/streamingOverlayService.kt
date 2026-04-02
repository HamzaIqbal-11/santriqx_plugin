package com.HamzaIqbal.gyroscope_plugin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * StreamingOverlayService
 * EarnSpace pattern — game ke upar floating overlay
 * Sirf timer dikhata hai, drag bhi kar sako
 */
class StreamingOverlayService : Service() {

    companion object {
        const val CHANNEL_ID      = "StreamingOverlayChannel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START    = "START_OVERLAY"
        const val ACTION_STOP     = "STOP_OVERLAY"
        private const val TAG     = "StreamingOverlayService"
    }

    private var windowManager: WindowManager? = null

    // ── Overlay views (EarnSpace style) ──────────────────────────────────────
    private var overlayView: LinearLayout? = null
    private lateinit var timerTextView: TextView
    private lateinit var redDotView: View

    // ── Timer state ───────────────────────────────────────────────────────────
    private var startTimeMs = 0L
    private val mainHandler  = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var dotVisible = true

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                if (canDrawOverlays()) {
                    showOverlay()
                } else {
                    Log.e(TAG, "❌ No overlay permission!")
                    openOverlayPermissionSettings()
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                removeOverlay()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Permission ────────────────────────────────────────────────────────────

    private fun canDrawOverlays() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true

    private fun openOverlayPermissionSettings() {
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .also { startActivity(it) }
    }

    // ── Show Overlay (EarnSpace showOverlayIcon pattern) ─────────────────────

    private fun showOverlay() {
        if (overlayView != null) return

        try {
            val dm = resources.displayMetrics

            // ── Outer container — rounded dark pill ──
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = dp(18).toFloat()
                    setColor(Color.parseColor("#CC4B4A4A"))   // same as EarnSpace
                }
                elevation = 12f
            }

            // ── App icon (EarnSpace uses app icon) ──
            val iconContainer = FrameLayout(this).apply {
                val sz = dp(36)
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                    gravity = Gravity.CENTER
                }
            }

            val appIcon = ImageView(this).apply {
                val drawable = packageManager.getApplicationIcon(applicationContext.applicationInfo)
                setImageDrawable(drawable)
                layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER)
            }

            // ── Red dot (pulsing live indicator) ──
            redDotView = View(this).apply {
                background = GradientDrawable().apply {
                    shape    = GradientDrawable.OVAL
                    setColor(Color.RED)
                }
                layoutParams = FrameLayout.LayoutParams(dp(10), dp(10)).apply {
                    gravity    = Gravity.TOP or Gravity.END
                    topMargin  = dp(0)
                    marginEnd  = dp(0)
                }
            }

            iconContainer.addView(appIcon)
            iconContainer.addView(redDotView)

            // ── Timer text ──
            timerTextView = TextView(this).apply {
                text      = "00:00"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface  = Typeface.MONOSPACE
                gravity   = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }

            container.addView(iconContainer)
            container.addView(timerTextView)
            overlayView = container

            // ── Window params (EarnSpace gravity TOP END) ──
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(16)
                y = dp(50)
            }

            windowManager?.addView(container, params)
            Log.d(TAG, "✅ Overlay added to window")

            // ── Drag (EarnSpace DragTouchListener pattern) ──
            container.setOnTouchListener(DragTouchListener(params))

            // ── Start timer + pulse ──
            startTimeMs = System.currentTimeMillis()
            startTimer()
            startDotPulse()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay: ${e.message}")
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (overlayView == null) return
                val elapsed = System.currentTimeMillis() - startTimeMs
                timerTextView.text = formatTime(elapsed)
                mainHandler.postDelayed(this, 1000)
            }
        }
        mainHandler.post(timerRunnable!!)
    }

    private fun startDotPulse() {
        val pulse = object : Runnable {
            override fun run() {
                if (overlayView == null) return
                dotVisible = !dotVisible
                redDotView.visibility = if (dotVisible) View.VISIBLE else View.INVISIBLE
                mainHandler.postDelayed(this, 800)
            }
        }
        mainHandler.postDelayed(pulse, 800)
    }

    // ── Remove ────────────────────────────────────────────────────────────────

    private fun removeOverlay() {
        timerRunnable?.let { mainHandler.removeCallbacks(it) }
        timerRunnable = null
        overlayView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "✅ Overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay: ${e.message}")
            }
            overlayView = null
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gyro Streaming Active")
            .setContentText("Gyroscope data recording...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "Gyroscope Overlay", NotificationManager.IMPORTANCE_LOW)
                .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / 60000) % 60
        val h = ms / 3600000
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    // ── DragTouchListener (EarnSpace pattern) ─────────────────────────────────

    private inner class DragTouchListener(
        private val lp: WindowManager.LayoutParams
    ) : View.OnTouchListener {

        private var initX = 0;  private var initY = 0
        private var initTx = 0f; private var initTy = 0f
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = lp.x; initY = lp.y
                    initTx = e.rawX; initTy = e.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - initTx
                    val dy = e.rawY - initTy
                    if (!dragging && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) dragging = true
                    if (dragging) {
                        lp.x = initX - dx.toInt()
                        lp.y = initY + dy.toInt()
                        overlayView?.let { windowManager?.updateViewLayout(it, lp) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> return dragging
            }
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}