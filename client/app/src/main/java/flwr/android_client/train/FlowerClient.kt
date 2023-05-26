package flwr.android_client.train

import android.os.ConditionVariable
import android.util.Log
import android.util.Pair
import androidx.lifecycle.MutableLiveData
import org.eu.fedcampus.train.TransferLearningModelWrapper
import org.tensorflow.lite.examples.transfer.api.TransferLearningModel
import java.nio.ByteBuffer

/**
 * Construction of this class requires disk read.
 */
class FlowerClient(model: TransferLearningModel) {
    private val isTraining = ConditionVariable()
    val tlModel = TransferLearningModelWrapper(model)
    private val lastLoss = MutableLiveData<Float>()
    private var localEpochs = 1

    val weights: Array<ByteBuffer>
        get() = tlModel.model.parameters

    fun fit(
        weights: Array<ByteBuffer?>?,
        epochs: Int
    ): Pair<Array<ByteBuffer>, Int> {
        localEpochs = epochs
        tlModel.updateParameters(weights)
        isTraining.close()
        tlModel.train(localEpochs)
        tlModel.enableTraining { epoch: Int, newLoss: Float -> setLastLoss(epoch, newLoss) }
        Log.d(TAG, "Training enabled. Local Epochs = $localEpochs")
        isTraining.block()
        return Pair.create(this.weights, tlModel.model.size_Training)
    }

    fun evaluate(weights: Array<ByteBuffer?>?): Pair<Pair<Float, Float>, Int> {
        tlModel.updateParameters(weights)
        tlModel.disableTraining()
        return Pair.create(tlModel.calculateTestStatistics(), tlModel.model.size_Testing)
    }

    fun setLastLoss(epoch: Int, newLoss: Float) {
        if (epoch == localEpochs - 1) {
            Log.d(TAG, "Training finished after epoch = $epoch")
            lastLoss.postValue(newLoss)
            tlModel.disableTraining()
            isTraining.open()
        }
    }

    companion object {
        private const val TAG = "Flower Client"
    }
}
