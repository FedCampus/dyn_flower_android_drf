import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'fed_kit_flutter_method_channel.dart';

abstract class FedKitFlutterPlatform extends PlatformInterface {
  /// Constructs a FedKitFlutterPlatform.
  FedKitFlutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static FedKitFlutterPlatform _instance = MethodChannelFedKitFlutter();

  /// The default instance of [FedKitFlutterPlatform] to use.
  ///
  /// Defaults to [MethodChannelFedKitFlutter].
  static FedKitFlutterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FedKitFlutterPlatform] when
  /// they register themselves.
  static set instance(FedKitFlutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
