package com.HamzaIqbal.gyroscope_plugin

import android.content.Context
import android.hardware.SensorManager
import com.earnscape.gyroscopesdk.GyroscopeSDK
import com.earnscape.gyroscopesdk.StreamingSDK
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * StreamingBridge
 * Sirf StreamingSDK ka bridge — session start/stop/status
 * GyroscopeSDK ko inject leta hai StreamingSDK ke liye
 */
class StreamingBridge(
    private val context: Context,
    private val gyroscopeSDK: GyroscopeSDK   // GyroscopeBridge wala same SDK
) {

    private val streamingSDK = StreamingSDK(context, gyroscopeSDK)

    // ── Method Handlers ───────────────────────────────────────────────────────

    fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {

            // Game session shuru karo — gyroscope + streaming dono ek saath
            "startSession" -> {
                val gameId     = call.argument<String>("gameId") ?: "default_game"
                val rateInt    = call.argument<Int>("samplingRate") ?: 1
                val autoLog    = call.argument<Boolean>("autoLog") ?: false
                val samplingRate = rateToConstant(rateInt)

                try {
                    val sessionId = streamingSDK.startSession(
                        gameId = gameId,
                        samplingRate = samplingRate,
                        autoLog = autoLog
                    )
                    result.success(sessionId)
                } catch (e: Exception) {
                    result.error("SESSION_START_ERROR", e.message, null)
                }
            }

            // Game session band karo — session data return karta hai
            "stopSession" -> {
                try {
                    val sessionResult = streamingSDK.stopSession()
                    result.success(mapOf(
                        "sessionId"     to sessionResult.sessionId,
                        "gameId"        to sessionResult.gameId,
                        "startTimeMs"   to sessionResult.startTimeMs,
                        "endTimeMs"     to sessionResult.endTimeMs,
                        "durationMs"    to sessionResult.durationMs,
                        "totalReadings" to sessionResult.totalReadings
                    ))
                } catch (e: Exception) {
                    result.error("SESSION_STOP_ERROR", e.message, null)
                }
            }

            // Session chal rahi hai?
            "isSessionActive" -> {
                result.success(streamingSDK.isActive())
            }

            // Current session ID kya hai?
            "getSessionId" -> {
                result.success(streamingSDK.getSessionId())
            }

            // Buffered readings lo (backend pe bhejne ke liye)
            "getBufferedReadings" -> {
                val readings = streamingSDK.getBufferedReadings().map { r ->
                    mapOf(
                        "x"           to r.x.toDouble(),
                        "y"           to r.y.toDouble(),
                        "z"           to r.z.toDouble(),
                        "timestampNs" to r.timestampNs,
                        "timestampMs" to r.timestampMs,
                        "isIdle"      to r.isIdle
                    )
                }
                result.success(readings)
            }

            else -> result.notImplemented()
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun dispose() {
        if (streamingSDK.isActive()) streamingSDK.stopSession()
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