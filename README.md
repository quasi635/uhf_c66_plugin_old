# uhf_c66_plugin

A flutter plugin for UHF type C66 to read UHF Cards. This was forked from the C72 plugin created by amorenew.

I included the newer API version from Chainway (2025-02-09) and updated the code for Flutter 3

## Getting Started

- Import the library:

    `import 'package:uhf_c66_plugin/uhf_c66_plugin.dart';`

- Open connection to the UHF reader

    `await UhfC66Plugin.connect();`

- Check if is the reader connected

    `await UhfC66Plugin.isConnected();`

- Start reading data a single UHF card

    `await UhfC66Plugin.startSingle();`

- Start reading data multi 'continuous' UHF cards

    `await UhfC66Plugin.startContinuous();`

- Is started reading

    `await UhfC66Plugin.isStarted();`

- Stop Reading

    `await UhfC66Plugin.stop();`

- Close the connection

    `await UhfC66Plugin.close();`

- Clear cached data for the reader

    `await UhfC66Plugin.clearData();`

- Is Empty Tags

    `await UhfC66Plugin.isEmptyTags();`

- Write to EPC Tag

    `await UhfC66Plugin.writeEpc(String writeData, String accessPwd)`

- Set Power level (1 dBm : 30 dBm use string numbers)

    `await UhfC66Plugin.setPowerLevel(String level);`

- Get Power level (returns string number, 1-30 dBm)

    `await UhfC66Plugin.getPowerLevel();`

- Set Frequency mode (string number)
    - 1：China Standard(840~845MHz)
    - 2：China Standard2(920~925MHz)
    - 4：Europe Standard(865~868MHz)
    - 8：USA(902-928MHz)
    - 22：Korea(917~923MHz)
    - 50: Japan(952~953MHz)
    - 51: South Africa(915~919MHz)
    - 52: China Taiwan
    - 53: Vietnam(918~923MHz)
    - 54: Peru(915MHz-928MHz)
    - 55: Russia( 860MHz-867.6MHz)
    - 128: Morocco

    `await UhfC66Plugin.setFrequencyMode(String area);`

- Get Work area (returns string number)

    `await UhfC66Plugin.getFrequencyMode();`

- Listen to tags status

    `UhfC66Plugin.tagsStatusStream.receiveBroadcastStream().listen(updateTags);`

    ```dart
       List<TagEpc> _data = [];
       void updateTags(dynamic result) {
        setState(() {
            _data = TagEpc.parseTags(result);
         });
       }
    ```

## Hardware Keys

The plugin installs an Activity-level key interceptor, so hardware key events
fire regardless of which Flutter widget currently holds input focus. No widget
wrapping is required.

- Toggle continuous scanning with the main trigger button

    Each press of the trigger toggles `startContinuous()` / `stop()`. Call once
    at startup (e.g. in `initState`); it's idempotent.

    ```dart
    UhfC66Plugin.enableTriggerScanning();
    ```

    To stop this behaviour:

    ```dart
    UhfC66Plugin.disableTriggerScanning();
    ```

- Listen to raw trigger press/release events

    ```dart
    UhfC66Plugin.triggerEvents.listen((UhfTriggerEvent event) {
      // event is UhfTriggerEvent.down or UhfTriggerEvent.up
    });
    ```

- Listen to the yellow left/right side keys

    ```dart
    UhfC66Plugin.sideKeyEvents.listen((UhfSideKeyEvent e) {
      if (!e.isDown) return;
      if (e.key == UhfSideKey.left) {
        // handle left yellow button press
      } else {
        // handle right yellow button press
      }
    });
    ```

- Register additional key codes (other Chainway models / firmware variants)

    If a different device reports a different Android keyCode, log the
    `keyCode` once and register it:

    ```dart
    UhfC66Plugin.addTriggerKeyCode(0x138);
    UhfC66Plugin.addSideKeyCode(UhfSideKey.left, 0x137);
    UhfC66Plugin.addSideKeyCode(UhfSideKey.right, 0x139);
    ```

## 2D Barcode Scanner

The plugin drives the onboard imager via `BarcodeUtility` in broadcast output
mode, so scans are delivered programmatically (independent of which widget has
focus, and without the scanner typing into text fields). The imager decodes
both 2D symbologies (QR, Data Matrix, PDF417, Aztec, …) and 1D symbologies
(Code 128, UPC-A/E, EAN-8/13, Code 39, ITF, Codabar, …).

- Start a scan

    Turns on the aimer. Stays on until a code decodes, the scanner times out,
    or you call `stopBarcodeScan()`. The module is lazily opened on first call.

    ```dart
    await UhfC66Plugin.startBarcodeScan();
    ```

- Cancel a scan

    ```dart
    await UhfC66Plugin.stopBarcodeScan();
    ```

- Listen for decode results

    ```dart
    UhfC66Plugin.barcodeEvents.listen((UhfBarcodeResult r) {
      if (r.isSuccess) {
        debugPrint('Scanned: ${r.data}');
      }
    });
    ```

    Fields on `UhfBarcodeResult`:

    - `resultCode`: `UhfBarcodeDecodeStatus.success` (1) or `failure` (2).
    - `data`: decoded text, or `null` when the scan didn't succeed.
    - `isSuccess`: convenience boolean.

- Power off the scanner module (optional)

    Call when you're done with barcode scanning (e.g. screen dispose) to save
    power. The next `startBarcodeScan()` transparently reopens it.

    ```dart
    await UhfC66Plugin.closeBarcodeScanner();
    ```

- Trigger from a hardware button

    Combine with `sideKeyEvents` to turn a yellow side key into a scan trigger:

    ```dart
    UhfC66Plugin.sideKeyEvents.listen((e) {
      if (e.isDown && e.key == UhfSideKey.left) {
        UhfC66Plugin.startBarcodeScan();
      }
    });
    ```

## Finder Functions (Partial EPC Locate)

Use these methods to continuously search for a tag using a partial EPC, then start proximity locate when a match is found.

- Start find by partial EPC

          `await UhfC66Plugin.startFindByPartialEpc('3008A', matchType: 'startsWith', scanWindowMs: 1500);`

          Parameters:

          -   `partialEpc`: required partial EPC string.
          -   `matchType`: one of `startsWith`, `contains`, `endsWith`, `exact`.
          -   `scanWindowMs`: scan duration in milliseconds for each find attempt.

- Stop partial EPC find/locate

          `await UhfC66Plugin.stopFindByPartialEpc();`

- Check locate state

          `await UhfC66Plugin.isLocating();`

- Listen to locate/proximity status stream

          `UhfC66Plugin.locateStatusStream.receiveBroadcastStream().listen(updateLocate);`

          ```dart
          void updateLocate(dynamic raw) {
              final locate = TagLocate.fromDynamic(raw);
              if (locate == null) return;

              debugPrint(
                  'epc=${locate.epc} proximity=${locate.signalValue} valid=${locate.valid} rssi=${locate.rssi}',
              );
          }
          ```
