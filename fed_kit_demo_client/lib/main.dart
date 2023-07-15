import 'dart:core';
import 'package:flutter/material.dart';

main() {
  runApp(const App());
}

class App extends StatelessWidget {
  const App({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FedKit Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.yellow),
        useMaterial3: true,
      ),
      home: const HomePage(title: 'FedKit Demo Home Page'),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key, required this.title});
  final String title;

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int? clientPartitionId;
  Uri? flServerIP;
  int? flServerPort;

  var logs = [const Text('Welcome to Flower!')];
  var clientPartitionIdController = TextEditingController();
  var flServerIPController = TextEditingController();
  var flServerPortController = TextEditingController();
  var scrollController = ScrollController();

  appendLog(String message) {
    setState(() {
      logs.add(Text(message));
      scrollController.animateTo(
        scrollController.position.maxScrollExtent + 32.0,
        duration: const Duration(milliseconds: 100),
        curve: Curves.easeInOut,
      );
    });
  }

  handleInput() {
    try {
      clientPartitionId = int.parse(clientPartitionIdController.text);
    } catch (e) {
      return appendLog('Invalid client partition id!');
    }
    try {
      flServerIP = Uri.parse(flServerIPController.text);
      if (!flServerIP!.isScheme('http')) {
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
  }

  @override
  Widget build(BuildContext context) {
    var children = [
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
          padding: const EdgeInsets.only(
              top: 16.0, bottom: 32.0, left: 12.0, right: 12.0),
          itemCount: logs.length,
          itemBuilder: (context, index) => logs[index],
        ),
      )
    ];

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        centerTitle: true,
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: children,
        ),
      ),
    );
  }
}
