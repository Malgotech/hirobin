import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hirobin/main.dart';

void main() {
  testWidgets('HiRobinApp renders call feed screen', (tester) async {
    // Stub the MethodChannel so CallChannel().init() doesn't throw in tests.
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('com.yourcompany.hirobin/calls'),
      (call) async => call.method == 'getCallLogs' ? <Object?>[] : null,
    );

    await tester.pumpWidget(const HiRobinApp());
    await tester.pump();

    expect(find.text('HiRobin'), findsOneWidget);
  });
}
