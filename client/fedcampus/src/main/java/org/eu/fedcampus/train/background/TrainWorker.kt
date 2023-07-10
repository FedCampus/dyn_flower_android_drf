package org.eu.fedcampus.train.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class TrainWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork() = try {
        // TODO: implement
        Result.success()
    } catch (err: Throwable) {
        Log.e(TAG, err.stackTraceToString())
        Result.retry()
    }

    companion object {
        const val TAG = "TrainWorker"
    }
}

fun trainWorkRequest() = PeriodicWorkRequestBuilder<TrainWorker>(
    12, TimeUnit.HOURS,
    10, TimeUnit.HOURS,
).setConstraints(realIdleConstraints())
    .addTag(TrainWorker.TAG).build()

fun realIdleConstraints() =
    Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresCharging(true)
        .setRequiresDeviceIdle(true).build()
