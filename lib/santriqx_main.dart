 

// import 'dart:async';
// import 'dart:convert';
// import 'package:flutter/foundation.dart';
// import 'package:flutter/services.dart';

// // ── Sensor Data Model ──
// class GyroAndAccelData {
//   final double x, y, z;
//   final double ax, ay, az;
//   final int timestampNs;
//   final bool isIdle;
//   final String sessionId;

//   GyroAndAccelData({this.x=0, this.y=0, this.z=0, this.ax=0, this.ay=0, this.az=0,
//     this.timestampNs=0, this.isIdle=false, this.sessionId=''});

//   factory GyroAndAccelData.fromMap(Map<String, dynamic> m) => GyroAndAccelData(
//     x: (m['x'] ?? 0).toDouble(), y: (m['y'] ?? 0).toDouble(), z: (m['z'] ?? 0).toDouble(),
//     ax: (m['ax'] ?? 0).toDouble(), ay: (m['ay'] ?? 0).toDouble(), az: (m['az'] ?? 0).toDouble(),
//     timestampNs: m['timestampNs'] ?? 0, isIdle: m['isIdle'] ?? false, sessionId: m['sessionId'] ?? '',
//   );
// }

// class SessionInfo {
//   final String sessionId;
//   final int readings;
//   final int durationMs;
//   SessionInfo({this.sessionId = '', this.readings = 0, this.durationMs = 0});
// }

// enum PhoneState { idle, active }

// class GyroSamplingRate {
//   static const int normal = 3;
//   static const int ui = 2;
//   static const int game = 1;
//   static const int fastest = 0;
// }

// // ── Sensor Plugin Class ──
// class SensorPlugin {
//   static const MethodChannel _methodChannel = MethodChannel('gyroscope_plugin/methods');
//   static const EventChannel _eventChannel = EventChannel('gyroscope_plugin/events');

//   final _gyroController = StreamController<GyroAndAccelData>.broadcast();
//   Stream<GyroAndAccelData> get gyroStream => _gyroController.stream;

//   Future<bool> hasGyroscope() async {
//     final r = await _methodChannel.invokeMethod<bool>('hasGyroscope');
//     return r ?? false;
//   }

//   Future<String?> startGame({
//     required String gameId,
//     int samplingRate = GyroSamplingRate.game,
//     bool autoLog = false,
//     Function(GyroAndAccelData)? onData,
//     Function(SessionInfo)? onSessionStart,
//     Function(SessionInfo)? onSessionStop,
//     Function(PhoneState, String)? onPhoneState,
//   }) async {
//     await _methodChannel.invokeMethod('start', {
//       'samplingRate': samplingRate, 'autoLog': autoLog,
//     });

//     _eventChannel.receiveBroadcastStream().listen((event) {
//       final map = Map<String, dynamic>.from(event);
//       final data = GyroAndAccelData.fromMap(map);
//       _gyroController.add(data);
//       onData?.call(data);
//     });

//     final sessionId = await _methodChannel.invokeMethod<String>('startSession', {
//       'gameId': gameId, 'samplingRate': samplingRate,
//     });

//     onSessionStart?.call(SessionInfo(sessionId: sessionId ?? ''));
//     return sessionId;
//   }

//   Future<void> stopGame() async {
//     await _methodChannel.invokeMethod('stopSession');
//     await _methodChannel.invokeMethod('stop');
//   }

//   void dispose() {
//     _gyroController.close();
//   }
// }

// // ── SantriqxSdk (All SDK Methods) ──
// class SantriqxSdk {
//   static const MethodChannel _channel = MethodChannel('gyroscope_plugin/overlay');

//   // ── Init (orgId/productId optional — config API se aayega) ──
//  static Future<Map> init({
//   required String appId,
//   required String apiSecretKey,
//   String? baseUrl,
// }) async => await _channel.invokeMethod('initSdk', {
//   'appId': appId,
//   'apiSecretKey': apiSecretKey,
//   'baseUrl': baseUrl ?? '',

// });

//   // ── Config ──
//   static Future<Map> fetchConfig() async =>
//       await _channel.invokeMethod('fetchConfig', {});

//   static Future<bool> isServiceActive(String service) async {
//     final r = await _channel.invokeMethod('isServiceActive', {'service': service});
//     return r['active'] == true;
//   }

