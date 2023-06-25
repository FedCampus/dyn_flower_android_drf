package org.eu.fedcampus.train

import android.util.Log
import com.google.protobuf.ByteString
import flwr.android_client.ClientMessage
import flwr.android_client.FlowerServiceGrpc
import flwr.android_client.Parameters
import flwr.android_client.Scalar
import flwr.android_client.ServerMessage
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

class FlowerServiceRunnable
/**
 * Start communication with Flower server and training in the background.
 */
@Throws constructor(
    asyncStub: FlowerServiceGrpc.FlowerServiceStub, val train: Train, val callback: (String) -> Unit
) {
    private val scope = MainScope()
    private val sampleSize: Int
        get() = train.flowerClient.trainingSamples.size
    val finishLatch = CountDownLatch(1)
    val requestObserver = asyncStub.join(object : StreamObserver<ServerMessage> {
        override fun onNext(msg: ServerMessage) {
            try {
                handleMessage(msg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onError(t: Throwable) {
            t.printStackTrace()
            finishLatch.countDown()
            Log.e(TAG, t.message!!)
        }

        override fun onCompleted() {
            finishLatch.countDown()
            Log.d(TAG, "Done")
        }
    })

    @Throws
    fun handleMessage(message: ServerMessage) {
        val clientMessage = if (message.hasGetParametersIns()) {
            handleGetParamsIns()
        } else if (message.hasFitIns()) {
            handleFitIns(message)
        } else if (message.hasEvaluateIns()) {
            handleEvaluateIns(message)
        } else {
            throw Error("Unreachable! Unknown client message")
        }
        requestObserver.onNext(clientMessage)
        callback("Response sent to the server")
    }

    @Throws
    fun handleGetParamsIns(): ClientMessage {
        Log.d(TAG, "Handling GetParameters")
        callback("Handling GetParameters message from the server.")
        return weightsAsProto(weightsByteBuffers())
    }

    @Throws
    fun handleFitIns(message: ServerMessage): ClientMessage {
        Log.d(TAG, "Handling FitIns")
        callback("Handling Fit request from the server.")
        val start = if (train.telemetry) System.currentTimeMillis() else null
        val layers = message.fitIns.parameters.tensorsList
        assert(layers.size.toLong() == train.model.n_layers)
        val epoch_config = message.fitIns.configMap.getOrDefault(
            "local_epochs", Scalar.newBuilder().setSint64(1).build()
        )!!
        val epochs = epoch_config.sint64.toInt()
        val newWeights = weightsFromLayers(layers)
        train.flowerClient.updateParameters(newWeights.toTypedArray())
        val losses = train.flowerClient.fit(epochs, lossCallback = { callback("Loss: $it.") })
        val msg = "Training done. Losses: $losses."
        Log.d(TAG, msg)
        callback(msg)
        if (start != null) {
            val end = System.currentTimeMillis()
            scope.launch { train.fitInsTelemetry(start, end) }
        }
        return fitResAsProto(weightsByteBuffers(), sampleSize)
    }

    @Throws
    fun handleEvaluateIns(message: ServerMessage): ClientMessage {
        Log.d(TAG, "Handling EvaluateIns")
        callback("Handling Evaluate request from the server")
        val start = if (train.telemetry) System.currentTimeMillis() else null
        val layers = message.evaluateIns.parameters.tensorsList
        assert(layers.size.toLong() == train.model.n_layers)
        val newWeights = weightsFromLayers(layers)
        train.flowerClient.updateParameters(newWeights.toTypedArray())
        val inference = train.flowerClient.evaluate()
        val loss = inference.first
        val accuracy = inference.second
        callback("Test Accuracy after this round = $accuracy")
        if (start != null) {
            val end = System.currentTimeMillis()
            scope.launch { train.evaluateInsTelemetry(start, end, loss, accuracy, sampleSize) }
        }
        return evaluateResAsProto(loss, sampleSize)
    }

    private fun weightsByteBuffers(): Array<ByteBuffer> {
        return train.flowerClient.weights().map { it.asReadOnlyBuffer() }.toTypedArray()
    }

    private fun weightsFromLayers(layers: List<ByteString>): List<ByteBuffer> {
        return layers.map { ByteBuffer.wrap(it.toByteArray()) }
    }

    companion object {
        private const val TAG = "Flower Service Runnable"
    }
}

fun weightsAsProto(weights: Array<ByteBuffer>): ClientMessage {
    val layers: MutableList<ByteString> = ArrayList()
    for (weight in weights) {
        layers.add(ByteString.copyFrom(weight))
    }
    val p = Parameters.newBuilder().addAllTensors(layers).setTensorType("ND").build()
    val res = ClientMessage.GetParametersRes.newBuilder().setParameters(p).build()
    return ClientMessage.newBuilder().setGetParametersRes(res).build()
}

fun fitResAsProto(weights: Array<ByteBuffer>, training_size: Int): ClientMessage {
    val layers: MutableList<ByteString> = ArrayList()
    for (weight in weights) {
        layers.add(ByteString.copyFrom(weight))
    }
    val p = Parameters.newBuilder().addAllTensors(layers).setTensorType("ND").build()
    val res =
        ClientMessage.FitRes.newBuilder().setParameters(p).setNumExamples(training_size.toLong())
            .build()
    return ClientMessage.newBuilder().setFitRes(res).build()
}

fun evaluateResAsProto(accuracy: Float, testing_size: Int): ClientMessage {
    val res = ClientMessage.EvaluateRes.newBuilder().setLoss(accuracy)
        .setNumExamples(testing_size.toLong()).build()
    return ClientMessage.newBuilder().setEvaluateRes(res).build()
}
