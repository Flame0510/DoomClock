import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'native_bridge.dart';

void main() => runApp(const DoomClockApp());

class DoomClockApp extends StatelessWidget {
  const DoomClockApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'DoomClock',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.deepPurple,
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  static const _prefsKey = 'doomclock.v1';

  // Stato persistito
  TimeOfDay _time = const TimeOfDay(hour: 7, minute: 0);
  bool _enabled = false;
  String? _targetPackage;
  String? _targetLabel;
  String? _label;
  bool _fullScreenOk = true;

  // Countdown live
  Timer? _timer;
  String _countdown = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _startCountdown();
    _load();
    _checkFullScreen();
  }

  void _startCountdown() {
    _timer?.cancel();
    _updateCountdown();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _updateCountdown());
  }

  void _updateCountdown() {
    if (!_enabled) {
      if (_countdown.isNotEmpty) setState(() => _countdown = '');
      return;
    }
    final diff = _nextTrigger().difference(DateTime.now());
    if (diff.isNegative) return;
    final d = diff.inDays;
    final h = diff.inHours % 24;
    final m = diff.inMinutes % 60;
    final s = diff.inSeconds % 60;
    String text;
    if (d > 0) {
      text = 'Will ring in $d d $h h $m min $s s';
    } else if (h > 0) {
      text = 'Will ring in $h h $m min $s s';
    } else if (m > 0) {
      text = 'Will ring in $m min $s s';
    } else {
      text = 'Will ring in $s s';
    }
    if (_countdown != text) setState(() => _countdown = text);
  }

  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// Re-checks every time the app returns to foreground,
  /// so the banner disappears as soon as the setting is enabled.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkFullScreen();
    }
  }

  Future<void> _checkFullScreen() async {
    final ok = await NativeBridge.hasFullScreenIntentPermission();
    if (mounted && ok != _fullScreenOk) setState(() => _fullScreenOk = ok);
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _time = TimeOfDay(
        hour: prefs.getInt('hour') ?? 7,
        minute: prefs.getInt('minute') ?? 0,
      );
      _enabled = prefs.getBool('enabled') ?? false;
      _targetPackage = prefs.getString('targetPackage');
      _targetLabel = prefs.getString('targetLabel');
      _label = prefs.getString('label') ?? 'Alarm';
    });

    // Chiedi permessi al primo avvio se necessario
    if (!await NativeBridge.requestExactAlarmPermission()) {
      await NativeBridge.requestExactAlarmPermission();
    }
  }

  Future<void> _save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('hour', _time.hour);
    await prefs.setInt('minute', _time.minute);
    await prefs.setBool('enabled', _enabled);
    await prefs.setString('targetPackage', _targetPackage ?? '');
    await prefs.setString('targetLabel', _targetLabel ?? '');
    await prefs.setString('label', _label ?? 'Alarm');

    // Reschedule/cancel the alarm
    if (_enabled) {
      await NativeBridge.scheduleAlarm(
        triggerAt: _nextTrigger(),
        label: _label ?? 'Alarm',
        targetPackage: _targetPackage?.isNotEmpty == true
            ? _targetPackage
            : null,
        targetLabel: _targetLabel,
      );
    } else {
      await NativeBridge.cancelAlarm();
    }
    _updateCountdown();
  }

  DateTime _nextTrigger() {
    final now = DateTime.now();
    var next = DateTime(now.year, now.month, now.day, _time.hour, _time.minute);
    if (!next.isAfter(now)) {
      next = next.add(const Duration(days: 1));
    }
    return next;
  }

  Future<void> _pickTime() async {
    final picked = await showTimePicker(
      context: context,
      initialTime: _time,
      builder: (context, child) => Theme(
        data: ThemeData(colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.deepPurple, brightness: Brightness.dark)),
        child: child!,
      ),
    );
    if (picked != null) {
      setState(() => _time = picked);
      await _save();
    }
  }

  Future<void> _pickApp() async {
    final apps = await NativeBridge.pickApp();
    if (apps['packages']!.isEmpty) return;

    final selected = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: Colors.grey[900],
      builder: (context) {
        return ListView.builder(
          itemCount: apps['packages']!.length + 1,
          itemBuilder: (context, i) {
            if (i == 0) {
              return ListTile(
                leading: const Icon(Icons.block),
                title: const Text('No app (alarm only)'),
                onTap: () => Navigator.pop(context, ''),
              );
            }
            final pkg = apps['packages']![i - 1];
            final label = apps['labels']![i - 1];
            return ListTile(
              leading: const Icon(Icons.apps),
              title: Text(label),
              subtitle: Text(pkg),
              onTap: () => Navigator.pop(context, pkg),
            );
          },
        );
      },
    );

    if (selected != null) {
      final idx = apps['packages']!.indexOf(selected);
      setState(() {
        if (selected.isEmpty) {
          _targetPackage = null;
          _targetLabel = null;
        } else {
          _targetPackage = selected;
          _targetLabel = apps['labels']![idx];
        }
      });
      await _save();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final now = DateTime.now();
    final next = _nextTrigger();
    final formattedTime =
        '${_time.hour.toString().padLeft(2, '0')}:${_time.minute.toString().padLeft(2, '0')}';

    return Scaffold(
      appBar: AppBar(
        title: const Text('DoomClock'),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            // Full screen permission notice (Android 14+)
            if (!_fullScreenOk)
              Container(
                width: double.infinity,
                margin: const EdgeInsets.only(bottom: 16),
                padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
                decoration: BoxDecoration(
                  color: theme.colorScheme.errorContainer,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    Icon(Icons.alarm_off,
                        color: theme.colorScheme.onErrorContainer, size: 32),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Enable full screen',
                            style: theme.textTheme.titleSmall?.copyWith(
                              color: theme.colorScheme.onErrorContainer,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'This way the alarm rings by itself even with the screen off.',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onErrorContainer,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    FilledButton(
                      onPressed: () async {
                        await NativeBridge.openFullScreenSettings();
                        await _checkFullScreen(); // Re-checks on return and hides the banner
                      },
                      style: FilledButton.styleFrom(
                        backgroundColor: theme.colorScheme.onErrorContainer,
                        foregroundColor: theme.colorScheme.errorContainer,
                        padding: const EdgeInsets.symmetric(horizontal: 14),
                      ),
                      child: const Text('Open'),
                    ),
                  ],
                ),
              ),

            // Alarm time
            InkWell(
              onTap: _pickTime,
              borderRadius: BorderRadius.circular(16),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 40),
                width: double.infinity,
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Column(
                  children: [
                    Text(
                      formattedTime,
                      style: TextStyle(
                        fontSize: 72,
                        fontWeight: FontWeight.w200,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${next.day}/${next.month} · next alarm $formattedTime',
                      style: theme.textTheme.bodySmall,
                    ),
                    if (_countdown.isNotEmpty) ...[
                      const SizedBox(height: 6),
                      Text(
                        _countdown,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            // Enable/disable toggle
            SwitchListTile(
              title: const Text('Enable alarm'),
              subtitle: _enabled
                  ? const Text('Will ring at the next set time')
                  : const Text('Alarm off'),
              value: _enabled,
              onChanged: (v) async {
                setState(() => _enabled = v);
                await _save();
              },
            ),
            const Divider(),
            const SizedBox(height: 8),

            // Label
            ListTile(
              leading: const Icon(Icons.label_outline),
              title: const Text('Alarm name'),
              subtitle: Text(_label ?? 'Alarm'),
              trailing: const Icon(Icons.edit),
              onTap: () async {
                final controller = TextEditingController(
                    text: _label ?? 'Alarm');
                final newLabel = await showDialog<String>(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: const Text('Alarm name'),
                    content: TextField(
                      controller: controller,
                      autofocus: true,
                    ),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context),
                        child: const Text('Cancel'),
                      ),
                      FilledButton(
                        onPressed: () =>
                            Navigator.pop(context, controller.text),
                        child: const Text('OK'),
                      ),
                    ],
                  ),
                );
                if (newLabel != null) {
                  setState(() => _label = newLabel);
                  await _save();
                }
              },
            ),
            const Divider(),
            const SizedBox(height: 8),

            // App to open on STOP
            ListTile(
              leading: const Icon(Icons.apps),
              title: const Text('Open on STOP'),
              subtitle: Text(_targetLabel != null
                  ? 'Opens: $_targetLabel'
                  : 'No app selected'),
              trailing: const Icon(Icons.chevron_right),
              onTap: _pickApp,
            ),
            const Divider(),
            const Spacer(),

            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                icon: Icon(_enabled ? Icons.alarm_on : Icons.alarm_off),
                label: Text(_enabled ? 'Alarm scheduled' : 'Alarm off'),
                style: FilledButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
                onPressed: () async {
                  if (!await NativeBridge.requestExactAlarmPermission()) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Grant the exact alarm permission'),
                      ),
                    );
                    return;
                  }
                  if (!await NativeBridge.requestNotificationPermission()) {
                    // continua comunque
                  }
                  setState(() => _enabled = !_enabled);
                  await _save();
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                          _enabled
                              ? 'Alarm set for $formattedTime'
                              : 'Alarm cancelled'),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
