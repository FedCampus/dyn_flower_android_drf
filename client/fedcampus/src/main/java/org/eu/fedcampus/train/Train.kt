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
    lateinit var model: TFLiteModel
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
    suspend fun advertisedModel(dataType: String): TFLiteModel {
        model = client.advertisedModel(PostAdvertisedData(dataType))
        Log.d("Model", "$model")
        return model
    }

    suspend fun modelDownloaded(): Boolean {
        return modelDao?.findById(model.id)?.equals(model.toDbModel()) ?: false
    }

    /**
     * Download TFLite files to `"models/$path"` if they have not been saved to DB.
     */
    @Throws
    suspend fun downloadModelFile(modelDir: File): File {
        val fileUrl = model.file_path
        val fileName = fileUrl.split("/").last()
        if (modelDownloaded()) {
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
    suspend fun getServerInfo(): ServerData {
        val serverData = client.postServer(model)
        sessionId = serverData.session_id
        Log.i("Server data", "$serverData")
        return serverData
    }

    /**
     * Ask backend for advertised model, initialize [model], and download its corresponding file.
     * @return Model file.
     */
    @Throws
    suspend fun prepareModel(dataType: String): File {
        return withContext(Dispatchers.IO) {
            advertisedModel(dataType)
            val modelDir = model.getModelDir(context)
            downloadModelFile(modelDir)
        }
    }

    /**
     * Initialize [flowerClient] with [TFLiteModel] and establish [channel] connection to Flower server.
     */
    @Throws
    suspend fun prepare(TFLiteModel: MappedByteBuffer, address: String, secure: Boolean) {
        flowerClient = FlowerClient(TFLiteModel, model)
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
     * Only call this after loading training data into [flowerClient].
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
const val downloadModelFileTag = "Download TFLite model"
