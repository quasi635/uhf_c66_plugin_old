import 'dart:async';

import 'package:flutter/services.dart';
import 'uhf_c66_plugin_platform_interface.dart';

/// Hardware trigger state on the C66 handheld.
enum UhfTriggerEvent { down, up }

/// Which yellow side key fired on the C66.
enum UhfSideKey { left, right }

/// A press/release event from one of the yellow side keys.
class UhfSideKeyEvent {
  const UhfSideKeyEvent(this.key, this.isDown);

  final UhfSideKey key;
  final bool isDown;

  @override
  String toString() =>
      'UhfSideKeyEvent($key, ${isDown ? 'down' : 'up'})';
}

class UhfC66Plugin {
  static const MethodChannel _channel = MethodChannel('uhf_c66_plugin');

  // --- Hardware trigger (scan button) ---

  /// Android keyCodes that the C66 trigger reports. Extendable via
  /// [addTriggerKeyCode] for other Chainway models / firmware variants.
  /// 0x138 (312) is the C66 trigger.
  static final Set<int> _triggerKeyCodes = <int>{0x138};

  /// Android keyCodes for the yellow left side key. 0x137 (311) on the C66.
  static final Set<int> _leftSideKeyCodes = <int>{0x137};

  /// Android keyCodes for the yellow right side key. 0x139 (313) on the C66.
  static final Set<int> _rightSideKeyCodes = <int>{0x139};

  static final StreamController<UhfTriggerEvent> _triggerController =
      StreamController<UhfTriggerEvent>.broadcast();
  static final StreamController<UhfSideKeyEvent> _sideKeyController =
      StreamController<UhfSideKeyEvent>.broadcast();
  static StreamSubscription<dynamic>? _triggerNativeSubscription;
  static bool _triggerDown = false;
  static bool _leftSideDown = false;
  static bool _rightSideDown = false;
  static StreamSubscription<UhfTriggerEvent>? _autoScanSubscription;

  /// Broadcast stream of trigger press/release events. Backed by an Android
  /// Activity-level key interceptor installed by the plugin, so events fire
  /// regardless of which widget holds Flutter input focus.
  static Stream<UhfTriggerEvent> get triggerEvents {
    _ensureNativeTriggerSubscription();
    return _triggerController.stream;
  }

  /// Broadcast stream of press/release events for the yellow side keys.
  /// Same Activity-level interceptor as [triggerEvents].
  static Stream<UhfSideKeyEvent> get sideKeyEvents {
    _ensureNativeTriggerSubscription();
    return _sideKeyController.stream;
  }

  /// Register an additional Android keyCode to treat as the hardware trigger.
  /// Use this if a future firmware / different Chainway model reports a new
  /// keyCode — log the `keyCode` field from a raw trigger event once, then
  /// call this at app start.
  static void addTriggerKeyCode(int keyCode) {
    _triggerKeyCodes.add(keyCode);
  }

  /// Register an additional Android keyCode to treat as a yellow side key.
  static void addSideKeyCode(UhfSideKey side, int keyCode) {
    (side == UhfSideKey.left ? _leftSideKeyCodes : _rightSideKeyCodes)
        .add(keyCode);
  }

  /// Convenience: each trigger press toggles continuous scanning on/off.
  /// Idempotent — safe to call more than once.
  static void enableTriggerScanning() {
    _autoScanSubscription ??= triggerEvents.listen((UhfTriggerEvent event) async {
      if (event != UhfTriggerEvent.down) return;
      final bool running = (await isStarted()) ?? false;
      if (running) {
        await stop();
      } else {
        await startContinuous();
      }
    });
  }

  /// Stop the auto start/stop behaviour installed by [enableTriggerScanning].
  /// The underlying [triggerEvents] stream keeps working.
  static void disableTriggerScanning() {
    _autoScanSubscription?.cancel();
    _autoScanSubscription = null;
  }

  static void _ensureNativeTriggerSubscription() {
    _triggerNativeSubscription ??= const EventChannel('TriggerKey')
        .receiveBroadcastStream()
        .listen(_handleNativeTriggerEvent);
  }

  static void _handleNativeTriggerEvent(dynamic payload) {
    if (payload is! Map) return;
    final Object? keyCode = payload['keyCode'];
    final Object? action = payload['action'];
    if (keyCode is! int || action is! String) return;
    final bool isDown = action == 'down';
    if (!isDown && action != 'up') return;

    if (_triggerKeyCodes.contains(keyCode)) {
      if (isDown) {
        if (_triggerDown) return; // swallow auto-repeat
        _triggerDown = true;
        _triggerController.add(UhfTriggerEvent.down);
      } else {
        if (!_triggerDown) return;
        _triggerDown = false;
        _triggerController.add(UhfTriggerEvent.up);
      }
      return;
    }

    if (_leftSideKeyCodes.contains(keyCode)) {
      if (isDown) {
        if (_leftSideDown) return;
        _leftSideDown = true;
      } else {
        if (!_leftSideDown) return;
        _leftSideDown = false;
      }
      _sideKeyController.add(UhfSideKeyEvent(UhfSideKey.left, isDown));
      return;
    }

    if (_rightSideKeyCodes.contains(keyCode)) {
      if (isDown) {
        if (_rightSideDown) return;
        _rightSideDown = true;
      } else {
        if (!_rightSideDown) return;
        _rightSideDown = false;
      }
      _sideKeyController.add(UhfSideKeyEvent(UhfSideKey.right, isDown));
    }
  }

