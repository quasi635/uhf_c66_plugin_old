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
