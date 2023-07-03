package org.eu.fedcampus.train

import io.grpc.ManagedChannel
import org.eu.fedcampus.train.db.TFLiteModel

sealed class TrainState<X : Any, Y : Any> {
    open val model: TFLiteModel
        get() = throw IllegalStateException("No model available")
    open val flowerClient: FlowerClient<X, Y>
        get() = throw IllegalStateException("No Flower Client available")
    open val channel: ManagedChannel
        get() = throw IllegalStateException("No channel available")

    class Initialized<X : Any, Y : Any> : TrainState<X, Y>()

    data class WithModel<X : Any, Y : Any>(override val model: TFLiteModel) : TrainState<X, Y>()

    data class Prepared<X : Any, Y : Any>(
        override val model: TFLiteModel,
        override val flowerClient: FlowerClient<X, Y>,
        override val channel: ManagedChannel
    ) : TrainState<X, Y>()

    data class Training<X : Any, Y : Any>(
        override val model: TFLiteModel,
        override val flowerClient: FlowerClient<X, Y>,
        val flowerServiceRunnable: FlowerServiceRunnable<X, Y>
    ) : TrainState<X, Y>()
}
