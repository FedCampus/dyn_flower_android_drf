package org.eu.fedcampus.train.background

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class TrainWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork() = try {
        // TODO: implement
        Result.success()
    } catch (err: Throwable) {
        Log.e(TAG, err.stackTraceToString())
        Result.failure()
    }

    companion object {
        const val TAG = "TrainWorker"
    }
}
