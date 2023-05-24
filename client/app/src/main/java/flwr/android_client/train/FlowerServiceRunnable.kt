package flwr.android_client.train

import android.util.Log
import com.google.protobuf.ByteString
import flwr.android_client.*
import io.grpc.stub.StreamObserver
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

class FlowerServiceRunnable {
    private lateinit var requestObserver: StreamObserver<ClientMessage>

    @Throws(RuntimeException::class)
    fun run(asyncStub: FlowerServiceGrpc.FlowerServiceStub, activity: MainActivity) {
        val finishLatch = CountDownLatch(1)
        requestObserver = asyncStub.join(
            object : StreamObserver<ServerMessage> {
                override fun onNext(msg: ServerMessage) {
                    try {
                        handleMessage(msg, activity)
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
    }

    @Throws
    private fun handleMessage(message: ServerMessage, activity: MainActivity) {
        val weights: Array<ByteBuffer>
        val clientMessage = if (message.hasGetParametersIns()) {
            Log.d(TAG, "Handling GetParameters")
            activity.setResultText("Handling GetParameters message from the server.")
            weights = activity.fc.weights
            weightsAsProto(weights)
        } else if (message.hasFitIns()) {
            Log.d(TAG, "Handling FitIns")
            activity.setResultText("Handling Fit request from the server.")
            val layers = message.fitIns.parameters.tensorsList
            val nLayers = layers.size
            assert(nLayers.toLong() == activity.train.model.n_layers)
            val epoch_config = message.fitIns.configMap.getOrDefault(
                "local_epochs",
                Scalar.newBuilder().setSint64(1).build()
            )!!
            val local_epochs = epoch_config.sint64.toInt()
            val newWeights = arrayOfNulls<ByteBuffer>(nLayers)
            for (i in 0 until nLayers) {
                val bytes = layers[i].toByteArray()
                newWeights[i] = ByteBuffer.wrap(bytes)
            }
            val outputs = activity.fc.fit(newWeights, local_epochs)
            fitResAsProto(outputs.first, outputs.second)
        } else if (message.hasEvaluateIns()) {
            Log.d(TAG, "Handling EvaluateIns")
            activity.setResultText("Handling Evaluate request from the server")
            val layers = message.evaluateIns.parameters.tensorsList
            val nLayers = layers.size
            assert(nLayers.toLong() == activity.train.model.n_layers)
            val newWeights = arrayOfNulls<ByteBuffer>(nLayers)
            for (i in 0 until nLayers) {
                val bytes = layers[i].toByteArray()
                newWeights[i] = ByteBuffer.wrap(bytes)
            }
            val inference = activity.fc.evaluate(newWeights)
            val loss = inference.first.first
            val accuracy = inference.first.second
            activity.setResultText("Test Accuracy after this round = $accuracy")
            val test_size = inference.second
            evaluateResAsProto(loss, test_size)
        } else {
            throw Error("Unreachable! Unknown client message")
        }
        requestObserver.onNext(clientMessage)
        activity.setResultText("Response sent to the server")
    }
}

private const val TAG = "Flower Service Runnable"

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
        .setNumExamples(testing_size.toLong())
        .build()
    return ClientMessage.newBuilder().setEvaluateRes(res).build()
}
