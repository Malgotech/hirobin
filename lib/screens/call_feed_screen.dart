import 'package:flutter/material.dart';
import '../models/call_log.dart';
import '../services/call_channel.dart';

class CallFeedScreen extends StatefulWidget {
  const CallFeedScreen({super.key});

  @override
  State<CallFeedScreen> createState() => _CallFeedScreenState();
}

class _CallFeedScreenState extends State<CallFeedScreen>
    with SingleTickerProviderStateMixin {
  final _channel = CallChannel();

  List<CallLog> _logs = [];
  CallLog? _activeCall;
  double _audioLevel = 0.0;
  String _audioState = 'idle';
  bool _loading = true;

  late final AnimationController _pulseController;

  @override
  void initState() {
    super.initState();

    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    )..repeat(reverse: true);

    _channel.onIncomingCall = (log) {
      if (!mounted) return;
      setState(() {
        _logs = List.of(_channel.logs);
        _activeCall = log;
        _audioState = 'ringing';
      });
    };

    _channel.onAudioLevel = (rms) {
      if (!mounted) return;
      setState(() => _audioLevel = rms);
    };

    _channel.onAudioStateChanged = (state, error) {
      if (!mounted) return;
      setState(() {
        _audioState = state;
        if (state == 'stopped' || state == 'error') _activeCall = null;
        if (error != null) _showError(error);
      });
    };

    _channel.init().then((_) {
      if (!mounted) return;
      setState(() {
        _logs = List.of(_channel.logs);
        _loading = false;
      });
    });
  }

  @override
  void dispose() {
    _channel.onIncomingCall = null;
    _channel.onAudioLevel = null;
    _channel.onAudioStateChanged = null;
    _pulseController.dispose();
    super.dispose();
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Theme.of(context).colorScheme.error,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('HiRobin'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'Settings',
            onPressed: () => Navigator.of(context).pushNamed('/settings'),
          ),
        ],
      ),
      body: Column(
        children: [
          if (_activeCall != null) _ActiveCallBanner(
            log: _activeCall!,
            audioLevel: _audioLevel,
            audioState: _audioState,
            pulseController: _pulseController,
          ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _logs.isEmpty
                    ? _EmptyState()
                    : _CallLogList(logs: _logs),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------

class _ActiveCallBanner extends StatelessWidget {
  const _ActiveCallBanner({
    required this.log,
    required this.audioLevel,
    required this.audioState,
    required this.pulseController,
  });

  final CallLog log;
  final double audioLevel;
  final String audioState;
  final AnimationController pulseController;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final isRinging = audioState == 'ringing';

    return AnimatedBuilder(
      animation: pulseController,
      builder: (context, child) {
        final opacity = isRinging
            ? 0.7 + 0.3 * pulseController.value
            : 1.0;
        return Opacity(opacity: opacity, child: child);
      },
      child: Container(
        width: double.infinity,
        color: isRinging
            ? colorScheme.primaryContainer
            : colorScheme.secondaryContainer,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Icon(
              isRinging ? Icons.ring_volume : Icons.mic,
              color: isRinging
                  ? colorScheme.onPrimaryContainer
                  : colorScheme.onSecondaryContainer,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    log.caller,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          color: isRinging
                              ? colorScheme.onPrimaryContainer
                              : colorScheme.onSecondaryContainer,
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                  Text(
                    isRinging ? 'Incoming call…' : 'Active — AI listening',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: isRinging
                              ? colorScheme.onPrimaryContainer
                              : colorScheme.onSecondaryContainer,
                        ),
                  ),
                ],
              ),
            ),
            if (!isRinging)
              _AudioLevelBar(level: audioLevel),
          ],
        ),
      ),
    );
  }
}

class _AudioLevelBar extends StatelessWidget {
  const _AudioLevelBar({required this.level});

  final double level;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 48,
      height: 24,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: List.generate(5, (i) {
          final threshold = (i + 1) / 5;
          final active = level >= threshold;
          return AnimatedContainer(
            duration: const Duration(milliseconds: 60),
            width: 6,
            height: 6 + i * 4.0,
            decoration: BoxDecoration(
              color: active
                  ? Theme.of(context).colorScheme.primary
                  : Theme.of(context).colorScheme.outline.withValues(alpha: 0.3),
              borderRadius: BorderRadius.circular(2),
            ),
          );
        }),
      ),
    );
  }
}

// ---------------------------------------------------------------------------

class _CallLogList extends StatelessWidget {
  const _CallLogList({required this.logs});

  final List<CallLog> logs;

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      itemCount: logs.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) => _CallLogTile(log: logs[index]),
    );
  }
}

class _CallLogTile extends StatelessWidget {
  const _CallLogTile({required this.log});

  final CallLog log;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return ListTile(
      leading: CircleAvatar(
        backgroundColor: _statusColor(log.status, colorScheme).withValues(alpha: 0.15),
        child: Icon(
          _statusIcon(log.status),
          color: _statusColor(log.status, colorScheme),
          size: 20,
        ),
      ),
      title: Text(
        log.caller,
        style: const TextStyle(fontWeight: FontWeight.w500),
      ),
      subtitle: Text(_formatTime(log.timestamp)),
      trailing: _StatusChip(status: log.status),
    );
  }

  Color _statusColor(CallStatus status, ColorScheme cs) => switch (status) {
        CallStatus.ringing => cs.primary,
        CallStatus.active  => cs.tertiary,
        CallStatus.ended   => cs.outline,
        CallStatus.rejected => cs.error,
        CallStatus.missed  => cs.error,
      };

  IconData _statusIcon(CallStatus status) => switch (status) {
        CallStatus.ringing  => Icons.ring_volume,
        CallStatus.active   => Icons.call,
        CallStatus.ended    => Icons.call_end,
        CallStatus.rejected => Icons.call_missed_outgoing,
        CallStatus.missed   => Icons.call_missed,
      };

  String _formatTime(DateTime dt) {
    final now = DateTime.now();
    final diff = now.difference(dt);
    if (diff.inSeconds < 60) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    return '${dt.day}/${dt.month}/${dt.year}';
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final CallStatus status;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final label = status.value[0].toUpperCase() + status.value.substring(1);
    final color = switch (status) {
      CallStatus.ringing  => colorScheme.primary,
      CallStatus.active   => colorScheme.tertiary,
      CallStatus.ended    => colorScheme.outline,
      CallStatus.rejected => colorScheme.error,
      CallStatus.missed   => colorScheme.error,
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------

class _EmptyState extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.call_outlined,
            size: 64,
            color: Theme.of(context).colorScheme.outline,
          ),
          const SizedBox(height: 16),
          Text(
            'No calls yet',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: Theme.of(context).colorScheme.outline,
                ),
          ),
          const SizedBox(height: 8),
          Text(
            'Incoming calls will appear here.',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context).colorScheme.outline,
                ),
          ),
        ],
      ),
    );
  }
}
