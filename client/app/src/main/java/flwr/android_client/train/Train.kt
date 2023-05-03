package flwr.android_client.train

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

@OptIn(DelicateCoroutinesApi::class)
fun getAdvertisedModel(url: String) {
    Log.i("URL", url)
    GlobalScope.launch {
        try {
            val model = Train(url).getAdvertisedModel()
            Log.d("Model", "$model")
        } catch (err: Exception) {
            Log.e("get advertised model", "request failed", err)
        }
    }
}
