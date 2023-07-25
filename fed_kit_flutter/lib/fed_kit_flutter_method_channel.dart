import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'fed_kit_flutter_platform_interface.dart';

/// An implementation of [FedKitFlutterPlatform] that uses method channels.
class MethodChannelFedKitFlutter extends FedKitFlutterPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('fed_kit_flutter');

  @override
  Future<String?> getPlatformVersion() async {
    final version =
        await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
