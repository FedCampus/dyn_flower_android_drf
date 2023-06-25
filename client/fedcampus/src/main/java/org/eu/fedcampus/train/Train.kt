package org.eu.fedcampus.train

import android.content.Context
import android.util.Log
import flwr.android_client.FlowerServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import org.eu.fedcampus.train.db.Model
import org.eu.fedcampus.train.db.ModelDao
import org.tensorflow.lite.examples.transfer.api.ExternalModelLoader
import org.tensorflow.lite.examples.transfer.api.TransferLearningModel
import retrofit2.http.*
import java.io.File
import kotlin.properties.Delegates

class Train constructor(
    val context: Context,
    backendUrl: String,
    val modelDao: ModelDao? = null
) {
    var sessionId: Int? = null
    var telemetry = false
        private set
    var deviceId by Delegates.notNull<Long>()
        private set
    lateinit var channel: ManagedChannel
    val client = HttpClient(backendUrl)


    /**
     * Model to train with. Initialized after calling [advertisedModel].
     */
    lateinit var model: Model
    lateinit var modelDir: File
    lateinit var flowerClient: FlowerClient

    /**
     * Communication and training instance. Initialized after calling [start].
     */
    lateinit var flowerServiceRunnable: FlowerServiceRunnable


    fun enableTelemetry(id: Long) {
        deviceId = id
        telemetry = true
    }

    @Suppress("unused")
    fun disableTelemetry() {
        deviceId = 0
        telemetry = false
    }

    /**
     * Download advertised model information.
     */
    @Throws
    suspend fun advertisedModel(dataType: String): Model {
        model = client.advertisedModel(PostAdvertisedData(dataType))
        Log.d("Model", "$model")
        return model
    }

    suspend fun modelDownloaded(): Boolean {
        return modelDao?.findById(model.id)?.equals(model) ?: false
    }

    /**
     * Download TFLite files to `"models/$path"` if they have not been saved to DB.
     */
    @Throws
    suspend fun downloadModelFiles() {
        if (modelDownloaded()) {
            // The model is already in the DB
            Log.i("downloadModelFiles", "skipping already downloaded model ${model.name}")
            return
        }
        val scope = CoroutineScope(Job())
        val downloadTasks = mutableListOf<Deferred<Unit>>()
        for (fileUrl in model.tflite_files) {
            val task = scope.async {
                val fileName = fileUrl.split("/").last()
                Log.i("Download TFLite model", "$fileUrl -> ${modelDir.absolutePath}$fileName")
                client.downloadFile(fileUrl, modelDir, fileName)
            }
            downloadTasks.add(task)
        }
        downloadTasks.awaitAll()
        Log.i("Downloaded TFLite model", "at models/${model.name}/")
        modelDao?.upsertAll(model)
    }

    @Throws
    suspend fun getServerInfo(): ServerData {
        val serverData = client.postServer(model)
        sessionId = serverData.session_id
        Log.i("Server data", "$serverData")
        return serverData
    }

    /**
     * Ask backend for advertised model, download its files, and ask backend for Flower server.
     * @return Model loader.
     */
    @Throws
    suspend fun prepareModelLoader(dataType: String): ExternalModelLoader {
        withContext(Dispatchers.IO) {
            advertisedModel(dataType)
            modelDir = model.getModelDir(context)
            downloadModelFiles()
        }
        // TODO: Return [MappedByteBuffer] instead.
        return ExternalModelLoader(modelDir)
    }

    /**
     * Load [model] into Flower client and establish connection to Flower server.
     */
    @Throws
    suspend fun prepare(model: TransferLearningModel, address: String, secure: Boolean) {
        // TODO: Remove hardcoded file.
        flowerClient = FlowerClient(loadMappedAssetFile(context, "cifar10.tflite"))
        val channelBuilder =
            ManagedChannelBuilder.forTarget(address).maxInboundMessageSize(HUNDRED_MEBIBYTE)
        if (!secure) {
            channelBuilder.usePlaintext()
        }
        withContext(Dispatchers.IO) {
            channel = channelBuilder.build()
        }
    }

    /**
     * Only call this after loading training data into `flowerClient.tlModel`.
     */
    @Throws
    fun start(callback: (String) -> Unit) {
        flowerServiceRunnable =
            FlowerServiceRunnable(FlowerServiceGrpc.newStub(channel), this, callback)
    }

    /**
     * Ensure that telemetry is enabled and [sessionId] is non-null.
     */
    @Throws(AssertionError::class)
    fun checkTelemetryEnabled() {
        assert(telemetry)
        assert(sessionId !== null)
    }

    @Throws
    suspend fun fitInsTelemetry(start: Long, end: Long) {
        checkTelemetryEnabled()
        val body = FitInsTelemetryData(deviceId, sessionId!!, start, end)
        client.fitInsTelemetry(body)
        Log.i("Telemetry", "Sent fit instruction telemetry")
    }

    @Throws
    suspend fun evaluateInsTelemetry(
        start: Long,
        end: Long,
        loss: Float,
        accuracy: Float,
        test_size: Int
    ) {
        checkTelemetryEnabled()
        val body =
            EvaluateInsTelemetryData(deviceId, sessionId!!, start, end, loss, accuracy, test_size)
        client.evaluateInsTelemetry(body)
        Log.i("Telemetry", "Sent evaluate instruction telemetry")
    }
}

const val HUNDRED_MEBIBYTE = 100 * 1024 * 1024