//   // ── Device ──
//   static Future<Map> registerDevice() async =>
//       await _channel.invokeMethod('registerDevice', {});

//   static Future<Map> getDeviceInfo() async =>
//       await _channel.invokeMethod('getDeviceInfo', {});

//   static Future<Map> getDeviceId() async =>
//       await _channel.invokeMethod('getDeviceId', {});

//   static Future<Map> getLocation() async =>
//       await _channel.invokeMethod('getLocation', {});

//   // ── Streaming ──
//   static Future<Map> startStream() async =>
//       await _channel.invokeMethod('startStream', {});

//   static Future<void> requestMediaProjection(Map<String, String> params) async =>
//       await _channel.invokeMethod('requestMediaProjection', params);

//   static Future<Map> startStreamingWithUrl(String streamUrl, String streamKey, {String audio = 'false'}) async =>
//       await _channel.invokeMethod('startStreamingWithUrl', {
//         'streamUrl': streamUrl, 'streamKey': streamKey, 'audio': audio,
//       });

//   /// Stops recording + notifies backend (stream ended API)
//   static Future<void> stopStreaming() async =>
//       await _channel.invokeMethod('stopStreaming');

//   static Future<Map> getStreamDetails(String streamKey) async =>
//       await _channel.invokeMethod('getStreamDetails', {'streamKey': streamKey});

//   static Future<bool> isStreaming() async {
//     final r = await _channel.invokeMethod('isStreaming');
//     return r == true;
//   }

//   // ── Face Recognition ──
//   static Future<Map> openFaceRecognition() async =>
//       await _channel.invokeMethod('openFaceRecognition', {});

//   static Future<Map> uploadFace(String imagePath, String username) async =>
//       await _channel.invokeMethod('uploadFace', {'imagePath': imagePath, 'username': username});

//   // ── KYC ──
//   static Future<Map> openKyc() async =>
//       await _channel.invokeMethod('openKyc', {});

//   // ── Transactions ──
//   static Future<Map> recordTransaction(Map<String, String> fields) async =>
//       await _channel.invokeMethod('recordTransaction', {'fields': fields});

//   static Future<Map> getTransactions() async =>
//       await _channel.invokeMethod('getTransactions', {});

//   static Future<Map> validateTransaction(Map<String, String> fields) async =>
//       await _channel.invokeMethod('validateTransaction', {'fields': fields});

//   // ── Sensors ──
//   static Future<Map> sendSensorData({
//     double gyroX=0, double gyroY=0, double gyroZ=0,
//     double accelX=0, double accelY=0, double accelZ=0,
//   }) async => await _channel.invokeMethod('sendSensorData', {
//     'gyro_x': gyroX, 'gyro_y': gyroY, 'gyro_z': gyroZ,
//     'accel_x': accelX, 'accel_y': accelY, 'accel_z': accelZ,
//   });

//   // ── Permissions ──
//   static Future<Map> checkPermissions() async =>
//       await _channel.invokeMethod('checkPermissions', {});

//   // ── Overlay ──
//   static Future<bool> checkOverlayPermission() async {
//     final r = await _channel.invokeMethod('checkOverlayPermission');
//     return r == true;
//   }

//   static Future<void> requestOverlayPermission() async =>
//       await _channel.invokeMethod('requestOverlayPermission');

//   static Future<void> startOverlay({int? startTimeMs}) async =>
//       await _channel.invokeMethod('startOverlay', {'startTimeMs': startTimeMs});

//   static Future<void> stopOverlay() async =>
//       await _channel.invokeMethod('stopOverlay');


//    /// All-in-one recording — API + permission + RTMP streaming
// static Future<Map> startRecording() async {
//   // 1. Backend se RTMP URL lo
//   final stream = await startStream();
//   debugPrint("Strea $stream");
//   if (stream['success'] == false || stream['success'] == 'false') {
//     return {'success': false, 'message': stream['message'] ?? 'Stream error'};
//   }

//   final dataStr = stream['data'];
//   if (dataStr == null) {
//     return {'success': false, 'message': 'No stream data'};
//   }

//   Map data;
//   if (dataStr is String) {
//     data = jsonDecode(dataStr);
//   } else {
//     data = Map.from(dataStr as Map);
//   }

