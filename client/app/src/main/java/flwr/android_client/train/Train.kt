package flwr.android_client.train

import android.util.Log
import flwr.android_client.MainActivity
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.File

class Train constructor(url: String) {
    private val retrofit = Retrofit.Builder()
        // https://developer.android.com/studio/run/emulator-networking#networkaddresses
        .baseUrl(url)
        .addConverterFactory(GsonConverterFactory.create()).build()

    interface GetAdvertised {
        @GET("train/get_advertised")
        suspend fun getAdvertised(): TFLiteModelData
    }

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
}

data class TFLiteModelData(
    val name: String,
    val n_layers: Long,
    val tflite_files: List<String>
)

/**
 * Download advertised model information.
Download TFLite files to `"models/$path"`.
 */
@OptIn(DelicateCoroutinesApi::class)
fun getAdvertisedModel(activity: MainActivity, host: String, port: Int) {
    // TODO: HTTPS
    val url = "http://$host:$port"
    Log.i("URL", url)
    GlobalScope.launch {
        val train = Train(url)
        lateinit var model: TFLiteModelData
        try {
            model = train.getAdvertisedModel()
        } catch (err: Exception) {
            Log.e("get advertised model", "request failed", err)
        }
        Log.d("Model", "$model")
        val downloadTasks = mutableListOf<Deferred<Unit>>()
        val modelPath = "models/${model.name}/"
        val modelDir = activity.getExternalFilesDir(modelPath)!!
        for (fileUrl in model.tflite_files) {
            val task = GlobalScope.async {
                val fileName = fileUrl.split("/").last()
                Log.i("Download TFLite model", "$fileUrl -> $modelPath$fileName")
                train.downloadFile(fileUrl, modelDir, fileName)
            }
            downloadTasks.add(task)
        }
        downloadTasks.awaitAll()
        Log.i("Downloaded TFLite model", "at models/${model.name}/")
        activity.model = model
        activity.connectGrpc(modelDir)
    }
}
