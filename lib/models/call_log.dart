class CallLog {
  final String caller;
  final DateTime timestamp;
  CallStatus status;

  CallLog({
    required this.caller,
    required this.timestamp,
    this.status = CallStatus.ringing,
  });

  factory CallLog.fromMap(Map<Object?, Object?> map) {
    return CallLog(
      caller: map['caller'] as String? ?? 'Unknown',
      timestamp: DateTime.fromMillisecondsSinceEpoch(
        (map['timestamp'] as int?) ?? 0,
      ),
      status: CallStatus.fromString(map['status'] as String?),
    );
  }

  Map<String, dynamic> toMap() => {
        'caller': caller,
        'timestamp': timestamp.millisecondsSinceEpoch,
        'status': status.value,
      };

  @override
  String toString() => 'CallLog(caller: $caller, status: $status, at: $timestamp)';
}

enum CallStatus {
  ringing('ringing'),
  active('active'),
  ended('ended'),
  rejected('rejected'),
  missed('missed');

  const CallStatus(this.value);
  final String value;

  static CallStatus fromString(String? s) =>
      CallStatus.values.firstWhere((e) => e.value == s, orElse: () => CallStatus.ringing);
}
