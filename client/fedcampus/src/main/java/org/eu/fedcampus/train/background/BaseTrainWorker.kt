package org.eu.fedcampus.train.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
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
    val flowerHost: String,
    backendUrl: String,
    sampleSpec: SampleSpec<X, Y>,
    val dataType: String,
    val loadData: (Context, FlowerClient<X, Y>) -> Unit,
    val trainCallback: (String) -> Unit,
    modelDao: ModelDao? = null,
    deviceId: Long? = null,
    val startFresh: Boolean = false,
    val useTLS: Boolean = false,
) : CoroutineWorker(context, params) {
    val train = Train(context, backendUrl, sampleSpec, modelDao)

    init {
        if (deviceId != null) train.enableTelemetry(deviceId)
        Log.i(TAG, "Starting with backend $backendUrl for $dataType.")
    }

    override suspend fun doWork() = try {
        val flowerClient = prepare()
        Log.i(TAG, "Prepared $flowerClient.")
        loadData(context, flowerClient)
        Log.i(TAG, "Loaded data.")
        train.start(trainCallback)
        Log.i(TAG, "Finished.")
        Result.success()
    } catch (err: Throwable) {
        Log.e(TAG, err.stackTraceToString())
        Result.retry()
    }

    private suspend fun prepare(): FlowerClient<X, Y> {
        val modelFile = train.prepareModel(dataType)
        val serverData = train.getServerInfo(startFresh)
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

fun <Y : Any, X : Any> trainWorkRequest() = PeriodicWorkRequestBuilder<BaseTrainWorker<X, Y>>(
    12, TimeUnit.HOURS,
    10, TimeUnit.HOURS,
).setConstraints(realIdleConstraints())
    .addTag(BaseTrainWorker.TAG).build()

fun realIdleConstraints() =
    Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresCharging(true)
        .setRequiresDeviceIdle(true).build()
