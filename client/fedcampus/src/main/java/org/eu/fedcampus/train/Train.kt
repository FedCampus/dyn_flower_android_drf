package org.eu.fedcampus.train

import android.content.Context
import android.util.Log
import flwr.android_client.FlowerServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import org.eu.fedcampus.train.db.Model
import org.eu.fedcampus.train.db.ModelDao
import org.tensorflow.lite.examples.transfer.api.ExternalModelLoader
import org.tensorflow.lite.examples.transfer.api.TransferLearningModel
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
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
     * Model to train with. Initialized after calling [getAdvertisedModel].
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

    /**
     * Download advertised model information.
     */
    @Throws
    suspend fun getAdvertisedModel(): Model {
        model = client.getAdvertisedModel()
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
    suspend fun prepareModelLoader(): ExternalModelLoader {
        withContext(Dispatchers.IO) {
            getAdvertisedModel()
            modelDir = model.getModelDir(context)
            downloadModelFiles()
        }
        return ExternalModelLoader(modelDir)
    }

    /**
     * Load [model] into Flower client and establish connection to Flower server.
     */
    @Throws
    suspend fun prepare(model: TransferLearningModel, address: String, secure: Boolean) {
        flowerClient = FlowerClient(model)
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

class HttpClient constructor(url: String) {
    private val retrofit = Retrofit.Builder()
        // https://developer.android.com/studio/run/emulator-networking#networkaddresses
        .baseUrl(url)
        .addConverterFactory(GsonConverterFactory.create()).build()

    interface GetAdvertised {
        @GET("train/get_advertised")
        suspend fun getAdvertised(): Model
    }

    /**
     * Download advertised model information.
     */
    @Throws
    suspend fun getAdvertisedModel(): Model {
        val getAdvertised = retrofit.create<GetAdvertised>()
        return getAdvertised.getAdvertised()
    }

    interface DownloadFile {
        @GET
        @Streaming
        suspend fun download(@Url url: String): ResponseBody
    }

    @Throws
    suspend fun downloadFile(url: String, parentDir: File, fileName: String) {
        parentDir.mkdirs()
        val file = File(parentDir, fileName)
        val download = retrofit.create<DownloadFile>()
        download.download(url).byteStream().use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    interface PostServer {
        @POST("train/server")
        suspend fun postServer(@Body body: PostServerData): ServerData
    }

    @Throws
    suspend fun postServer(model: Model): ServerData {
        val body = PostServerData(model.id)
        val postServer = retrofit.create<PostServer>()
        return postServer.postServer(body)
    }

    interface FitInsTelemetry {
        @POST("telemetry/fit_ins")
        suspend fun fitInsTelemetry(@Body body: FitInsTelemetryData)
    }

    @Throws
    suspend fun fitInsTelemetry(body: FitInsTelemetryData) {
        val fitInsTelemetry = retrofit.create<FitInsTelemetry>()
        fitInsTelemetry.fitInsTelemetry(body)
    }

    interface EvaluateInsTelemetry {
        @POST("telemetry/evaluate_ins")
        suspend fun evaluateInsTelemetry(@Body body: EvaluateInsTelemetryData)
    }

    @Throws
    suspend fun evaluateInsTelemetry(body: EvaluateInsTelemetryData) {
        val evaluateInsTelemetry = retrofit.create<EvaluateInsTelemetry>()
        evaluateInsTelemetry.evaluateInsTelemetry(body)
    }
}

// Always change together with Python `train.data.ServerData`.
data class ServerData(val status: String, val session_id: Int?, val port: Int?)

data class PostServerData(val id: Long)

// Always change together with Python `telemetry.models.FitInsTelemetryData`.
data class FitInsTelemetryData(
    val device_id: Long,
    val session_id: Int,
    val start: Long,
    val end: Long
)

// Always change together with Python `telemetry.models.EvaluateInsTelemetryData`.
data class EvaluateInsTelemetryData(
    val device_id: Long,
    val session_id: Int,
    val start: Long,
    val end: Long,
    val loss: Float,
    val accuracy: Float,
    val test_size: Int
)
