package org.eu.fedcampus.train

import android.util.Log
import android.util.Pair
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eu.fedcampus.train.db.TFLiteModel
import org.tensorflow.lite.Interpreter
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer

/**
 * Construction of this class requires disk read.
 */
class FlowerClient(tfliteFile: MappedByteBuffer, val model: TFLiteModel) : AutoCloseable {
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

    suspend fun updateParameters(parameters: Array<ByteBuffer>): Array<ByteBuffer> {
        val outputs = emptyParameterMap()
        mutex.withLock {
            interpreter.runSignature(parametersToMap(parameters), outputs, "restore")
        }
        return parametersFromMap(outputs)
    }

    suspend fun fit(
        epochs: Int = 1, batchSize: Int = 32, lossCallback: ((List<Float>) -> Unit)? = null
    ): List<Double> {
        Log.d(TAG, "Starting to train for $epochs epochs with batch size $batchSize.")
        return mutex.withLock {
            (1..epochs).map {
                val losses = trainOneEpoch(batchSize)
                val avgLoss = losses.average()
                Log.d(TAG, "Epoch $it: average loss = $avgLoss.")
                lossCallback?.invoke(losses)
                avgLoss
            }
        }
    }

    fun evaluate(): Pair<Float, Float> {
        // TODO: Return test loss and accuracy. Reference [TransferLearningModel.getTestStatistics].
        return Pair(0f, 0f)
    }

    /**
     * Not thread-safe.
     */
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

    /**
     * Not thread-safe.
     */
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
        assertIntsEqual(model.layers_sizes.size, map.size)
        return (0 until map.size).map {
            val buffer = map["a$it"] as ByteBuffer
            buffer.rewind()
            buffer
        }.toTypedArray()
    }

    fun parametersToMap(parameters: Array<ByteBuffer>): Map<String, Any> {
        assertIntsEqual(model.layers_sizes.size, parameters.size)
        return parameters.mapIndexed { index, bytes -> "a$index" to bytes }.toMap()
    }

    private fun emptyParameterMap(): Map<String, Any> {
        return model.layers_sizes.mapIndexed { index, size -> "a$index" to ByteBuffer.allocate(size) }
            .toMap()
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
