import 'fed_kit_flutter_platform_interface.dart';

class FedKitFlutter {
  Future<String?> getPlatformVersion() {
    return FedKitFlutterPlatform.instance.getPlatformVersion();
  }
}
