import 'package:flutter_test/flutter_test.dart';
import 'package:fed_kit_flutter/fed_kit_flutter.dart';
import 'package:fed_kit_flutter/fed_kit_flutter_platform_interface.dart';
import 'package:fed_kit_flutter/fed_kit_flutter_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFedKitFlutterPlatform
    with MockPlatformInterfaceMixin
    implements FedKitFlutterPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FedKitFlutterPlatform initialPlatform = FedKitFlutterPlatform.instance;

  test('$MethodChannelFedKitFlutter is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFedKitFlutter>());
  });

  test('getPlatformVersion', () async {
    FedKitFlutter fedKitFlutterPlugin = FedKitFlutter();
    MockFedKitFlutterPlatform fakePlatform = MockFedKitFlutterPlatform();
    FedKitFlutterPlatform.instance = fakePlatform;

    expect(await fedKitFlutterPlugin.getPlatformVersion(), '42');
  });
}
