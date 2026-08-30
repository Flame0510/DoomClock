import 'dart:async';

import 'package:flutter/services.dart';

/// Bridge verso i moduli nativi Android (MethodChannel).
class NativeBridge {
  static const MethodChannel _channel = MethodChannel('doomclock/native');

  // Alarm scheduling
  static Future<void> scheduleAlarm({
    required DateTime triggerAt,
    required String label,
    String? targetPackage,
    String? targetLabel,
  }) async {
    await _channel.invokeMethod('scheduleAlarm', {
      'triggerAtMs': triggerAt.millisecondsSinceEpoch,
      'label': label,
      'fullScreen': true,
      'targetPackage': targetPackage,
      'targetLabel': targetLabel,
    });
  }

  static Future<void> cancelAlarm() async {
    await _channel.invokeMethod('cancelAlarm');
  }

  // Permessi
  static Future<bool> hasExactAlarmPermission() async {
    return await _channel.invokeMethod('hasExactAlarmPermission') ?? false;
  }

  static Future<bool> requestExactAlarmPermission() async {
    return await _channel.invokeMethod('requestExactAlarmPermission') ?? false;
  }

  static Future<bool> requestNotificationPermission() async {
    return await _channel.invokeMethod('requestNotificationPermission') ?? false;
  }

  static Future<bool> hasFullScreenIntentPermission() async {
    return await _channel.invokeMethod('hasFullScreenIntentPermission') ?? false;
  }

  static Future<void> openFullScreenSettings() async {
    await _channel.invokeMethod('openFullScreenSettings');
  }

  // Picker app + apertura
  static Future<Map<String, List<String>>> pickApp() async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>('pickApp');
    return {
      'packages': (result?['packages'] as List<dynamic>? ?? []).cast<String>(),
      'labels': (result?['labels'] as List<dynamic>? ?? []).cast<String>(),
    };
  }

  static Future<bool> openApp(String package) async {
    return await _channel.invokeMethod('openApp', {'package': package}) ?? false;
  }
}
