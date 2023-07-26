package org.eu.fedcampus.fed_kit_client

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger, "fed_kit_flutter"
        ).setMethodCallHandler(::handle)
    }

    fun handle(call: MethodCall, result: MethodChannel.Result) = when (call.method) {
        "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")
        else -> result.notImplemented()
    }
}
