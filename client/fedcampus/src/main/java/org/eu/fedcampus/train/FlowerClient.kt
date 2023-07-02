package org.eu.fedcampus.train

import android.util.Log
import android.util.Pair
import org.eu.fedcampus.train.db.TFLiteModel
import org.tensorflow.lite.Interpreter
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * Construction of this class requires disk read.
 */
class FlowerClient<X : Any, Y : Any>(
    tfliteFile: MappedByteBuffer,
    val model: TFLiteModel,
    val spec: SampleSpec<X, Y>,
) : AutoCloseable {
    val interpreter = Interpreter(tfliteFile)
    val interpreterLock = ReentrantLock()
    val trainingSamples = mutableListOf<Sample<X, Y>>()
    val testSamples = mutableListOf<Sample<X, Y>>()
    val trainSampleLock = ReentrantReadWriteLock()
    val testSampleLock = ReentrantReadWriteLock()

    /**
     * Thread-safe.
     */
    fun addSample(
        bottleneck: X, label: Y, isTraining: Boolean
    ) {
        val samples = if (isTraining) trainingSamples else testSamples
        val lock = if (isTraining) trainSampleLock else testSampleLock
        lock.writeLock().withLock {
            samples.add(Sample(bottleneck, label))
        }
    }

    fun weights(): Array<ByteBuffer> {
        val inputs: Map<String, Any> = FakeNonEmptyMap()
        val outputs = emptyParameterMap()
        runSignatureLocked(inputs, outputs, "parameters")
        Log.i(TAG, "Raw weights: $outputs.")
        return parametersFromMap(outputs)
    }

    fun updateParameters(parameters: Array<ByteBuffer>): Array<ByteBuffer> {
        val outputs = emptyParameterMap()
        runSignatureLocked(parametersToMap(parameters), outputs, "restore")
        return parametersFromMap(outputs)
    }

    /**
     * Thread-safe, and block operations on [trainingSamples].
     */
    fun fit(
        epochs: Int = 1, batchSize: Int = 32, lossCallback: ((List<Float>) -> Unit)? = null
    ): List<Double> {
        Log.d(TAG, "Starting to train for $epochs epochs with batch size $batchSize.")
        // Obtain write lock to prevent training samples from being modified.
        return trainSampleLock.writeLock().withLock {
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
     * [logits] is the pre-allocated output.
     */
    fun inference(x: X, logits: Y) {
        val inputs = mapOf("x" to x)
        val outputs = mapOf("logits" to logits)
        runSignatureLocked(inputs, outputs, "infer")
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
            val bottlenecks = it.map { sample -> sample.bottleneck }
            val labels = it.map { sample -> sample.label }
            training(spec.convertX(bottlenecks), spec.convertY(labels))
        }.toList()
    }

    /**
     * Not thread-safe because we assume [trainSampleLock] is already acquired.
     */
    private fun training(
        bottlenecks: Array<X>, labels: Array<Y>
    ): Float {
        val inputs = mapOf<String, Any>(
            "x" to bottlenecks,
            "y" to labels,
        )
        val loss = FloatBuffer.allocate(1)
        val outputs = mapOf<String, Any>(
            "loss" to loss,
        )
        runSignatureLocked(inputs, outputs, "train")
        return loss.get(0)
    }


    /**
     * Constructs an iterator that iterates over training sample batches.
     */
    private fun trainingBatches(trainBatchSize: Int): Sequence<List<Sample<X, Y>>> {
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

    private fun runSignatureLocked(
        inputs: Map<String, Any>,
        outputs: Map<String, Any>,
        signatureKey: String
    ) {
        interpreterLock.withLock {
            interpreter.runSignature(inputs, outputs, signatureKey)
        }
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

data class Sample<X, Y>(val bottleneck: X, val label: Y)

/**
 * This map always returns `false` when `isEmpty` is called to bypass TFLite interpreter's
 * stupid empty check on the `input` argument of `runSignature`.
 */
class FakeNonEmptyMap<K, V> : HashMap<K, V>() {
    override fun isEmpty(): Boolean {
        return false
    }
}
