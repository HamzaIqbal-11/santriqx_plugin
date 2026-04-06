package com.HamzaIqbal.gyroscope_plugin

import android.content.Context
import android.hardware.SensorManager
import com.earnscape.gyroscopesdk.GyroscopeSDK
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * GyroscopeBridge
 * Sirf GyroscopeSDK ka bridge — sensor start/stop/data
 * Flutter EventChannel se live data stream karta hai
 */
class GyroscopeBridge(private val context: Context) {

    private var gyroscopeSDK: GyroscopeSDK = GyroscopeSDK(context)
    private var eventSink: EventChannel.EventSink? = null

    // ── EventChannel Stream Handler ───────────────────────────────────────────

    val streamHandler = object : EventChannel.StreamHandler {
        override fun onListen(args: Any?, sink: EventChannel.EventSink?) {
            eventSink = sink
        }
        override fun onCancel(args: Any?) {
            eventSink = null
        }
    }

    // ── Method Handlers ───────────────────────────────────────────────────────

    fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {

            // Gyroscope start karo
            "start" -> {
                val samplingRate = rateToConstant(call.argument<Int>("samplingRate") ?: 1)
                val autoLog = call.argument<Boolean>("autoLog") ?: false

                try {
                   gyroscopeSDK.start(
    samplingRate = samplingRate,
    autoLog = autoLog,
    onData = { data ->
        eventSink?.success(mapOf(
            "x"  to data.x.toDouble(),
            "y"  to data.y.toDouble(),
            "z"  to data.z.toDouble(),
            "ax" to data.ax.toDouble(),
            "ay" to data.ay.toDouble(),
            "az" to data.az.toDouble(),
            "timestampNs" to data.timestampNs,
            "isIdle" to data.isIdle,
            "sessionId" to ""
        ))
    }
)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("GYRO_START_ERROR", e.message, null)
                }
            }

            // Gyroscope stop karo
            "stop" -> {
                gyroscopeSDK.stop()
                result.success(null)
            }

            // Device mein gyroscope hai?
            "hasGyroscope" -> {
                result.success(GyroscopeSDK.hasGyroscope(context))
            }

            // Gyroscope chal raha hai?
            "isGyroActive" -> {
                result.success(gyroscopeSDK.isActive())
            }

            else -> result.notImplemented()
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun dispose() {
        gyroscopeSDK.stop()
        eventSink = null
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun rateToConstant(rateInt: Int) = when (rateInt) {
        0    -> SensorManager.SENSOR_DELAY_FASTEST
        1    -> SensorManager.SENSOR_DELAY_GAME
        2    -> SensorManager.SENSOR_DELAY_UI
        3    -> SensorManager.SENSOR_DELAY_NORMAL
        else -> SensorManager.SENSOR_DELAY_GAME
    }
}