import 'package:flutter/material.dart';
import 'dart:async';
import 'package:http/http.dart' as http;

import 'package:flutter/services.dart';
import 'package:fed_kit_flutter/fed_kit_flutter.dart';
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
  final _fedKitFlutterPlugin = FedKitFlutter();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  Future<void> initPlatformState() async {
    String platformVersion;
    try {
      platformVersion = await _fedKitFlutterPlugin.getPlatformVersion() ??
          'Unknown platform version';
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
  late Uri flServerIP;
  late int flServerPort;

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

  handleInput() async {
    try {
      clientPartitionId = int.parse(clientPartitionIdController.text);
    } catch (e) {
      return appendLog('Invalid client partition id!');
    }
    try {
      flServerIP = Uri.parse(flServerIPController.text);
      if (!flServerIP.isScheme('http')) {
        throw Exception();
      }
    } catch (e) {
      return appendLog('Invalid Flower server IP!');
    }
    try {
      flServerPort = int.parse(flServerPortController.text);
    } catch (e) {
      return appendLog('Invalid Flower server port!');
    }
    appendLog(
        'Connecting with Partition ID: $clientPartitionId, Server IP: $flServerIP, Port: $flServerPort');
    final flServerUrl =
        flServerIP.replace(port: flServerPort, path: '/train/advertised');
    try {
      final response =
          await http.post(flServerUrl, body: {'data_type': 'CIFAR10_32x32x3'});
      appendLog('Sending to $flServerUrl.');
      appendLog(
          "Response status: ${response.statusCode}, body: ${response.body}");
    } catch (error, stacktrace) {
      appendLog('Request to $flServerUrl failed: $error');
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
          labelText: 'FL Server IP',
          filled: true,
        ),
        keyboardType: TextInputType.url,
      ),
      TextFormField(
        controller: flServerPortController,
        decoration: const InputDecoration(
          labelText: 'FL Server Port',
          filled: true,
        ),
        keyboardType: TextInputType.number,
      ),
      Row(mainAxisAlignment: MainAxisAlignment.center, children: [
        ElevatedButton(
          onPressed: handleInput,
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
