import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'gyroscope_plugin_method_channel.dart';

abstract class GyroscopePluginPlatform extends PlatformInterface {
  /// Constructs a GyroscopePluginPlatform.
  GyroscopePluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static GyroscopePluginPlatform _instance = MethodChannelGyroscopePlugin();

  /// The default instance of [GyroscopePluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelGyroscopePlugin].
  static GyroscopePluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [GyroscopePluginPlatform] when
  /// they register themselves.
  static set instance(GyroscopePluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
