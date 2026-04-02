
import 'gyroscope_plugin_platform_interface.dart';

class GyroscopePlugin {
  Future<String?> getPlatformVersion() {
    return GyroscopePluginPlatform.instance.getPlatformVersion();
  }
}
