import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:uhf_c66_plugin/uhf_c66_plugin.dart';
import 'package:uhf_c66_plugin/tag_epc.dart';
import 'package:uhf_c66_plugin/tag_locate.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  //final _uhfC66Plugin = UhfC66Plugin();
  //String _platformVersion = 'Unknown';
  //final bool _isStarted = false;
  //final bool _isEmptyTags = false;
  //bool _isConnected = false;

  TextEditingController powerLevelController = TextEditingController(text: "");
  TextEditingController frequencyModeController = TextEditingController(text: "");
  TextEditingController partialEpcController = TextEditingController(text: "");

  String _partialMatchType = 'startsWith';
  bool _isLocating = false;
  bool _isSearchingForMatch = false;
  bool _continuousSearchRequested = false;
  List<TagLocate> _locates = [];
  int? _latestProximityValue;
  bool _latestProximityValid = false;
  String _latestProximityEpc = '-';
  String _latestProximityRssi = '-';
  int _proximityUpdateCount = 0;
  int _lastProximityCallbackMs = 0;
  bool _outOfRangeReported = false;

  StreamSubscription? _connectedSubscription;
  StreamSubscription? _tagsSubscription;
  StreamSubscription? _locateSubscription;
  StreamSubscription? _barcodeSubscription;
  Timer? _proximityWatchdogTimer;

  static const int _minUiUpdateGapMs = 120;
  static const int _fixedScanWindowMs = 1500;
  static const int _proximityTimeoutMs = 1500;
  int _lastTagsUiUpdateMs = 0;
  int _lastLocateUiUpdateMs = 0;

  @override
  void initState() {
    super.initState();
    UhfC66Plugin.enableTriggerScanning();

    UhfC66Plugin.sideKeyEvents.listen((UhfSideKeyEvent e) {
      if (!e.isDown) return;
      if (e.key == UhfSideKey.left) {
        log('Left yellow button: calling startBarcodeScan');
        UhfC66Plugin.startBarcodeScan().then((bool? ok) {
          log('startBarcodeScan returned $ok');
        });
      } else {
        log('Right yellow button: calling stopBarcodeScan');
        UhfC66Plugin.stopBarcodeScan().then((bool? ok) {
          log('stopBarcodeScan returned $ok');
        });
      }
    });

    _barcodeSubscription = UhfC66Plugin.barcodeEvents.listen((r) {
      debugPrint('[BARCODE] resultCode=${r.resultCode} data=${r.data}');
      if (r.isSuccess) {
        log('Barcode: ${r.data}');
      } else {
        log('Barcode scan ended (code=${r.resultCode})');
      }
    });

    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    //String? platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.

    String uhfFrequencyMode = (await UhfC66Plugin.getFrequencyMode()).toString();
    String uhfPowerLevel = (await UhfC66Plugin.getPowerLevel()).toString();

    //String uhfFrequencyMode = "8"; // 0x08 for USA (902-928MHz)
    uhfFrequencyMode = uhfFrequencyMode == "-1" ? "8" : uhfFrequencyMode; // Default to US
    uhfPowerLevel = uhfPowerLevel == "-1" ? "20" : uhfPowerLevel; // Range 5-30 - Default to Medium Power

    powerLevelController = TextEditingController(text: uhfPowerLevel);
    frequencyModeController = TextEditingController(text: uhfFrequencyMode);

    try {
      //platformVersion = await _uhfC66Plugin.getPlatformVersion();
    } on PlatformException {
      //platformVersion = 'Failed to get platform version.';
    }
    _connectedSubscription = UhfC66Plugin.connectedStatusStream.receiveBroadcastStream().listen(updateIsConnected);
    _tagsSubscription = UhfC66Plugin.tagsStatusStream.receiveBroadcastStream().listen(updateTags);
    _locateSubscription = UhfC66Plugin.locateStatusStream.receiveBroadcastStream().listen(updateLocate);
    await UhfC66Plugin.connect();
    await UhfC66Plugin.setFrequencyMode(uhfFrequencyMode);
    await UhfC66Plugin.setPowerLevel(uhfPowerLevel);
    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      //_platformVersion = platformVersion!;
    });
  }

  final List<String> _logs = [];
  void log(String msg) {
    debugPrint('[UHF Demo] $msg');
    setState(() {
      _logs.add(msg);
      if (_logs.length > 80) {
        _logs.removeRange(0, _logs.length - 80);
      }
    });
  }

  List<TagEpc> _data = [];
  void updateTags(dynamic result) {
    final int nowMs = DateTime.now().millisecondsSinceEpoch;
    if (nowMs - _lastTagsUiUpdateMs < _minUiUpdateGapMs) {
      return;
    }
    _lastTagsUiUpdateMs = nowMs;

    final List<TagEpc> parsed = TagEpc.parseTags(result.toString());
    if (_sameTags(_data, parsed)) {
      return;
    }

    setState(() {
      _data = parsed;
    });
  }

  void updateIsConnected(dynamic isConnected) {
    log('connected $isConnected');
    //setState(() {
    //_isConnected = isConnected;
    //});
  }

  void updateLocate(dynamic raw) {
    if (!_isLocating) {
      return;
    }

    final int nowMs = DateTime.now().millisecondsSinceEpoch;
    if (nowMs - _lastLocateUiUpdateMs < _minUiUpdateGapMs) {
      return;
    }
    _lastLocateUiUpdateMs = nowMs;

    final locate = TagLocate.fromDynamic(raw);
    if (locate == null) {
      return;
    }
    _lastProximityCallbackMs = nowMs;
    _outOfRangeReported = false;
    _proximityUpdateCount += 1;
    debugPrint(
      '[UHF PROXIMITY] #$_proximityUpdateCount epc=${locate.epc} proximity=${locate.signalValue} valid=${locate.valid} rssi=${locate.rssi}',
    );
    setState(() {
      _latestProximityValue = locate.signalValue;
      _latestProximityValid = locate.valid;
      _latestProximityEpc = locate.epc;
      _latestProximityRssi = locate.rssi;
      _locates = [locate, ..._locates.where((TagLocate t) => t.epc != locate.epc)].take(20).toList();
    });
  }

  Future<void> _startContinuousFindThenLocate() async {
    if (_continuousSearchRequested || _isLocating) {
      return;
    }

    final String partial = partialEpcController.text.trim();
    if (partial.isEmpty) {
      log('Please enter a partial EPC');
      return;
    }

    _continuousSearchRequested = true;
    setState(() {
      _isSearchingForMatch = true;
      _isLocating = false;
      _locates = [];
      _latestProximityValue = null;
      _latestProximityValid = false;
      _latestProximityEpc = '-';
      _latestProximityRssi = '-';
      _proximityUpdateCount = 0;
      _lastProximityCallbackMs = 0;
      _outOfRangeReported = false;
    });

    int attempts = 0;
    log('searching continuously for match...');
    while (_continuousSearchRequested && mounted && !_isLocating) {
      attempts++;
      final bool? started = await UhfC66Plugin.startFindByPartialEpc(
        partial,
        matchType: _partialMatchType,
        scanWindowMs: _fixedScanWindowMs,
      );

      if (!_continuousSearchRequested || !mounted) {
        return;
      }

      if (started == true) {
        setState(() {
          _isLocating = true;
          _isSearchingForMatch = false;
        });
        _startProximityWatchdog();
        log('match found. now locating proximity...');
        debugPrint('[UHF PROXIMITY] waiting for proximity callbacks...');
        return;
      }

      if (attempts % 3 == 0) {
        log('still searching... (attempt $attempts)');
      }

      await Future<void>.delayed(const Duration(milliseconds: 120));
    }
  }

  Future<void> _stopFindAndLocate() async {
    _continuousSearchRequested = false;
    _proximityWatchdogTimer?.cancel();
    _proximityWatchdogTimer = null;
    final bool? stopped = await UhfC66Plugin.stopFindByPartialEpc();
    if (!mounted) {
      return;
    }
    setState(() {
      _isSearchingForMatch = false;
      _isLocating = false;
      _locates = [];
      _latestProximityValue = null;
      _latestProximityValid = false;
      _latestProximityEpc = '-';
      _latestProximityRssi = '-';
      _proximityUpdateCount = 0;
      _lastProximityCallbackMs = 0;
      _outOfRangeReported = false;
    });
    log('stop partial locate: $stopped');
  }

  void _startProximityWatchdog() {
    _proximityWatchdogTimer?.cancel();
    _proximityWatchdogTimer = Timer.periodic(const Duration(milliseconds: 350), (_) {
      if (!_isLocating || !mounted) {
        return;
      }
      final int nowMs = DateTime.now().millisecondsSinceEpoch;
      if (_lastProximityCallbackMs == 0 || nowMs - _lastProximityCallbackMs < _proximityTimeoutMs) {
        return;
      }
      if (_outOfRangeReported) {
        return;
      }

      _outOfRangeReported = true;
      debugPrint('[UHF PROXIMITY] timeout/no callback -> forcing proximity=0 (out of range)');
      setState(() {
        _latestProximityValue = 0;
        _latestProximityValid = false;
      });
    });
  }

  bool _sameTags(List<TagEpc> a, List<TagEpc> b) {
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i].epc != b[i].epc || a[i].count != b[i].count || a[i].rssi != b[i].rssi) {
        return false;
      }
    }
    return true;
  }

  @override
  void dispose() {
    _proximityWatchdogTimer?.cancel();
    _connectedSubscription?.cancel();
    _tagsSubscription?.cancel();
    _locateSubscription?.cancel();
    _barcodeSubscription?.cancel();
    powerLevelController.dispose();
    frequencyModeController.dispose();
    partialEpcController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    //_data.add(TagEpc(count: 10, epc: '5SETF7656GGY5578'));
    //_data.add(TagEpc(count: 10, epc: '6757568YG76658GH'));
    // _data.add(TagEpc(count: 10, epc: 'TNB75G568YG758GH'));
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('C66 UHF Scanner')),
        body: SingleChildScrollView(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.start,
            children: <Widget>[
              /*
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(3.0),
                  child: Image.asset('assets/logo.png', width: double.infinity, height: 80, fit: BoxFit.contain),
                ),
              ),*/
              /*Text('Running on: $_platformVersion'),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: <Widget>[
                  RaisedButton(
                      child: Text('Call connect'),
                      onPressed: () async {
                        await UhfC66Plugin.connect;
                      }),
                  RaisedButton(
                      child: Text('Call is Connected'),
                      onPressed: () async {
                        bool isConnected = await UhfC66Plugin.isConnected;
                        setState(() {
                          this._isConnected = isConnected;
                        });
                      }),
                ],
              ),
              Text(
                'UHF Reader isConnected:$_isConnected',
                style: TextStyle(color: Colors.blue.shade800),
              ),*/
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: <Widget>[
                  ElevatedButton(
                    onPressed: () async {
                      bool? isStarted = await UhfC66Plugin.startSingle();
                      log('Start single $isStarted');
                    },
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.blueAccent),
                    child: const Text('Start Single', style: TextStyle(color: Colors.white)),
                  ),
                  ElevatedButton(
                    onPressed: () async {
                      bool? isStarted = await UhfC66Plugin.startContinuous();
                      log('Start Continuous $isStarted');
                    },
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.blueAccent),
                    child: const Text('Start Continuous Reading', style: TextStyle(color: Colors.white)),
                  ),
                  /* RaisedButton(
                      child: Text('Call isStarted'),
                      onPressed: () async {
                        bool isStarted = await UhfC66Plugin.isStarted;
                        setState(() {
                          this._isStarted = isStarted;
                        });
                      }),*/
                ],
              ),
              /*Text(
                'UHF Reader isStarted:$_isStarted',
                style: TextStyle(color: Colors.blue.shade800),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: <Widget>[*/
              ElevatedButton(
                onPressed: () async {
                  bool? isStopped = await UhfC66Plugin.stop();
                  log('Stop $isStopped');
                },
                style: ElevatedButton.styleFrom(backgroundColor: Colors.redAccent),
                child: const Text('Call Stop', style: TextStyle(color: Colors.white)),
              ),
              /*   RaisedButton(
                      child: Text('Call Close'),
                      onPressed: () async {
                        await UhfC66Plugin.close;
                      }),
                ],
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: <Widget>[*/
              ElevatedButton(
                onPressed: () async {
                  await UhfC66Plugin.clearData();
                  setState(() {
                    _data = [];
                  });
                },
                style: ElevatedButton.styleFrom(backgroundColor: Colors.blueAccent),
                child: const Text('Call Clear Data', style: TextStyle(color: Colors.white)),
              ),
              Visibility(
                visible: true,
                child: ElevatedButton(
                  onPressed: () async {
                    String accessPwd = "000000000000";
                    String epcData = "112233445566778899101112";
                    bool? isWritten = await UhfC66Plugin.writeEpc(epcData, accessPwd);
                    log('Written $isWritten');
                  },
                  style: ElevatedButton.styleFrom(backgroundColor: Colors.orange),
                  child: const Text('Write EPC', style: TextStyle(color: Colors.white)),
                ),
              ),
              /* RaisedButton(
                      child: Text('Call is Empty Tags'),
                      onPressed: () async {
                        bool isEmptyTags = await UhfC66Plugin.isEmptyTags;
                        setState(() {
                          this._isEmptyTags = isEmptyTags;
                        });
                      }),
                ],
              ),
              Text(
                'UHF Reader isEmptyTags:$_isEmptyTags',
                style: TextStyle(color: Colors.blue.shade800),
              ),*/
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: <Widget>[
                  SizedBox(
                    width: 100,
                    child: TextFormField(
                      controller: powerLevelController,
                      keyboardType: TextInputType.number,
                      textAlign: TextAlign.center,
                      decoration: const InputDecoration(labelText: 'Power Level 5-30'),
                    ),
                  ),
                  ElevatedButton(
                    onPressed: () async {
                      bool? isSetPower = await UhfC66Plugin.setPowerLevel(powerLevelController.text);
                      log('isSetPower $isSetPower');
                    },
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
                    child: const Text('Set Power Level', style: TextStyle(color: Colors.white)),
                  ),
                ],
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: <Widget>[
                  SizedBox(
                    width: 100,
                    child: TextFormField(
                      controller: frequencyModeController,
                      keyboardType: TextInputType.number,
                      textAlign: TextAlign.center,
                      decoration: const InputDecoration(labelText: 'Frequency Mode'),
                    ),
                  ),
                  ElevatedButton(
                    onPressed: () async {
                      bool? isSetFrequencyMode = await UhfC66Plugin.setFrequencyMode(frequencyModeController.text);
                      log('isSetFrequencyMode $isSetFrequencyMode');
                    },
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
                    child: const Text('Set Work Area', style: TextStyle(color: Colors.white)),
                  ),
                ],
              ),
              Card(
                margin: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Find Item by Partial EPC', style: TextStyle(fontWeight: FontWeight.bold)),
                      const SizedBox(height: 8),
                      TextFormField(
                        controller: partialEpcController,
                        decoration: const InputDecoration(labelText: 'Partial EPC', hintText: 'Ex: 3008A'),
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: DropdownButtonFormField<String>(
                              value: _partialMatchType,
                              decoration: const InputDecoration(labelText: 'Match Type'),
                              items: const [
                                DropdownMenuItem(value: 'startsWith', child: Text('Starts With')),
                                DropdownMenuItem(value: 'contains', child: Text('Contains')),
                                DropdownMenuItem(value: 'endsWith', child: Text('Ends With')),
                                DropdownMenuItem(value: 'exact', child: Text('Exact')),
                              ],
                              onChanged: (String? value) {
                                if (value == null) return;
                                setState(() {
                                  _partialMatchType = value;
                                });
                              },
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Expanded(
                            child: ElevatedButton(
                              onPressed: () async {
                                await _startContinuousFindThenLocate();
                              },
                              style: ElevatedButton.styleFrom(backgroundColor: Colors.purple),
                              child: const Text('Start Search', style: TextStyle(color: Colors.white)),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: ElevatedButton(
                              onPressed: () async {
                                await _stopFindAndLocate();
                              },
                              style: ElevatedButton.styleFrom(backgroundColor: Colors.deepOrange),
                              child: const Text('Stop Find', style: TextStyle(color: Colors.white)),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _isLocating
                            ? 'Match found. Locating in progress...'
                            : _isSearchingForMatch
                            ? 'Searching continuously for match...'
                            : 'Locating stopped',
                        style: TextStyle(
                          color:
                              _isLocating
                                  ? Colors.green
                                  : _isSearchingForMatch
                                  ? Colors.blue
                                  : Colors.grey.shade700,
                        ),
                      ),
                      const SizedBox(height: 10),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: _latestProximityValid ? Colors.green.shade50 : Colors.grey.shade100,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: _latestProximityValid ? Colors.green.shade300 : Colors.grey.shade400,
                          ),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Proximity Status', style: TextStyle(fontWeight: FontWeight.bold)),
                            const SizedBox(height: 4),
                            Text(
                              _latestProximityValue == null
                                  ? 'No proximity callback yet'
                                  : 'Proximity: ${_latestProximityValue!} (0-100)',
                              style: TextStyle(
                                fontSize: 16,
                                color: _latestProximityValid ? Colors.green.shade800 : Colors.grey.shade800,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Text(
                              'Valid: $_latestProximityValid | EPC: $_latestProximityEpc | RSSI: $_latestProximityRssi',
                            ),
                            Text('Updates: $_proximityUpdateCount'),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              Container(
                width: double.infinity,
                height: 2,
                margin: const EdgeInsets.symmetric(vertical: 8),
                color: Colors.blueAccent,
              ),
              ..._locates.map(
                (TagLocate locate) => Card(
                  color: locate.valid ? Colors.green.shade50 : Colors.orange.shade50,
                  child: Container(
                    width: 330,
                    alignment: Alignment.centerLeft,
                    padding: const EdgeInsets.all(8.0),
                    child: Text(
                      'Locate ${locate.epc} | signal:${locate.signalValue} | valid:${locate.valid} | rssi:${locate.rssi}',
                      style: TextStyle(color: Colors.blue.shade800),
                    ),
                  ),
                ),
              ),
              ..._data.map(
                (TagEpc tag) => Card(
                  color: Colors.blue.shade50,
                  child: Container(
                    width: 330,
                    alignment: Alignment.center,
                    padding: const EdgeInsets.all(8.0),
                    child: Text('Tag ${tag.epc} Count:${tag.count}', style: TextStyle(color: Colors.blue.shade800)),
                  ),
                ),
              ),
              ..._logs.map(
                (String msg) => Card(
                  color: Colors.blue.shade50,
                  child: Container(
                    width: 330,
                    alignment: Alignment.center,
                    padding: const EdgeInsets.all(8.0),
                    child: Text('Log: $msg', style: TextStyle(color: Colors.blue.shade800)),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
