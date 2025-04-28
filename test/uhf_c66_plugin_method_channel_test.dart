import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:uhf_c66_plugin/uhf_c66_plugin_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelUhfC66Plugin platform = MethodChannelUhfC66Plugin();
  const MethodChannel channel = MethodChannel('uhf_c66_plugin');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
