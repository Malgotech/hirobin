import 'package:flutter/services.dart';
import '../models/call_log.dart';

typedef CallHandler = void Function(CallLog log);
typedef AudioLevelHandler = void Function(double rms);
typedef AudioStateHandler = void Function(String state, String? error);

class CallChannel {
  static const _channel = MethodChannel('com.yourcompany.hirobin/calls');

  static final CallChannel _instance = CallChannel._();
  factory CallChannel() => _instance;
  CallChannel._() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  final List<CallLog> _logs = [];
  List<CallLog> get logs => List.unmodifiable(_logs);

  CallHandler? onIncomingCall;
  AudioLevelHandler? onAudioLevel;
  AudioStateHandler? onAudioStateChanged;

  Future<void> initBackend(String url) async {
    await _channel.invokeMethod<void>('setBackendUrl', url);
  }

  Future<void> init() async {
    final raw = await _channel.invokeMethod<List<Object?>>('getCallLogs');
    if (raw != null) {
      _logs
        ..clear()
        ..addAll(raw
            .whereType<Map<Object?, Object?>>()
            .map(CallLog.fromMap));
    }
  }

  Future<bool> updateContext(Map<String, String> context) async {
    final result = await _channel.invokeMethod<bool>('updateContext', context);
    return result ?? false;
  }

  Future<void> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onIncomingCall':
        final map = call.arguments as Map<Object?, Object?>;
        final log = CallLog.fromMap(map);
        _logs.insert(0, log);
        onIncomingCall?.call(log);

      case 'onAudioLevel':
        final rms = (call.arguments as num).toDouble();
        onAudioLevel?.call(rms);

      case 'onAudioStateChanged':
        final args = call.arguments as Map<Object?, Object?>;
        final state = args['state'] as String? ?? '';
        final error = (args['error'] as String?)?.let((e) => e.isEmpty ? null : e);
        onAudioStateChanged?.call(state, error);
    }
  }
}

extension _NullIfEmpty on String {
  T? let<T>(T Function(String) f) => f(this);
}
