import 'package:flutter_test/flutter_test.dart';
import 'package:gyroscope_plugin/gyroscope_plugin.dart';
import 'package:gyroscope_plugin/gyroscope_plugin_platform_interface.dart';
import 'package:gyroscope_plugin/gyroscope_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockGyroscopePluginPlatform
    with MockPlatformInterfaceMixin
    implements GyroscopePluginPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final GyroscopePluginPlatform initialPlatform = GyroscopePluginPlatform.instance;

  test('$MethodChannelGyroscopePlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelGyroscopePlugin>());
  });

  test('getPlatformVersion', () async {
    GyroscopePlugin gyroscopePlugin = GyroscopePlugin();
    MockGyroscopePluginPlatform fakePlatform = MockGyroscopePluginPlatform();
    GyroscopePluginPlatform.instance = fakePlatform;

    expect(await gyroscopePlugin.getPlatformVersion(), '42');
  });
}
