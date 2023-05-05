package flwr.android_client.train

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class Train constructor(val url: String) {
    val retrofit = Retrofit.Builder()
        // https://developer.android.com/studio/run/emulator-networking#networkaddresses
        .baseUrl(url)
        .addConverterFactory(GsonConverterFactory.create()).build()

    interface GetAdvertised {
        @GET("train/get_advertised")
        suspend fun getAdvertised(): TFLiteModelData
    }

    suspend fun getAdvertisedModel(): TFLiteModelData {
        val getAdvertised = retrofit.create(GetAdvertised::class.java)
        return getAdvertised.getAdvertised()
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
fun getAdvertisedModel(context: Context, host: String, port: Int) {
    // TODO: HTTPS
    val url = "http://$host:$port"
    Log.i("URL", url)
    GlobalScope.launch {
        lateinit var model: TFLiteModelData
        try {
            model = Train(url).getAdvertisedModel()
        } catch (err: Exception) {
            Log.e("get advertised model", "request failed", err)
        }
        Log.d("Model", "$model")
        val downloadManager =
            context.getSystemService(DownloadManager::class.java)
        for (path in model.tflite_files) {
            Log.i("Download TFLite model", path)
            val request = DownloadManager.Request(Uri.parse("$url/$path"))
                .setDestinationInExternalFilesDir(context, "models/", path)
            downloadManager.enqueue(request)
        }
    }
}
