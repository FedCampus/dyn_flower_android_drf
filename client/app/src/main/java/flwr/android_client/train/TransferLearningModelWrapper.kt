package flwr.android_client.train

import android.os.ConditionVariable
import android.util.Pair
import org.tensorflow.lite.examples.transfer.api.TransferLearningModel
import org.tensorflow.lite.examples.transfer.api.TransferLearningModel.LossConsumer
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

/**
 * App-layer wrapper for [TransferLearningModel].
 *
 *
 * This wrapper allows to run training continuously, using start/stop API, in contrast to
 * run-once API of [TransferLearningModel].
 */
class TransferLearningModelWrapper constructor(val model: TransferLearningModel) : Closeable {
    private val shouldTrain = ConditionVariable()

    @Volatile
    private var lossConsumer: LossConsumer? = null

    @Throws
    fun train(epochs: Int) {
        Thread {
            shouldTrain.block()
            try {
                model.train(epochs, lossConsumer).get()
            } catch (e: ExecutionException) {
                throw RuntimeException("Exception occurred during model training", e.cause)
            } catch (e: InterruptedException) {
                // no-op
            }
        }.start()
    }

    /**
     * This method is thread-safe.
     */
    fun addSample(image: FloatArray, className: String, isTraining: Boolean): Future<Void> {
        return model.addSample(image, className, isTraining)
    }

    fun calculateTestStatistics(): Pair<Float, Float> {
        return model.testStatistics
    }

    /**
     * Start training the model continuously until [disableTraining][.disableTraining] is
     * called.
     *
     * @param lossConsumer callback that the loss values will be passed to.
     */
    fun enableTraining(lossConsumer: LossConsumer?) {
        this.lossConsumer = lossConsumer
        shouldTrain.open()
    }

    fun updateParameters(newParams: Array<ByteBuffer?>?) {
        model.updateParameters(newParams)
    }

    /**
     * Stops training the model.
     */
    fun disableTraining() {
        shouldTrain.close()
    }

    /**
     * Frees all model resources and shuts down all background threads.
     */
    override fun close() {
        model.close()
    }
}
