import 'package:flutter_test/flutter_test.dart';
import 'package:uhf_c66_plugin/uhf_c66_plugin.dart';
import 'package:uhf_c66_plugin/uhf_c66_plugin_platform_interface.dart';
import 'package:uhf_c66_plugin/uhf_c66_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:flutter/services.dart';

class MockUhfC66PluginPlatform with MockPlatformInterfaceMixin implements UhfC66PluginPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  const MethodChannel channel = MethodChannel('uhf_plugin');

  TestWidgetsFlutterBinding.ensureInitialized();

  final UhfC66PluginPlatform initialPlatform = UhfC66PluginPlatform.instance;

  setUpAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      MethodCall methodCall,
    ) async {
      return '42';
    });
  });

  tearDownAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('$MethodChannelUhfC66Plugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelUhfC66Plugin>());
  });

  test('getPlatformVersion', () async {
    UhfC66Plugin uhfC66Plugin = UhfC66Plugin();
    MockUhfC66PluginPlatform fakePlatform = MockUhfC66PluginPlatform();
    UhfC66PluginPlatform.instance = fakePlatform;

    expect(await uhfC66Plugin.getPlatformVersion(), '42');
  });
}
