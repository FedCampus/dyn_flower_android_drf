package org.eu.fedcampus.train

import android.util.Log
import android.util.Pair
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eu.fedcampus.train.db.Model
import org.tensorflow.lite.Interpreter
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer

/**
 * Construction of this class requires disk read.
 */
class FlowerClient(tfliteFile: MappedByteBuffer, val model: Model) : AutoCloseable {
    val interpreter = Interpreter(tfliteFile)
    val trainingSamples = mutableListOf<TrainingSample>()
    val testSamples = mutableListOf<TrainingSample>()
    val mutex = Mutex()

    suspend fun addSample(
        bottleneck: Array<Array<FloatArray>>, label: FloatArray, isTraining: Boolean
    ) {
        mutex.withLock {
            val samples = if (isTraining) trainingSamples else testSamples
            samples.add(TrainingSample(bottleneck, label))
        }
    }

    fun weights(): Array<ByteBuffer> {
        val inputs = FakeNonEmptyMap<String, Any>()
        val outputs = emptyParameterMap()
        interpreter.runSignature(inputs, outputs, "parameters")
        Log.i(TAG, "Raw weights: $outputs.")
        return parametersFromMap(outputs)
    }

    fun updateParameters(parameters: Array<ByteBuffer>): Array<ByteBuffer> {
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
        bottlenecks: Array<Array<Array<FloatArray>>>, labels: Array<FloatArray>
    ): Float {
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

    fun parametersFromMap(map: Map<String, Any>): Array<ByteBuffer> {
        assert(model.n_layers == map.size.toLong())
        return (0 until model.n_layers).map { map["a$it"] as ByteBuffer }.toTypedArray()
    }

    fun parametersToMap(parameters: Array<ByteBuffer>): Map<String, Any> {
        assert(model.n_layers == parameters.size.toLong())
        return parameters.mapIndexed { index, bytes -> "a$index" to bytes }.toMap()
    }

    // TODO: Use different length for each layer.
    private fun emptyParameterMap(): Map<String, Any> {
        return (0 until model.n_layers).map { "a$it" to ByteBuffer.allocate(1800) }.toMap()
    }

    companion object {
        private const val TAG = "Flower Client"
    }

    override fun close() {
        interpreter.close()
    }
}

// TODO: Make generic.
@Suppress("ArrayInDataClass")
data class TrainingSample(val bottleneck: Array<Array<FloatArray>>, val label: FloatArray)

/**
 * This map always returns `false` when `isEmpty` is called to bypass TFLite interpreter's
 * stupid empty check on the `input` argument of `runSignature`.
 */
class FakeNonEmptyMap<K, V> : HashMap<K, V>() {
    override fun isEmpty(): Boolean {
        return false
    }
}
