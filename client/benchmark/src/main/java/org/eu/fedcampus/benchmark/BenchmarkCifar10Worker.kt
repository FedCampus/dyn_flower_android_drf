package org.eu.fedcampus.benchmark

import android.content.Context
import android.util.Log
import androidx.work.WorkerParameters
import org.eu.fedcampus.train.SampleSpec
import org.eu.fedcampus.train.background.BaseTrainWorker
import org.eu.fedcampus.train.helpers.classifierAccuracy
import org.eu.fedcampus.train.helpers.negativeLogLikelihoodLoss

class BenchmarkCifar10Worker(context: Context, params: WorkerParameters) :
    BaseTrainWorker<Float3DArray, FloatArray>(
        context,
        params,
        sampleSpec(),
        DATA_TYPE,
        ::loadData,
        ::logTrain
    ) {
    companion object {
        const val TAG = "BenchmarkCifar10Worker"
    }
}

fun logTrain(msg: String) {
    Log.i(BenchmarkCifar10Worker.TAG, msg)
}

fun sampleSpec() = SampleSpec<Float3DArray, FloatArray>(
    { it.toTypedArray() },
    { it.toTypedArray() },
    { Array(it) { FloatArray(10) } },
    ::negativeLogLikelihoodLoss,
    ::classifierAccuracy,
)


const val DATA_TYPE = "CIFAR10_32x32x3"

typealias Float3DArray = Array<Array<FloatArray>>
