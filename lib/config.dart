import 'package:flutter/foundation.dart';

const _envUrl = String.fromEnvironment('BACKEND_URL');

final backendUrl = _envUrl.isNotEmpty
    ? _envUrl
    : kReleaseMode
        ? 'ws://YOUR_SERVER_IP:3000'
        : 'ws://10.0.2.2:3000';
