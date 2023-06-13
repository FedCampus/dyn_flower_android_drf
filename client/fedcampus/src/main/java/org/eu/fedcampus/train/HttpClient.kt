package org.eu.fedcampus.train

import okhttp3.ResponseBody
import org.eu.fedcampus.train.db.Model
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.*
import java.io.File

class HttpClient constructor(url: String) {
    private val retrofit = Retrofit.Builder()
        // https://developer.android.com/studio/run/emulator-networking#networkaddresses
        .baseUrl(url)
        .addConverterFactory(GsonConverterFactory.create()).build()

    interface Advertised {
        @POST("train/advertised")
        suspend fun advertised(@Body body: PostAdvertisedData): Model
    }

    /**
     * Download advertised model information.
     */
    @Throws
    suspend fun advertisedModel(body: PostAdvertisedData): Model {
        val advertised = retrofit.create<Advertised>()
        return advertised.advertised(body)
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

data class PostAdvertisedData(val data_type: String)

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
