package org.eu.fedcampus.train

import android.util.Log
import android.util.Pair
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.lang.Integer.min
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer

/**
 * Construction of this class requires disk read.
 */
class FlowerClient(tfliteFile: MappedByteBuffer) : AutoCloseable {
    val interpreter = Interpreter(tfliteFile)
    val trainingSamples = mutableListOf<TrainingSample>()
    val testSamples = mutableListOf<TrainingSample>()
    val mutex = Mutex()

    suspend fun addSample(bottleneck: FloatArray, label: FloatArray, isTraining: Boolean) {
        mutex.withLock {
            val samples = if (isTraining) trainingSamples else testSamples
            samples.add(TrainingSample(bottleneck, label))
        }
    }

    fun weights(): Array<Tensor> {
        val inputs = mapOf<String, Any>()
        val outputs = mutableMapOf<String, Any>()
        interpreter.runSignature(inputs, outputs, "parameters")
        return parametersFromMap(outputs)
    }

    fun updateParameters(parameters: Array<Any>): Array<Tensor> {
        val outputs = mutableMapOf<String, Any>()
        interpreter.runSignature(parametersToMap(parameters), outputs, "restore")
        return parametersFromMap(outputs)
    }

    fun fit(
        epochs: Int = 1, batchSize: Int = 32, lossCallback: ((List<Float>) -> Unit)? = null
    ): List<Double> {
        Log.d(TAG, "Starting to train for $epochs epochs with batch size $batchSize.")
        return (1..epochs).map {
            val losses = trainOneEpoch(batchSize)
            val avgLoss = losses.average()
            Log.d(TAG, "Epoch $it: average loss = $avgLoss.")
            lossCallback?.invoke(losses)
            avgLoss
        }
    }

    fun evaluate(): Pair<Float, Float> {
        // TODO: Return test loss and accuracy. Reference [TransferLearningModel.getTestStatistics].
        return Pair(0f, 0f)
    }

    private fun trainOneEpoch(batchSize: Int): List<Float> {
        if (trainingSamples.isEmpty()) {
            Log.d(TAG, "No training samples available.")
            return listOf()
        }

        trainingSamples.shuffle()
        return trainingBatches(min(batchSize, trainingSamples.size)).map {
            val bottlenecks = it.map { sample -> sample.bottleneck }.toTypedArray()
            val labels = it.map { sample -> sample.label }.toTypedArray()
            training(bottlenecks, labels)
        }.toList()
    }

    private fun training(
        bottlenecks: Array<FloatArray>, labels: Array<FloatArray>
    ): Float {
        Log.i(
            "Flower client training",
            "bottlenecks: (${bottlenecks.size}, ${bottlenecks[0].size}), labels: (${labels.size}, ${labels[0].size})"
        )
        val inputs = mapOf<String, Any>(
            "x" to bottlenecks,
            "y" to labels,
        )
        val loss = FloatBuffer.allocate(1)
        val outputs = mapOf<String, Any>(
            "loss" to loss,
        )
        interpreter.runSignature(inputs, outputs, "train")
        return loss.get(0)
    }


    /**
     * Constructs an iterator that iterates over training sample batches.
     */
    private fun trainingBatches(trainBatchSize: Int): Sequence<List<TrainingSample>> {
        return sequence {
            var nextIndex = 0

            while (nextIndex < trainingSamples.size) {
                val fromIndex = nextIndex
                nextIndex += trainBatchSize

                val batch = if (nextIndex >= trainingSamples.size) {
                    trainingSamples.subList(
                        trainingSamples.size - trainBatchSize, trainingSamples.size
                    )
                } else {
                    trainingSamples.subList(fromIndex, nextIndex)
                }

                yield(batch)
            }
        }
    }

    fun parametersFromMap(map: Map<String, Any>): Array<Tensor> {
        val length = map.size - 1
        return (0..length).map { map["a$it"] as Tensor }.toTypedArray()
    }

    fun parametersToMap(parameters: Array<Any>): Map<String, Any> {
        return parameters.mapIndexed { index, tensor -> "a$index" to tensor }.toMap()
    }

    companion object {
        private const val TAG = "Flower Client"
    }

    override fun close() {
        interpreter.close()
    }
}

data class TrainingSample(val bottleneck: FloatArray, val label: FloatArray)
