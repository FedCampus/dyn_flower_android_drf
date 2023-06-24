import tensorflow as tf

from .. import *


class ToyRegressionModel(BaseModel):
    def __init__(self, lr=0.000000001):
        self.model = tf.keras.Sequential(
            [
                tf.keras.layers.Dense(
                    units=1,
                    input_shape=[
                        2,
                    ],
                    name="regression",
                )
            ]
        )

        opt = tf.keras.optimizers.SGD(learning_rate=lr)

        self.model.compile(optimizer=opt, loss=tf.keras.losses.MeanSquaredError())

    # The `train` function takes a batch of input images and labels.
    @tf.function(
        input_signature=[
            tf.TensorSpec([None, 2], tf.float32),
            tf.TensorSpec([None, 1], tf.float32),
        ]
    )
    def train(self, x, y):
        return self.model.train_step((x, y))

    @tf.function(
        input_signature=[
            tf.TensorSpec([None, 2], tf.float32),
        ]
    )
    def infer(self, x):
        logits = self.model(x)
        return {"logits": logits}
