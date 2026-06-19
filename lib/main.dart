import 'package:flutter/material.dart';
import 'config.dart';
import 'screens/call_feed_screen.dart';
import 'screens/settings_screen.dart';
import 'services/call_channel.dart';

Future<void> main() async {
  // Must be called before any platform channel interaction.
  WidgetsFlutterBinding.ensureInitialized();

  // Construct the singleton and register the MethodChannel handler now,
  // before runApp, so no incoming call notification is dropped during startup.
  // init() also fetches any call logs that arrived before the app launched.
  await CallChannel().init();
  await CallChannel().initBackend(backendUrl);

  runApp(const HiRobinApp());
}

class HiRobinApp extends StatelessWidget {
  const HiRobinApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HiRobin',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1A73E8)),
        useMaterial3: true,
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1A73E8),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      initialRoute: '/',
      routes: {
        '/': (_) => const CallFeedScreen(),
        '/settings': (_) => const SettingsScreen(),
      },
    );
  }
}
