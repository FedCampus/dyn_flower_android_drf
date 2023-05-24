package flwr.android_client.train

import android.content.Context
import android.util.Log
import flwr.android_client.FlowerClient
import flwr.android_client.FlowerServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.*
import java.io.File

class Train constructor(val context: Context, val host: String, port: Int) {
    lateinit var channel: ManagedChannel
    val url = generateUrl(host, port)
    val client = HttpClient(url)
    lateinit var model: TFLiteModelData
    lateinit var modelDir: File
    lateinit var server: ServerData
    lateinit var flowerClient: FlowerClient
    lateinit var flowerServiceRunnable: FlowerServiceRunnable

    /**
     * Download advertised model information.
     */
    suspend fun getAdvertisedModel(): TFLiteModelData {
        model = client.getAdvertisedModel()
        Log.d("Model", "$model")
        return model
    }

    /**
     * Download TFLite files to `"models/$path"`.
     */
    suspend fun downloadModelFiles() {
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
    }

    suspend fun getServerInfo(): ServerData {
        server = client.postServer(model)
        Log.i("Server data", "$server")
        return server
    }

    suspend fun issueTrain() {
        getAdvertisedModel()
        modelDir = model.getModelDir(context)
        downloadModelFiles()
        getServerInfo()
    }

    @Throws
    fun prepare() {
        if (server.port == null) {
            throw Error("Flower server port not available, status ${server.status}")
        }
        flowerClient = FlowerClient(context, modelDir)
        channel = ManagedChannelBuilder.forAddress(host, server.port!!)
            .maxInboundMessageSize(10 * 1024 * 1024)
            .usePlaintext().build()
        flowerServiceRunnable = FlowerServiceRunnable()
    }

    @Throws
    fun start(callback: (String) -> Unit) {
        flowerServiceRunnable.run(FlowerServiceGrpc.newStub(channel), this, callback)
    }
}

class HttpClient constructor(url: String) {
    private val retrofit = Retrofit.Builder()
        // https://developer.android.com/studio/run/emulator-networking#networkaddresses
        .baseUrl(url)
        .addConverterFactory(GsonConverterFactory.create()).build()

    interface GetAdvertised {
        @GET("train/get_advertised")
        suspend fun getAdvertised(): TFLiteModelData
    }

    /**
     * Download advertised model information.
     */
    suspend fun getAdvertisedModel(): TFLiteModelData {
        val getAdvertised = retrofit.create<GetAdvertised>()
        return getAdvertised.getAdvertised()
    }

    interface DownloadFile {
        @GET
        @Streaming
        suspend fun download(@Url url: String): ResponseBody
    }

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

    suspend fun postServer(model: TFLiteModelData): ServerData {
        val body = PostServerData(model.id)
        val postServer = retrofit.create<PostServer>()
        return postServer.postServer(body)
    }

}

data class TFLiteModelData(
    val id: Long,
    val name: String,
    val n_layers: Long,
    val tflite_files: List<String>
) {
    fun getModelDir(context: Context): File {
        return context.getExternalFilesDir("models/$name/")!!
    }
}

data class ServerData(val status: String, val port: Int?)

data class PostServerData(val id: Long)

fun generateUrl(host: String, port: Int): String {
    // TODO: HTTPS
    val url = "http://$host:$port"
    Log.i("URL", url)
    return url
}
