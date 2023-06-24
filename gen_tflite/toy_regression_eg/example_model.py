import tensorflow as tf


class ExampleModel(tf.Module):
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
        with tf.GradientTape() as tape:
            prediction = self.model(x)
            loss = self.model.loss(y, prediction)
        gradients = tape.gradient(loss, self.model.trainable_variables)
        self.model.optimizer.apply_gradients(
            zip(gradients, self.model.trainable_variables)
        )
        result = {"loss": loss}
        return result

    @tf.function(
        input_signature=[
            tf.TensorSpec([None, 2], tf.float32),
        ]
    )
    def infer(self, x):
        logits = self.model(x)
        return {"logits": logits}

    @tf.function(input_signature=[])
    def parameters(self):
        return [weight.read_value() for weight in self.model.weights]

    @tf.function
    def restore(self, **parameters):
        for index, weight in enumerate(self.model.weights):
            parameter = parameters[f"output_{index}"]
            weight.assign(parameter)
        return self.parameters()
