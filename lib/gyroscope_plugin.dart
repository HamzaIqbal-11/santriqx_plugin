
import 'gyroscope_plugin_platform_interface.dart';

// class GyroscopePlugin {
//   Future<String?> getPlatformVersion() {
//     return GyroscopePluginPlatform.instance.getPlatformVersion();
//   }
// }
import 'dart:async';

import 'package:flutter/services.dart';

class GyroscopePlugin {
  static const MethodChannel _methodChannel = MethodChannel('gyroscope_plugin/methods');
  static const EventChannel _eventChannel = EventChannel('gyroscope_plugin/events');

    Stream<Map<String, dynamic>> get gyroscopeStream {
    return _eventChannel.receiveBroadcastStream().map((dynamic event) {
      return Map<String, dynamic>.from(event);
    });
  }

    Future<void> start({
    int samplingRate = 1, // SENSOR_DELAY_GAME = 1, but pass as int
    bool autoLog = true,
  }) async {
    await _methodChannel.invokeMethod('start', {
      'samplingRate': samplingRate,
      'autoLog': autoLog,
    });
  }

    Future<void> stop() async {
    await _methodChannel.invokeMethod('stop');
  }

    Future<bool> hasGyroscope() async {
    final result = await _methodChannel.invokeMethod<bool>('hasGyroscope');
    return result ?? false;
  }
}