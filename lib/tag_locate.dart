import 'dart:convert';

class TagLocate {
  final String epc;
  final String rssi;
  final int signalValue;
  final bool valid;

  TagLocate({
    required this.epc,
    required this.rssi,
    required this.signalValue,
    required this.valid,
  });

  factory TagLocate.fromMap(Map<String, dynamic> json) {
    return TagLocate(
      epc: (json['epc'] ?? '').toString(),
      rssi: (json['rssi'] ?? '').toString(),
      signalValue: (json['signalValue'] as num?)?.toInt() ?? 0,
      valid: json['valid'] == true,
    );
  }

  factory TagLocate.fromJson(String source) {
    return TagLocate.fromMap(json.decode(source) as Map<String, dynamic>);
  }

  static TagLocate? fromDynamic(dynamic raw) {
    try {
      if (raw is String) {
        return TagLocate.fromJson(raw);
      }
      if (raw is Map) {
        return TagLocate.fromMap(Map<String, dynamic>.from(raw));
      }
    } catch (_) {
      return null;
    }
    return null;
  }
}
