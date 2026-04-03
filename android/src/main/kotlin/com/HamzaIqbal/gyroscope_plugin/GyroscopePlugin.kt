package com.HamzaIqbal.gyroscope_plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.earnscape.gyroscopesdk.GyroscopeSDK
import com.earnscape.gyroscopesdk.SantriqxSDK
import com.earnscape.gyroscopesdk.TransactionService
import com.earnscape.gyroscopesdk.DeviceService
import com.earnscape.gyroscopesdk.StreamingSDK
import com.example.gyroscope.kyc.FaceRecognitionActivity
import com.example.gyroscope.kyc.KycActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class GyroscopePlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener, PluginRegistry.RequestPermissionsResultListener {

    companion object {
        private const val TAG = "GyroscopePlugin"
        private const val MEDIA_PROJECTION_REQUEST_CODE = 2001
        private const val FACE_RECOGNITION_REQUEST_CODE = 2004
        private const val KYC_REQUEST_CODE = 2005
    }

    private lateinit var methodChannel: MethodChannel
    private lateinit var overlayChannel: MethodChannel
    private lateinit var eventChannel: EventChannel

    private var gyroscopeSDK: GyroscopeSDK? = null
    private var gyroscopeBridge: GyroscopeBridge? = null
    private var streamingBridge: StreamingBridge? = null
    private var gyroscopeReceiver: GyroscopeReceiver? = null
    private var eventSink: EventChannel.EventSink? = null
    private var context: Context? = null

    private var activity: Activity? = null
    private var activityBinding: ActivityPluginBinding? = null

    // Pending results
    private var pendingResult: Result? = null
    private var pendingFaceResult: Result? = null
    private var pendingKycResult: Result? = null

    private var savedProjectionResultCode: Int = 0
private var savedProjectionData: Intent? = null

    // Pending stream config (stored until permission granted)
    private var pendingGameId: String? = null
    private var pendingStreamUrl: String? = null
    private var pendingTargetPackage: String? = null
    private var pendingStreamTitle: String? = null
    private var pendingPlayerId: String? = null
    private var pendingAudio: String? = null
    private var pendingVideoEnabled: String? = null
    private var pendingStreamKey: String? = null
    private var pendingPlayerName: String? = null
    private var pendingGameName: String? = null
    private var pendingMinStreamSpeed: String? = null
    private var pendingBadConnectionTimeout: String? = null

    // ── FlutterPlugin ─────────────────────────────────────────────────────────

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext

        gyroscopeSDK = GyroscopeSDK(binding.applicationContext)
        gyroscopeBridge = GyroscopeBridge(binding.applicationContext)
        streamingBridge = StreamingBridge(binding.applicationContext, gyroscopeSDK!!)

        methodChannel = MethodChannel(binding.binaryMessenger, "gyroscope_plugin/methods")
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(binding.binaryMessenger, "gyroscope_plugin/events")
        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(args: Any?, sink: EventChannel.EventSink?) { eventSink = sink }
            override fun onCancel(args: Any?) { eventSink = null }
        })

        overlayChannel = MethodChannel(binding.binaryMessenger, "gyroscope_plugin/overlay")
        overlayChannel.setMethodCallHandler { call, result ->
            when (call.method) {

                // ── Overlay Permission ──
                "checkOverlayPermission" -> {
                    val has = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        android.provider.Settings.canDrawOverlays(context) else true
                    result.success(has)
                }

                "requestOverlayPermission" -> {
                    context?.let { ctx ->
                        try {
                            val intent = Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${ctx.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                            result.success(null)
                        } catch (e: Exception) { result.error("OVERLAY_ERROR", e.message, null) }
                    } ?: result.error("OVERLAY_ERROR", "Context is null", null)
                }

                // ── Overlay Start/Stop ──
                "startOverlay" -> {
                    try {
                        val intent = Intent(context, StreamingOverlayService::class.java).apply {
                            action = StreamingOverlayService.ACTION_START
                            putExtra("startTimeMs", call.argument<Long>("startTimeMs") ?: System.currentTimeMillis())
                        }
                        context?.startService(intent)
                        result.success(null)
                    } catch (e: Exception) { result.error("OVERLAY_ERROR", e.message, null) }
                }

                "stopOverlay" -> {
                    try {
                        context?.startService(Intent(context, StreamingOverlayService::class.java).apply {
                            action = StreamingOverlayService.ACTION_STOP
                        })
                        result.success(null)
                    } catch (e: Exception) { result.error("OVERLAY_ERROR", e.message, null) }
                }

                // ── SantriqxSDK Init ──
                "initSdk" -> {
    val appId = call.argument<String>("appId") ?: ""
    val apiSecretKey = call.argument<String>("apiSecretKey") ?: ""
   
    val orgId = call.argument<String>("organizationId") ?: ""
    val prodId = call.argument<String>("productId") ?: ""
    SantriqxSDK.init(appId, apiSecretKey, orgId, prodId)
    result.success(mapOf("success" to true,"baseUrl" to SantriqxSDK.baseUrl))
}

                // ── Fetch Config ──
                "fetchConfig" -> {
                    Log.d(TAG, "fetchConfig called")
                    SantriqxSDK.fetchConfig { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                // ── Check Service Active ──
                "isServiceActive" -> {
                    val service = call.argument<String>("service") ?: ""
                    result.success(mapOf("active" to SantriqxSDK.isServiceActive(service)))
                }

                // ── Register Device (SDK handles API) ──
                "registerDevice" -> {
                    Log.d(TAG, "registerDevice called")
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    SantriqxSDK.registerDevice(context!!) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                // ── Start Stream (SDK calls backend, returns RTMP) ──
                "startStream" -> {
                    Log.d(TAG, "startStream called")
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    SantriqxSDK.startStream(context!!) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                // ── Get Stream Details (recording URL) ──
                "getStreamDetails" -> {
                    Log.d(TAG, "getStreamDetails called")
                    val streamKey = call.argument<String>("streamKey") ?: ""
                    SantriqxSDK.getStreamDetails(streamKey) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                // ── Upload Face (SDK handles API) ──
                "uploadFace" -> {
                    Log.d(TAG, "uploadFace called")
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    val imagePath = call.argument<String>("imagePath") ?: ""
                    val username = call.argument<String>("username") ?: ""
                    SantriqxSDK.uploadFace(context!!, imagePath, username) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                // ── Record Transaction (SDK validates + calls API) ──
                "recordTransaction" -> {
                    Log.d(TAG, "recordTransaction called")
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    val fields = call.argument<Map<String, String>>("fields") ?: emptyMap()
                    SantriqxSDK.recordTransaction(context!!, fields) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                // ── Get Transactions ──
                "getTransactions" -> {
                    Log.d(TAG, "getTransactions called")
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    SantriqxSDK.getTransactions(context!!) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                // ── Send Sensor Data (SDK calls API) ──
                "sendSensorData" -> {
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    SantriqxSDK.sendSensorData(
                        context!!,
                        call.argument<Double>("gyro_x") ?: 0.0,
                        call.argument<Double>("gyro_y") ?: 0.0,
                        call.argument<Double>("gyro_z") ?: 0.0,
                        call.argument<Double>("accel_x") ?: 0.0,
                        call.argument<Double>("accel_y") ?: 0.0,
                        call.argument<Double>("accel_z") ?: 0.0
                    ) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                // ── Face Recognition (just capture, return path) ──────────
                "openFaceRecognition" -> {
                    Log.d(TAG, "openFaceRecognition called")

                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity not available", null)
                        return@setMethodCallHandler
                    }

                    // Permission check
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CAMERA)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        result.error("PERMISSION_DENIED", "Camera permission required. Call Permission.camera.request() first.", null)
                        return@setMethodCallHandler
                    }

                    pendingFaceResult = result
                    try {
                        val intent = Intent(activity, FaceRecognitionActivity::class.java)
                        activity!!.startActivityForResult(intent, FACE_RECOGNITION_REQUEST_CODE)
                    } catch (e: Exception) {
                        pendingFaceResult = null
                        result.error("LAUNCH_ERROR", e.message, null)
                    }
                }

                // ── KYC (opens home screen with doc + video cards) ──
                "openKyc" -> {
                    Log.d(TAG, "openKyc called")
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity not available", null)
                        return@setMethodCallHandler
                    }

                    // Permission check — KYC needs camera + mic
                    val camOk = androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val micOk = androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val missingPerms = mutableListOf<String>()
                    if (!camOk) missingPerms.add("CAMERA")
                    if (!micOk) missingPerms.add("MICROPHONE")
                    if (missingPerms.isNotEmpty()) {
                        result.error("PERMISSION_DENIED", "Missing permissions: ${missingPerms.joinToString(", ")}. Request them first.", null)
                        return@setMethodCallHandler
                    }

                    pendingKycResult = result
                    try {
                        val intent = Intent(activity, KycActivity::class.java)
                        activity!!.startActivityForResult(intent, KYC_REQUEST_CODE)
                    } catch (e: Exception) {
                        pendingKycResult = null
                        result.error("LAUNCH_ERROR", e.message, null)
                    }
                }

                // ── Check Permissions (inline, no PermissionHelper) ──
                "checkPermissions" -> {
                    Log.d(TAG, "checkPermissions called")
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    fun has(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(context!!, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val cam = has(android.Manifest.permission.CAMERA)
                    val mic = has(android.Manifest.permission.RECORD_AUDIO)
                    val loc = has(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    result.success(mapOf(
                        "allGranted" to (cam && mic && loc),
                        "camera" to cam, "microphone" to mic, "locationFine" to loc
                    ))
                }

                // ── Transaction (validate + structure only, API on Dart side) ──
                "validateTransaction" -> {
                    Log.d(TAG, "validateTransaction called")
                    val fieldsMap = call.argument<Map<String, String>>("fields") ?: emptyMap()
                    val txResult = TransactionService.validateTransaction(fieldsMap)
                    result.success(txResult)
                }

                // ── Device Info (ID + details + location) ──
                "getDeviceInfo" -> {
                    Log.d(TAG, "getDeviceInfo called")
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    DeviceService.getFullDeviceInfo(context!!) { info ->
                        result.success(info)
                    }
                }

                "getDeviceId" -> {
                    Log.d(TAG, "getDeviceId called")
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    result.success(mapOf("deviceId" to DeviceService.getOrCreateDeviceId(context!!)))
                }

                "getLocation" -> {
                    Log.d(TAG, "getLocation called")
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }

                    // Permission check
                    val fineOk = androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val coarseOk = androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!fineOk && !coarseOk) {
                        result.error("PERMISSION_DENIED", "Location permission required. Call Permission.location.request() first.", null)
                        return@setMethodCallHandler
                    }

                    DeviceService.getLocation(context!!) { location ->
                        result.success(location)
                    }
                }

                // ── Start streaming with saved permission (no new dialog) ──
"startStreamingWithUrl" -> {
    Log.d(TAG, "startStreamingWithUrl called")
    val streamUrl = call.argument<String>("streamUrl") ?: ""
    
    if (streamUrl.isEmpty()) {
        result.error("NO_URL", "streamUrl required", null)
        return@setMethodCallHandler
    }

    // Update pending config with new URL
    pendingStreamUrl = streamUrl
    pendingStreamKey = call.argument<String>("streamKey") ?: pendingStreamKey
    pendingAudio = call.argument<String>("audio") ?: pendingAudio ?: "false"

    try {
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START                          // ← FIX 1
            putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, savedProjectionResultCode)  // ← FIX 2
            putExtra(ScreenRecordingService.EXTRA_DATA, savedProjectionData)               // ← FIX 3
            putExtra(ScreenRecordingService.EXTRA_GAME_ID, pendingGameId ?: "recording")
            putExtra(ScreenRecordingService.EXTRA_STREAM_URL, streamUrl)
            putExtra("streamKey", pendingStreamKey)
            putExtra("audio", pendingAudio)
            putExtra("targetPackageName", pendingTargetPackage ?: "")
            putExtra("streamTitle", pendingStreamTitle ?: "recording")
            putExtra("playerId", pendingPlayerId ?: "")
            putExtra("videoEnabled", pendingVideoEnabled ?: "false")
            putExtra("playerName", pendingPlayerName ?: "")
            putExtra("gameName", pendingGameName ?: "Recording")
            putExtra("minimumStreamSpeed", pendingMinStreamSpeed ?: "1")
            putExtra("badConnectionTimeout", pendingBadConnectionTimeout ?: "30")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(intent)
        } else {
            context?.startService(intent)
        }
        result.success(mapOf("success" to true))
    } catch (e: Exception) {
        result.error("STREAM_ERROR", e.message, null)
    }
}

                // ── MediaProjection (via SDK) ──
                "requestMediaProjection" -> {
                    Log.d(TAG, "requestMediaProjection called")

                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity not available", null)
                        return@setMethodCallHandler
                    }

                    // Permission check — mic needed for audio recording
                   

                    pendingResult = result

                    // Store stream config
                    pendingGameId = call.argument<String>("gameId") ?: "unknown"
                    pendingStreamUrl = call.argument<String>("streamUrl")
                    pendingTargetPackage = call.argument<String>("targetPackageName") ?: ""
                    pendingStreamTitle = call.argument<String>("streamTitle") ?: ""
                    pendingPlayerId = call.argument<String>("playerId") ?: ""
                    pendingAudio = call.argument<String>("audio") ?: "true"
                    pendingVideoEnabled = call.argument<String>("videoEnabled") ?: "false"
                    pendingStreamKey = call.argument<String>("streamKey") ?: ""
                    pendingPlayerName = call.argument<String>("playerName") ?: ""
                    pendingGameName = call.argument<String>("gameName") ?: ""
                    pendingMinStreamSpeed = call.argument<String>("minimumStreamSpeed") ?: "1"
                    pendingBadConnectionTimeout = call.argument<String>("badConnectionTimeout") ?: "30"

                    // SDK handles permission request
                    StreamingSDK.requestMediaProjectionPermission(activity!!, MEDIA_PROJECTION_REQUEST_CODE)
                }

                // ── Stop Streaming ──
               // ── Stop Streaming ──
"stopStreaming" -> {
    try {
        // 1. Stop recording service
        context?.startService(Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP
        })
        
        // 2. Call stream ended API (if streamKey exists)
        val streamKey = pendingStreamKey
        if (!streamKey.isNullOrEmpty()) {
            SantriqxSDK.endStream(streamKey) { res ->
                Log.d(TAG, "Stream ended API: $res")
            }
        }
        
        result.success(null)
    } catch (e: Exception) { result.error("STOP_ERROR", e.message, null) }
}

                "isStreaming" -> result.success(ScreenRecordingService.isRunning)

                else -> result.notImplemented()
            }
        }

        gyroscopeReceiver = GyroscopeReceiver(methodChannel)
        gyroscopeReceiver?.registerReceivers(binding.applicationContext)
    }

    // ── Start ScreenRecordingService (called after SDK grants permissions) ────

    private fun startStreamingService(resultCode: Int, data: Intent?) {
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START
            putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordingService.EXTRA_DATA, data)
            putExtra(ScreenRecordingService.EXTRA_GAME_ID, pendingGameId)
            putExtra(ScreenRecordingService.EXTRA_STREAM_URL, pendingStreamUrl)
            putExtra("targetPackageName", pendingTargetPackage)
            putExtra("streamTitle", pendingStreamTitle)
            putExtra("playerId", pendingPlayerId)
            putExtra("audio", pendingAudio)
            putExtra("videoEnabled", pendingVideoEnabled)
            putExtra("streamKey", pendingStreamKey)
            putExtra("playerName", pendingPlayerName)
            putExtra("gameName", pendingGameName)
            putExtra("minimumStreamSpeed", pendingMinStreamSpeed)
            putExtra("badConnectionTimeout", pendingBadConnectionTimeout)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(intent)
        } else {
            context?.startService(intent)
        }

        pendingResult?.success("recording_started")
        pendingResult = null
    }

    // ── Activity Results ─────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {

        // MediaProjection result (via SDK)
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            StreamingSDK.handleMediaProjectionResult(
                requestCode = requestCode,
                expectedCode = MEDIA_PROJECTION_REQUEST_CODE,
                resultCode = resultCode,
                data = data,
                onGranted = { code, projData ->
    Log.d(TAG, "✅ MediaProjection granted via SDK")
    savedProjectionResultCode = code        // ← ADD
    savedProjectionData = projData          // ← ADD
    startStreamingService(code, projData)
},
                onDenied = {
                    Log.e(TAG, "❌ MediaProjection denied")
                    pendingResult?.error("DENIED", "MediaProjection permission denied", null)
                    pendingResult = null
                }
            )
            return true
        }

        // Face Recognition result
        if (requestCode == FACE_RECOGNITION_REQUEST_CODE) {
            val faceResult = pendingFaceResult
            pendingFaceResult = null

            if (faceResult != null) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    faceResult.success(mapOf(
                        "success" to true,
                        "imagePath" to (data.getStringExtra("imagePath") ?: "")
                    ))
                } else {
                    faceResult.success(mapOf("success" to false, "error" to "cancelled"))
                }
            }
            return true
        }

        // KYC result
        if (requestCode == KYC_REQUEST_CODE) {
            val r = pendingKycResult
            pendingKycResult = null
            if (r != null) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    r.success(mapOf(
                        "success" to true,
                        "docType" to (data.getStringExtra(KycActivity.RESULT_DOC_TYPE) ?: ""),
                        "frontPhoto" to (data.getStringExtra(KycActivity.RESULT_FRONT_PHOTO) ?: ""),
                        "backPhoto" to (data.getStringExtra(KycActivity.RESULT_BACK_PHOTO) ?: ""),
                        "selfieVideo" to (data.getStringExtra(KycActivity.RESULT_SELFIE_VIDEO) ?: "")
                    ))
                } else {
                    r.success(mapOf("success" to false, "error" to "cancelled"))
                }
            }
            return true
        }

        return false
    }

    // ── Permission Results ──────────────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ): Boolean {
        return false
    }

    // ── MethodCallHandler (main channel) ──

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "start", "stop", "hasGyroscope", "isGyroActive" ->
                gyroscopeBridge?.handleMethodCall(call, result) ?: result.error("NOT_INIT", "GyroscopeBridge not ready", null)
            "startSession", "stopSession", "isSessionActive", "getSessionId", "getBufferedReadings" ->
                streamingBridge?.handleMethodCall(call, result) ?: result.error("NOT_INIT", "StreamingBridge not ready", null)
            else -> result.notImplemented()
        }
    }

    // ── ActivityAware ─────────────────────────────────────────────────────────

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity; activityBinding = binding
        binding.addActivityResultListener(this); binding.addRequestPermissionsResultListener(this)
    }
    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding?.removeActivityResultListener(this); activityBinding?.removeRequestPermissionsResultListener(this)
        activity = null; activityBinding = null
    }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity; activityBinding = binding
        binding.addActivityResultListener(this); binding.addRequestPermissionsResultListener(this)
    }
    override fun onDetachedFromActivity() {
        activityBinding?.removeActivityResultListener(this); activityBinding?.removeRequestPermissionsResultListener(this)
        activity = null; activityBinding = null
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null); overlayChannel.setMethodCallHandler(null)
        gyroscopeBridge?.dispose(); streamingBridge?.dispose()
        gyroscopeReceiver?.unregisterReceivers(binding.applicationContext)
        gyroscopeReceiver = null; gyroscopeBridge = null; streamingBridge = null
        gyroscopeSDK = null; context = null
    }
}