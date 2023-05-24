package flwr.android_client

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ConditionVariable
import android.util.Log
import android.util.Pair
import androidx.lifecycle.MutableLiveData
import org.tensorflow.lite.examples.transfer.api.ExternalModelLoader
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException

class FlowerClient constructor(val context: Context, modelDir: File) {
    private val isTraining = ConditionVariable()
    private val tlModel = run {
        val modelLoader = ExternalModelLoader(modelDir)
        TransferLearningModelWrapper(modelLoader)
    }
    private val lastLoss = MutableLiveData<Float>()
    private var localEpochs = 1

    val weights: Array<ByteBuffer>
        get() = tlModel.model.parameters

    fun fit(weights: Array<ByteBuffer?>?, epochs: Int): Pair<Array<ByteBuffer>, Int> {
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

    private fun readAssetLines(fileName: String, call: (Int, String) -> Unit) {
        BufferedReader(InputStreamReader(context.assets.open(fileName))).useLines {
            it.forEachIndexed(call)
        }
    }

    @Throws
    fun loadData(device_id: Int) {
        readAssetLines("data/partition_${device_id - 1}_train.txt") { index, line ->
            if (index % 500 == 499) {
                Log.i(TAG, index.toString() + "th training image loaded")
            }
            addSample("data/$line", true)
        }
        readAssetLines("data/partition_${device_id - 1}_test.txt") { index, line ->
            if (index % 500 == 499) {
                Log.i(TAG, index.toString() + "th test image loaded")
            }
            addSample("data/$line", false)
        }
    }

    @Throws
    private fun addSample(photoPath: String, isTraining: Boolean) {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val bitmap = BitmapFactory.decodeStream(context.assets.open(photoPath), null, options)
        val sampleClass = getClass(photoPath)

        // get rgb equivalent and class
        val rgbImage = prepareImage(bitmap)

        // add to the list.
        try {
            tlModel.addSample(rgbImage, sampleClass, isTraining).get()
        } catch (e: ExecutionException) {
            throw RuntimeException("Failed to add sample to model", e.cause)
        } catch (e: InterruptedException) {
            // no-op
        }
    }

    fun getClass(path: String): String {
        return path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[2]
    }
}

private const val LOWER_BYTE_MASK = 0xFF
private const val TAG = "Flower"

/**
 * Normalizes a camera image to [0; 1], cropping it
 * to size expected by the model and adjusting for camera rotation.
 */
private fun prepareImage(bitmap: Bitmap?): FloatArray {
    val modelImageSize = TransferLearningModelWrapper.IMAGE_SIZE
    val normalizedRgb = FloatArray(modelImageSize * modelImageSize * 3)
    var nextIdx = 0
    for (y in 0 until modelImageSize) {
        for (x in 0 until modelImageSize) {
            val rgb = bitmap!!.getPixel(x, y)
            val r = (rgb shr 16 and LOWER_BYTE_MASK) * (1 / 255.0f)
            val g = (rgb shr 8 and LOWER_BYTE_MASK) * (1 / 255.0f)
            val b = (rgb and LOWER_BYTE_MASK) * (1 / 255.0f)
            normalizedRgb[nextIdx++] = r
            normalizedRgb[nextIdx++] = g
            normalizedRgb[nextIdx++] = b
        }
    }
    return normalizedRgb
}
