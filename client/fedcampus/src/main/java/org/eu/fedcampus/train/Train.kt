package org.eu.fedcampus.train

import android.content.Context
import android.util.Log
import flwr.android_client.FlowerServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import org.eu.fedcampus.train.db.ModelDao
import org.eu.fedcampus.train.db.TFLiteModel
import retrofit2.http.*
import java.io.File
import java.nio.MappedByteBuffer
import kotlin.properties.Delegates

class Train<X : Any, Y : Any> constructor(
    val context: Context,
    backendUrl: String,
    val sampleSpec: SampleSpec<X, Y>,
    val modelDao: ModelDao? = null
) {
    var sessionId: Int? = null
    var telemetry = false
        private set
    var deviceId by Delegates.notNull<Long>()
        private set
    val client = HttpClient(backendUrl)
    var state: TrainState<X, Y> = TrainState.Initialized()

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
    suspend fun advertisedModel(dataType: String): TFLiteModel = when (state) {
        is TrainState.Initialized, is TrainState.WithModel -> doAdvertisedModel(dataType)
        else -> throw IllegalStateException("`advertisedModel` called with $state")
    }

    private suspend fun doAdvertisedModel(dataType: String): TFLiteModel {
        val model = client.advertisedModel(PostAdvertisedData(dataType))
        Log.d("Model", "$model")
        state = TrainState.WithModel(model)
        return model
    }

    @Throws
    suspend fun modelDownloaded(model: TFLiteModel): Boolean {
        return modelDao?.findById(model.id)?.equals(model.toDbModel()) ?: false
    }

    /**
     * Download TFLite files to `"models/$path"` if they have not been saved to DB.
     */
    @Throws
    suspend fun downloadModelFile(modelDir: File): File = when (state) {
        is TrainState.WithModel -> doDownloadModelFile(modelDir)
        else -> throw IllegalStateException("`downloadModelFile` called with $state")
    }

    private suspend fun doDownloadModelFile(modelDir: File): File {
        val model = state.model
        val fileUrl = model.file_path
        val fileName = fileUrl.split("/").last()
        if (modelDownloaded(model)) {
            // The model is already in the DB
            Log.i(downloadModelFileTag, "skipping already downloaded model ${model.name}")
            return File(modelDir, fileName)
        }
        val fileDir = client.downloadFile(fileUrl, modelDir, fileName)
        Log.i(downloadModelFileTag, "$fileUrl -> ${fileDir.absolutePath}")
        modelDao?.upsertAll(model.toDbModel())
        return fileDir
    }

    @Throws
    suspend fun getServerInfo(start_fresh: Boolean = false): ServerData = when (state) {
        is TrainState.WithModel -> doGetServerInfo(state.model, start_fresh)
        else -> throw IllegalStateException("`getServerInfo` called with $state")
    }

    private suspend fun doGetServerInfo(model: TFLiteModel, start_fresh: Boolean): ServerData {
        val serverData = client.postServer(model, start_fresh)
        sessionId = serverData.session_id
        Log.i("Server data", "$serverData")
        return serverData
    }

    /**
     * Ask backend for advertised model, load model into [state], and download its corresponding file.
     * @return Model file.
     */
    @Throws
    suspend fun prepareModel(dataType: String): File = when (state) {
        is TrainState.Initialized, is TrainState.WithModel -> doPrepareModel(dataType)
        else -> throw IllegalStateException("`prepareModel` called with $state")
    }

    private suspend fun doPrepareModel(dataType: String) =
        withContext(Dispatchers.IO) {
            val model = advertisedModel(dataType)
            val modelDir = model.getModelDir(context)
            downloadModelFile(modelDir)
        }

    /**
     * Initialize Flower Client with TFLite model [buffer] and establish channel connection to Flower server.
     */
    @Throws
    suspend fun prepare(buffer: MappedByteBuffer, address: String, secure: Boolean) = when (state) {
        is TrainState.WithModel -> doPrepare(buffer, address, secure, state.model)
        else -> throw IllegalStateException("`prepare` called with $state")
    }

    private suspend fun doPrepare(
        buffer: MappedByteBuffer,
        address: String,
        secure: Boolean,
        model: TFLiteModel
    ): FlowerClient<X, Y> {
        val flowerClient = FlowerClient(buffer, model, sampleSpec)
        val channelBuilder =
            ManagedChannelBuilder.forTarget(address).maxInboundMessageSize(HUNDRED_MEBIBYTE)
        if (!secure) {
            channelBuilder.usePlaintext()
        }
        val channel = withContext(Dispatchers.IO) {
            channelBuilder.build()
        }
        state = TrainState.Prepared(model, flowerClient, channel)
        return flowerClient
    }

    /**
     * Only call this after loading training data into the Flower Client.
     */
    @Throws
    fun start(callback: (String) -> Unit) = when (state) {
        is TrainState.Prepared -> doStart(callback, state.model, state.flowerClient, state.channel)
        else -> throw IllegalStateException("`start` called with $state")
    }

    private fun doStart(
        callback: (String) -> Unit,
        model: TFLiteModel,
        flowerClient: FlowerClient<X, Y>,
        channel: ManagedChannel
    ) {
        val service = FlowerServiceGrpc.newStub(channel)
        val flowerServiceRunnable =
            FlowerServiceRunnable(service, this, model, flowerClient, callback)
        state = TrainState.Training(model, flowerClient, flowerServiceRunnable)
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
const val downloadModelFileTag = "Download TFLite model"
