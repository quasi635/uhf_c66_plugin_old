import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:uhf_c66_plugin/uhf_c66_plugin.dart';
import 'package:uhf_c66_plugin/tag_epc.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _uhfC66Plugin = UhfC66Plugin();
  String _platformVersion = 'Unknown';
  final bool _isStarted = false;
  final bool _isEmptyTags = false;
  bool _isConnected = false;

  TextEditingController powerLevelController = TextEditingController(text: "");
  TextEditingController frequencyModeController = TextEditingController(text: "");
  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String? platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.

    String uhfFrequencyMode = (await UhfC66Plugin.getFrequencyMode()).toString();
    String uhfPowerLevel = (await UhfC66Plugin.getPowerLevel()).toString();

    //String uhfFrequencyMode = "8"; // 0x08 for USA (902-928MHz)
    uhfFrequencyMode = uhfFrequencyMode == "-1" ? "8" : uhfFrequencyMode; // Default to US
    uhfPowerLevel = uhfPowerLevel == "-1" ? "20" : uhfPowerLevel; // Range 5-30 - Default to Medium Power

    powerLevelController = TextEditingController(text: uhfPowerLevel);
    frequencyModeController = TextEditingController(text: uhfFrequencyMode);

    try {
      platformVersion = await _uhfC66Plugin.getPlatformVersion();
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }
    UhfC66Plugin.connectedStatusStream.receiveBroadcastStream().listen(updateIsConnected);
    UhfC66Plugin.tagsStatusStream.receiveBroadcastStream().listen(updateTags);
    await UhfC66Plugin.connect();
    await UhfC66Plugin.setFrequencyMode(uhfFrequencyMode);
    await UhfC66Plugin.setPowerLevel(uhfPowerLevel);
    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion!;
    });
  }

  final List<String> _logs = [];
  void log(String msg) {
    setState(() {
      _logs.add(msg);
    });
  }

  List<TagEpc> _data = [];
  void updateTags(dynamic result) {
    log('update tags');
    setState(() {
      _data = TagEpc.parseTags(result);
    });
  }

  void updateIsConnected(dynamic isConnected) {
    log('connected $isConnected');
    //setState(() {
    _isConnected = isConnected;
    //});
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
              Container(
                width: double.infinity,
                height: 2,
                margin: const EdgeInsets.symmetric(vertical: 8),
                color: Colors.blueAccent,
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