  Future<String?> getPlatformVersion() {
    return UhfC66PluginPlatform.instance.getPlatformVersion();
  }

  static const EventChannel connectedStatusStream = EventChannel(
    'ConnectedStatus',
  );
  static const EventChannel tagsStatusStream = EventChannel('TagsStatus');
  static const EventChannel locateStatusStream = EventChannel('LocateStatus');
  static const EventChannel barcodeStatusStream = EventChannel('BarcodeStatus');

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

  /// Free the native RFID handle, re-acquire it from the SDK singleton, and
  /// re-init. Also re-applies the last power level and frequency mode set
  /// through this plugin.
  ///
  /// Use this to recover when the UHF module has been powered off by a
  /// `UHF_POWER_OFF` broadcast — typically caused by a Flutter hot-restart
  /// that left an older `UhfBase` instance alive, or by another UHF-using
  /// app on the device. Symptom: scans stop working and native logs show
  /// `startInventory() err :-1`.
  ///
  /// [startContinuous] already calls this automatically on failure; expose
  /// it explicitly so UIs can offer a manual "Reconnect" button.
  static Future<bool?> reconnect() async {
    return _channel.invokeMethod('reconnect');
  }

  static Future<bool?> isConnected() async {
    return _channel.invokeMethod('isConnected');
  }

  static Future<bool?> writeEpc(String writeData, String accessPwd) async {
    return _channel.invokeMethod('writeEpc', <String, String>{
      'writeData': writeData,
      'accessPwd': accessPwd,
    });
  }

  static Future<bool?> setPowerLevel(String value) async {
    return _channel.invokeMethod('setPowerLevel', <String, String>{
      'value': value,
    });
  }

  static Future<int?> getPowerLevel() async {
    return _channel.invokeMethod('getPowerLevel');
  }

  static Future<bool?> setFrequencyMode(String value) async {
    return _channel.invokeMethod('setFrequencyMode', <String, String>{
      'value': value,
    });
  }

  static Future<int?> getFrequencyMode() async {
    return _channel.invokeMethod('getFrequencyMode');
  }

  static Future<bool?> startFindByPartialEpc(
    String partialEpc, {
    String matchType = 'startsWith',
    int scanWindowMs = 1500,
  }) async {
    return _channel.invokeMethod('startFindByPartialEpc', <String, dynamic>{
      'partialEpc': partialEpc,
      'matchType': matchType,
      'scanWindowMs': scanWindowMs,
    });
  }

  static Future<bool?> stopFindByPartialEpc() async {
    return _channel.invokeMethod('stopFindByPartialEpc');
  }

  static Future<bool?> isLocating() async {
    return _channel.invokeMethod('isLocating');
  }

  // --- 2D barcode scanner ---

  /// Start a barcode scan. The scanner stays on until a code decodes, the
  /// SDK timeout (60s) elapses, or [stopBarcodeScan] is called. Results are
  /// delivered over [barcodeEvents]. The scanner module is lazily opened on
  /// first call.
  static Future<bool?> startBarcodeScan() async {
    return _channel.invokeMethod('startBarcodeScan');
  }

  /// Cancel an in-progress barcode scan.
  static Future<bool?> stopBarcodeScan() async {
    return _channel.invokeMethod('stopBarcodeScan');
  }

  /// Power off the barcode scanner module. Call when you're done (e.g. app
  /// background / dispose) to save power. The next [startBarcodeScan] will
  /// transparently reopen it.
  static Future<bool?> closeBarcodeScanner() async {
    return _channel.invokeMethod('closeBarcodeScanner');
  }

  /// Broadcast stream of barcode decode events.
  static Stream<UhfBarcodeResult> get barcodeEvents => barcodeStatusStream
      .receiveBroadcastStream()
      .map<UhfBarcodeResult>(UhfBarcodeResult._fromPayload);
}

/// Barcode decode result codes emitted by the native side.
class UhfBarcodeDecodeStatus {
  static const int success = 1;
  static const int failure = 2;
}

/// A single decode event from the C66 2D barcode scanner.
class UhfBarcodeResult {
  const UhfBarcodeResult({required this.resultCode, required this.data});

  /// One of [UhfBarcodeDecodeStatus] constants.
  final int resultCode;

  /// Decoded text, or null when the decode didn't succeed.
  final String? data;

  bool get isSuccess => resultCode == UhfBarcodeDecodeStatus.success;

  static UhfBarcodeResult _fromPayload(dynamic payload) {
    final Map<Object?, Object?> map = payload is Map
        ? payload.cast<Object?, Object?>()
        : const <Object?, Object?>{};
    return UhfBarcodeResult(
      resultCode: (map['resultCode'] as int?) ?? UhfBarcodeDecodeStatus.failure,
      data: map['data'] as String?,
    );
  }

  @override
  String toString() => 'UhfBarcodeResult(code=$resultCode, data=$data)';
}
