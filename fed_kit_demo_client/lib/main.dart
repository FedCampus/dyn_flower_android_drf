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
  var logs = const Text('Welcome to Flower!\n');

  appendLog(String message) {
    setState(() {
      var newText = '${logs.data!}$message\n';
      logs = Text(newText);
    });
  }

  @override
  Widget build(BuildContext context) {
    var clientPartitionIdController = TextEditingController();
    var flServerIPController = TextEditingController();
    var flServerPortController = TextEditingController();
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
      ElevatedButton(
        onPressed: () {
          var clientPartitionId = clientPartitionIdController.text;
          var flServerIP = flServerIPController.text;
          var flServerPort = flServerPortController.text;
          appendLog(
              'Connecting with Partition ID: $clientPartitionId, Server IP: $flServerIP, Port: $flServerPort');
        },
        child: const Text('Connect'),
      ),
      ElevatedButton(
        onPressed: () => appendLog('Training started.'),
        child: const Text('Train'),
      ),
      const Text('Activity Log'),
      Expanded(
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: Container(
            margin: const EdgeInsets.only(bottom: 16.0),
            padding: const EdgeInsets.all(16.0),
            child: logs,
          ),
        ),
      ),
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
