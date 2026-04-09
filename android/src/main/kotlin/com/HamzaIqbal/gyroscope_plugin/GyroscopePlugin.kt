 package com.HamzaIqbal.gyroscope_plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.earnscape.gyroscopesdk.GyroscopeSDK
import com.earnscape.gyroscopesdk.SantriqxSDK
import com.earnscape.gyroscopesdk.TransactionService
import com.earnscape.gyroscopesdk.DeviceService
import com.earnscape.gyroscopesdk.ScreenRecordingService
import com.earnscape.gyroscopesdk.StreamingOverlayService
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
    private var context: Context? = null

    private var activity: Activity? = null
    private var activityBinding: ActivityPluginBinding? = null

    // Pending results
    private var pendingRecordingResult: Result? = null
    private var pendingFaceResult: Result? = null
    private var pendingKycResult: Result? = null

    // ── FlutterPlugin ─────────────────────────────────────────────────────────

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext

        gyroscopeSDK = GyroscopeSDK(binding.applicationContext)
        gyroscopeBridge = GyroscopeBridge(binding.applicationContext)
        streamingBridge = StreamingBridge(binding.applicationContext, gyroscopeSDK!!)

        methodChannel = MethodChannel(binding.binaryMessenger, "gyroscope_plugin/methods")
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(binding.binaryMessenger, "gyroscope_plugin/events")
        eventChannel.setStreamHandler(gyroscopeBridge!!.streamHandler)

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

                // ── SDK Init ──
                "initSdk" -> {
                    val appId = call.argument<String>("appId") ?: ""
                    val apiSecretKey = call.argument<String>("apiSecretKey") ?: ""
                    val baseUrl = call.argument<String>("baseUrl") ?: ""
                      Log.d(TAG, "✅ init trigger")
                     SantriqxSDK.init(
        appId = appId,
        apiSecretKey = apiSecretKey,
        baseUrl = baseUrl    // ← named argument
    )
                    result.success(mapOf("success" to true, "baseUrl" to SantriqxSDK.baseUrl))
                }

                // ── Fetch Config ──
                "fetchConfig" -> {
                    SantriqxSDK.fetchConfig { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                "isServiceActive" -> {
                    val service = call.argument<String>("service") ?: ""
                    result.success(mapOf("active" to SantriqxSDK.isServiceActive(service)))
                }

                "registerDevice" -> {
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    SantriqxSDK.registerDevice(context!!) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                "startStream" -> {
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    SantriqxSDK.startStream(context!!) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                "getStreamDetails" -> {
                    val streamKey = call.argument<String>("streamKey") ?: ""
                    SantriqxSDK.getStreamDetails(streamKey) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                "uploadFace" -> {
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

                "recordTransaction" -> {
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    val fields = call.argument<Map<String, String>>("fields") ?: emptyMap()
                    SantriqxSDK.recordTransaction(context!!, fields) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

                "getTransactions" -> {
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    SantriqxSDK.getTransactions(context!!) { res ->
                        activity?.runOnUiThread { result.success(res) }
                    }
                }

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

                // ── Face Recognition ──
                "openFaceRecognition" -> {
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity not available", null)
                        return@setMethodCallHandler
                    }
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CAMERA)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        result.error("PERMISSION_DENIED", "Camera permission required.", null)
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

                // ── KYC ──
                "openKyc" -> {
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity not available", null)
                        return@setMethodCallHandler
                    }
                    val camOk = androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val micOk = androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val missing = mutableListOf<String>()
                    if (!camOk) missing.add("CAMERA")
                    if (!micOk) missing.add("MICROPHONE")
                    if (missing.isNotEmpty()) {
                        result.error("PERMISSION_DENIED", "Missing: ${missing.joinToString(", ")}", null)
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

                "checkPermissions" -> {
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

                "validateTransaction" -> {
                    val fieldsMap = call.argument<Map<String, String>>("fields") ?: emptyMap()
                    result.success(TransactionService.validateTransaction(fieldsMap))
                }

                "getDeviceInfo" -> {
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    DeviceService.getFullDeviceInfo(context!!) { info ->
                        result.success(info)
                    }
                }

                "getDeviceId" -> {
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    result.success(mapOf("deviceId" to DeviceService.getOrCreateDeviceId(context!!)))
                }

                "getLocation" -> {
                    if (context == null) {
                        result.error("NO_CONTEXT", "Context not available", null)
                        return@setMethodCallHandler
                    }
                    val fineOk = androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val coarseOk = androidx.core.content.ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!fineOk && !coarseOk) {
                        result.error("PERMISSION_DENIED", "Location permission required.", null)
                        return@setMethodCallHandler
                    }
                    DeviceService.getLocation(context!!) { location ->
                        result.success(location)
                    }
                }

                // ── ⭐ NEW: All-in-one Recording (SDK handles everything) ──
                "startRecording" -> {
                    if (activity == null) {
                        result.error("NO_ACTIVITY", "Activity not available", null)
                        return@setMethodCallHandler
                    }
                    pendingRecordingResult = result
                    SantriqxSDK.startRecording(
                        activity = activity!!,
                        requestCode = MEDIA_PROJECTION_REQUEST_CODE,
                        onSuccess = { streamKey, rtmpUrl ->
                            Log.d(TAG, "✅ RTMP ready: $streamKey")
                            // Don't return yet — wait for permission result
                            // Store streamKey + rtmpUrl to return after service starts
                            pendingRecordingResult?.success(mapOf(
                                "success" to true,
                                "streamKey" to streamKey,
                                "rtmpUrl" to rtmpUrl
                            ))
                            pendingRecordingResult = null
                        },
                        onError = { err ->
                            Log.e(TAG, "❌ Recording error: $err")
                            pendingRecordingResult?.success(mapOf(
                                "success" to false,
                                "message" to err
                            ))
                            pendingRecordingResult = null
                        }
                    )
                }

                // ── Stop Recording ──
                "stopStreaming" -> {
                    try {
                        if (context != null) {
                            SantriqxSDK.stopRecording(context!!)
                        }
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("STOP_ERROR", e.message, null)
                    }
                }

                "isStreaming" -> result.success(ScreenRecordingService.isRunning)

                else -> result.notImplemented()
            }
        }

        gyroscopeReceiver = GyroscopeReceiver(methodChannel)
        gyroscopeReceiver?.registerReceivers(binding.applicationContext)
    }

    // ── Activity Results ─────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {

        // ── MediaProjection result — forward to SDK ──
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            SantriqxSDK.handleRecordingResult(
                activity = activity!!,
                requestCode = requestCode,
                expectedCode = MEDIA_PROJECTION_REQUEST_CODE,
                resultCode = resultCode,
                data = data,
                onGranted = {
                    Log.d(TAG, "✅ Recording started via SDK")
                },
                onDenied = {
                    Log.e(TAG, "❌ Recording permission denied")
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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ): Boolean = false

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

    // ── ActivityAware ──
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