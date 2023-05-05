package flwr.android_client.train

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import flwr.android_client.FlowerClient
import flwr.android_client.MainActivity
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
    val n_layers: Int,
    val tflite_files: List<String>
)

fun startFlowerClient(context: MainActivity, host: String) {
    context.fc = FlowerClient(context, "models/${context.model.name}/")
    context.trainButton.isEnabled = true
    context.setResultText("Channel object created. Ready to train!")
}

/**
 * Download advertised model information.
Download TFLite files to `"models/$path"`.
 */
@OptIn(DelicateCoroutinesApi::class)
fun getAdvertisedModel(context: MainActivity, host: String, port: Int) {
    // TODO: HTTPS
    val url = "http://$host:$port"
    Log.i("URL", url)
    GlobalScope.launch {
        try {
            context.model = Train(url).getAdvertisedModel()
        } catch (err: Exception) {
            Log.e("get advertised model", "request failed", err)
            return@launch
        }
        Log.d("Model", "${context.model}")
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val downloads = HashSet<Long>()
        for (url_path in context.model.tflite_files) {
            var path = url_path.split("/").last()
            path = "${context.model.name}/$path"
            Log.i("Download TFLite model", path)
            val request = DownloadManager.Request(Uri.parse("$url/$url_path"))
                .setDestinationInExternalFilesDir(context, "models/", path)
            val runId = downloadManager.enqueue(request)
            downloads.add(runId)
        }
        Log.i("TFLite model download tasks IDs", "$downloads")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val runId = intent.getLongExtra(DownloadManager.ACTION_DOWNLOAD_COMPLETE, -1)
                Log.i("Receiver received intent", "$intent")
                if (downloads.contains(runId)) {
                    downloads.remove(runId)
                    Log.i("Download task finished", "$runId")
                }
                if (downloads.isEmpty()) {
                    // All download finished.
                    startFlowerClient(context, host)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}
