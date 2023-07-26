package org.eu.fedcampus.fed_kit_client

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.eu.fedcampus.train.FlowerClient
import org.eu.fedcampus.train.Train
import org.eu.fedcampus.train.examples.cifar10.DATA_TYPE
import org.eu.fedcampus.train.examples.cifar10.Float3DArray
import org.eu.fedcampus.train.examples.cifar10.sampleSpec
import org.eu.fedcampus.train.helpers.deviceId
import org.eu.fedcampus.train.helpers.loadMappedFile

class MainActivity : FlutterActivity() {
    val scope = MainScope()
    lateinit var train: Train<Float3DArray, FloatArray>
    lateinit var flowerClient: FlowerClient<Float3DArray, FloatArray>

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger, "fed_kit_flutter"
        ).setMethodCallHandler(::handle)
    }

    fun handle(call: MethodCall, result: Result) = scope.launch {
        when (call.method) {
            "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")
            "connect" -> {
                val host = call.argument<String>("host")!!
                val backendUrl = call.argument<String>("backendUrl")!!
                connect(host, backendUrl, result)
            }

            else -> result.notImplemented()
        }
    }

    suspend fun connect(host: String, backendUrl: String, result: Result) {
        train = Train(this, backendUrl, sampleSpec())
        train.enableTelemetry(deviceId(this))
        val modelFile = train.prepareModel(DATA_TYPE)
        // TODO: freshStartCheckbox
        val serverData = train.getServerInfo()
        if (serverData.port == null) {
            return result.error(
                TAG, "Flower server port not available", "status ${serverData.status}"
            )
        }
        flowerClient =
            train.prepare(loadMappedFile(modelFile), "dns:///$host:${serverData.port}", false)
        result.success(serverData.port)
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