//   final rtmpUrl = data['rtmpUrl']?.toString() ?? '';
//   final streamKey = data['streamKey']?.toString() ?? '';

//   if (rtmpUrl.isEmpty || streamKey.isEmpty) {
//     return {'success': false, 'message': 'Could not get RTMP URL'};
//   }

//   // 2. Device info
//   final info = await getDeviceInfo();
//   final deviceId = info['deviceId']?.toString() ?? '';

//   // 3. Permission + start streaming (ek dialog, service start)
//    try {
//     await requestMediaProjection({
//       'audio': 'false',
//       'streamUrl': '$rtmpUrl/$streamKey',
//       'streamKey': streamKey,
//       'gameId': 'recording',
//       'playerId': deviceId,
//       'videoEnabled': 'false',
//       'targetPackageName': '',
//       'streamTitle': 'recording',
//       'playerName': deviceId,
//       'gameName': 'Recording',
//       'minimumStreamSpeed': '1',
//       'badConnectionTimeout': '30',
//     });
//   } catch (e) {
//     // Plugin andar hi clean kar deta hai — client ko pata bhi nahi chalta
//     await stopStreaming();
//     return {'success': false, 'message': 'Permission denied'};
//   }

//   return {
//     'success': true,
//     'streamKey': streamKey,
//     'rtmpUrl': rtmpUrl,
//   };
// }   


// }

import 'dart:async';
import 'package:flutter/services.dart';

// ── Sensor Data Model ──
class GyroAndAccelData {
  final double x, y, z;
  final double ax, ay, az;
  final int timestampNs;
  final bool isIdle;
  final String sessionId;

  GyroAndAccelData({this.x=0, this.y=0, this.z=0, this.ax=0, this.ay=0, this.az=0,
    this.timestampNs=0, this.isIdle=false, this.sessionId=''});

  factory GyroAndAccelData.fromMap(Map<String, dynamic> m) => GyroAndAccelData(
    x: (m['x'] ?? 0).toDouble(), y: (m['y'] ?? 0).toDouble(), z: (m['z'] ?? 0).toDouble(),
    ax: (m['ax'] ?? 0).toDouble(), ay: (m['ay'] ?? 0).toDouble(), az: (m['az'] ?? 0).toDouble(),
    timestampNs: m['timestampNs'] ?? 0, isIdle: m['isIdle'] ?? false, sessionId: m['sessionId'] ?? '',
  );
}

class SessionInfo {
  final String sessionId;
  final int readings;
  final int durationMs;
  SessionInfo({this.sessionId = '', this.readings = 0, this.durationMs = 0});
}

enum PhoneState { idle, active }

class GyroSamplingRate {
  static const int normal = 3;
  static const int ui = 2;
  static const int game = 1;
  static const int fastest = 0;
}

// ── Sensor Plugin Class ──
class SensorPlugin {
  static const MethodChannel _methodChannel = MethodChannel('gyroscope_plugin/methods');
  static const EventChannel _eventChannel = EventChannel('gyroscope_plugin/events');

  final _gyroController = StreamController<GyroAndAccelData>.broadcast();
  Stream<GyroAndAccelData> get gyroStream => _gyroController.stream;

  Future<bool> hasGyroscope() async {
    final r = await _methodChannel.invokeMethod<bool>('hasGyroscope');
    return r ?? false;
  }

  Future<String?> startGame({
    required String gameId,
    int samplingRate = GyroSamplingRate.game,
    bool autoLog = false,
    Function(GyroAndAccelData)? onData,
    Function(SessionInfo)? onSessionStart,
    Function(SessionInfo)? onSessionStop,
    Function(PhoneState, String)? onPhoneState,
  }) async {
    await _methodChannel.invokeMethod('start', {
      'samplingRate': samplingRate, 'autoLog': autoLog,
    });

    _eventChannel.receiveBroadcastStream().listen((event) {
      final map = Map<String, dynamic>.from(event);
      final data = GyroAndAccelData.fromMap(map);
      _gyroController.add(data);
      onData?.call(data);
    });

    final sessionId = await _methodChannel.invokeMethod<String>('startSession', {
      'gameId': gameId, 'samplingRate': samplingRate,
    });

    onSessionStart?.call(SessionInfo(sessionId: sessionId ?? ''));
    return sessionId;
  }

