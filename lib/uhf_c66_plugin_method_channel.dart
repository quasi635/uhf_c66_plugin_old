import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'uhf_c66_plugin_platform_interface.dart';

/// An implementation of [UhfC66PluginPlatform] that uses method channels.
class MethodChannelUhfC66Plugin extends UhfC66PluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('uhf_c66_plugin');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
