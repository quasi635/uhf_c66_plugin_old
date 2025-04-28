import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'uhf_c66_plugin_method_channel.dart';

abstract class UhfC66PluginPlatform extends PlatformInterface {
  /// Constructs a UhfC66PluginPlatform.
  UhfC66PluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static UhfC66PluginPlatform _instance = MethodChannelUhfC66Plugin();

  /// The default instance of [UhfC66PluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelUhfC66Plugin].
  static UhfC66PluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [UhfC66PluginPlatform] when
  /// they register themselves.
  static set instance(UhfC66PluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