  Future<void> stopGame() async {
    await _methodChannel.invokeMethod('stopSession');
    await _methodChannel.invokeMethod('stop');
  }

  void dispose() {
    _gyroController.close();
  }
}

// ── SantriqxSdk (All SDK Methods) ──
class SantriqxSdk {
  static const MethodChannel _channel = MethodChannel('gyroscope_plugin/overlay');

  // ── Init ──
  static Future<Map> init({
    required String appId,
    required String apiSecretKey,
    String? baseUrl,
  }) async => await _channel.invokeMethod('initSdk', {
    'appId': appId,
    'apiSecretKey': apiSecretKey,
    'baseUrl': baseUrl ?? '',
  });

  // ── Config ──
  static Future<Map> fetchConfig() async =>
      await _channel.invokeMethod('fetchConfig', {});

  static Future<bool> isServiceActive(String service) async {
    final r = await _channel.invokeMethod('isServiceActive', {'service': service});
    return r['active'] == true;
  }

  // ── Device ──
  static Future<Map> registerDevice() async =>
      await _channel.invokeMethod('registerDevice', {});

  static Future<Map> getDeviceInfo() async =>
      await _channel.invokeMethod('getDeviceInfo', {});

  static Future<Map> getDeviceId() async =>
      await _channel.invokeMethod('getDeviceId', {});

  static Future<Map> getLocation() async =>
      await _channel.invokeMethod('getLocation', {});

  // ── Streaming / Recording ──
  
  /// Low-level: just get RTMP URL from backend (no permission, no streaming)
  static Future<Map> startStream() async =>
      await _channel.invokeMethod('startStream', {});

  /// ⭐ All-in-one recording — SDK handles API + permission + RTMP streaming
  /// Returns: {success: bool, streamKey: String, rtmpUrl: String, message?: String}
  static Future<Map> startRecording() async {
    final result = await _channel.invokeMethod('startRecording');
    return Map.from(result);
  }

  /// Stops recording + notifies backend (stream ended API)
  static Future<void> stopStreaming() async =>
      await _channel.invokeMethod('stopStreaming');

  static Future<Map> getStreamDetails(String streamKey) async =>
      await _channel.invokeMethod('getStreamDetails', {'streamKey': streamKey});

  static Future<bool> isStreaming() async {
    final r = await _channel.invokeMethod('isStreaming');
    return r == true;
  }

  // ── Face Recognition ──
  static Future<Map> openFaceRecognition() async =>
      await _channel.invokeMethod('openFaceRecognition', {});

  static Future<Map> uploadFace(String imagePath, String username) async =>
      await _channel.invokeMethod('uploadFace', {'imagePath': imagePath, 'username': username});

  // ── KYC ──
  static Future<Map> openKyc() async =>
      await _channel.invokeMethod('openKyc', {});

  // ── Transactions ──
  static Future<Map> recordTransaction(Map<String, String> fields) async =>
      await _channel.invokeMethod('recordTransaction', {'fields': fields});

  static Future<Map> getTransactions() async =>
      await _channel.invokeMethod('getTransactions', {});

  static Future<Map> validateTransaction(Map<String, String> fields) async =>
      await _channel.invokeMethod('validateTransaction', {'fields': fields});

  // ── Sensors ──
  static Future<Map> sendSensorData({
    double gyroX=0, double gyroY=0, double gyroZ=0,
    double accelX=0, double accelY=0, double accelZ=0,
  }) async => await _channel.invokeMethod('sendSensorData', {
    'gyro_x': gyroX, 'gyro_y': gyroY, 'gyro_z': gyroZ,
    'accel_x': accelX, 'accel_y': accelY, 'accel_z': accelZ,
  });

  // ── Permissions ──
  static Future<Map> checkPermissions() async =>
      await _channel.invokeMethod('checkPermissions', {});

  // ── Overlay ──
  static Future<bool> checkOverlayPermission() async {
    final r = await _channel.invokeMethod('checkOverlayPermission');
    return r == true;
  }

  static Future<void> requestOverlayPermission() async =>
      await _channel.invokeMethod('requestOverlayPermission');

  static Future<void> startOverlay({int? startTimeMs}) async =>
      await _channel.invokeMethod('startOverlay', {'startTimeMs': startTimeMs});

  static Future<void> stopOverlay() async =>
      await _channel.invokeMethod('stopOverlay');
}