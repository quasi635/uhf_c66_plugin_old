import 'package:flutter/services.dart';
import 'uhf_c66_plugin_platform_interface.dart';

class UhfC66Plugin {
  static const MethodChannel _channel = MethodChannel('uhf_c66_plugin');

  Future<String?> getPlatformVersion() {
    return UhfC66PluginPlatform.instance.getPlatformVersion();
  }

  static const EventChannel connectedStatusStream = EventChannel('ConnectedStatus');
  static const EventChannel tagsStatusStream = EventChannel('TagsStatus');

  static Future<bool?> isStarted() async {
    return _channel.invokeMethod('isStarted');
  }

  static Future<bool?> startSingle() async {
    return _channel.invokeMethod('startSingle');
  }

  static Future<bool?> startContinuous() async {
    return _channel.invokeMethod('startContinuous');
  }

  static Future<bool?> stop() async {
    return _channel.invokeMethod('stop');
  }

  static Future<bool?> close() async {
    return _channel.invokeMethod('close');
  }

  static Future<bool?> clearData() async {
    return _channel.invokeMethod('clearData');
  }

  static Future<bool?> isEmptyTags() async {
    return _channel.invokeMethod('isEmptyTags');
  }

  static Future<bool?> connect() async {
    return _channel.invokeMethod('connect');
  }

  static Future<bool?> isConnected() async {
    return _channel.invokeMethod('isConnected');
  }

  static Future<bool?> writeEpc(String writeData, String accessPwd) async {
    return _channel.invokeMethod('writeEpc', <String, String>{'writeData': writeData, 'accessPwd': accessPwd});
  }

  static Future<bool?> setPowerLevel(String value) async {
    return _channel.invokeMethod('setPowerLevel', <String, String>{'value': value});
  }

  static Future<int?> getPowerLevel() async {
    return _channel.invokeMethod('getPowerLevel');
  }

  static Future<bool?> setFrequencyMode(String value) async {
    return _channel.invokeMethod('setFrequencyMode', <String, String>{'value': value});
  }

  static Future<int?> getFrequencyMode() async {
    return _channel.invokeMethod('getFrequencyMode');
  }
}
