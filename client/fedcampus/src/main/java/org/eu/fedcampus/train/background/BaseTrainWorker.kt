package org.eu.fedcampus.train.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import org.eu.fedcampus.train.FlowerClient
import org.eu.fedcampus.train.SampleSpec
import org.eu.fedcampus.train.Train
import org.eu.fedcampus.train.db.ModelDao
import org.eu.fedcampus.train.helpers.loadMappedFile
import java.util.concurrent.TimeUnit

/**
 * Inherit your training worker from this and fill in the constructor parameters
 * (except for [context] and `params`) with concrete values.
 */
open class BaseTrainWorker<X : Any, Y : Any>(
    val context: Context, params: WorkerParameters,
    val sampleSpec: SampleSpec<X, Y>,
    val dataType: String,
    val loadData: suspend (Context, FlowerClient<X, Y>, Int) -> Unit,
    val trainCallback: (String) -> Unit,
    val modelDao: ModelDao? = null,
    val useTLS: Boolean = false,
) : CoroutineWorker(context, params) {
    val data = inputData
    lateinit var train: Train<X, Y>

    override suspend fun doWork() = try {
        val backendUrl = data.getString("backendUrl")!!
        val deviceId = data.getLong("deviceId", 0L)
        val flowerHost = data.getString("flowerHost")!!
        val participantId = data.getInt("participantId", 1)

        train = Train(context, backendUrl, sampleSpec, modelDao)
        if (deviceId != 0L) train.enableTelemetry(deviceId)
        Log.i(TAG, "Starting with backend $backendUrl for $dataType.")

        val flowerClient = prepare(flowerHost)
        Log.i(TAG, "Prepared $flowerClient.")

        loadData(context, flowerClient, participantId)
        Log.i(TAG, "Loaded data.")

        train.start(trainCallback)
        Log.i(TAG, "Finished.")

        Result.success()
    } catch (err: Throwable) {
        Log.e(TAG, err.stackTraceToString())
        Result.retry()
    }

    private suspend fun prepare(flowerHost: String): FlowerClient<X, Y> {
        val modelFile = train.prepareModel(dataType)
        val serverData = train.getServerInfo()
        if (serverData.port == null) {
            throw Error("Flower server port not available, status ${serverData.status}")
        }
        val address = "dns:///$flowerHost:${serverData.port}"
        return train.prepare(loadMappedFile(modelFile), address, useTLS)
    }

    companion object {
        const val TAG = "TrainWorker"
    }
}

fun trainWorkerData(backendUrl: String, deviceId: Long, flowerHost: String, participantId: Int) =
    Data.Builder().putString("backendUrl", backendUrl)
        .putLong("deviceId", deviceId)
        .putString("flowerHost", flowerHost)
        .putInt("participantId", participantId)
        .build()

inline fun <reified W : BaseTrainWorker<X, Y>, X : Any, Y : Any> trainWorkRequest(inputData: Data) =
    PeriodicWorkRequestBuilder<W>(
        8, TimeUnit.HOURS,
        6, TimeUnit.HOURS,
    ).setConstraints(realIdleConstraints())
        .setInputData(inputData)
        .addTag(BaseTrainWorker.TAG).build()

inline fun <reified W : BaseTrainWorker<X, Y>, X : Any, Y : Any> fastTrainWorkRequest(inputData: Data) =
    PeriodicWorkRequestBuilder<W>(
        8, TimeUnit.MINUTES,
        6, TimeUnit.MINUTES,
    ).setConstraints(wifiConstraints())
        .setInputData(inputData)
        .addTag(BaseTrainWorker.TAG).build()

fun realIdleConstraints() =
    Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresCharging(true)
        .setRequiresDeviceIdle(true).build()

fun wifiConstraints() =
    Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
