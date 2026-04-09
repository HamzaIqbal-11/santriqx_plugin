package com.HamzaIqbal.gyroscope_plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.earnscape.gyroscopesdk.StreamingSDK
import io.flutter.plugin.common.MethodChannel

class GyroscopeReceiver(private val methodChannel: MethodChannel) {

    private fun ui(action: () -> Unit) = Handler(Looper.getMainLooper()).post(action)

    private val gyroDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StreamingSDK.ACTION_GYRO_DATA) return
            ui {
                methodChannel.invokeMethod("onGyroData", mapOf(
                    "x"           to intent.getFloatExtra("x", 0f).toDouble(),
                    "y"           to intent.getFloatExtra("y", 0f).toDouble(),
                    "z"           to intent.getFloatExtra("z", 0f).toDouble(),
                    "timestampNs" to intent.getLongExtra("timestampNs", 0L),
                    "sessionId"   to (intent.getStringExtra("sessionId") ?: ""),
                    "gameId"      to (intent.getStringExtra("gameId") ?: ""),
                    "isIdle"      to intent.getBooleanExtra("isIdle", false),
                    "ax"          to intent.getFloatExtra("ax", 0f).toDouble(),
                    "ay"          to intent.getFloatExtra("ay", 0f).toDouble(),
                    "az"          to intent.getFloatExtra("az", 0f).toDouble()
                ))
            }
        }
    }

    private val gyroIdleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StreamingSDK.ACTION_GYRO_IDLE) return
            ui {
                methodChannel.invokeMethod("onGyroIdle", mapOf(
                    "sessionId"   to (intent.getStringExtra("sessionId") ?: ""),
                    "timestampMs" to intent.getLongExtra("timestampMs", 0L)
                ))
            }
        }
    }

    private val gyroActiveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StreamingSDK.ACTION_GYRO_ACTIVE) return
            ui {
                methodChannel.invokeMethod("onGyroActive", mapOf(
                    "sessionId"   to (intent.getStringExtra("sessionId") ?: ""),
                    "timestampMs" to intent.getLongExtra("timestampMs", 0L)
                ))
            }
        }
    }

    private val sessionStartedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StreamingSDK.ACTION_SESSION_STARTED) return
            ui {
                methodChannel.invokeMethod("onSessionStarted", mapOf(
                    "sessionId"   to (intent.getStringExtra("sessionId") ?: ""),
                    "gameId"      to (intent.getStringExtra("gameId") ?: ""),
                    "startTimeMs" to intent.getLongExtra("startTimeMs", 0L)
                ))
            }
        }
    }

    private val sessionStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StreamingSDK.ACTION_SESSION_STOPPED) return
            ui {
                methodChannel.invokeMethod("onSessionStopped", mapOf(
                    "sessionId"     to (intent.getStringExtra("sessionId") ?: ""),
                    "gameId"        to (intent.getStringExtra("gameId") ?: ""),
                    "startTimeMs"   to intent.getLongExtra("startTimeMs", 0L),
                    "endTimeMs"     to intent.getLongExtra("endTimeMs", 0L),
                    "durationMs"    to intent.getLongExtra("durationMs", 0L),
                    "totalReadings" to intent.getIntExtra("totalReadings", 0)
                ))
            }
        }
    }

    fun registerReceivers(context: Context) {
        val lbm = LocalBroadcastManager.getInstance(context)
        lbm.registerReceiver(gyroDataReceiver,       IntentFilter(StreamingSDK.ACTION_GYRO_DATA))
        lbm.registerReceiver(gyroIdleReceiver,       IntentFilter(StreamingSDK.ACTION_GYRO_IDLE))
        lbm.registerReceiver(gyroActiveReceiver,     IntentFilter(StreamingSDK.ACTION_GYRO_ACTIVE))
        lbm.registerReceiver(sessionStartedReceiver, IntentFilter(StreamingSDK.ACTION_SESSION_STARTED))
        lbm.registerReceiver(sessionStoppedReceiver, IntentFilter(StreamingSDK.ACTION_SESSION_STOPPED))
        Log.d("GyroscopeReceiver", "All receivers registered ✅")
    }

    fun unregisterReceivers(context: Context) {
        val lbm = LocalBroadcastManager.getInstance(context)
        lbm.unregisterReceiver(gyroDataReceiver)
        lbm.unregisterReceiver(gyroIdleReceiver)
        lbm.unregisterReceiver(gyroActiveReceiver)
        lbm.unregisterReceiver(sessionStartedReceiver)
        lbm.unregisterReceiver(sessionStoppedReceiver)
        Log.d("GyroscopeReceiver", "All receivers unregistered ✅")
    }
}