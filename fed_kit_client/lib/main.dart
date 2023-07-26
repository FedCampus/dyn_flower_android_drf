import 'package:fed_kit_client/platform_channel.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:logger/logger.dart';

final logger = Logger();

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _channel = PlatformChannel();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  Future<void> initPlatformState() async {
    String platformVersion;
    try {
      platformVersion =
          await _channel.getPlatformVersion() ?? 'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
      appendLog('Running on: $_platformVersion.');
    });
  }

  late int clientPartitionId;
  late Uri host;
  late int backendPort;

  final logs = [const Text('Welcome to Flower!')];
  final clientPartitionIdController = TextEditingController();
  final flServerIPController = TextEditingController();
  final flServerPortController = TextEditingController();
  final scrollController = ScrollController();

  appendLog(String message) {
    logger.d('appendLog: $message');
    setState(() {
      logs.add(Text(message));
    });
  }

  connect() async {
    try {
      clientPartitionId = int.parse(clientPartitionIdController.text);
    } catch (e) {
      return appendLog('Invalid client partition id!');
    }
    try {
      host = Uri.parse('http://${flServerIPController.text}');
      if (!host.hasEmptyPath || host.host.isEmpty || host.hasPort) {
        throw Exception();
      }
    } catch (e) {
      return appendLog('Invalid backend server host!');
    }
    Uri backendUrl;
    try {
      backendPort = int.parse(flServerPortController.text);
      backendUrl = host.replace(port: backendPort);
    } catch (e) {
      return appendLog('Invalid backend server port!');
    }
    appendLog(
        'Connecting with Partition ID: $clientPartitionId, Server IP: $host, Port: $backendPort');
    try {
      final serverPort = await _channel.connect(host, backendUrl);
      appendLog('Connected to Flower server on port $serverPort.');
    } catch (error, stacktrace) {
      appendLog('Request failed: $error');
      logger.e(stacktrace);
    }
  }

  @override
  Widget build(BuildContext context) {
    final children = [
      TextFormField(
        controller: clientPartitionIdController,
        decoration: const InputDecoration(
          labelText: 'Client Partition ID (1-10)',
          filled: true,
        ),
        keyboardType: TextInputType.number,
      ),
      TextFormField(
        controller: flServerIPController,
        decoration: const InputDecoration(
          labelText: 'Backend Server Host',
          filled: true,
        ),
        keyboardType: TextInputType.text,
      ),
      TextFormField(
        controller: flServerPortController,
        decoration: const InputDecoration(
          labelText: 'Backend Server Port',
          filled: true,
        ),
        keyboardType: TextInputType.number,
      ),
      Row(mainAxisAlignment: MainAxisAlignment.center, children: [
        ElevatedButton(
          onPressed: connect,
          child: const Text('Connect'),
        ),
        ElevatedButton(
          onPressed: () {
            appendLog('Training started.');
          },
          child: const Text('Train'),
        ),
      ]),
      const Text('Activity Log'),
      Expanded(
        child: ListView.builder(
          controller: scrollController,
          reverse: true,
          padding: const EdgeInsets.only(
              top: 16.0, bottom: 32.0, left: 12.0, right: 12.0),
          itemCount: logs.length,
          itemBuilder: (context, index) => logs[logs.length - index - 1],
        ),
      )
    ];

    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('FedKit example app'),
        ),
        body: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: children,
        ),
      ),
    );
  }
}
